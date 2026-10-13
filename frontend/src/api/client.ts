export type User = {
  id: number
  username: string
  email: string
}

export type LoginResponse = {
  access_token: string
  expires_at: string
  user: User
}

export type TaskStatus = 'todo' | 'in_progress' | 'done' | 'archived'
export type TaskArchiveReason = 'unfriended' | 'manual_delete'

export type Task = {
  id: number
  assigner_id: number
  assignee_id: number
  assigner_username: string
  assignee_username: string
  title: string
  description: string | null
  due_date: string | null
  status: TaskStatus
  archived_at: string | null
  archived_reason: TaskArchiveReason | null
  created_at: string
  updated_at: string
}

export type FriendshipStatus = 'pending' | 'accepted' | 'removed'

export type FriendRequest = {
  id: number
  user_a: number
  user_b: number
  requested_by: number
  other_username: string
  requested_by_username: string
  status: FriendshipStatus
  created_at: string
  updated_at: string
}

export class ApiError extends Error {
  status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

const TOKEN_KEY = 'todosocial.token'
const USER_KEY = 'todosocial.user'

export function getStoredToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser(): User | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) {
    return null
  }
  try {
    return JSON.parse(raw) as User
  } catch {
    return null
  }
}

export function storeSession(token: string, user: User): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

function apiBaseUrl(): string {
  return import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'
}

export async function apiRequest<T>(
  path: string,
  options: RequestInit = {},
  authenticated = true,
): Promise<T> {
  const headers = new Headers(options.headers)
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (authenticated) {
    const token = getStoredToken()
    if (!token) {
      throw new ApiError(401, 'Authentication is required')
    }
    headers.set('Authorization', `Bearer ${token}`)
  }

  const response = await fetch(`${apiBaseUrl()}${path}`, {
    ...options,
    headers,
  })

  if (response.status === 204) {
    return undefined as T
  }

  const text = await response.text()
  const body = text ? (JSON.parse(text) as Record<string, unknown>) : null

  if (!response.ok) {
    const message =
      typeof body?.message === 'string' ? body.message : `Request failed (${response.status})`
    throw new ApiError(response.status, message)
  }

  return body as T
}
