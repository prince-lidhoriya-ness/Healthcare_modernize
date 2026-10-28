import { api } from './client'
import type { AuthUser, CustomerSignupInput, LoginResponse, Role } from '../types'

export const authApi = {
  login: (username: string, password: string, portal: Role) =>
    api.post<LoginResponse>('/auth/login', { username, password, portal }).then((r) => r.data),
  registerCustomer: (input: CustomerSignupInput) =>
    api.post<LoginResponse>('/auth/register', input).then((r) => r.data),
  me: () => api.get<AuthUser>('/auth/me').then((r) => r.data),
  logout: () => api.post<void>('/auth/logout').then(() => undefined),
}
