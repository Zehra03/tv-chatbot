import { setupServer } from 'msw/node'
import { handlers } from './handlers'

/** Node (vitest) için MSW sunucusu — tarayıcıdaki worker'ın test karşılığı.
 * Aynı handler seti kullanılır; testler gerçek endpoint sözleşmesine çalışır. */
export const server = setupServer(...handlers)
