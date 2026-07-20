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
