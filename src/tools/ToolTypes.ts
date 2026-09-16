export type ToolResult<T> = { ok: true; data: T } | { ok: false; error: string };

export interface FileSearchInput { pattern: string; maxResults?: number }
export interface FileSearchOutput { results: Array<{ filePath: string; line?: number; snippet?: string }> }

export interface FileReadInput { path: string; range?: { startLine: number; endLine: number }; chunkSize?: number }
export interface FileReadOutput { text?: string; chunks?: string[]; meta?: { size: number; mtimeMs: number; sha256: string } }

export interface FileSymbolSearchInput { symbol: string; extensions?: string[] }
export interface FileSymbolSearchOutput { matches: Array<{ filePath: string; snippet?: string }> }

export interface FileReferenceSearchInput { path: string; symbol: string }
export interface FileReferenceSearchOutput { matches: Array<{ filePath: string; snippet?: string }> }

export interface ModelCallInput { prompt: string; maxTokens?: number; temperature?: number }
export interface ModelCallOutput { text: string; modelId: string; provider: string }

export class ToolExecutionError extends Error {
  public code: string;
  constructor(code: string, message: string) {
    super(message);
    this.code = code;
  }
}
