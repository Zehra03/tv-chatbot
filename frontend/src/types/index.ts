/**
 * Domain tip katmanı barrel'ı (docs/frontend-architecture.md §7).
 * Tüketiciler tek yerden içe aktarır: `import type { HotelProduct } from '@/types'`.
 */
export * from './common'
export * from './search'
export * from './product'
export * from './reservation'
export * from './chat'
