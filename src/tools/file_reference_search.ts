import ts from 'typescript';
import path from 'path';
import { FileDiscovery } from '../discovery/FileDiscovery.js';
import { FileReferenceSearchInput, FileReferenceSearchOutput } from './ToolTypes.js';
import { ToolExecutionError } from './ToolTypes.js';

export function createFileReferenceSearchTool(discovery: FileDiscovery) {
  return {
    async run(input: FileReferenceSearchInput): Promise<FileReferenceSearchOutput> {
      if (!input || typeof input.path !== 'string' || input.path.length === 0 || typeof input.symbol !== 'string') {
        throw new ToolExecutionError('INVALID_INPUT', 'path and symbol are required');
      }

      try {
        const matches: Array<{ filePath: string; line?: number; snippet?: string; matchType?: string; confidence?: string }> = [];
        const origin = input.path;
        // read origin file content
        const originText = await discovery.readFileComplete(origin);

        // For JS/TS, resolve imports and search for occurrences of the symbol in other files
        const allCandidates = await discovery.searchInFileContents(input.symbol, 500);
        const uniquePaths = Array.from(new Set(allCandidates.map((r) => r.filePath)));

        for (const rel of uniquePaths) {
          try {
            const ext = path.extname(rel).toLowerCase();
            const text = await discovery.readFileComplete(rel);
            let matchType = 'substring';
            let confidence = 'low';
            let lineNum: number | undefined = undefined;
            // For TS/JS, parse and look for import specifiers, require calls, and references
            if (['.ts', '.tsx', '.js', '.jsx'].includes(ext)) {
              try {
                const sourceFile = ts.createSourceFile(rel, text, ts.ScriptTarget.Latest, true);
                let found = false;
                function walk(node) {
                  // import ... from '...' or import { sym } from '...'
                  if (ts.isImportDeclaration(node)) {
                    const importText = node.getText(sourceFile);
                    if (importText.includes(input.symbol) || importText.includes(`'${input.symbol}'`) || importText.includes(`"${input.symbol}"`)) {
                      found = true;
                      matchType = 'import_declaration';
                    }
                    // named imports
                    if (node.importClause && node.importClause.namedBindings) {
                      const named = node.importClause.namedBindings.getText(sourceFile);
                      if (named.includes(input.symbol)) {
                        found = true;
                        matchType = 'import_named';
                      }
                    }
                  }
                  // require('...') calls
                  if (ts.isCallExpression(node) && node.expression && node.expression.getText(sourceFile) === 'require') {
                    const txt = node.getText(sourceFile);
                    if (txt.includes(input.symbol)) {
                      found = true;
                      matchType = 'require_call';
                    }
                  }
                  // identifier references
                  if (ts.isIdentifier(node) && node.getText(sourceFile) === input.symbol) {
                    found = true;
                    matchType = 'identifier_reference';
                  }
                  ts.forEachChild(node, walk);
                }
                walk(sourceFile);
                if (found) {
                  confidence = 'high';
                }
                // attempt to derive a line number for first occurrence
                const idx = text.indexOf(input.symbol);
                if (idx >= 0) {
                  const before = text.slice(0, idx).split('\n');
                  lineNum = before.length;
                }
              } catch {
                // parsing failed: fallback to substring detection
                if (text.includes(input.symbol)) {
                  confidence = 'low';
                  matchType = 'substring';
                }
              }
            } else if (ext === '.kt') {
              // Kotlin heuristics
              if (text.match(new RegExp(`\b${input.symbol}\b`))) {
                confidence = 'medium';
                matchType = 'kotlin_reference';
                const idx = text.indexOf(input.symbol);
                if (idx >= 0) lineNum = text.slice(0, idx).split('\n').length;
              }
            } else {
              if (text.includes(input.symbol)) {
                confidence = 'low';
              }
            }

            if (confidence !== 'low' || text.includes(input.symbol)) {
              const snippet = text.split('\n').slice((lineNum || 1) - 2, (lineNum || 1) + 2).join('\n').slice(0, 400);
              matches.push({ filePath: rel, line: lineNum, snippet, matchType, confidence });
            }
          } catch (err) {
            // continue on error with single file
          }
        }

        return { matches };
      } catch (err: unknown) {
        throw new ToolExecutionError('REFERENCE_SEARCH_ERROR', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
