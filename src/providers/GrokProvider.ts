import { AIProvider, ProviderCallOptions, ProviderResponse } from './AIProvider.js';
import {
  ChatMessage,
  ErrorClassification,
  ModelCapabilities,
  ModelRegistration,
  ProviderId,
} from '../types/index.js';
import { config } from '../config/index.js';

/**
 * GrokProvider — concrete implementation targeting xAI (Grok) OpenAI-compatible chat endpoint
 * Uses POST ${baseUrl}/v1/chat/completions with Bearer auth.
 * Configurable via GROK_API_KEY and GROK_BASE_URL and GROK_MODEL.
 */
export class GrokProvider implements AIProvider {
  public readonly id: ProviderId = 'grok';
  public readonly name = 'Grok / xAI';

  private apiKey: string;
  private baseUrl: string;

  constructor(apiKey?: string, baseUrl?: string) {
    this.apiKey = apiKey ?? (config as any).grokApiKey ?? '';
    this.baseUrl = baseUrl ?? (config as any).grokBaseUrl ?? 'https://api.x.ai/v1';
  }

  public setApiKey(key: string): void {
    this.apiKey = key;
  }

  private buildChatEndpoint(): string {
    return `${this.baseUrl.replace(/\/+$/, '')}/chat/completions`;
  }

  public async chat(messages: ChatMessage[], options: ProviderCallOptions): Promise<ProviderResponse> {
    const startTime = Date.now();

    if (!this.apiKey) {
      throw new Error('GROK_API_KEY_NOT_CONFIGURED: Grok API key is missing or invalid');
    }

    const model = options.modelId || (config as any).grokModel || 'grok-1';
    const endpoint = this.buildChatEndpoint();

    const body: Record<string, unknown> = {
      model,
      messages: messages.map((m) => ({ role: m.role, content: m.content })),
      temperature: options.temperature ?? config.defaultTemperature,
      max_tokens: options.maxTokens ?? config.defaultMaxOutputTokens,
    };

    // If user requested structured JSON output via jsonSchema, prefer the responses endpoint style
    if (options.jsonSchema) {
      // Some xAI deployments support response_format; include a hint but still use chat/completions
      // The test suite mocks chat responses as OpenAI-style choices.
      // Keep this minimal so we don't send unrecognized fields to providers that may reject them.
      (body as any).response_format = { type: 'json' };
    }

    const timeoutMs = (options as any).timeoutMs ?? config.requestTimeoutMs;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - startTime;

      if (!res.ok) {
        const text = await res.text().catch(() => '');
        // Normalize common HTTP errors
        const msg = `HTTP_${res.status}: ${text.slice(0, 300)}`;
        throw new Error(msg);
      }

      const data = await res.json().catch(() => ({}));

      // Parse OpenAI-style chat/completions response
      let text = '';
      if (data?.choices && Array.isArray(data.choices) && data.choices[0]) {
        const choice = data.choices[0];
        if (choice?.message?.content) {
          text = String(choice.message.content);
        } else if (typeof choice?.text === 'string') {
          text = choice.text;
        }
      }

      // Fallback: some xAI responses include output_text
      if (!text && (data as any).output_text) {
        text = typeof (data as any).output_text === 'string' ? (data as any).output_text : JSON.stringify((data as any).output_text);
      }

      if (!text) {
        // If still empty, stringify the body so callers can inspect raw
        text = JSON.stringify(data);
      }

      return {
        text: String(text),
        modelId: model,
        provider: 'grok',
        latencyMs,
        raw: data,
      };
    } catch (err) {
      clearTimeout(timeoutId);
      const msg = err instanceof Error ? err.message : String(err);
      // rethrow normalized error
      throw new Error(msg);
    }
  }

  public async healthCheck(): Promise<{ healthy: boolean; latencyMs: number; error?: string | undefined }> {
    const start = Date.now();
    if (!this.apiKey) return { healthy: false, latencyMs: Date.now() - start, error: 'NO_API_KEY' };
    const endpoint = `${this.baseUrl.replace(/\/+$/, '')}/health`;
    try {
      const res = await fetch(endpoint, {
        method: 'GET',
        headers: { Authorization: `Bearer ${this.apiKey}` },
      });
      const latencyMs = Date.now() - start;
      if (!res.ok) return { healthy: false, latencyMs, error: `HTTP_${res.status}` };
      return { healthy: true, latencyMs };
    } catch (err) {
      return { healthy: false, latencyMs: Date.now() - start, error: err instanceof Error ? err.message : String(err) };
    }
  }

  public getCapabilities(modelId: string): ModelCapabilities {
    // Conservative capability claims that reflect what this provider wrapper supports
    return {
      coding: true,
      reasoning: true,
      longContext: true,
      vision: false,
      toolCalling: true,
      structuredOutput: true,
      streaming: false,
      fastChat: true,
    };
  }

  public async getModels(): Promise<ModelRegistration[]> {
    const model = (config as any).grokModel || 'grok-1';
    return [
      {
        id: model,
        provider: 'grok',
        displayName: 'Grok (xAI)',
        type: 'general_reasoning',
        capabilities: this.getCapabilities(model),
        contextLimit: 262144,
        outputLimit: 8192,
        costPer1kInputTokens: 0.0005,
        costPer1kOutputTokens: 0.001,
        priority: 95,
        isEnabled: true,
        isPrimary: false,
        isFallback: false,
      },
    ];
  }

  public estimateCost(modelId: string, promptTokens: number, completionTokens: number): number {
    return 0.0005 * (promptTokens / 1000) + 0.001 * (completionTokens / 1000);
  }

  public validateResponse(response: ProviderResponse, jsonSchema?: Record<string, unknown> | undefined): boolean {
    if (!response || !response.text) return false;
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
    if (msg.includes('429')) return { httpCode: 429, category: 'RATE_LIMIT', retryable: false, message: msg } as ErrorClassification;
    if (msg.includes('408') || msg.toLowerCase().includes('timeout') || msg.toLowerCase().includes('aborted'))
      return { httpCode: 408, category: 'TIMEOUT', retryable: true, message: msg } as ErrorClassification;
    if (msg.includes('503')) return { httpCode: 503, category: 'PROVIDER_UNAVAILABLE', retryable: true, message: msg } as ErrorClassification;
    if (msg.includes('401') || msg.toLowerCase().includes('unauthorized')) return { httpCode: 401, category: 'UNAUTHORIZED', retryable: false, message: msg } as ErrorClassification;
    if (msg.includes('500')) return { httpCode: 500, category: 'SERVER_ERROR', retryable: true, message: msg } as ErrorClassification;
    return { httpCode: 520, category: 'UNKNOWN', retryable: false, message: msg } as ErrorClassification;
  }
}
