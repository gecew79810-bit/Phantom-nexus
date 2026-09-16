import fs from 'fs';
import path from 'path';
import crypto from 'crypto';

export interface FileMatch {
  filePath: string;
  line?: number;
  snippet?: string;
}

export interface FileMeta {
  path: string;
  size: number;
  mtimeMs: number;
  sha256: string;
}

export class FileDiscovery {
  private workspaceRoot: string;

  constructor(workspaceRoot?: string) {
    // Default to repository root
    this.workspaceRoot = workspaceRoot ? path.resolve(workspaceRoot) : process.cwd();
  }

  // Ensure path is inside workspace and prevent traversal/symlink escape
  private resolveSafe(p: string): string {
    if (path.isAbsolute(p)) {
      throw new Error('ABSOLUTE_PATH_NOT_ALLOWED');
    }
    if (p.includes('..')) {
      throw new Error('PATH_TRAVERSAL_NOT_ALLOWED');
    }
    const resolved = path.resolve(this.workspaceRoot, p);
    if (!resolved.startsWith(this.workspaceRoot)) {
      throw new Error('PATH_OUT_OF_WORKSPACE');
    }
    // Prevent symlink escapes by resolving realpath of both
    const realRoot = fs.realpathSync(this.workspaceRoot);
    const realResolved = fs.realpathSync(resolved);
    if (!realResolved.startsWith(realRoot)) {
      throw new Error('SYMLINK_ESCAPE_NOT_ALLOWED');
    }
    return resolved;
  }

  public async searchByFilename(pattern: string, maxResults = 100): Promise<FileMatch[]> {
    // Simple wildcard search using substring match for now
    const results: FileMatch[] = [];
    const walk = (dir: string) => {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
          walk(full);
        } else if (e.isFile()) {
          if (e.name.includes(pattern)) {
            results.push({ filePath: path.relative(this.workspaceRoot, full) });
            if (results.length >= maxResults) return;
          }
        }
      }
    };
    walk(this.workspaceRoot);
    return results;
  }

  public async searchInFileContents(query: string, maxResults = 100): Promise<FileMatch[]> {
    const results: FileMatch[] = [];
    const walk = (dir: string) => {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
          walk(full);
        } else if (e.isFile()) {
          const content = fs.readFileSync(full, 'utf8');
          const lines = content.split('\n');
          for (let i = 0; i < lines.length; i++) {
            if (lines[i].includes(query)) {
              results.push({ filePath: path.relative(this.workspaceRoot, full), line: i + 1, snippet: lines[i].slice(0, 400) });
              if (results.length >= maxResults) return;
            }
          }
        }
      }
    };
    walk(this.workspaceRoot);
    return results;
  }

  public async readFileComplete(relPath: string): Promise<string> {
    const safe = this.resolveSafe(relPath);
    return fs.readFileSync(safe, 'utf8');
  }

  public async readFileRange(relPath: string, startLine: number, endLine: number): Promise<string> {
    const safe = this.resolveSafe(relPath);
    const content = fs.readFileSync(safe, 'utf8');
    const lines = content.split('\n');
    const slice = lines.slice(Math.max(0, startLine - 1), Math.min(lines.length, endLine));
    return slice.join('\n');
  }

  public async readFileChunked(relPath: string, chunkSize = 64 * 1024): Promise<AsyncGenerator<string>> {
    const safe = this.resolveSafe(relPath);
    const stat = fs.statSync(safe);
    const stream = fs.createReadStream(safe, { highWaterMark: chunkSize, encoding: 'utf8' });
    async function* gen() {
      for await (const chunk of stream) {
        yield chunk as string;
      }
    }
    return gen();
  }

  public async symbolSearch(symbol: string, extensions = ['.ts', '.js', '.java', '.kt', '.py']): Promise<FileMatch[]> {
    // naive symbol search by file extension and substring match
    const results: FileMatch[] = [];
    const walk = (dir: string) => {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
          walk(full);
        } else if (e.isFile()) {
          if (!extensions.includes(path.extname(e.name))) continue;
          const content = fs.readFileSync(full, 'utf8');
          if (content.includes(symbol)) {
            results.push({ filePath: path.relative(this.workspaceRoot, full), snippet: content.slice(0, 400) });
          }
        }
      }
    };
    walk(this.workspaceRoot);
    return results;
  }

  public async referenceSearch(relPath: string, symbol: string): Promise<FileMatch[]> {
    // Find files that import or reference the symbol — naive substring search for now.
    const safeTarget = this.resolveSafe(relPath);
    const results: FileMatch[] = [];
    const walk = (dir: string) => {
      const entries = fs.readdirSync(dir, { withFileTypes: true });
      for (const e of entries) {
        const full = path.join(dir, e.name);
        if (e.isDirectory()) {
          walk(full);
        } else if (e.isFile()) {
          const content = fs.readFileSync(full, 'utf8');
          if (content.includes(symbol) || content.includes(path.basename(safeTarget))) {
            results.push({ filePath: path.relative(this.workspaceRoot, full), snippet: content.slice(0, 400) });
          }
        }
      }
    };
    walk(this.workspaceRoot);
    return results;
  }

  public async fileMeta(relPath: string): Promise<FileMeta> {
    const safe = this.resolveSafe(relPath);
    const stat = fs.statSync(safe);
    const content = fs.readFileSync(safe);
    const hash = crypto.createHash('sha256').update(content).digest('hex');
    return { path: relPath, size: stat.size, mtimeMs: stat.mtimeMs, sha256: hash };
  }
}
