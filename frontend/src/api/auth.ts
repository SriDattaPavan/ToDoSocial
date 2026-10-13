import { apiRequest, type LoginResponse } from './client'

export function register(username: string, email: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>(
    '/auth/register',
    {
      method: 'POST',
      body: JSON.stringify({
        username,
        email,
        password,
      }),
    },
    false,
  )
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return apiRequest<LoginResponse>(
    '/auth/login',
    {
      method: 'POST',
      body: JSON.stringify({
        email,
        password,
      }),
    },
    false,
  )
}
