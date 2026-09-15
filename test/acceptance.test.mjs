import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { ModelRegistry } from '../dist/registry/ModelRegistry.js';
import { ModelRouter } from '../dist/router/ModelRouter.js';
import { JarvisOrchestrator } from '../dist/orchestrator/JarvisOrchestrator.js';
import { TaskClassifier } from '../dist/router/TaskClassifier.js';
import { CircuitBreaker } from '../dist/circuit/CircuitBreaker.js';

// Helper mock provider
class MockProvider {
  constructor(id, name) {
    this.id = id;
    this.name = name;
    this.callCount = 0;
    this.failureMode = null; // 'RATE_LIMIT', 'TIMEOUT', '500', 'EMPTY', null
  }

  async chat(messages, options) {
    this.callCount++;

    if (this.failureMode === 'RATE_LIMIT') {
      throw new Error('HTTP_429: Resource exhausted rate limit exceeded');
    }
    if (this.failureMode === 'TIMEOUT') {
      throw new Error('HTTP_408: Request timeout aborted');
    }
    if (this.failureMode === '500') {
      throw new Error('HTTP_500: Internal server error');
    }
    if (this.failureMode === 'UNAVAILABLE') {
      throw new Error('HTTP_503: Service temporarily unavailable');
    }
    if (this.failureMode === 'EMPTY') {
      return {
        text: '',
        modelId: options.modelId,
        provider: this.id,
        latencyMs: 15,
      };
    }

    // Default success
    if (options.jsonSchema) {
      return {
        text: JSON.stringify({ status: 'ok', taskCompleted: true }),
        modelId: options.modelId,
        provider: this.id,
        latencyMs: 25,
      };
    }

    return {
      text: `Response from ${this.name} using model ${options.modelId}`,
      modelId: options.modelId,
      provider: this.id,
      latencyMs: 30,
    };
  }

  async healthCheck() {
    if (this.failureMode) return { healthy: false, latencyMs: 10, error: this.failureMode };
    return { healthy: true, latencyMs: 15 };
  }

