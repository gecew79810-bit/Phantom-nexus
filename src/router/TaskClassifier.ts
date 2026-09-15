import { ModelCapabilities, TaskClass } from '../types/index.js';

export interface TaskRequirement {
  taskClass: TaskClass;
  requiredCapabilities: Partial<ModelCapabilities>;
  isComplex: boolean;
  requiresReview: boolean;
  estimatedTokens: number;
}

export class TaskClassifier {
  public static classify(
    prompt: string,
    historyText = '',
    hasImages = false,
    hasTools = false,
    requestedJsonSchema?: Record<string, unknown>
  ): TaskRequirement {
    const text = (prompt + ' ' + historyText).toLowerCase();

    if (hasImages) {
      return {
        taskClass: 'VISION',
        requiredCapabilities: { vision: true },
        isComplex: false,
        requiresReview: false,
        estimatedTokens: 2000,
      };
    }

    if (requestedJsonSchema && Object.keys(requestedJsonSchema).length > 0) {
      return {
        taskClass: 'STRUCTURED_JSON',
        requiredCapabilities: { structuredOutput: true },
        isComplex: false,
        requiresReview: false,
        estimatedTokens: 1500,
      };
    }

    if (hasTools) {
      return {
        taskClass: 'TOOL_CALLING',
        requiredCapabilities: { toolCalling: true },
        isComplex: true,
        requiresReview: false,
        estimatedTokens: 3000,
      };
    }

    // Coding and Debugging keywords
    const isDebugging =
      text.includes('debug') ||
      text.includes('stack trace') ||
      text.includes('exception') ||
      text.includes('fix bug') ||
      text.includes('error:') ||
      text.includes('crash');

    if (isDebugging) {
      return {
        taskClass: 'DEBUGGING',
        requiredCapabilities: { coding: true, reasoning: true },
        isComplex: true,
        requiresReview: true,
        estimatedTokens: 4000,
      };
    }

    // Repository analysis / long context / large document analysis
    if (
      text.length > 25000 ||
      text.includes('repository') ||
      text.includes('codebase') ||
      text.includes('entire project') ||
      text.includes('long context') ||
      text.includes('long-context')
    ) {
      return {
        taskClass: text.includes('repository') || text.includes('codebase') ? 'REPOSITORY_ANALYSIS' : 'LONG_CONTEXT_ANALYSIS',
        requiredCapabilities: { longContext: true, reasoning: true },
        isComplex: true,
        requiresReview: false,
        estimatedTokens: Math.max(8000, Math.round(text.length / 4)),
      };
    }

    const isCoding =
      text.includes('code') ||
      text.includes('function') ||
      text.includes('class ') ||
      text.includes('script') ||
      text.includes('typescript') ||
      text.includes('kotlin') ||
      text.includes('python') ||
      text.includes('react') ||
      text.includes('implement') ||
      text.includes('algorithm') ||
      text.includes('refactor');

    if (isCoding) {
      return {
        taskClass: 'CODING',
        requiredCapabilities: { coding: true },
        isComplex: true,
        requiresReview: true,
        estimatedTokens: 4000,
      };
    }

    if (text.includes('summarize') || text.includes('summary') || text.includes('tl;dr')) {
      return {
        taskClass: 'SUMMARIZATION',
        requiredCapabilities: { longContext: true },
        isComplex: false,
        requiresReview: false,
        estimatedTokens: 2000,
      };
    }

    if (text.includes('translate') || text.includes('अनुवाद') || text.includes('translation')) {
      return {
        taskClass: 'TRANSLATION',
        requiredCapabilities: { fastChat: true },
        isComplex: false,
        requiresReview: false,
        estimatedTokens: 1000,
      };
    }

    // Deep Reasoning / Math / Logic / Architecture planning
    const isReasoning =
      text.includes('why') ||
      text.includes('prove') ||
      text.includes('derive') ||
      text.includes('step-by-step') ||
      text.includes('plan') ||
      text.includes('architecture') ||
      text.includes('compare');

    if (isReasoning) {
      return {
        taskClass: 'REASONING',
        requiredCapabilities: { reasoning: true },
        isComplex: true,
        requiresReview: false,
        estimatedTokens: 3500,
      };
    }

    // Fast simple query (greetings, quick facts, short Q&A)
    const isFastSimple =
      prompt.trim().split(/\s+/).length <= 6 &&
      (text.includes('hi') ||
        text.includes('hello') ||
        text.includes('नमस्ते') ||
        text.includes('time') ||
        text.includes('date') ||
        text.includes('weather') ||
        text.includes('who are you'));

    if (isFastSimple) {
      return {
        taskClass: 'FAST_SIMPLE_QUERY',
        requiredCapabilities: { fastChat: true },
        isComplex: false,
        requiresReview: false,
        estimatedTokens: 400,
      };
    }

    return {
      taskClass: 'GENERAL_CONVERSATION',
      requiredCapabilities: { fastChat: true },
      isComplex: false,
      requiresReview: false,
      estimatedTokens: 1500,
    };
  }
}
