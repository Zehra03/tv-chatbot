import type { RequestHandler } from 'msw'

/**
 * MSW istek yakalayıcıları. Her sayfa/feature kendi handler'ını (ör. chat,
 * hotels, flights, reservation) buraya ekler (docs/frontend-architecture.md §6).
 * Şimdilik boş; yakalanmayan istekler main.tsx'te `onUnhandledRequest: 'bypass'`
 * ile geçirilir.
 */
export const handlers: RequestHandler[] = []
