import ts from 'typescript';
import path from 'path';
import { FileDiscovery } from '../discovery/FileDiscovery.js';
import { FileSymbolSearchInput, FileSymbolSearchOutput } from './ToolTypes.js';
import { ToolExecutionError } from './ToolTypes.js';

export function createFileSymbolSearchTool(discovery: FileDiscovery) {
  return {
    async run(input: FileSymbolSearchInput): Promise<FileSymbolSearchOutput> {
      if (!input || typeof input.symbol !== 'string' || input.symbol.length === 0) {
        throw new ToolExecutionError('INVALID_INPUT', 'symbol is required');
      }

      try {
        const matches = [];
        // Use discovery to find candidate files with relevant extensions
        const candidates = await discovery.searchByFilename(input.symbol, 200);
        // Additionally search in contents for broader matches
        const contentMatches = await discovery.searchInFileContents(input.symbol, 200);
        const byPath = new Map();
        for (const c of [...candidates, ...contentMatches]) {
          byPath.set(c.filePath, true);
        }
        const extensions = input.extensions ?? ['.ts', '.js', '.jsx', '.tsx', '.kt'];

        for (const rel of Array.from(byPath.keys())) {
          try {
            const ext = path.extname(rel).toLowerCase();
            const full = rel;
            const snippet = await discovery.readFileRange(rel, 1, 200).catch(() => '');
            let confidence = 'low';
            let matchType = 'heuristic';

            if (['.ts', '.tsx', '.js', '.jsx'].includes(ext)) {
              // Attempt to parse with TypeScript
              try {
                const text = await discovery.readFileComplete(rel);
                const sourceFile = ts.createSourceFile(rel, text, ts.ScriptTarget.Latest, true);
                // walk AST for declarations and references matching symbol
                let found = false;
                function walk(node) {
                  // simple checks for function/class/variable names
                  if ((ts.isFunctionDeclaration(node) || ts.isClassDeclaration(node) || ts.isInterfaceDeclaration(node)) && node.name && node.name.text === input.symbol) {
                    found = true;
                    matchType = ts.isFunctionDeclaration(node) ? 'function_declaration' : 'class_declaration';
                  }
                  if (ts.isVariableStatement(node)) {
                    for (const decl of node.declarationList.declarations) {
                      if (decl.name && decl.name.getText() === input.symbol) {
                        found = true;
                        matchType = 'variable_declaration';
                      }
                    }
                  }
                  if (ts.isImportDeclaration(node)) {
                    const spec = node.moduleSpecifier.getText().replace(/['"]/g, '');
                    if (spec.includes(input.symbol)) {
                      found = true;
                      matchType = 'import';
                    }
                  }
                  ts.forEachChild(node, walk);
                }
                walk(sourceFile);
                if (found) confidence = 'high';
              } catch {
                // parsing failure -> leave heuristic
              }
            } else if (ext === '.kt') {
              // Kotlin heuristic: look for 'fun symbol' or 'class symbol'
              const text = await discovery.readFileComplete(rel);
              if (text.match(new RegExp(`\b(fun|class|object)\s+${input.symbol}\b`))) {
                confidence = 'medium';
                matchType = 'kotlin_declaration';
              }
            } else {
              // fallback: content contains symbol
              const text = await discovery.readFileComplete(rel);
              if (text.includes(input.symbol)) {
                confidence = 'low';
                matchType = 'substring';
              }
            }

            matches.push({ filePath: rel, snippet: snippet.slice(0, 400), matchType, confidence });
          } catch (err) {
            // continue on single-file error
          }
        }

        return { matches };
      } catch (err: unknown) {
        throw new ToolExecutionError('SYMBOL_SEARCH_ERROR', err instanceof Error ? err.message : String(err));
      }
    },
  };
}
