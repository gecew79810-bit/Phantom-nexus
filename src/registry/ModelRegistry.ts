import {
  CircuitState,
  ModelCapabilities,
  ModelRegistration,
  ModelRuntimeMetrics,
  ProviderId,
} from '../types/index.js';
import { CircuitBreaker } from '../circuit/CircuitBreaker.js';
import { config } from '../config/index.js';

export class ModelRegistry {
  private models = new Map<string, ModelRegistration>();
  private metrics = new Map<string, ModelRuntimeMetrics>();
  private circuitBreakers = new Map<string, CircuitBreaker>();

  constructor() {
    this.initializeDefaultRegistry();
  }

  private initializeDefaultRegistry(): void {
    // Primary: Google Gemini 2.5 Flash / 1.5 Pro
    this.registerModel({
      id: config.geminiModel,
      provider: 'gemini',
      displayName: 'Google Gemini Flash (Primary)',
      type: 'multimodal_general',
      capabilities: {
        coding: true,
        reasoning: true,
        longContext: true,
        vision: true,
        toolCalling: true,
        structuredOutput: true,
        streaming: true,
        fastChat: true,
      },
      contextLimit: 1048576,
      outputLimit: 8192,
      costPer1kInputTokens: 0.0001,
      costPer1kOutputTokens: 0.0004,
      priority: 100,
      isEnabled: true,
      isPrimary: true,
      isFallback: false,
    });

    // TokenRa Fallback Model: deepseek-chat (Coding, fast reasoning)
    this.registerModel({
      id: config.tokenraModel || 'deepseek-chat',
      provider: 'tokenra',
      displayName: 'DeepSeek Chat (TokenRa)',
      type: 'general_reasoning',
      capabilities: {
        coding: true,
        reasoning: true,
        longContext: true,
        vision: false,
        toolCalling: true,
        structuredOutput: true,
        streaming: true,
        fastChat: true,
      },
      contextLimit: 65536,
      outputLimit: 4096,
      costPer1kInputTokens: 0.00014,
      costPer1kOutputTokens: 0.00028,
      priority: 90,
      isEnabled: true,
      isPrimary: false,
      isFallback: true,
    });

    // TokenRa Alternate Fallback: deepseek-reasoner (Deep math & coding reasoning)
    this.registerModel({
      id: 'deepseek-reasoner',
      provider: 'tokenra',
      displayName: 'DeepSeek Reasoner (TokenRa)',
      type: 'deep_reasoning',
      capabilities: {
        coding: true,
        reasoning: true,
        longContext: true,
        vision: false,
        toolCalling: false,
        structuredOutput: true,
        streaming: true,
        fastChat: false,
      },
      contextLimit: 65536,
      outputLimit: 8192,
      costPer1kInputTokens: 0.00055,
      costPer1kOutputTokens: 0.00219,
      priority: 85,
      isEnabled: true,
      isPrimary: false,
      isFallback: true,
    });

    // TokenRa Alternate Fallback: kimi-k1.5 (High Context, Long document analysis)
    this.registerModel({
      id: 'kimi-k1.5',
      provider: 'tokenra',
      displayName: 'Moonshot Kimi (TokenRa)',
      type: 'long_context',
      capabilities: {
        coding: true,
        reasoning: true,
        longContext: true,
        vision: false,
        toolCalling: true,
        structuredOutput: true,
        streaming: true,
        fastChat: true,
      },
      contextLimit: 128000,
      outputLimit: 4096,
      costPer1kInputTokens: 0.0008,
      costPer1kOutputTokens: 0.0016,
      priority: 80,
      isEnabled: true,
      isPrimary: false,
      isFallback: true,
    });

    // TokenRa Alternate Fallback: glm-4-plus (Multimodal & agentic tool calling)
    this.registerModel({
      id: 'glm-4-plus',
      provider: 'tokenra',
      displayName: 'GLM 4 Plus (TokenRa)',
      type: 'agentic_tooling',
      capabilities: {
        coding: true,
        reasoning: true,
        longContext: true,
        vision: true,
        toolCalling: true,
        structuredOutput: true,
        streaming: true,
        fastChat: true,
      },
      contextLimit: 128000,
      outputLimit: 4096,
      costPer1kInputTokens: 0.001,
      costPer1kOutputTokens: 0.002,
      priority: 75,
      isEnabled: true,
      isPrimary: false,
      isFallback: true,
    });
  }

