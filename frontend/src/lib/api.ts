const TOKEN_KEY = 'batchmaker.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

/** Error carrying the backend's stable error code so callers can branch on it. */
export class ApiError extends Error {
  // Declared as plain fields rather than constructor parameter properties,
  // which this project's `erasableSyntaxOnly` setting disallows.
  readonly status: number
  readonly errorCode: string
  readonly details?: Record<string, unknown>

  constructor(
    status: number,
    errorCode: string,
    message: string,
    details?: Record<string, unknown>,
  ) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = errorCode
    this.details = details
  }
}

type Options = {
  method?: string
  body?: unknown
  signal?: AbortSignal
  /** Solver calls legitimately take minutes; everything else should be quick. */
  timeoutMs?: number
}

export async function api<T>(path: string, options: Options = {}): Promise<T> {
  const { method = 'GET', body, signal, timeoutMs = 30_000 } = options

  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), timeoutMs)
  signal?.addEventListener('abort', () => controller.abort())

  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response: Response
  try {
    response = await fetch(`/api${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    })
  } catch (error) {
    clearTimeout(timer)
    if ((error as Error).name === 'AbortError') {
      throw new ApiError(0, 'TIMEOUT', 'The request took too long and was cancelled.')
    }
    throw new ApiError(0, 'NETWORK_ERROR', 'The server could not be reached.')
  }
  clearTimeout(timer)

  if (response.status === 204) return undefined as T

  const text = await response.text()
  const payload = text ? safeParse(text) : null

  if (!response.ok) {
    // A 401 means the token is gone or expired; drop it so the app returns to login.
    if (response.status === 401) setToken(null)
    const envelope = payload as
      | { errorCode?: string; message?: string; details?: Record<string, unknown> }
      | null
    throw new ApiError(
      response.status,
      envelope?.errorCode ?? 'UNKNOWN',
      envelope?.message ?? `Request failed with status ${response.status}.`,
      envelope?.details,
    )
  }

  return payload as T
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text)
  } catch {
    return { message: text }
  }
}

/** Formats "09:00:00" or "09:00" as "09:00". */
export function hhmm(time?: string | null): string {
  if (!time) return ''
  return time.slice(0, 5)
}

export function titleCase(value?: string | null): string {
  if (!value) return ''
  return value.charAt(0) + value.slice(1).toLowerCase()
}

export function formatDateTime(iso?: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatDate(iso?: string | null): string {
  if (!iso) return '—'
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) return '—'
  return date.toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
}
