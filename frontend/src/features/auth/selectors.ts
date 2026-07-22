import type { RootState } from '@/app/store'

/**
 * Aktif kimlik: üye id'si ya da misafir kimliği (oturum yoksa null).
 *
 * Sunucu verisi taşıyan her query key bunu taşımalı — aynı tarayıcıda kimlik değişince
 * (çıkış, başka hesapla giriş, misafire düşme) bir öncekinin cache'lenmiş verisi ASLA
 * gösterilmemeli. Ayrı kimlik = ayrı cache girdisi. Kimlik sınırında cache'i tümden atan
 * ikinci savunma providers.tsx'teki SessionManager'da.
 */
export function selectIdentity(state: RootState): string | null {
  return state.auth.user?.guest ? state.auth.guestId : (state.auth.user?.id ?? null)
}

/**
 * Oturumdaki kullanıcı admin mi — admin rotalarının ve panel bağlantısının tek okuma noktası.
 *
 * Bu bir GÖRÜNÜRLÜK kontrolüdür, güvenlik sınırı değil: gerçek koruma backend'de, SecurityConfig
 * `/api/v1/admin/**` yolunu `hasRole('ADMIN')` ile kapatır. localStorage'daki oturumu elle
 * düzenleyen biri yalnızca her isteği 403 alan bir ekran açar, veri göremez.
 *
 * Misafir açıkça elenir: misafirin rolü hiç olmaz, ama `guest` bayrağını da kontrol ederek
 * ileride misafir nesnesine bir rol sızsa bile kapı kapalı kalır.
 */
export function selectIsAdmin(state: RootState): boolean {
  const user = state.auth.user
  return !!user && !user.guest && user.role === 'ADMIN'
}
