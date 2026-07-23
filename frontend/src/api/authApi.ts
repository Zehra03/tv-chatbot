import { apiClient } from './client'

/**
 * Kimlik endpoint'leri. Gerçek doğrulama backend'de; frontend sadece oturumu
 * Redux'ta tutar (docs/frontend-architecture.md §5). Token OPAK bir dizedir —
 * frontend içeriğini yorumlamaz, hiçbir sır burada tutulmaz.
 *   POST  /api/v1/auth/register
 *   POST  /api/v1/auth/login
 *   POST  /api/v1/auth/refresh   (kısa ömürlü access jetonunu refresh ile yeniler)
 *   POST  /api/v1/auth/logout
 *   POST  /api/v1/auth/reset-password   (şifreyi doğrudan değiştirir — jetonsuz, e-posta bağlantısı yok)
 *   GET   /api/v1/auth/me    (Authorization: Bearer <token> — interceptor ekler)
 *   PATCH /api/v1/auth/me    (oturumdaki kullanıcının e-postasını günceller)
 * LoginPage bu API'ye bağlıdır; jetonlar authSlice → setAuthToken/setRefreshToken
 * ile taşınır. refresh çağrısı normalde client.ts interceptor'ında otomatik yapılır.
 */

/** Yetki rolü — backend `auth/domain/Role` ile birebir. */
export type UserRole = 'USER' | 'ADMIN'

export interface AuthUser {
  id: string
  email: string
  name?: string
  /**
   * Oturumdaki kullanıcının rolü. Admin panelinin GÖSTERİLİP gösterilmeyeceğine bunun için
   * bakılır — ama bu bir güvenlik sınırı DEĞİLDİR: gerçek koruma backend'de, SecurityConfig
   * `/api/v1/admin/**` yolunu `hasRole('ADMIN')` ile kapatır. Bu alanı kurcalamak yalnızca
   * her isteği 403 alan bir ekran açar. Eski/misafir oturumlarda tanımsız olabilir.
   */
  role?: UserRole
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

export interface UpdateEmailRequest {
  email: string
}

export interface ResetPasswordRequest {
  email: string
  password: string
}

export interface AuthResponse {
  user: AuthUser
  /** Opak, kısa ömürlü access jetonu (mock'ta sahte dize). */
  token: string
  /** Opak, uzun ömürlü refresh jetonu — /auth/refresh ile yeni access jetonu alır. */
  refreshToken: string
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

  async refresh(refreshToken: string): Promise<AuthResponse> {
    const res = await apiClient.post<AuthResponse>('/api/v1/auth/refresh', { refreshToken })
    return res.data
  },

  /**
   * Oturumu SUNUCUDA kapatır (refresh jetonunu iptal eder). Kullanıcı çıkışı bunu beklediği
   * için kısa timeout: sunucu asılırsa genel 60 sn'lik sınır "Çıkış"ı bir dakika kilitlerdi.
   * Süre dolarsa çağıran yutar ve yerel oturumu yine de kapatır (useLogout).
   */
  async logout(): Promise<void> {
    await apiClient.post('/api/v1/auth/logout', undefined, { timeout: 5000 })
  },

  /**
   * Şifreyi doğrudan değiştirir — e-posta bağlantısı yok (SMTP kapsam dışı). Jetonsuz
   * (public) çağrıdır: kullanıcı zaten şifresini unuttuğu için geçerli jetonu olmayabilir.
   * Bağlantılı akıştan farklı olarak doğrudan sıfırladığı için e-postanın kayıtlı olup
   * olmadığını zorunlu olarak ele verir — kayıtsız e-posta 404 EMAIL_NOT_FOUND döner.
   */
  async resetPassword(body: ResetPasswordRequest): Promise<void> {
    await apiClient.post('/api/v1/auth/reset-password', body)
  },

  async me(): Promise<AuthUser> {
    const res = await apiClient.get<AuthUser>('/api/v1/auth/me')
    return res.data
  },

  /** Oturumdaki kullanıcının e-postasını günceller; güncel AuthUser döner. */
  async updateEmail(body: UpdateEmailRequest): Promise<AuthUser> {
    const res = await apiClient.patch<AuthUser>('/api/v1/auth/me', body)
    return res.data
  },
}
