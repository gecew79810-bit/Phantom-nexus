import { ToolExecutionError, ModelCallInput, ModelCallOutput } from './ToolTypes.js';
import { ModelRouter } from '../router/ModelRouter.js';
import { ChatMessage } from '../types/index.js';

export function createModelCallTool(router: ModelRouter) {
  return {
    async run(input: ModelCallInput): Promise<ModelCallOutput> {
      if (!input || typeof input.prompt !== 'string' || input.prompt.length === 0) {
        throw new ToolExecutionError('INVALID_INPUT', 'prompt is required');
      }

      // Build history as ChatMessage[] if provided
      const history: ChatMessage[] = (input.history || []).map((h: any) => ({ role: h.role, content: h.content, name: h.name }));

      try {
        const routeResult = await router.executeWithSmartFailover(input.prompt, history, {
          mode: input.mode,
          taskClassHint: input.taskClassHint,
          preferredProvider: input.preferredProvider,
          jsonSchema: input.jsonSchema,
          temperature: input.temperature,
          maxTokens: input.maxTokens,
        });

        return {
          text: routeResult.response.text,
          modelId: routeResult.selectedModel.id,
          provider: routeResult.selectedModel.provider,
          latencyMs: routeResult.response.latencyMs,
          failoverOccurred: routeResult.failoverOccurred,
          failoverHistory: routeResult.failoverHistory,
          promptTokens: routeResult.response.promptTokens,
          completionTokens: routeResult.response.completionTokens,
          totalTokens: routeResult.response.totalTokens,
        };
      } catch (err: unknown) {
        throw new ToolExecutionError('MODEL_CALL_ERROR', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
