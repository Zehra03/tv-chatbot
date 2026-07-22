import { apiClient } from './client'
import type { Page, ReservationStatus, ReservationSummary } from '@/types'
import type { OutcomeResponse, CancelRequest, CancelResult } from './reservationApi'

/**
 * Admin panel endpoint'leri — backend `admin/` modülüyle birebir sözleşme:
 *   GET /api/v1/admin/dashboard/stats            → özet sayaçlar
 *   GET /api/v1/admin/users?page&size            → kullanıcılar (Page zarfı)
 *   GET /api/v1/admin/reservations?page&size&q&status&productType → rezervasyonlar (Page zarfı)
 *   PUT /api/v1/admin/reservations/{id}/status   → iptal
 *
 * Hepsi ROLE_ADMIN ister; SecurityConfig `/api/v1/admin/**` yolunu `hasRole('ADMIN')` ile
 * kapatır, yani yetkisiz bir jetonla bu çağrılar 403 döner. Frontend'deki rol kontrolü
 * yalnızca ekranı gizlemek içindir, koruma buradan gelmez.
 */

/** Dashboard özet sayaçları (backend `DashboardStatsResponse`). */
export interface DashboardStats {
  totalReservations: number
  /** Kayıtlı kullanıcı sayısı (backend alan adı `activeUsers`). */
  activeUsers: number
  /** Para birimi → onaylanmış rezervasyonların toplamı. */
  totalRevenueByCurrency: Record<string, number>
  /**
   * Ürün tipi → REZERVASYON sayısı ("hotel" | "flight" | "combined"). Envanter DEĞİLDİR:
   * sistemde uçuş/otel kataloğu tutulmuyor, aranabilir ürünler TourVisio'dan canlı geliyor.
   */
  reservationsByProductType: Record<string, number>
}

/** Kullanıcı listesi satırı (backend `UserAdminDto`). */
export interface AdminUser {
  id: number
  email: string
  displayName?: string | null
  role: string
  createdAt: string
}

/**
 * Admin rezervasyon listesi satırı (backend `AdminReservationResponse`).
 *
 * `ReservationSummary`'yi genişletir: aynı başlık alanları + rezervasyonun HANGİ HESABA ait
 * olduğu. Kullanıcının kendi listesinde bu alanlar yoktur — sahip kimliği yalnızca admin
 * görünümünün sorusudur, o yüzden backend'de de ayrı bir DTO.
 *
 * Misafir rezervasyonlarda `ownerEmail`/`ownerName` null'dur (hesap yok, `guest` bunu söyler).
 * Misafirin taşıyıcı jetonu hiçbir zaman dönmez.
 */
export interface AdminReservationRow extends ReservationSummary {
  ownerEmail?: string | null
  ownerName?: string | null
}

/** Rezervasyon listesi sorgusu; tüm alanlar opsiyonel (yok = filtre yok). */
export interface AdminReservationQuery {
  /** 0 tabanlı sayfa indeksi. */
  page?: number
  size?: number
  /** PNR metni — backend hem iç numarada hem TourVisio numarasında arar. */
  q?: string
  status?: ReservationStatus
  productType?: string
}

export const adminApi = {
  async getStats(): Promise<DashboardStats> {
    const res = await apiClient.get<DashboardStats>('/api/v1/admin/dashboard/stats')
    return res.data
  },

  async listUsers(page = 0, size = 20): Promise<Page<AdminUser>> {
    const res = await apiClient.get<Page<AdminUser>>('/api/v1/admin/users', {
      params: { page, size },
    })
    return res.data
  },

  async listReservations(query: AdminReservationQuery = {}): Promise<Page<AdminReservationRow>> {
    const { page = 0, size = 20, q, status, productType } = query
    const res = await apiClient.get<Page<AdminReservationRow>>('/api/v1/admin/reservations', {
      // Boş/tanımsız filtreler hiç gönderilmez: backend boş `q`'yu zaten "filtre yok" sayıyor
      // ama isteği de kirletmeyelim — Ağ sekmesinde ne filtrelendiği okunur kalsın.
      params: {
        page,
        size,
        ...(q?.trim() ? { q: q.trim() } : {}),
        ...(status ? { status } : {}),
        ...(productType ? { productType } : {}),
      },
    })
    return res.data
  },

  /**
   * Rezervasyonu iptal eder. `reservationApi.cancel` ile AYNI çok-durumlu okuma: axios her 2xx'i
   * resolve eder ama 202 "iptal edildi" DEMEK DEĞİLDİR — TourVisio yanıtsız kalınca backend
   * CANCEL_OUTCOME_UNKNOWN + 202 döner ve sonucu bilmediğini söyler. Durum tipe taşınır ki
   * çağıran 202'yi yanlışlıkla başarı sanmasın.
   */
  async cancelReservation(id: number, body: CancelRequest): Promise<CancelResult> {
    const res = await apiClient.put<OutcomeResponse>(`/api/v1/admin/reservations/${id}/status`, body)
    return res.status === 202
      ? { kind: 'pending', outcome: res.data }
      : { kind: 'cancelled', outcome: res.data }
  },
}
