import { ModelRegistration } from '../types/index.js';
import { CircuitBreaker } from '../circuit/CircuitBreaker.js';
import { config } from '../config/index.js';

// Export helper so tests can import a Grok registration template
export function createGrokModelRegistration(modelId?: string): ModelRegistration {
  const id = modelId || config.grokModel || 'grok-1';
  return {
    id,
    provider: 'grok',
    displayName: 'Grok (xAI) Orchestrator',
    type: 'orchestration',
    capabilities: {
      coding: true,
      reasoning: true,
      longContext: true,
      vision: false,
      toolCalling: true,
      structuredOutput: true,
      streaming: false,
      fastChat: false,
    },
    contextLimit: 262144,
    outputLimit: 8192,
    costPer1kInputTokens: 0.0005,
    costPer1kOutputTokens: 0.001,
    priority: 95,
    isEnabled: config.grokEnabled,
    isPrimary: false,
    isFallback: false,
  };
}
