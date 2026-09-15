# Jarvis Multi-AI Brain

Resilient AI orchestration backend for an existing Jarvis app.

## Core behavior

- Gemini is the preferred primary provider.
- TokenRa is a configurable fallback and multi-model gateway.
- Routing is capability-aware, not name-aware.
- Failover handles rate limits, timeouts, auth/config errors, provider outages, malformed responses, and empty output.
- Circuit breakers temporarily quarantine repeatedly failing providers/models.
- Complex tasks can use planner, specialist, reviewer, and finalizer agents.
- Context is bounded and can be summarized/compressed by the host application later.
- API keys remain server-side.

## Important

This repository is intentionally provider-ID agnostic. Do not hard-code assumed future model names. Put the exact model IDs enabled on your accounts into environment variables or an admin/config store.

TokenRa documentation currently shows an OpenAI-compatible Chat Completions interface and advises verifying the live model identifier, endpoint, capabilities, and account availability before production use. Google documents both standard generation and streaming APIs for Gemini. Verify your current account/model settings before deployment.

## Quick start

1. Copy `.env.example` to `.env`.
2. Fill in `GEMINI_API_KEY` and, when desired, `TOKENRA_API_KEY` + `TOKENRA_MODEL`.
3. `npm install`
4. `npm run typecheck`
5. `npm run dev`
6. `POST /v1/chat` with JSON:

```json
{
  "message": "Hello Jarvis",
  "history": [],
  "mode": "balanced"
}
```

## Production note

Put this service behind HTTPS and authentication. For a mobile app, call this backend rather than embedding provider secrets in the APK.
