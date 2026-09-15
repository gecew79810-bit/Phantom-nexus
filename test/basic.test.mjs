import { test } from 'node:test';
import assert from 'node:assert';

test('basic arithmetic test', () => {
  assert.strictEqual(1 + 1, 2);
});

test('string comparison', () => {
  assert.strictEqual('hello' + ' world', 'hello world');
});
