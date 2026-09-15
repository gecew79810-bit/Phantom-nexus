import { AIProvider, ProviderCallOptions, ProviderResponse } from './AIProvider.js';
import {
  ChatMessage,
  ErrorClassification,
  ModelCapabilities,
  ModelRegistration,
  ProviderId,
} from '../types/index.js';
import { config } from '../config/index.js';

export class GeminiProvider implements AIProvider {
  public readonly id: ProviderId = 'gemini';
  public readonly name = 'Google Gemini';

  private apiKey: string;
  private baseUrl: string;

  constructor(apiKey?: string, baseUrl?: string) {
    this.apiKey = apiKey ?? config.geminiApiKey;
    this.baseUrl = baseUrl ?? config.geminiBaseUrl;
  }

  public setApiKey(key: string): void {
    this.apiKey = key;
  }

  public async chat(messages: ChatMessage[], options: ProviderCallOptions): Promise<ProviderResponse> {
    const startTime = Date.now();

    if (!this.apiKey || this.apiKey === 'MY_GEMINI_API_KEY') {
      throw new Error('GEMINI_API_KEY_NOT_CONFIGURED: Gemini API key is missing or invalid');
    }

    const model = options.modelId || config.geminiModel;
    const url = `${this.baseUrl}/models/${model}:generateContent?key=${encodeURIComponent(this.apiKey)}`;

    // Convert messages to Gemini format
    const contents: Array<{ role: string; parts: Array<{ text: string }> }> = [];
    let systemInstruction = options.systemInstruction;

    for (const msg of messages) {
      if (msg.role === 'system') {
        systemInstruction = (systemInstruction ? systemInstruction + '\n' : '') + msg.content;
      } else {
        contents.push({
          role: msg.role === 'user' ? 'user' : 'model',
          parts: [{ text: msg.content }],
        });
      }
    }

    // Ensure at least one content entry
    if (contents.length === 0) {
      contents.push({
        role: 'user',
        parts: [{ text: 'Hello' }],
      });
    }

    const generationConfig: Record<string, unknown> = {
      temperature: options.temperature ?? config.defaultTemperature,
      maxOutputTokens: options.maxTokens ?? config.defaultMaxOutputTokens,
    };

    if (options.jsonSchema) {
      generationConfig['responseMimeType'] = 'application/json';
      generationConfig['responseSchema'] = options.jsonSchema;
    }

    const body: Record<string, unknown> = {
      contents,
      generationConfig,
    };

    if (systemInstruction) {
      body['systemInstruction'] = {
        parts: [{ text: systemInstruction }],
      };
    }

    const timeoutMs = options.timeoutMs ?? config.requestTimeoutMs;
    const controller = new AbortController();
    const timeoutId = setTimeout(() => controller.abort(), timeoutMs);

    try {
      const response = await fetch(url, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
        signal: controller.signal,
      });

      clearTimeout(timeoutId);
      const latencyMs = Date.now() - startTime;

      if (!response.ok) {
        const errorText = await response.text().catch(() => '');
        throw new Error(`HTTP_${response.status}: ${errorText.slice(0, 300)}`);
      }

      const data = (await response.json()) as {
        candidates?: Array<{
          content?: {
            parts?: Array<{ text?: string }>;
          };
          finishReason?: string;
        }>;
        usageMetadata?: {
          promptTokenCount?: number;
          candidatesTokenCount?: number;
          totalTokenCount?: number;
        };
      };

      const candidate = data.candidates?.[0];
      const text = candidate?.content?.parts?.map((p) => p.text || '').join('') || '';

      if (!text || text.trim() === '') {
        if (candidate?.finishReason === 'MAX_TOKENS') {
          throw new Error('OUTPUT_LIMIT_FAILURE: Response exceeded max output tokens without content');
        }
        throw new Error('EMPTY_RESPONSE: Gemini returned empty content');
      }

      return {
        text,
        modelId: model,
        provider: 'gemini',
        latencyMs,
        promptTokens: data.usageMetadata?.promptTokenCount,
        completionTokens: data.usageMetadata?.candidatesTokenCount,
        totalTokens: data.usageMetadata?.totalTokenCount,
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
      if (!this.apiKey || this.apiKey === 'MY_GEMINI_API_KEY') {
        return { healthy: false, latencyMs: 0, error: 'GEMINI_API_KEY not configured' };
      }
      const model = modelId || config.geminiModel;
      const url = `${this.baseUrl}/models/${model}?key=${encodeURIComponent(this.apiKey)}`;
      const res = await fetch(url, { method: 'GET', signal: AbortSignal.timeout(10000) });
      const latencyMs = Date.now() - start;
      if (res.ok) {
        return { healthy: true, latencyMs };
      }
      return { healthy: false, latencyMs, error: `HTTP ${res.status}` };
    } catch (e: unknown) {
      return {
        healthy: false,
        latencyMs: Date.now() - start,
        error: e instanceof Error ? e.message : String(e),
      };
    }
  }

  public getCapabilities(modelId: string): ModelCapabilities {
    return {
      coding: true,
      reasoning: true,
      longContext: true,
      vision: true,
      toolCalling: true,
      structuredOutput: true,
      streaming: true,
      fastChat: true,
    };
  }

  public async getModels(): Promise<ModelRegistration[]> {
    return [];
  }

  public estimateCost(modelId: string, promptTokens: number, completionTokens: number): number {
    // ~$0.0001 per 1k input tokens, $0.0004 per 1k output tokens for Flash
    return (promptTokens / 1000) * 0.0001 + (completionTokens / 1000) * 0.0004;
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

    if (lower.includes('gemini_api_key_not_configured') || lower.includes('http_401')) {
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
    if (lower.includes('http_429') || lower.includes('resource_exhausted') || lower.includes('rate limit') || lower.includes('quota')) {
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
    if (lower.includes('model not found') || lower.includes('is not supported')) {
      return { category: 'INVALID_MODEL', retryable: false, message: msg };
    }

    return { category: 'UNKNOWN', retryable: false, message: msg };
  }
}
