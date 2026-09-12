import test from 'node:test'
import assert from 'node:assert/strict'

import { normalizeHitlStatus } from './hitl.js'

test('numeric HITL status from backend is normalized for the UI', () => {
  assert.equal(normalizeHitlStatus('0'), 'PENDING')
  assert.equal(normalizeHitlStatus(1), 'APPROVED')
  assert.equal(normalizeHitlStatus('2'), 'REJECTED')
})

test('named HITL status remains compatible and unknown values stay explicit', () => {
  assert.equal(normalizeHitlStatus('PENDING'), 'PENDING')
  assert.equal(normalizeHitlStatus('approved'), 'APPROVED')
  assert.equal(normalizeHitlStatus(undefined), 'UNKNOWN')
  assert.equal(normalizeHitlStatus('unexpected'), 'UNKNOWN')
})
