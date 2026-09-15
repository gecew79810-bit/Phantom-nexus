// Lightweight routing smoke test for future CI. Keep provider calls mocked in CI.
import assert from 'node:assert/strict';

assert.equal(typeof fetch, 'function');
console.log('Smoke checks loaded. Add mocked provider integration tests before production deployment.');
