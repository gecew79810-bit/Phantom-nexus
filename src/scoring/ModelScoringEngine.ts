import {
  ModelCapabilities,
  ModelRegistration,
  RoutingMode,
  TaskClass,
} from '../types/index.js';
import { ModelRegistry } from '../registry/ModelRegistry.js';

export interface ScoreFactors {
  capabilityMatch: number;
  reliability: number;
  recentSuccessRate: number;
  latencyScore: number;
  contextFit: number;
  toolCompatibility: number;
  qualityPreference: number;
  costPenalty: number;
  errorPenalty: number;
  finalScore: number;
}

export class ModelScoringEngine {
  constructor(private registry: ModelRegistry) {}

  public scoreModel(
    model: ModelRegistration,
    taskClass: TaskClass,
    routingMode: RoutingMode = 'AUTO',
    contextLengthEstimate = 1000,
    requiresTools = false
  ): ScoreFactors {
    const metrics = this.registry.getMetrics(model.id);

    // 1. Capability Match (0 to 30)
    let capabilityMatch = 15;
    const caps = model.capabilities;

    switch (taskClass) {
      case 'CODING':
      case 'DEBUGGING':
      case 'REPOSITORY_ANALYSIS':
        capabilityMatch += caps.coding ? 15 : -15;
        if (caps.reasoning) capabilityMatch += 5;
        break;
      case 'REASONING':
      case 'PLANNING':
        capabilityMatch += caps.reasoning ? 15 : -10;
        break;
      case 'LONG_CONTEXT_ANALYSIS':
      case 'DOCUMENT_ANALYSIS':
        capabilityMatch += caps.longContext ? 15 : -15;
        break;
      case 'VISION':
        capabilityMatch += caps.vision ? 20 : -30;
        break;
      case 'TOOL_CALLING':
      case 'AGENT_EXECUTION':
        capabilityMatch += caps.toolCalling ? 15 : -25;
        break;
      case 'STRUCTURED_JSON':
        capabilityMatch += caps.structuredOutput ? 15 : -10;
        break;
      case 'FAST_SIMPLE_QUERY':
      case 'GENERAL_CONVERSATION':
        capabilityMatch += caps.fastChat ? 15 : 0;
        break;
      default:
        capabilityMatch += 10;
    }

    // 2. Reliability & Recent Success Rate (0 to 20)
    let recentSuccessRate = 15;
    if (metrics.totalCalls > 0) {
      const rate = metrics.successCalls / metrics.totalCalls;
      recentSuccessRate = Math.round(rate * 20);
    }
    const reliability = metrics.consecutiveFailures === 0 ? 15 : Math.max(0, 15 - metrics.consecutiveFailures * 5);

    // 3. Latency Score (0 to 15)
    let latencyScore = 12;
    if (metrics.averageLatencyMs > 0) {
      if (metrics.averageLatencyMs < 800) latencyScore = 15;
      else if (metrics.averageLatencyMs < 2000) latencyScore = 12;
      else if (metrics.averageLatencyMs < 5000) latencyScore = 8;
      else latencyScore = 4;
    }

    // 4. Context Fit (0 to 10)
    let contextFit = 8;
    if (contextLengthEstimate > model.contextLimit) {
      contextFit = -50; // Context overflow penalty
    } else if (contextLengthEstimate > model.contextLimit * 0.8) {
      contextFit = 3;
    } else {
      contextFit = 10;
    }

    // 5. Tool Compatibility (0 to 10)
    let toolCompatibility = 0;
    if (requiresTools) {
      toolCompatibility = caps.toolCalling ? 10 : -30;
    }

    // 6. Quality Preference vs Cost vs Speed (Weighted by RoutingMode)
    let qualityPreference = Math.round(model.priority * 0.1);
    let costPenalty = Math.round(model.costPer1kOutputTokens * 5000);
    let errorPenalty = metrics.consecutiveFailures * 10;

    if (routingMode === 'QUALITY_FIRST') {
      qualityPreference *= 2;
      costPenalty *= 0.2;
    } else if (routingMode === 'SPEED_FIRST') {
      latencyScore *= 2;
      costPenalty *= 0.5;
    } else if (routingMode === 'COST_FIRST') {
      costPenalty *= 3;
      qualityPreference *= 0.5;
    }

    // If circuit breaker is degraded or open
    if (metrics.circuitState === 'OPEN') {
      errorPenalty += 200; // Do not select
    } else if (metrics.circuitState === 'DEGRADED') {
      errorPenalty += 40;
    } else if (metrics.circuitState === 'RECOVERING') {
      errorPenalty += 10;
    }

    const finalScore =
      capabilityMatch +
      reliability +
      recentSuccessRate +
      latencyScore +
      contextFit +
      toolCompatibility +
      qualityPreference -
      costPenalty -
      errorPenalty;

    return {
      capabilityMatch,
      reliability,
      recentSuccessRate,
      latencyScore,
      contextFit,
      toolCompatibility,
      qualityPreference,
      costPenalty,
      errorPenalty,
      finalScore,
    };
  }
}
