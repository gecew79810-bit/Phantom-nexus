import {
  ChatMessage,
  ErrorClassification,
  ModelCapabilities,
  ModelRegistration,
  ProviderId,
} from '../types/index.js';

export interface ProviderCallOptions {
  modelId: string;
  temperature?: number | undefined;
  maxTokens?: number | undefined;
  jsonSchema?: Record<string, unknown> | undefined;
  systemInstruction?: string | undefined;
  timeoutMs?: number | undefined;
}

export interface ProviderResponse {
  text: string;
  modelId: string;
  provider: ProviderId;
  latencyMs: number;
  promptTokens?: number | undefined;
  completionTokens?: number | undefined;
  totalTokens?: number | undefined;
  raw?: unknown;
}

export interface AIProvider {
  readonly id: ProviderId;
  readonly name: string;

  chat(messages: ChatMessage[], options: ProviderCallOptions): Promise<ProviderResponse>;

  stream?(
    messages: ChatMessage[],
    options: ProviderCallOptions,
    onChunk: (chunk: string) => void
  ): Promise<ProviderResponse>;

  healthCheck(modelId?: string | undefined): Promise<{ healthy: boolean; latencyMs: number; error?: string | undefined }>;

  getCapabilities(modelId: string): ModelCapabilities;

  getModels(): Promise<ModelRegistration[]>;

  estimateCost(modelId: string, promptTokens: number, completionTokens: number): number;

  validateResponse(response: ProviderResponse, jsonSchema?: Record<string, unknown> | undefined): boolean;

  handleError(error: unknown): ErrorClassification;
}
