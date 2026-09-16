import { z } from 'zod';
import { ToolExecutionError } from './ToolTypes.js';

export const ToolMetaSchema = z.object({
  name: z.string(),
  description: z.string(),
  category: z.string(),
  inputSchema: z.any(),
  outputSchema: z.any(),
  permission: z.enum(['READ_ONLY', 'ANALYSIS', 'NETWORK', 'WRITE', 'EXECUTION', 'ADMIN']),
  timeoutMs: z.number().int().positive().optional(),
  readOnly: z.boolean().optional().default(true),
  supportsParallel: z.boolean().optional().default(false),
  version: z.string().optional().default('1.0'),
  available: z.boolean().optional().default(true),
});

export type ToolMeta = z.infer<typeof ToolMetaSchema>;

export interface RegisteredTool<TIn = any, TOut = any> {
  meta: ToolMeta;
  run: (input: TIn, opts?: { signal?: AbortSignal }) => Promise<TOut>;
}

export class ToolRegistry {
  private tools = new Map<string, RegisteredTool>();

  public register<TIn = any, TOut = any>(tool: RegisteredTool<TIn, TOut>) {
    const parsed = ToolMetaSchema.safeParse(tool.meta);
    if (!parsed.success) {
      throw new ToolExecutionError('INVALID_TOOL_META', JSON.stringify(parsed.error.format()));
    }
    this.tools.set(tool.meta.name, tool as RegisteredTool);
  }

  public unregister(name: string) {
    this.tools.delete(name);
  }

  public getTool(name: string): RegisteredTool | undefined {
    return this.tools.get(name);
  }

  public listTools(): ToolMeta[] {
    return Array.from(this.tools.values()).map((t) => t.meta);
  }
}

export const defaultToolRegistry = new ToolRegistry();
