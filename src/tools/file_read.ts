import { FileDiscovery } from '../discovery/FileDiscovery.js';
import { FileReadInput, FileReadOutput } from './ToolTypes.js';
import { ToolExecutionError } from './ToolTypes.js';

export function createFileReadTool(discovery: FileDiscovery) {
  return {
    async run(input: FileReadInput): Promise<FileReadOutput> {
      if (!input || typeof input.path !== 'string' || input.path.length === 0) {
        throw new ToolExecutionError('INVALID_INPUT', 'path is required');
      }

      try {
        // Range read
        if (input.range) {
          const start = Number(input.range.startLine) || 1;
          const end = Number(input.range.endLine) || start;
          const text = await discovery.readFileRange(input.path, start, end);
          const meta = await discovery.fileMeta(input.path);
          return { text, meta };
        }

        // Chunked read
        if (input.chunkSize && Number(input.chunkSize) > 0) {
          const gen = await discovery.readFileChunked(input.path, input.chunkSize);
          const chunks: string[] = [];
          for await (const c of gen) {
            chunks.push(c);
          }
          const meta = await discovery.fileMeta(input.path);
          return { chunks, meta };
        }

        // Full read
        const text = await discovery.readFileComplete(input.path);
        const meta = await discovery.fileMeta(input.path);
        return { text, meta };
      } catch (err: unknown) {
        throw new ToolExecutionError('FILE_READ_ERROR', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
