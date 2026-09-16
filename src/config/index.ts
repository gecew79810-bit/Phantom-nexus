import dotenv from 'dotenv';
import { ProviderId, RoutingMode } from '../types/index.js';

dotenv.config();

export interface AppConfig {
  port: number;
  host: string;
  logLevel: string;
  corsOrigin: string;

  geminiApiKey: string;
  geminiModel: string;
  geminiBaseUrl: string;

  tokenraApiKey: string;
  tokenraBaseUrl: string;
  tokenraModel: string;
  tokenraFallbackModels: string[];

  grokApiKey: string;
  grokBaseUrl: string;
  grokModel: string;
  grokEnabled: boolean;

  primaryProvider: ProviderId;
  autoFailover: boolean;
  maxRetriesPerProvider: number;
  requestTimeoutMs: number;
  circuitFailureThreshold: number;
  circuitOpenMs: number;

  defaultTemperature: number;
  defaultMaxOutputTokens: number;
  maxHistoryMessages: number;
  maxContextChars: number;

  jarvisApiKey: string;
  maxProviderCallsPerTask: number;
}

export function loadConfig(): AppConfig {
  const parseNum = (val: string | undefined, fallback: number): number => {
    if (!val) return fallback;
    const n = Number(val);
    return isNaN(n) ? fallback : n;
  };

  const parseBool = (val: string | undefined, fallback: boolean): boolean => {
    if (!val) return fallback;
    return val.toLowerCase() === 'true' || val === '1';
  };

  const fallbackModels = (process.env['TOKENRA_FALLBACK_MODELS'] || '')
    .split(',')
    .map((m) => m.trim())
    .filter(Boolean);

  return {
    port: parseNum(process.env['PORT'], 8787),
    host: process.env['HOST'] || '0.0.0.0',
    logLevel: process.env['LOG_LEVEL'] || 'info',
    corsOrigin: process.env['CORS_ORIGIN'] || '*',

    geminiApiKey: process.env['GEMINI_API_KEY'] || '',
    geminiModel: process.env['GEMINI_MODEL'] || 'gemini-2.5-flash',
    geminiBaseUrl: process.env['GEMINI_BASE_URL'] || 'https://generativelanguage.googleapis.com/v1beta',

    tokenraApiKey: process.env['TOKENRA_API_KEY'] || '',
    tokenraBaseUrl: process.env['TOKENRA_BASE_URL'] || 'https://tokenra.io/v1',
    tokenraModel: process.env['TOKENRA_MODEL'] || 'deepseek-chat',
    tokenraFallbackModels: fallbackModels.length > 0 ? fallbackModels : ['deepseek-reasoner', 'kimi-k1.5', 'glm-4-plus'],

    grokApiKey: process.env['GROK_API_KEY'] || '',
    grokBaseUrl: process.env['GROK_BASE_URL'] || 'https://api.grok.ai',
    grokModel: process.env['GROK_MODEL'] || 'grok-1',
    grokEnabled: (process.env['GROK_ENABLED'] || 'false').toLowerCase() === 'true',

    primaryProvider: (process.env['PRIMARY_PROVIDER'] as ProviderId) || 'gemini',
    autoFailover: parseBool(process.env['AUTO_FAILOVER'], true),
    maxRetriesPerProvider: parseNum(process.env['MAX_RETRIES_PER_PROVIDER'], 1),
    requestTimeoutMs: parseNum(process.env['REQUEST_TIMEOUT_MS'], 60000),
    circuitFailureThreshold: parseNum(process.env['CIRCUIT_FAILURE_THRESHOLD'], 3),
    circuitOpenMs: parseNum(process.env['CIRCUIT_OPEN_MS'], 60000),

    defaultTemperature: parseNum(process.env['DEFAULT_TEMPERATURE'], 0.2),
    defaultMaxOutputTokens: parseNum(process.env['DEFAULT_MAX_OUTPUT_TOKENS'], 4096),
    maxHistoryMessages: parseNum(process.env['MAX_HISTORY_MESSAGES'], 30),
    maxContextChars: parseNum(process.env['MAX_CONTEXT_CHARS'], 120000),

    jarvisApiKey: process.env['JARVIS_API_KEY'] || '',
    maxProviderCallsPerTask: parseNum(process.env['MAX_PROVIDER_CALLS_PER_TASK'], 6),
  };
}

export const config = loadConfig();
