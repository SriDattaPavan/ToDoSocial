import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/AuthContext'

export function LoginPage() {
  const { login, register } = useAuth()
  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  async function onSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setBusy(true)
    try {
      if (mode === 'register') {
        await register(username.trim(), email.trim(), password)
      } else {
        await login(email.trim(), password)
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="auth-page">
      <h1>ToDoSocial</h1>
      <h2>{mode === 'login' ? 'Login' : 'Register'}</h2>

      <form className="stack-form" onSubmit={onSubmit}>
        {mode === 'register' ? (
          <label>
            Username
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="jane_doe"
              autoComplete="username"
              required
            />
          </label>
        ) : null}

        <label>
          Email
          <input
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="jane@example.com"
            autoComplete="email"
            required
          />
        </label>

        <label>
          Password
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
            required
          />
        </label>

        {error ? <p className="error">{error}</p> : null}

        <button type="submit" className="primary" disabled={busy}>
          {busy ? 'Please wait…' : mode === 'login' ? 'Login' : 'Register'}
        </button>
      </form>

      <p className="auth-switch">
        {mode === 'login' ? (
          <>
            No account?{' '}
            <button type="button" className="linkish" onClick={() => setMode('register')}>
              Register
            </button>
          </>
        ) : (
          <>
            Already registered?{' '}
            <button type="button" className="linkish" onClick={() => setMode('login')}>
              Login
            </button>
          </>
        )}
      </p>
    </main>
  )
}
