import fastify from 'fastify';
import cors from '@fastify/cors';
import rateLimit from '@fastify/rate-limit';
import { config } from './config/index.js';
import {
  ChatRequest,
  ChatRequestSchema,
  OrchestrateRequestSchema,
  ProviderId,
} from './types/index.js';
import { JarvisOrchestrator } from './orchestrator/JarvisOrchestrator.js';
import { defaultModelRegistry } from './registry/ModelRegistry.js';

export function createServer(orchestrator?: JarvisOrchestrator) {
  const app = fastify({
    logger: {
      level: config.logLevel,
      // Sanitizer: never log auth headers or secrets
      serializers: {
        req(req) {
          return {
            method: req.method,
            url: req.url,
            hostname: req.hostname,
            remoteAddress: req.ip,
          };
        },
      },
    },
  });

  const brain = orchestrator ?? new JarvisOrchestrator(defaultModelRegistry);
  const registry = brain.getRegistry();

  // Register CORS
  app.register(cors, {
    origin: config.corsOrigin,
    methods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
  });

  // Register Rate Limit
  app.register(rateLimit, {
    max: 120,
    timeWindow: '1 minute',
  });

  // Health check
  app.get('/v1/health', async () => {
    const geminiProvider = brain.getRouter().getProvider('gemini');
    const tokenRaProvider = brain.getRouter().getProvider('tokenra');

    const geminiHealth = geminiProvider ? await geminiProvider.healthCheck() : { healthy: false, latencyMs: 0 };
    const tokenRaHealth = tokenRaProvider ? await tokenRaProvider.healthCheck() : { healthy: false, latencyMs: 0 };

    return {
      status: 'online',
      system: 'Jarvis Multi-AI Orchestrator',
      timestamp: Date.now(),
      providers: {
        gemini: geminiHealth,
        tokenra: tokenRaHealth,
      },
    };
  });

  // List all models with runtime status
  app.get('/v1/models', async () => {
    const models = registry.getAllModels().map((m) => {
      const metrics = registry.getMetrics(m.id);
      return {
        ...m,
        metrics,
      };
    });

    return {
      total: models.length,
      models,
    };
  });

  // Main Chat / Routing Endpoint
  app.post('/v1/chat', async (request, reply) => {
    // Optional internal authorization check
    if (config.jarvisApiKey) {
      const authHeader = request.headers['authorization'];
      if (!authHeader || authHeader !== `Bearer ${config.jarvisApiKey}`) {
        return reply.status(401).send({ error: 'Unauthorized: Invalid JARVIS_API_KEY' });
      }
    }

    const parseResult = ChatRequestSchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.status(400).send({
        error: 'Invalid chat request',
        details: parseResult.error.format(),
      });
    }

    try {
      const result = await brain.processRequest(parseResult.data as unknown as ChatRequest);
      return result;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      request.log.error({ err }, 'Orchestration execution failure');
      return reply.status(502).send({
        error: 'Orchestration failure',
        message: msg,
      });
    }
  });

  // Complex multi-agent workflow
  app.post('/v1/orchestrate', async (request, reply) => {
    if (config.jarvisApiKey) {
      const authHeader = request.headers['authorization'];
      if (!authHeader || authHeader !== `Bearer ${config.jarvisApiKey}`) {
        return reply.status(401).send({ error: 'Unauthorized: Invalid JARVIS_API_KEY' });
      }
    }

    const parseResult = OrchestrateRequestSchema.safeParse(request.body);
    if (!parseResult.success) {
      return reply.status(400).send({
        error: 'Invalid orchestration request',
        details: parseResult.error.format(),
      });
    }

    try {
      const job = await brain.executeComplexGoal(
        parseResult.data.goal,
        parseResult.data.context ?? ''
      );
      return job;
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return reply.status(500).send({
        error: 'Goal orchestration failure',
        message: msg,
      });
    }
  });

  // Admin: Toggle model enabled state
  app.post('/v1/admin/model/toggle', async (request, reply) => {
    const body = request.body as { modelId: string; isEnabled: boolean };
    if (!body || typeof body.modelId !== 'string' || typeof body.isEnabled !== 'boolean') {
      return reply.status(400).send({ error: 'Invalid parameters. Require modelId and isEnabled' });
    }
    const success = registry.setModelEnabled(body.modelId, body.isEnabled);
    return { success, modelId: body.modelId, isEnabled: body.isEnabled };
  });

  // Admin: Set model as primary
  app.post('/v1/admin/model/primary', async (request, reply) => {
    const body = request.body as { modelId: string };
    if (!body || typeof body.modelId !== 'string') {
      return reply.status(400).send({ error: 'Invalid parameters. Require modelId' });
    }
    const success = registry.setPrimaryModel(body.modelId);
    return { success, primaryModelId: body.modelId };
  });

  // Admin: Reset circuit breaker
  app.post('/v1/admin/circuit/reset', async (request, reply) => {
    const body = request.body as { modelId: string };
    if (!body || typeof body.modelId !== 'string') {
      return reply.status(400).send({ error: 'Invalid parameters. Require modelId' });
    }
    const success = registry.resetCircuit(body.modelId);
    return { success, modelId: body.modelId, circuitState: 'HEALTHY' };
  });

  // Admin: Test model
  app.post('/v1/admin/model/test', async (request, reply) => {
    const body = request.body as { modelId: string; prompt?: string };
    if (!body || typeof body.modelId !== 'string') {
      return reply.status(400).send({ error: 'Invalid parameters. Require modelId' });
    }
    const model = registry.getModel(body.modelId);
    if (!model) {
      return reply.status(404).send({ error: `Model '${body.modelId}' not found` });
    }

    const provider = brain.getRouter().getProvider(model.provider);
    if (!provider) {
      return reply.status(400).send({ error: `Provider '${model.provider}' not found` });
    }

    try {
      const response = await provider.chat(
        [{ role: 'user', content: body.prompt || 'Hello! Test message.' }],
        { modelId: model.id, maxTokens: 100 }
      );
      return { success: true, response };
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : String(err);
      return reply.status(500).send({ success: false, error: msg });
    }
  });

  // Admin: Observability metrics & audit logs
  app.get('/v1/admin/metrics', async () => {
    return {
      logs: brain.getLogs(50),
    };
  });

  return app;
}

// Direct execution entrypoint
if (process.argv[1]?.endsWith('server.ts') || process.argv[1]?.endsWith('server.js')) {
  const app = createServer();
  app.listen({ port: config.port, host: config.host }, (err, address) => {
    if (err) {
      console.error(err);
      process.exit(1);
    }
    console.log(`🚀 Jarvis Multi-AI Brain running at ${address}`);
  });
}
