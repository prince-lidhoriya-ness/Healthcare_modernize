import { createContext, ReactNode, useCallback, useContext, useEffect, useState } from 'react'
import { setAuthToken, setUnauthorizedHandler } from '../api/client'
import { authApi } from '../api/authApi'
import type { AuthUser, CustomerSignupInput, Role } from '../types'

const STORAGE_KEY = 'ivirma.session'

interface Session {
  token: string
  user: AuthUser
}

interface AuthContextValue {
  user: AuthUser | null
  login: (username: string, password: string, portal: Role) => Promise<AuthUser>
  registerCustomer: (input: CustomerSignupInput) => Promise<AuthUser>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | null>(null)

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as Session) : null
  } catch {
    return null
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // storage unavailable (private mode): the session simply lasts until reload
  }
}

// Set the token before the first render so pages can call the API straight away
const initialSession = loadSession()
setAuthToken(initialSession?.token ?? null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(initialSession)

  const apply = useCallback((next: Session | null) => {
    setAuthToken(next?.token ?? null)
    saveSession(next)
    setSession(next)
  }, [])

  useEffect(() => {
    setUnauthorizedHandler(() => apply(null))
    return () => setUnauthorizedHandler(null)
  }, [apply])

  const login = async (username: string, password: string, portal: Role) => {
    const res = await authApi.login(username, password, portal)
    apply(res)
    return res.user
  }

  const registerCustomer = async (input: CustomerSignupInput) => {
    const res = await authApi.registerCustomer(input)
    apply(res)
    return res.user
  }

  const logout = async () => {
    try {
      await authApi.logout()
    } finally {
      apply(null)
    }
  }

  return (
    <AuthContext.Provider value={{ user: session?.user ?? null, login, registerCustomer, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
