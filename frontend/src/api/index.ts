/**
 * API katmanı barrel'ı. Tüketiciler tek yerden içe aktarır:
 *   `import { hotelApi, apiClient } from '@/api'`
 * Endpoint modülleri backend sözleşmesini birebir yansıtır (§6).
 */
export { apiClient } from './client'
export type { ApiError } from './client'

export { chatApi } from './chatApi'
export type { SendMessageRequest, SendMessageResponse } from './chatApi'

export { hotelApi } from './hotelApi'
export { flightApi } from './flightApi'

export { reservationApi } from './reservationApi'
export type { CreateReservationRequest, ReservationPreview } from './reservationApi'

export { authApi } from './authApi'
export type { AuthUser, LoginRequest, RegisterRequest, AuthResponse } from './authApi'
