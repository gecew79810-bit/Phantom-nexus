import { AIProvider, ProviderCallOptions, ProviderResponse } from './AIProvider.js';
import {
  ChatMessage,
  ErrorClassification,
  ModelCapabilities,
  ModelRegistration,
  ProviderId,
} from '../types/index.js';
import { config } from '../config/index.js';

export class TokenRaProvider implements AIProvider {
  public readonly id: ProviderId = 'tokenra';
  public readonly name = 'TokenRa Gateway';

  private apiKey: string;
  private baseUrl: string;

  constructor(apiKey?: string, baseUrl?: string) {
    this.apiKey = apiKey ?? config.tokenraApiKey;
    this.baseUrl = (baseUrl ?? config.tokenraBaseUrl).replace(/\/+$/, '');
  }

  public setApiKey(key: string): void {
    this.apiKey = key;
  }

  public async chat(messages: ChatMessage[], options: ProviderCallOptions): Promise<ProviderResponse> {
    const startTime = Date.now();

    if (!this.apiKey) {
      throw new Error('TOKENRA_API_KEY_NOT_CONFIGURED: TokenRa API key is missing or invalid');
    }

    const model = options.modelId || config.tokenraModel || 'deepseek-chat';
    const endpoint = `${this.baseUrl}/chat/completions`;

    const openAiMessages: Array<{ role: string; content: string }> = [];

    if (options.systemInstruction) {
      openAiMessages.push({
        role: 'system',
        content: options.systemInstruction,
      });
    }

    for (const msg of messages) {
      openAiMessages.push({
        role: msg.role,
        content: msg.content,
      });
    }

    const requestPayload: Record<string, unknown> = {
      model,
      messages: openAiMessages,
      temperature: options.temperature ?? config.defaultTemperature,
      max_tokens: options.maxTokens ?? config.defaultMaxOutputTokens,
    };

    if (options.jsonSchema) {
      requestPayload['response_format'] = {
        type: 'json_object',
      };
    }

    const timeoutMs = options.timeoutMs ?? config.requestTimeoutMs;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify(requestPayload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - startTime;

      if (!response.ok) {
        const errText = await response.text().catch(() => '');
        throw new Error(`HTTP_${response.status}: ${errText.slice(0, 300)}`);
      }

      const data = (await response.json()) as {
        id?: string;
        choices?: Array<{
          message?: {
            content?: string;
          };
          finish_reason?: string;
        }>;
        usage?: {
          prompt_tokens?: number;
          completion_tokens?: number;
          total_tokens?: number;
        };
      };

      const choice = data.choices?.[0];
      const text = choice?.message?.content || '';

      if (!text || text.trim() === '') {
        if (choice?.finish_reason === 'length') {
          throw new Error('OUTPUT_LIMIT_FAILURE: TokenRa response reached max output tokens');
        }
        throw new Error('EMPTY_RESPONSE: TokenRa returned empty message content');
      }

      return {
        text,
        modelId: model,
        provider: 'tokenra',
        latencyMs,
        promptTokens: data.usage?.prompt_tokens,
        completionTokens: data.usage?.completion_tokens,
        totalTokens: data.usage?.total_tokens,
        raw: data,
      };
    } catch (err: unknown) {
      clearTimeout(timeoutId);
      throw err;
    }
  }

  public async healthCheck(modelId?: string): Promise<{ healthy: boolean; latencyMs: number; error?: string }> {
    const start = Date.now();
    try {
      if (!this.apiKey) {
        return { healthy: false, latencyMs: 0, error: 'TOKENRA_API_KEY not configured' };
      }
      const model = modelId || config.tokenraModel || 'deepseek-chat';
      // Fast single-token ping check
      const res = await this.chat([{ role: 'user', content: 'ping' }], {
        modelId: model,
        maxTokens: 5,
        timeoutMs: 8000,
      });
      return { healthy: !!res.text, latencyMs: Date.now() - start };
    } catch (e: unknown) {
      return {
        healthy: false,
        latencyMs: Date.now() - start,
        error: e instanceof Error ? e.message : String(e),
      };
    }
  }

  public getCapabilities(modelId: string): ModelCapabilities {
    const isReasoner = modelId.includes('reasoner') || modelId.includes('r1');
    const isKimi = modelId.includes('kimi');
    const isGlm = modelId.includes('glm');

    return {
      coding: true,
      reasoning: true,
      longContext: isKimi || isGlm,
      vision: isGlm,
      toolCalling: !isReasoner,
      structuredOutput: true,
      streaming: true,
      fastChat: !isReasoner,
    };
  }

  public async getModels(): Promise<ModelRegistration[]> {
    return [];
  }

  public estimateCost(modelId: string, promptTokens: number, completionTokens: number): number {
    return (promptTokens / 1000) * 0.00015 + (completionTokens / 1000) * 0.0003;
  }

  public validateResponse(response: ProviderResponse, jsonSchema?: Record<string, unknown>): boolean {
    if (!response.text || response.text.trim().length === 0) return false;
    if (jsonSchema) {
      try {
        JSON.parse(response.text);
        return true;
      } catch {
        return false;
      }
    }
    return true;
  }

  public handleError(error: unknown): ErrorClassification {
    const msg = error instanceof Error ? error.message : String(error);
    const lower = msg.toLowerCase();

    if (lower.includes('tokenra_api_key_not_configured') || lower.includes('http_401')) {
      return { httpCode: 401, category: 'UNAUTHORIZED', retryable: false, message: msg };
    }
    if (lower.includes('http_403')) {
      return { httpCode: 403, category: 'FORBIDDEN', retryable: false, message: msg };
    }
    if (lower.includes('http_408') || lower.includes('timeout') || lower.includes('aborted')) {
      return { httpCode: 408, category: 'TIMEOUT', retryable: true, message: msg };
    }
    if (lower.includes('http_409')) {
      return { httpCode: 409, category: 'CONFLICT', retryable: true, message: msg };
    }
    if (lower.includes('http_429') || lower.includes('rate limit') || lower.includes('quota')) {
      return { httpCode: 429, category: 'RATE_LIMIT', retryable: false, message: msg };
    }
    if (lower.includes('http_500')) {
      return { httpCode: 500, category: 'SERVER_ERROR', retryable: true, message: msg };
    }
    if (lower.includes('http_502')) {
      return { httpCode: 502, category: 'PROVIDER_UNAVAILABLE', retryable: true, message: msg };
    }
    if (lower.includes('http_503')) {
      return { httpCode: 503, category: 'PROVIDER_UNAVAILABLE', retryable: true, message: msg };
    }
    if (lower.includes('http_504')) {
      return { httpCode: 504, category: 'TIMEOUT', retryable: true, message: msg };
    }
    if (lower.includes('empty_response')) {
      return { category: 'EMPTY_RESPONSE', retryable: false, message: msg };
    }
    if (lower.includes('output_limit_failure')) {
      return { category: 'OUTPUT_LIMIT_FAILURE', retryable: false, message: msg };
    }
    if (lower.includes('enotfound') || lower.includes('econnrefused') || lower.includes('failed to fetch')) {
      return { category: 'CONNECTION_FAILURE', retryable: true, message: msg };
    }
    if (lower.includes('invalid model') || lower.includes('model not found')) {
      return { category: 'INVALID_MODEL', retryable: false, message: msg };
    }

    return { category: 'UNKNOWN', retryable: false, message: msg };
  }
}
