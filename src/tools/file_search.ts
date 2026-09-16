import { FileDiscovery } from '../discovery/FileDiscovery.js';
import { FileSearchInput, FileSearchOutput, FileReadInput, FileReadOutput, FileSymbolSearchInput, FileSymbolSearchOutput, FileReferenceSearchInput, FileReferenceSearchOutput } from './ToolTypes.js';
import { ToolExecutionError } from './ToolTypes.js';

export function createFileSearchTool(discovery: FileDiscovery) {
  return {
    async run(input: FileSearchInput): Promise<FileSearchOutput> {
      if (!input || typeof input.pattern !== 'string' || input.pattern.length === 0) {
        throw new ToolExecutionError('INVALID_INPUT', 'pattern is required');
      }
      const max = input.maxResults ?? 100;
      try {
        const results = await discovery.searchByFilename(input.pattern, max);
        return { results };
      } catch (err: unknown) {
        throw new ToolExecutionError('SEARCH_ERROR', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
