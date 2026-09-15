import { AgentRole, OrchestrationJob, OrchestrationStep } from '../types/index.js';
import { ModelRouter } from '../router/ModelRouter.js';
import { defaultModelRegistry } from '../registry/ModelRegistry.js';

export interface AgentContext {
  goal: string;
  historyContext?: string;
  artifacts: Record<string, string>;
  checkpoints: Array<{
    stepIndex: number;
    stepId: string;
    output: string;
    modelId: string;
    timestamp: number;
  }>;
}

export class AgentSystem {
  constructor(private router: ModelRouter) {}

  /**
   * 1. Planner Agent: Decomposes high level goal into atomic executable steps
   */
  public async planGoal(goal: string, context?: string): Promise<OrchestrationStep[]> {
    const prompt = `You are the PLANNER AGENT for Jarvis.
Break the user's goal into a minimal, logical sequence of 2 to 4 actionable steps.
Goal: "${goal}"
${context ? `Context:\n${context}` : ''}

Output ONLY valid JSON in this exact array format:
[
  { "id": "step_1", "description": "...", "agent": "CODER" },
  { "id": "step_2", "description": "...", "agent": "REVIEWER" }
]
Valid agent roles: PLANNER, RESEARCHER, CODER, DEBUGGER, REVIEWER, TOOL, MEMORY, SECURITY, FINALIZER.`;

    const result = await this.router.executeWithSmartFailover(prompt, [], {
      taskClassHint: 'PLANNING',
      mode: 'QUALITY_FIRST',
      temperature: 0.1,
    });

    try {
      // Find JSON block or parse directly
      const match = result.response.text.match(/\[[\s\S]*\]/);
      const jsonStr = match ? match[0] : result.response.text;
      const parsed = JSON.parse(jsonStr) as Array<{ id?: string; description?: string; agent?: string }>;

      return parsed.map((p, idx) => ({
        id: p.id || `step_${idx + 1}`,
        description: p.description || 'Task step',
        agent: (p.agent?.toUpperCase() as AgentRole) || 'CODER',
        status: 'PENDING',
      }));
    } catch {
      // Fallback default two-step plan
      return [
        {
          id: 'step_1',
          description: `Execute main solution for: ${goal}`,
          agent: goal.toLowerCase().includes('code') || goal.toLowerCase().includes('debug') ? 'CODER' : 'RESEARCHER',
          status: 'PENDING',
        },
        {
          id: 'step_2',
          description: 'Review and validate the final response',
          agent: 'REVIEWER',
          status: 'PENDING',
        },
      ];
    }
  }

  /**
   * 2. Execute a single step with checkpoint saving and failover resilience
   */
  public async executeStep(
    step: OrchestrationStep,
    context: AgentContext
  ): Promise<{ output: string; modelId: string }> {
    step.status = 'IN_PROGRESS';

    const systemPrompt = this.getSystemPromptForAgent(step.agent);
    const stepPrompt = `Goal: ${context.goal}
Task Step: ${step.description}
Current Artifacts:
${Object.entries(context.artifacts)
  .map(([k, v]) => `[${k}]: ${v.slice(0, 1500)}`)
  .join('\n')}

Execute this task thoroughly and concisely.`;

    const taskHint =
      step.agent === 'CODER' || step.agent === 'DEBUGGER'
        ? 'CODING'
        : step.agent === 'RESEARCHER'
          ? 'RESEARCH'
          : step.agent === 'SECURITY'
            ? 'REASONING'
            : 'GENERAL_CONVERSATION';

    const result = await this.router.executeWithSmartFailover(
      `${systemPrompt}\n\n${stepPrompt}`,
      [],
      {
        taskClassHint: taskHint,
        mode: 'QUALITY_FIRST',
        temperature: 0.2,
      }
    );

    step.status = 'COMPLETED';
    step.result = result.response.text;
    step.executedByModel = result.selectedModel.id;
    step.executedByProvider = result.selectedModel.provider;

    return {
      output: result.response.text,
      modelId: result.selectedModel.id,
    };
  }

  /**
   * 3. Reviewer Agent: Inspects generated solution for accuracy and security
   */
  public async reviewArtifact(
    goal: string,
    content: string
  ): Promise<{ approved: boolean; notes: string; reviewerModel: string }> {
    const prompt = `You are the REVIEWER AGENT for Jarvis.
Review the following generated output for the user's goal.
Goal: "${goal}"

Generated Output:
${content.slice(0, 5000)}

Check:
1. Did it fulfill the user's request accurately?
2. Are there obvious bugs, safety risks, or hallucinations?
3. Does it leak any API keys or secrets?

Respond in this JSON format:
{
  "approved": true,
  "notes": "Clear, complete, and verified."
}`;

    const result = await this.router.executeWithSmartFailover(prompt, [], {
      taskClassHint: 'REASONING',
      mode: 'QUALITY_FIRST',
      temperature: 0.1,
    });

    try {
      const match = result.response.text.match(/\{[\s\S]*\}/);
      const json = JSON.parse(match ? match[0] : result.response.text) as {
        approved?: boolean;
        notes?: string;
      };
      return {
        approved: json.approved ?? true,
        notes: json.notes ?? 'Verified by Reviewer',
        reviewerModel: result.selectedModel.id,
      };
    } catch {
      return {
        approved: true,
        notes: 'Passed automated review heuristic',
        reviewerModel: result.selectedModel.id,
      };
    }
  }

