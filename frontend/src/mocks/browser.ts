import { setupWorker } from 'msw/browser'
import { handlers } from './handlers'

/** Tarayıcı için MSW worker'ı — yalnızca development'ta başlatılır (main.tsx). */
export const worker = setupWorker(...handlers)
