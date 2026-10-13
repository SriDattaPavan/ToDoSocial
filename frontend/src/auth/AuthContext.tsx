import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import * as authApi from '../api/auth'
import {
  clearSession,
  getStoredToken,
  getStoredUser,
  storeSession,
  type User,
} from '../api/client'

type AuthContextValue = {
  user: User | null
  token: string | null
  login: (email: string, password: string) => Promise<void>
  register: (username: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(() => getStoredUser())
  const [token, setToken] = useState<string | null>(() => getStoredToken())

  const applySession = useCallback((result: { access_token: string; user: User }) => {
    storeSession(result.access_token, result.user)
    setToken(result.access_token)
    setUser(result.user)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    applySession(await authApi.login(email, password))
  }, [applySession])

  const register = useCallback(async (username: string, email: string, password: string) => {
    applySession(await authApi.register(username, email, password))
  }, [applySession])

  const logout = useCallback(() => {
    clearSession()
    setToken(null)
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, token, login, register, logout }),
    [user, token, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within AuthProvider')
  }
  return context
}
