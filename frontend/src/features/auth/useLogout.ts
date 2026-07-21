import { useNavigate } from 'react-router-dom'
import { authApi } from '@/api'
import { useAppDispatch } from '@/app/hooks'
import { logout } from '@/features/auth/authSlice'

/**
 * Tek çıkış yolu — Layout ve ProfilePage aynı davranışı paylaşır.
 *
 * SIRA ÖNEMLİ: `authApi.logout()` jeton HÂLÂ kuruluyken çağrılır. `dispatch(logout())` ilk
 * gelseydi setAuthToken(null) çalışır, istek yetkisiz giderdi ve sunucu hangi oturumun
 * kapandığını bilemezdi. Bu yüzden `await` var — istek yalnız "ateşlenip" bırakılsaydı,
 * Axios'un request interceptor'ı bir mikro-görevde çalıştığı için Authorization başlığını
 * okumaya sıra geldiğinde jeton çoktan null olabilirdi (yarış).
 *
 * Eskiden `authApi.logout()` hiç çağrılmıyordu (yalnız testlerden geçiyordu): "Çıkış" sadece
 * tarayıcının kopyasını siliyor, UZUN ÖMÜRLÜ refresh jetonu sunucuda doğal ömrü boyunca
 * geçerli kalıyordu. Jetonu önceden ele geçiren biri (localStorage'ı okuyan bir script, profil
 * kopyası, yedek) çıkıştan sonra da yeni access jetonu üretmeye devam edebiliyordu.
 *
 * Ağ hatası çıkışı ENGELLEMEZ: yerel oturum her hâlükârda kapanır — kullanıcı "çıktım" dediyse
 * çıkmıştır. Sunucu erişilemezse jeton doğal ömrüyle ölür; yerel oturumu açık bırakmak daha kötü.
 * React Query cache'ini ayrıca temizlemeye gerek yok: SessionManager kimlik değişimini görüp
 * (providers.tsx) cache'i düşürüyor.
 */
export function useLogout() {
  const dispatch = useAppDispatch()
  const navigate = useNavigate()

  return async () => {
    try {
      await authApi.logout()
    } catch {
      /* sunucuya ulaşılamadı — yerel çıkış yine de tamamlanır */
    }
    dispatch(logout())
    navigate('/login', { replace: true })
  }
}
