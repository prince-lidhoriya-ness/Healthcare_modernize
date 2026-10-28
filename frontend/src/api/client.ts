import axios from 'axios'

// All calls go to /api - nginx (Docker) or the Vite dev proxy forwards them to the backend.
export const api = axios.create({
  baseURL: '/api',
  headers: { 'Content-Type': 'application/json' },
})

// Bearer token of the signed-in user, set by AuthProvider
let authToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function setAuthToken(token: string | null) {
  authToken = token
}

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler
}

api.interceptors.request.use((config) => {
  if (authToken) {
    config.headers.Authorization = `Bearer ${authToken}`
  }
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    // An expired or revoked session: sign out. A failed login attempt is not a session problem.
    const url: string = error?.config?.url || ''
    const isLoginCall = url.startsWith('/auth/login') || url.startsWith('/auth/register')
    if (error?.response?.status === 401 && !isLoginCall && onUnauthorized) {
      onUnauthorized()
    }
    return Promise.reject(error)
  },
)

export function errorMessage(err: any): string {
  if (err && err.response && err.response.data && err.response.data.message) {
    return err.response.data.message
  }
  if (err && err.message) {
    return err.message
  }
  return 'Something went wrong'
}
