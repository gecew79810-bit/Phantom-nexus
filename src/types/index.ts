import { z } from 'zod';

export type ProviderId = 'gemini' | 'tokenra' | 'openai' | 'local';

export type CircuitState = 'HEALTHY' | 'DEGRADED' | 'OPEN' | 'RECOVERING';

export type RoutingMode = 'AUTO' | 'QUALITY_FIRST' | 'SPEED_FIRST' | 'COST_FIRST' | 'BALANCED';

export type TaskClass =
  | 'GENERAL_CONVERSATION'
  | 'REASONING'
  | 'CODING'
  | 'DEBUGGING'
  | 'REPOSITORY_ANALYSIS'
  | 'LONG_CONTEXT_ANALYSIS'
  | 'DOCUMENT_ANALYSIS'
  | 'RESEARCH'
  | 'SUMMARIZATION'
  | 'TRANSLATION'
  | 'STRUCTURED_JSON'
  | 'TOOL_CALLING'
  | 'PLANNING'
  | 'AGENT_EXECUTION'
  | 'VISION'
  | 'CREATIVE_WRITING'
  | 'FAST_SIMPLE_QUERY';

export type AgentRole =
  | 'ORCHESTRATOR'
  | 'PLANNER'
  | 'RESEARCHER'
  | 'CODER'
  | 'DEBUGGER'
  | 'REVIEWER'
  | 'TOOL'
  | 'MEMORY'
  | 'SECURITY'
  | 'FINALIZER';

export interface ModelCapabilities {
  coding: boolean;
  reasoning: boolean;
  longContext: boolean;
  vision: boolean;
  toolCalling: boolean;
  structuredOutput: boolean;
  streaming: boolean;
  fastChat: boolean;
}

export interface ModelRegistration {
  id: string;
  provider: ProviderId;
  displayName: string;
  type: string;
  capabilities: ModelCapabilities;
  contextLimit: number;
  outputLimit: number;
  costPer1kInputTokens: number;
  costPer1kOutputTokens: number;
  priority: number;
  isEnabled: boolean;
  isPrimary: boolean;
  isFallback: boolean;
}

export interface ModelRuntimeMetrics {
  totalCalls: number;
  successCalls: number;
  errorCalls: number;
  rateLimitCount: number;
  timeoutCount: number;
  consecutiveFailures: number;
  averageLatencyMs: number;
  lastSuccessTimestamp?: number | undefined;
  lastFailureTimestamp?: number | undefined;
  circuitState: CircuitState;
  circuitOpenUntilTimestamp?: number | undefined;
}

export interface ChatMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  name?: string | undefined;
}

export interface ChatRequest {
  message: string;
  history?: ChatMessage[] | undefined;
  mode?: RoutingMode | undefined;
  taskClassHint?: TaskClass | undefined;
  preferredProvider?: ProviderId | undefined;
  requiredCapabilities?: Partial<ModelCapabilities> | undefined;
  jsonSchema?: Record<string, unknown> | undefined;
  temperature?: number | undefined;
  maxTokens?: number | undefined;
  requireReview?: boolean | undefined;
}

export interface ChatResponse {
  text: string;
  modelId: string;
  provider: ProviderId;
  taskClass: TaskClass;
  latencyMs: number;
  failoverOccurred: boolean;
  failoverHistory?: string[] | undefined;
  reviewResult?:
    | {
        approved: boolean;
        notes: string;
        reviewerModel: string;
      }
    | undefined;
  usage?:
    | {
        promptTokens?: number | undefined;
        completionTokens?: number | undefined;
        totalTokens?: number | undefined;
      }
    | undefined;
}

export interface OrchestrationStep {
  id: string;
  description: string;
  agent: AgentRole;
  status: 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
  result?: string | undefined;
  executedByModel?: string | undefined;
  executedByProvider?: ProviderId | undefined;
  error?: string | undefined;
}

export interface OrchestrationJob {
  id: string;
  goal: string;
  plan: OrchestrationStep[];
  currentStepIndex: number;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';
  intermediateArtifacts: Record<string, string>;
  checkpoints: Array<{
    stepIndex: number;
    stepId: string;
    output: string;
    modelId: string;
    provider: ProviderId;
    timestamp: number;
  }>;
  finalResponse?: string | undefined;
  createdAt: number;
  updatedAt: number;
}

export interface ErrorClassification {
  httpCode?: number | undefined;
  category:
    | 'UNAUTHORIZED'
    | 'FORBIDDEN'
    | 'TIMEOUT'
    | 'CONFLICT'
    | 'RATE_LIMIT'
    | 'SERVER_ERROR'
    | 'CONNECTION_FAILURE'
    | 'INVALID_MODEL'
    | 'PROVIDER_UNAVAILABLE'
    | 'QUOTA_EXHAUSTED'
    | 'CONTEXT_OVERFLOW'
    | 'OUTPUT_LIMIT_FAILURE'
    | 'TOOL_FAILURE'
    | 'MALFORMED_RESPONSE'
    | 'EMPTY_RESPONSE'
    | 'UNKNOWN';
  retryable: boolean;
  message: string;
}

// Zod validation schemas
export const ChatMessageSchema = z.object({
  role: z.enum(['user', 'assistant', 'system']),
  content: z.string().min(1),
  name: z.string().optional(),
});

export const ChatRequestSchema = z.object({
  message: z.string().min(1),
  history: z.array(ChatMessageSchema).optional(),
  mode: z.enum(['AUTO', 'QUALITY_FIRST', 'SPEED_FIRST', 'COST_FIRST', 'BALANCED']).optional(),
  taskClassHint: z
    .enum([
      'GENERAL_CONVERSATION',
      'REASONING',
      'CODING',
      'DEBUGGING',
      'REPOSITORY_ANALYSIS',
      'LONG_CONTEXT_ANALYSIS',
      'DOCUMENT_ANALYSIS',
      'RESEARCH',
      'SUMMARIZATION',
      'TRANSLATION',
      'STRUCTURED_JSON',
      'TOOL_CALLING',
      'PLANNING',
      'AGENT_EXECUTION',
      'VISION',
      'CREATIVE_WRITING',
      'FAST_SIMPLE_QUERY',
    ])
    .optional(),
  preferredProvider: z.enum(['gemini', 'tokenra', 'openai', 'local']).optional(),
  requiredCapabilities: z
    .object({
      coding: z.boolean().optional(),
      reasoning: z.boolean().optional(),
      longContext: z.boolean().optional(),
      vision: z.boolean().optional(),
      toolCalling: z.boolean().optional(),
      structuredOutput: z.boolean().optional(),
      streaming: z.boolean().optional(),
      fastChat: z.boolean().optional(),
    })
    .optional(),
  jsonSchema: z.record(z.unknown()).optional(),
  temperature: z.number().min(0).max(2).optional(),
  maxTokens: z.number().int().positive().optional(),
  requireReview: z.boolean().optional(),
});

export const OrchestrateRequestSchema = z.object({
  goal: z.string().min(1),
  context: z.string().optional(),
  mode: z.enum(['AUTO', 'QUALITY_FIRST', 'SPEED_FIRST', 'COST_FIRST', 'BALANCED']).optional(),
  requireReview: z.boolean().optional(),
});
