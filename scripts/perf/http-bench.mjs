import { performance } from 'node:perf_hooks'

const env = process.env
const baseUrl = env.BASE_URL ?? 'http://localhost:8080'
const targetPath = env.TARGET_PATH ?? '/courses/1'
const method = (env.METHOD ?? 'GET').toUpperCase()
const requests = positiveInt(env.REQUESTS, 100)
const concurrency = Math.min(positiveInt(env.CONCURRENCY, 10), requests)
const timeoutMs = positiveInt(env.TIMEOUT_MS, 30_000)
const successMode = env.SUCCESS_MODE ?? 'transport'
const requestBody = env.REQUEST_BODY || undefined

let token = env.TOKEN
if (!token && env.LMS_USERNAME && env.LMS_PASSWORD) {
  const response = await fetch(`${baseUrl}/auth/login`, {
    method: 'POST',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify({ username: env.LMS_USERNAME, password: env.LMS_PASSWORD }),
    signal: AbortSignal.timeout(timeoutMs),
  })
  const payload = await response.json()
  token = payload?.data?.token
  if (!response.ok || !token) {
    throw new Error(`Login failed with HTTP ${response.status}`)
  }
}

const latencies = []
const statuses = new Map()
let passed = 0
let failed = 0
let cursor = 0
const startedAt = performance.now()

await Promise.all(Array.from({ length: concurrency }, async () => {
  while (true) {
    const index = cursor++
    if (index >= requests) return
    const started = performance.now()
    try {
      const headers = {}
      if (token) headers.authorization = `Bearer ${token}`
      if (requestBody !== undefined) headers['content-type'] = 'application/json'
      const response = await fetch(`${baseUrl}${targetPath}`, {
        method,
        headers,
        body: requestBody,
        signal: AbortSignal.timeout(timeoutMs),
      })
      const text = await response.text()
      statuses.set(response.status, (statuses.get(response.status) ?? 0) + 1)
      if (isSuccess(response, text, successMode)) passed++
      else failed++
    } catch {
      statuses.set('network-error', (statuses.get('network-error') ?? 0) + 1)
      failed++
    } finally {
      latencies.push(performance.now() - started)
    }
  }
}))

const durationMs = performance.now() - startedAt
latencies.sort((a, b) => a - b)
const result = {
  target: `${method} ${targetPath}`,
  requests,
  concurrency,
  passed,
  failed,
  successRate: round(passed / requests * 100),
  throughputRps: round(requests / (durationMs / 1000)),
  durationMs: round(durationMs),
  latencyMs: {
    mean: round(latencies.reduce((sum, value) => sum + value, 0) / latencies.length),
    p50: round(percentile(latencies, 0.50)),
    p95: round(percentile(latencies, 0.95)),
    p99: round(percentile(latencies, 0.99)),
    max: round(latencies.at(-1)),
  },
  httpStatuses: Object.fromEntries(statuses),
  successMode,
  measuredAt: new Date().toISOString(),
}

console.log(JSON.stringify(result, null, 2))

function isSuccess(response, text, mode) {
  if (!response.ok) return false
  if (mode === 'transport') return true
  if (mode === 'sse-done') {
    return /(?:^|\n)event:\s*done\s*(?:\n|$)/m.test(text)
      && !/(?:^|\n)event:\s*error\s*(?:\n|$)/m.test(text)
  }
  if (mode === 'lms-json') {
    try {
      const payload = JSON.parse(text)
      return payload?.code === 1 || payload?.code === 200
    } catch {
      return false
    }
  }
  throw new Error(`Unknown SUCCESS_MODE: ${mode}`)
}

function percentile(values, ratio) {
  const index = Math.max(0, Math.ceil(values.length * ratio) - 1)
  return values[index]
}

function positiveInt(value, fallback) {
  const parsed = Number.parseInt(value ?? '', 10)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : fallback
}

function round(value) {
  return Math.round(value * 1000) / 1000
}