  public registerModel(model: ModelRegistration): void {
    this.models.set(model.id, model);

    if (!this.metrics.has(model.id)) {
      this.metrics.set(model.id, {
        totalCalls: 0,
        successCalls: 0,
        errorCalls: 0,
        rateLimitCount: 0,
        timeoutCount: 0,
        consecutiveFailures: 0,
        averageLatencyMs: 0,
        circuitState: 'HEALTHY',
      });
    }

    if (!this.circuitBreakers.has(model.id)) {
      this.circuitBreakers.set(
        model.id,
        new CircuitBreaker(`model:${model.id}`, {
          failureThreshold: config.circuitFailureThreshold,
          openTimeoutMs: config.circuitOpenMs,
        })
      );
    }
  }

  public getModel(modelId: string): ModelRegistration | undefined {
    return this.models.get(modelId);
  }

  public getAllModels(): ModelRegistration[] {
    return Array.from(this.models.values());
  }

  public getEnabledModels(): ModelRegistration[] {
    return Array.from(this.models.values()).filter((m) => m.isEnabled);
  }

  public getPrimaryModel(): ModelRegistration | undefined {
    return Array.from(this.models.values()).find((m) => m.isPrimary && m.isEnabled);
  }

  public getCircuitBreaker(modelId: string): CircuitBreaker {
    let cb = this.circuitBreakers.get(modelId);
    if (!cb) {
      cb = new CircuitBreaker(`model:${modelId}`, {
        failureThreshold: config.circuitFailureThreshold,
        openTimeoutMs: config.circuitOpenMs,
      });
      this.circuitBreakers.set(modelId, cb);
    }
    return cb;
  }

  public getMetrics(modelId: string): ModelRuntimeMetrics {
    let m = this.metrics.get(modelId);
    if (!m) {
      m = {
        totalCalls: 0,
        successCalls: 0,
        errorCalls: 0,
        rateLimitCount: 0,
        timeoutCount: 0,
        consecutiveFailures: 0,
        averageLatencyMs: 0,
        circuitState: 'HEALTHY',
      };
      this.metrics.set(modelId, m);
    }
    const cb = this.getCircuitBreaker(modelId);
    m.circuitState = cb.getState();
    return m;
  }

  public recordSuccess(modelId: string, latencyMs: number): void {
    const m = this.getMetrics(modelId);
    m.totalCalls++;
    m.successCalls++;
    m.consecutiveFailures = 0;
    m.lastSuccessTimestamp = Date.now();
    // Rolling latency average
    m.averageLatencyMs = m.averageLatencyMs === 0 ? latencyMs : Math.round(m.averageLatencyMs * 0.8 + latencyMs * 0.2);

    const cb = this.getCircuitBreaker(modelId);
    cb.recordSuccess();
    m.circuitState = cb.getState();
  }

  public recordFailure(modelId: string, isRateLimit = false, isTimeout = false): void {
    const m = this.getMetrics(modelId);
    m.totalCalls++;
    m.errorCalls++;
    m.consecutiveFailures++;
    m.lastFailureTimestamp = Date.now();
    if (isRateLimit) m.rateLimitCount++;
    if (isTimeout) m.timeoutCount++;

    const cb = this.getCircuitBreaker(modelId);
    cb.recordFailure();
    m.circuitState = cb.getState();
  }

  public setModelEnabled(modelId: string, isEnabled: boolean): boolean {
    const model = this.models.get(modelId);
    if (!model) return false;
    model.isEnabled = isEnabled;
    return true;
  }

  public setPrimaryModel(modelId: string): boolean {
    const model = this.models.get(modelId);
    if (!model) return false;
    for (const m of this.models.values()) {
      m.isPrimary = m.id === modelId;
    }
    return true;
  }

  public resetCircuit(modelId: string): boolean {
    const cb = this.circuitBreakers.get(modelId);
    if (cb) {
      cb.reset();
      const m = this.getMetrics(modelId);
      m.circuitState = 'HEALTHY';
      m.consecutiveFailures = 0;
      return true;
    }
    return false;
  }
}

export const defaultModelRegistry = new ModelRegistry();