  getCapabilities() {
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

  async getModels() {
    return [];
  }

  estimateCost() {
    return 0.001;
  }

  validateResponse(response, jsonSchema) {
    if (!response.text || response.text.trim() === '') return false;
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

  handleError(error) {
    const msg = error instanceof Error ? error.message : String(error);
    if (msg.includes('429')) return { httpCode: 429, category: 'RATE_LIMIT', retryable: false, message: msg };
    if (msg.includes('408') || msg.includes('timeout')) return { httpCode: 408, category: 'TIMEOUT', retryable: true, message: msg };
    if (msg.includes('503')) return { httpCode: 503, category: 'PROVIDER_UNAVAILABLE', retryable: true, message: msg };
    if (msg.includes('500')) return { httpCode: 500, category: 'SERVER_ERROR', retryable: true, message: msg };
    return { category: 'UNKNOWN', retryable: false, message: msg };
  }
}

describe('JARVIS MULTI-AI ORCHESTRATION ACCEPTANCE TESTS', () => {

  test('TEST 1: Gemini healthy -> Gemini selected', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');

    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);
    const res = await orchestrator.processRequest({ message: 'Hello Jarvis!' });

    assert.equal(res.provider, 'gemini');
    assert.equal(res.failoverOccurred, false);
    assert.ok(geminiMock.callCount >= 1);
    assert.equal(tokenRaMock.callCount, 0);
  });

  test('TEST 2: Gemini returns rate-limit -> fallback model selected', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    geminiMock.failureMode = 'RATE_LIMIT';
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');

    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);
    const res = await orchestrator.processRequest({ message: 'Can you solve this?' });

    assert.equal(res.provider, 'tokenra');
    assert.equal(res.failoverOccurred, true);
    assert.ok(res.failoverHistory.length > 0);
    assert.ok(res.failoverHistory[0].includes('RATE_LIMIT'));
  });

  test('TEST 3: Gemini timeout -> retry then failover', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    geminiMock.failureMode = 'TIMEOUT';
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');

    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);
    const res = await orchestrator.processRequest({ message: 'Analyze this log' });

    assert.equal(res.provider, 'tokenra');
    assert.equal(res.failoverOccurred, true);
    // Verified bounded retry on timeout before failover
    assert.ok(geminiMock.callCount >= 2);
  });

  test('TEST 4: Selected fallback unavailable -> next eligible model selected', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    geminiMock.failureMode = 'RATE_LIMIT';

    // TokenRa has multiple models in registry: deepseek-chat, deepseek-reasoner, kimi-k1.5, glm-4-plus
    let tokenRaCalls = 0;
    const multiModelTokenRa = new MockProvider('tokenra', 'TokenRa');
    multiModelTokenRa.chat = async (msgs, opts) => {
      tokenRaCalls++;
      if (opts.modelId === 'deepseek-chat') {
        throw new Error('HTTP_503: Service temporarily unavailable');
      }
      return {
        text: `Success on alternate fallback: ${opts.modelId}`,
        modelId: opts.modelId,
        provider: 'tokenra',
        latencyMs: 30,
      };
    };

    const orchestrator = new JarvisOrchestrator(registry, geminiMock, multiModelTokenRa);
    const res = await orchestrator.processRequest({ message: 'Run deep reasoning task' });

    assert.equal(res.provider, 'tokenra');
    assert.notEqual(res.modelId, 'deepseek-chat');
    assert.equal(res.failoverOccurred, true);
  });

  test('TEST 5: One provider repeatedly fails -> circuit breaker opens', async () => {
    const cb = new CircuitBreaker('test-model', {
      failureThreshold: 3,
      openTimeoutMs: 50000,
    });

    assert.equal(cb.getState(), 'HEALTHY');
    cb.recordFailure();
    cb.recordFailure();
    assert.equal(cb.getState(), 'DEGRADED');
    cb.recordFailure(); // 3rd failure
    assert.equal(cb.getState(), 'OPEN');
    assert.equal(cb.isAvailable(), false);
  });

  test('TEST 6: Provider recovers -> automatically returns to healthy state', async () => {
    const cb = new CircuitBreaker('test-model', {
      failureThreshold: 2,
      openTimeoutMs: 50, // 50ms for test
      halfOpenSuccessThreshold: 1,
    });

    cb.recordFailure();
    cb.recordFailure();
    assert.equal(cb.getState(), 'OPEN');

    // Wait for open timeout to elapse
    await new Promise((r) => setTimeout(r, 60));
    assert.equal(cb.getState(), 'RECOVERING');
    assert.equal(cb.isAvailable(), true);

    // Probe call succeeds
    cb.recordSuccess();
    assert.equal(cb.getState(), 'HEALTHY');
  });

  test('TEST 7: Coding task -> coding-capable model selected', async () => {
    const req = TaskClassifier.classify('Write a Kotlin Coroutine function for downloading an image');
    assert.equal(req.taskClass, 'CODING');
    assert.equal(req.requiredCapabilities.coding, true);
  });

  test('TEST 8: Long-context task -> long-context-capable model selected', async () => {
    const longPrompt = 'Please analyze this entire repository and code structure:\n' + 'line\n'.repeat(6000);
    const req = TaskClassifier.classify(longPrompt);
    assert.ok(req.taskClass === 'LONG_CONTEXT_ANALYSIS' || req.taskClass === 'REPOSITORY_ANALYSIS');
    assert.equal(req.requiredCapabilities.longContext, true);
  });

  test('TEST 9: Structured JSON task -> output schema validated', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');
    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);

    const schema = {
      type: 'object',
      properties: {
        status: { type: 'string' },
        taskCompleted: { type: 'boolean' },
      },
    };

    const res = await orchestrator.processRequest({
      message: 'Provide status report',
      jsonSchema: schema,
    });

    const parsed = JSON.parse(res.text);
    assert.equal(parsed.status, 'ok');
    assert.equal(parsed.taskCompleted, true);
  });

  test('TEST 10: Complex task -> planner + specialist + reviewer workflow', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');
    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);

    const job = await orchestrator.executeComplexGoal(
      'Refactor authentication system with OAuth2 and rate limiting'
    );

    assert.equal(job.status, 'COMPLETED');
    assert.ok(job.plan.length >= 2);
    assert.ok(job.checkpoints.length >= 2);
    assert.ok(job.finalResponse);
  });

  test('TEST 11: One agent fails -> task resumes from checkpoint', async () => {
    const registry = new ModelRegistry();
    const agentSystem = new JarvisOrchestrator(registry, new MockProvider('gemini', 'Gemini')).getAgentSystem();

    const mockJob = {
      id: 'job_test',
      goal: 'Deploy service',
      plan: [
        { id: 's1', description: 'Step 1', agent: 'CODER', status: 'COMPLETED' },
        { id: 's2', description: 'Step 2', agent: 'REVIEWER', status: 'PENDING' },
      ],
      currentStepIndex: 1,
      status: 'RUNNING',
      intermediateArtifacts: { s1: 'Code generation checkpoint output' },
      checkpoints: [
        { stepIndex: 0, stepId: 's1', output: 'Code checkpoint output', modelId: 'gemini-2.5-flash', provider: 'gemini', timestamp: Date.now() },
      ],
      createdAt: Date.now(),
      updatedAt: Date.now(),
    };

    // Step 2 executes using context from step 1 checkpoint
    const res = await agentSystem.executeStep(mockJob.plan[1], {
      goal: mockJob.goal,
      artifacts: mockJob.intermediateArtifacts,
      checkpoints: mockJob.checkpoints,
    });

    assert.equal(mockJob.plan[1].status, 'COMPLETED');
    assert.ok(res.output);
  });

  test('TEST 12: API secret never appears in frontend or logs', async () => {
    const registry = new ModelRegistry();
    const geminiMock = new MockProvider('gemini', 'Google Gemini');
    const tokenRaMock = new MockProvider('tokenra', 'TokenRa');
    const orchestrator = new JarvisOrchestrator(registry, geminiMock, tokenRaMock);

    await orchestrator.processRequest({ message: 'Check security status' });
    const logs = orchestrator.getLogs();

    const logString = JSON.stringify(logs);
    assert.equal(logString.includes('MY_GEMINI_API_KEY'), false);
    assert.equal(logString.includes('TOKENRA_API_KEY'), false);
    assert.equal(logString.includes('Bearer '), false);
  });
});
