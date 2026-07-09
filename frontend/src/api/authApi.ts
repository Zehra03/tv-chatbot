import { apiClient } from './client'

/**
 * Kimlik endpoint'leri. Gerçek doğrulama backend'de; frontend sadece oturumu
 * Redux'ta tutar (docs/frontend-architecture.md §5). Token OPAK bir dizedir —
 * frontend içeriğini yorumlamaz, hiçbir sır burada tutulmaz.
 *   POST /api/v1/auth/register
 *   POST /api/v1/auth/login
 *   POST /api/v1/auth/logout
 *   GET  /api/v1/auth/me   (Authorization: Bearer <token> — interceptor ekler)
 * LoginPage bu API'ye bağlıdır; jeton authSlice → setAuthToken ile taşınır.
 */

export interface AuthUser {
  id: string
  email: string
  name?: string
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  email: string
  password: string
  name?: string
}

export interface AuthResponse {
  user: AuthUser
  /** Opak oturum jetonu (mock'ta sahte dize). */
  token: string
}

export const authApi = {
  async login(body: LoginRequest): Promise<AuthResponse> {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/login', body)
    return res.data
  },

  async register(body: RegisterRequest): Promise<AuthResponse> {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/register', body)
    return res.data
  },

  async logout(): Promise<void> {
    await apiClient.post('/api/v1/auth/logout')
  },

  async me(): Promise<AuthUser> {
    const res = await apiClient.get<AuthUser>('/api/v1/auth/me')
    return res.data
  },
}
