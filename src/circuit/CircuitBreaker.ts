import { CircuitState } from '../types/index.js';

export interface CircuitBreakerOptions {
  failureThreshold: number;
  openTimeoutMs: number;
  halfOpenSuccessThreshold?: number;
}

export class CircuitBreaker {
  private state: CircuitState = 'HEALTHY';
  private failureCount = 0;
  private consecutiveSuccesses = 0;
  private lastFailureTime = 0;
  private nextAttemptTime = 0;
  private readonly failureThreshold: number;
  private readonly openTimeoutMs: number;
  private readonly halfOpenSuccessThreshold: number;

  constructor(
    public readonly key: string,
    options: CircuitBreakerOptions
  ) {
    this.failureThreshold = options.failureThreshold;
    this.openTimeoutMs = options.openTimeoutMs;
    this.halfOpenSuccessThreshold = options.halfOpenSuccessThreshold ?? 1;
  }

  public getState(): CircuitState {
    const now = Date.now();
    if (this.state === 'OPEN' && now >= this.nextAttemptTime) {
      this.state = 'RECOVERING';
    }
    return this.state;
  }

  public isAvailable(): boolean {
    const currentState = this.getState();
    return currentState === 'HEALTHY' || currentState === 'DEGRADED' || currentState === 'RECOVERING';
  }

  public recordSuccess(): void {
    if (this.state === 'RECOVERING') {
      this.consecutiveSuccesses++;
      if (this.consecutiveSuccesses >= this.halfOpenSuccessThreshold) {
        this.state = 'HEALTHY';
        this.failureCount = 0;
        this.consecutiveSuccesses = 0;
      }
    } else if (this.state === 'DEGRADED') {
      this.consecutiveSuccesses++;
      if (this.consecutiveSuccesses >= 2) {
        this.state = 'HEALTHY';
        this.failureCount = 0;
      }
    } else {
      this.failureCount = 0;
    }
  }

  public recordFailure(): void {
    this.lastFailureTime = Date.now();
    this.failureCount++;
    this.consecutiveSuccesses = 0;

    if (this.state === 'RECOVERING') {
      // Re-open immediately if probe fails
      this.state = 'OPEN';
      this.nextAttemptTime = Date.now() + this.openTimeoutMs;
    } else if (this.failureCount >= this.failureThreshold) {
      this.state = 'OPEN';
      this.nextAttemptTime = Date.now() + this.openTimeoutMs;
    } else if (this.failureCount >= Math.floor(this.failureThreshold / 2)) {
      this.state = 'DEGRADED';
    }
  }

  public forceOpen(durationMs?: number): void {
    this.state = 'OPEN';
    this.nextAttemptTime = Date.now() + (durationMs ?? this.openTimeoutMs);
  }

  public reset(): void {
    this.state = 'HEALTHY';
    this.failureCount = 0;
    this.consecutiveSuccesses = 0;
    this.lastFailureTime = 0;
    this.nextAttemptTime = 0;
  }

  public getMetrics() {
    return {
      key: this.key,
      state: this.getState(),
      failureCount: this.failureCount,
      consecutiveSuccesses: this.consecutiveSuccesses,
      lastFailureTime: this.lastFailureTime,
      nextAttemptTime: this.nextAttemptTime,
    };
  }
}
