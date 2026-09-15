import {
  ChatMessage,
  ModelRegistration,
  ProviderId,
  RoutingMode,
  TaskClass,
} from '../types/index.js';
import { ModelRegistry } from '../registry/ModelRegistry.js';
import { ModelScoringEngine } from '../scoring/ModelScoringEngine.js';
import { TaskClassifier } from './TaskClassifier.js';
import { AIProvider, ProviderCallOptions, ProviderResponse } from '../providers/AIProvider.js';
import { GeminiProvider } from '../providers/GeminiProvider.js';
import { TokenRaProvider } from '../providers/TokenRaProvider.js';
import { ContextManager } from '../context/ContextManager.js';
import { config } from '../config/index.js';

export interface RouteExecutionResult {
  response: ProviderResponse;
  selectedModel: ModelRegistration;
  taskClass: TaskClass;
  failoverOccurred: boolean;
  failoverHistory: string[];
  attempts: number;
}

export class ModelRouter {
  private providers = new Map<ProviderId, AIProvider>();
  private scoringEngine: ModelScoringEngine;

  constructor(
    private registry: ModelRegistry,
    geminiProvider?: AIProvider,
    tokenRaProvider?: AIProvider
  ) {
    this.scoringEngine = new ModelScoringEngine(registry);
    this.providers.set('gemini', geminiProvider ?? new GeminiProvider());
    this.providers.set('tokenra', tokenRaProvider ?? new TokenRaProvider());
  }

  public registerProvider(provider: AIProvider): void {
    this.providers.set(provider.id, provider);
  }

  public getProvider(id: ProviderId): AIProvider | undefined {
    return this.providers.get(id);
  }

  /**
   * Determine and rank eligible models for a task
   */
  public selectCandidateModels(
    taskClass: TaskClass,
    routingMode: RoutingMode = 'AUTO',
    contextLength = 1000,
    requiresTools = false,
    requiresVision = false,
    preferredProvider?: ProviderId
  ): ModelRegistration[] {
    const allModels = this.registry.getEnabledModels();

    // Filter by hard constraints
    const eligible = allModels.filter((model) => {
      // Must have provider adapter
      if (!this.providers.has(model.provider)) return false;

      // Circuit breaker check
      const cb = this.registry.getCircuitBreaker(model.id);
      if (!cb.isAvailable()) {
        return false;
      }

      // Vision requirement
      if (requiresVision && !model.capabilities.vision) {
        return false;
      }

      // Tool calling requirement
      if (requiresTools && !model.capabilities.toolCalling) {
        return false;
      }

      // Context size requirement
      if (contextLength > model.contextLimit) {
        return false;
      }

      return true;
    });

    if (eligible.length === 0) {
      // Fallback to any enabled model if all circuit breakers are tripping
      return allModels.filter((m) => this.providers.has(m.provider));
    }

    // Score eligible models
    const scored = eligible.map((model) => {
      const score = this.scoringEngine.scoreModel(
        model,
        taskClass,
        routingMode,
        contextLength,
        requiresTools
      );

      let finalScore = score.finalScore;
      // Preferred provider boost
      if (preferredProvider && model.provider === preferredProvider) {
        finalScore += 25;
      }
      // Gemini preferred default boost when healthy
      if (!preferredProvider && model.isPrimary) {
        finalScore += 10;
      }

      return { model, finalScore };
    });

    // Sort descending by score
    scored.sort((a, b) => b.finalScore - a.finalScore);

    return scored.map((s) => s.model);
  }

  /**
   * Execute chat request with smart failover, bounded retries, and circuit breaker
   */
  public async executeWithSmartFailover(
    userPrompt: string,
    history: ChatMessage[] = [],
    options: {
      mode?: RoutingMode | undefined;
      taskClassHint?: TaskClass | undefined;
      preferredProvider?: ProviderId | undefined;
      jsonSchema?: Record<string, unknown> | undefined;
      temperature?: number | undefined;
      maxTokens?: number | undefined;
      hasImages?: boolean | undefined;
      hasTools?: boolean | undefined;
    } = {}
  ): Promise<RouteExecutionResult> {
    // 1. Classify task
    const historyText = history.map((h) => h.content).join(' ');
    const taskReq = options.taskClassHint
      ? {
          taskClass: options.taskClassHint,
          requiredCapabilities: {},
          isComplex: false,
          requiresReview: false,
          estimatedTokens: ContextManager.estimateTokens(userPrompt),
        }
      : TaskClassifier.classify(
          userPrompt,
          historyText,
          options.hasImages ?? false,
          options.hasTools ?? false,
          options.jsonSchema
        );

    const estContextTokens = ContextManager.estimateTokens(userPrompt + historyText);

    // 2. Select candidates in ranked order
    const candidates = this.selectCandidateModels(
      taskReq.taskClass,
      options.mode ?? 'AUTO',
      estContextTokens,
      options.hasTools ?? false,
      options.hasImages ?? false,
      options.preferredProvider
    );

    if (candidates.length === 0) {
      throw new Error(
        'NO_ELIGIBLE_AI_MODEL: No healthy AI model matches the required capabilities or active providers.'
      );
    }

    const failoverHistory: string[] = [];
    let totalAttempts = 0;
    let lastError: Error | null = null;

    // 3. Try candidates in order
    for (let candidateIndex = 0; candidateIndex < candidates.length; candidateIndex++) {
      const model = candidates[candidateIndex]!;
      const provider = this.providers.get(model.provider);

      if (!provider) continue;

      // Prepare context bounded to this model's limits
      const contextMessages = ContextManager.prepareContext(
        userPrompt,
        history,
        model.contextLimit,
        options.maxTokens ?? config.defaultMaxOutputTokens
      );

      const callOpts: ProviderCallOptions = {
        modelId: model.id,
        temperature: options.temperature,
        maxTokens: options.maxTokens,
        jsonSchema: options.jsonSchema,
      };

      // Bounded retry per provider/model
      const maxRetries = config.maxRetriesPerProvider;
      let attempt = 0;

      while (attempt <= maxRetries) {
        totalAttempts++;
        try {
          const response = await provider.chat(contextMessages, callOpts);

          // Validate output
          if (!provider.validateResponse(response, options.jsonSchema)) {
            throw new Error('MALFORMED_RESPONSE: Output failed schema or content validation');
          }

          // Record success in registry & circuit breaker
          this.registry.recordSuccess(model.id, response.latencyMs);

          return {
            response,
            selectedModel: model,
            taskClass: taskReq.taskClass,
            failoverOccurred: candidateIndex > 0,
            failoverHistory,
            attempts: totalAttempts,
          };
        } catch (err: unknown) {
          const classification = provider.handleError(err);
          const isRateLimit = classification.category === 'RATE_LIMIT';
          const isTimeout = classification.category === 'TIMEOUT';

          this.registry.recordFailure(model.id, isRateLimit, isTimeout);

          const failureMsg = `[${model.provider}:${model.id}] failed: ${classification.category} (${classification.message})`;
          failoverHistory.push(failureMsg);
          lastError = err instanceof Error ? err : new Error(String(err));

          // If retryable and attempt < maxRetries, retry once
          if (classification.retryable && attempt < maxRetries) {
            attempt++;
            continue;
          }

          // Otherwise break out and proceed to next candidate fallback model!
          break;
        }
      }
    }

    // If all candidates failed
    throw new Error(
      `ALL_MODELS_FAILED: Failover exhausted across ${candidates.length} models. Details: ${failoverHistory.join(' -> ')}`
    );
  }
}
