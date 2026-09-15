import {
  ChatRequest,
  ChatResponse,
  ModelRegistration,
  OrchestrationJob,
  ProviderId,
  RoutingMode,
  TaskClass,
} from '../types/index.js';
import { defaultModelRegistry, ModelRegistry } from '../registry/ModelRegistry.js';
import { ModelRouter } from '../router/ModelRouter.js';
import { AgentSystem } from '../agents/AgentSystem.js';
import { AIProvider } from '../providers/AIProvider.js';

export interface OrchestratorLogEntry {
  requestId: string;
  taskId?: string | undefined;
  timestamp: number;
  taskClass: TaskClass;
  selectedModel: string;
  provider: string;
  latencyMs: number;
  status: 'SUCCESS' | 'FAILOVER' | 'ERROR';
  failoverOccurred: boolean;
  failoverHistory: string[];
  retryCount: number;
  promptTokens?: number | undefined;
  completionTokens?: number | undefined;
  errorCategory?: string | undefined;
}

export class JarvisOrchestrator {
  private router: ModelRouter;
  private agentSystem: AgentSystem;
  private structuredLogs: OrchestratorLogEntry[] = [];

  constructor(
    private registry: ModelRegistry = defaultModelRegistry,
    geminiProvider?: AIProvider,
    tokenRaProvider?: AIProvider
  ) {
    this.router = new ModelRouter(registry, geminiProvider, tokenRaProvider);
    this.agentSystem = new AgentSystem(this.router);
  }

  public getRegistry(): ModelRegistry {
    return this.registry;
  }

  public getRouter(): ModelRouter {
    return this.router;
  }

  public getAgentSystem(): AgentSystem {
    return this.agentSystem;
  }

  public getLogs(limit = 50): OrchestratorLogEntry[] {
    return this.structuredLogs.slice(-limit);
  }

  /**
   * Main conversational and task execution turn
   */
  public async processRequest(request: ChatRequest): Promise<ChatResponse> {
    const requestId = `req_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`;
    const startTime = Date.now();

    try {
      // Step 1 - 5: Task analysis, capability check, model selection, execution with smart failover
      const routeResult = await this.router.executeWithSmartFailover(
        request.message,
        request.history ?? [],
        {
          mode: request.mode ?? 'AUTO',
          taskClassHint: request.taskClassHint,
          preferredProvider: request.preferredProvider,
          jsonSchema: request.jsonSchema,
          temperature: request.temperature,
          maxTokens: request.maxTokens,
        }
      );

      let reviewResult: { approved: boolean; notes: string; reviewerModel: string } | undefined;

      // Step 6: Optional Secondary Model Review
      if (request.requireReview) {
        reviewResult = await this.agentSystem.reviewArtifact(
          request.message,
          routeResult.response.text
        );
      }

      const totalLatency = Date.now() - startTime;

      // Observability log (Safe: never prints secrets or sensitive keys)
      this.structuredLogs.push({
        requestId,
        timestamp: Date.now(),
        taskClass: routeResult.taskClass,
        selectedModel: routeResult.selectedModel.id,
        provider: routeResult.selectedModel.provider,
        latencyMs: totalLatency,
        status: routeResult.failoverOccurred ? 'FAILOVER' : 'SUCCESS',
        failoverOccurred: routeResult.failoverOccurred,
        failoverHistory: routeResult.failoverHistory,
        retryCount: routeResult.attempts - 1,
        promptTokens: routeResult.response.promptTokens,
        completionTokens: routeResult.response.completionTokens,
      });

      return {
        text: routeResult.response.text,
        modelId: routeResult.selectedModel.id,
        provider: routeResult.selectedModel.provider,
        taskClass: routeResult.taskClass,
        latencyMs: totalLatency,
        failoverOccurred: routeResult.failoverOccurred,
        failoverHistory: routeResult.failoverHistory,
        reviewResult,
        usage: {
          promptTokens: routeResult.response.promptTokens,
          completionTokens: routeResult.response.completionTokens,
          totalTokens: routeResult.response.totalTokens,
        },
      };
    } catch (err: unknown) {
      const totalLatency = Date.now() - startTime;
      const errMsg = err instanceof Error ? err.message : String(err);

      this.structuredLogs.push({
        requestId,
        timestamp: Date.now(),
        taskClass: 'GENERAL_CONVERSATION',
        selectedModel: 'NONE',
        provider: 'NONE',
        latencyMs: totalLatency,
        status: 'ERROR',
        failoverOccurred: true,
        failoverHistory: [errMsg],
        retryCount: 0,
        errorCategory: errMsg.slice(0, 50),
      });

      throw err;
    }
  }

  /**
   * Orchestrate complex multi-agent execution with checkpoints
   */
  public async executeComplexGoal(
    goal: string,
    contextInfo = ''
  ): Promise<OrchestrationJob> {
    return this.agentSystem.orchestrateTask(goal, contextInfo);
  }
}