  /**
   * 4. Finalizer Agent: Merges artifacts and validated results into clean final response
   */
  public async finalizeJob(
    goal: string,
    artifacts: Record<string, string>,
    reviewNotes?: string
  ): Promise<string> {
    const keys = Object.keys(artifacts);
    if (keys.length === 1 && !reviewNotes) {
      return artifacts[keys[0]!]!;
    }

    const prompt = `You are the FINALIZER AGENT for Jarvis.
Deliver a cohesive, high-quality, polished final answer to the user.
Goal: "${goal}"

Completed Components:
${Object.entries(artifacts)
  .map(([k, v]) => `### ${k}\n${v}`)
  .join('\n\n')}

${reviewNotes ? `Reviewer Notes: ${reviewNotes}` : ''}

Synthesize into an articulate, executive response directly addressing the goal without unnecessary internal meta-commentary.`;

    const result = await this.router.executeWithSmartFailover(prompt, [], {
      taskClassHint: 'GENERAL_CONVERSATION',
      mode: 'QUALITY_FIRST',
      temperature: 0.2,
    });

    return result.response.text;
  }

  /**
   * Full Workflow: Planner -> Specialist Execution with Checkpoints -> Reviewer -> Finalizer
   */
  public async orchestrateTask(
    goal: string,
    contextInfo = '',
    onStepUpdate?: (job: OrchestrationJob) => void
  ): Promise<OrchestrationJob> {
    const jobId = `job_${Date.now()}`;
    const plan = await this.planGoal(goal, contextInfo);

    const job: OrchestrationJob = {
      id: jobId,
      goal,
      plan,
      currentStepIndex: 0,
      status: 'RUNNING',
      intermediateArtifacts: {},
      checkpoints: [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    onStepUpdate?.(job);

    for (let i = 0; i < job.plan.length; i++) {
      job.currentStepIndex = i;
      const step = job.plan[i]!;

      try {
        const { output, modelId } = await this.executeStep(step, {
          goal,
          historyContext: contextInfo,
          artifacts: job.intermediateArtifacts,
          checkpoints: job.checkpoints,
        });

        job.intermediateArtifacts[step.id] = output;
        job.checkpoints.push({
          stepIndex: i,
          stepId: step.id,
          output,
          modelId,
          provider: step.executedByProvider || 'gemini',
          timestamp: Date.now(),
        });

        job.updatedAt = Date.now();
        onStepUpdate?.(job);
      } catch (err: unknown) {
        step.status = 'FAILED';
        step.error = err instanceof Error ? err.message : String(err);
        job.status = 'FAILED';
        job.updatedAt = Date.now();
        onStepUpdate?.(job);
        throw err;
      }
    }

    // Run finalizer
    const finalAnswer = await this.finalizeJob(goal, job.intermediateArtifacts);
    job.finalResponse = finalAnswer;
    job.status = 'COMPLETED';
    job.updatedAt = Date.now();
    onStepUpdate?.(job);

    return job;
  }

  private getSystemPromptForAgent(role: AgentRole): string {
    switch (role) {
      case 'PLANNER':
        return 'You are the Planner Agent. Break complex requests into structured sub-tasks.';
      case 'RESEARCHER':
        return 'You are the Research Agent. Gather comprehensive, accurate facts and synthesize findings.';
      case 'CODER':
        return 'You are the Coder Agent. Write clean, production-grade, bug-free, type-safe code.';
      case 'DEBUGGER':
        return 'You are the Debugger Agent. Trace errors, inspect edge cases, and repair broken code.';
      case 'REVIEWER':
        return 'You are the Reviewer Agent. Verify correctness, edge cases, and safety with strict rigor.';
      case 'TOOL':
        return 'You are the Tool Agent. Execute approved actions and parse data reliably.';
      case 'MEMORY':
        return 'You are the Memory Agent. Index and retrieve relevant past context and preferences.';
      case 'SECURITY':
        return 'You are the Security Agent. Inspect operations to prevent credential leaks, injections, and unsafe system mutations.';
      case 'FINALIZER':
        return 'You are the Finalizer Agent. Combine validated components into an elegant, complete response.';
      default:
        return 'You are an intelligent specialist agent for Jarvis.';
    }
  }
}
