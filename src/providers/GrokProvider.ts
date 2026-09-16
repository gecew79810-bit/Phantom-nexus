import { AIProvider, ProviderCallOptions, ProviderResponse } from './AIProvider.js';
import {
  ChatMessage,
  ErrorClassification,
  ModelCapabilities,
  ModelRegistration,
  ProviderId,
} from '../types/index.js';
import { config } from '../config/index.js';

export class GrokProvider implements AIProvider {
  public readonly id: ProviderId = 'grok';
  public readonly name = 'Grok / xAI';

  private apiKey: string;
  private baseUrl: string;

  constructor(apiKey?: string, baseUrl?: string) {
    this.apiKey = apiKey ?? (config as any).grokApiKey ?? '';
    this.baseUrl = baseUrl ?? (config as any).grokBaseUrl ?? '';
  }

  public setApiKey(key: string): void {
    this.apiKey = key;
  }

  public async chat(messages: ChatMessage[], options: ProviderCallOptions): Promise<ProviderResponse> {
    const startTime = Date.now();

    if (!this.apiKey) {
      throw new Error('GROK_API_KEY_NOT_CONFIGURED: Grok API key is missing or invalid');
    }

    const model = options.modelId || ((config as any).grokModel || 'grok-1');
    const endpoint = `${this.baseUrl.replace(/\/+$/, '')}/v1/generate`;

    // Convert messages into a simple payload
    const payload: Record<string, unknown> = {
      model,
      inputs: messages.map((m) => ({ role: m.role, content: m.content })),
      temperature: options.temperature ?? config.defaultTemperature,
      max_output_tokens: options.maxTokens ?? config.defaultMaxOutputTokens,
    };

    if (options.jsonSchema) {
      payload['response_format'] = { type: 'json' };
    }

    const timeoutMs = options.timeoutMs ?? config.requestTimeoutMs;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const res = await fetch(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${this.apiKey}`,
        },
        body: JSON.stringify(payload),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - startTime;

      if (!res.ok) {
        const errText = await res.text().catch(() => '');
        throw new Error(`HTTP_${res.status}: ${errText.slice(0, 300)}`);
      }

      const data = await res.json().catch(() => ({}));

      // Attempt to extract text in a provider-agnostic way
      let text = '';
      if (typeof data === 'string') {
        text = data;
      } else if (data?.output) {
        text = Array.isArray(data.output) ? data.output.map((o: any) => o?.content || '').join('\n') : String(data.output);
      } else if (data?.choices?.[0]?.message?.content) {
        text = data.choices[0].message.content;
      } else if (data?.candidates?.[0]?.content) {
        text = data.candidates[0].content;
      } else {
        // Fallback to stringified body
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
      // Normalize errors
      const msg = err instanceof Error ? err.message : String(err);
      throw new Error(msg);
    }
  }

  public async healthCheck(modelId?: string): Promise<{ healthy: boolean; latencyMs: number; error?: string | undefined }> {
    const start = Date.now();
    if (!this.apiKey) return { healthy: false, latencyMs: Date.now() - start, error: 'NO_API_KEY' };
    const endpoint = `${this.baseUrl.replace(/\/+$/, '')}/v1/health`;
    try {
      const res = await fetch(endpoint, {
        method: 'GET',
        headers: { Authorization: `Bearer ${this.apiKey}` },
      });
      const latencyMs = Date.now() - start;
      if (!res.ok) return { healthy: false, latencyMs, error: `HTTP_${res.status}` };
      return { healthy: true, latencyMs };
    } catch (err) {
      return { healthy: false, latencyMs: Date.now() - start, error: (err instanceof Error ? err.message : String(err)) };
    }
  }

  public getCapabilities(modelId: string): ModelCapabilities {
    // Conservative capability claims: avoid overstating streaming/vision unless configured
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
    // Return a minimal registration set derived from config
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
    // Simple cost estimator
    const reg = 0.0005 * (promptTokens / 1000) + 0.001 * (completionTokens / 1000);
    return reg;
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
    if (msg.includes('429')) return { httpCode: 429, category: 'RATE_LIMIT', retryable: false, message: msg };
    if (msg.includes('408') || msg.includes('timeout') || msg.includes('aborted')) return { httpCode: 408, category: 'TIMEOUT', retryable: true, message: msg };
    if (msg.includes('503')) return { httpCode: 503, category: 'PROVIDER_UNAVAILABLE', retryable: true, message: msg };
    if (msg.includes('401') || msg.toLowerCase().includes('unauthorized')) return { httpCode: 401, category: 'AUTH_ERROR', retryable: false, message: msg };
    return { httpCode: 500, category: 'UNKNOWN', retryable: false, message: msg };
  }
}
