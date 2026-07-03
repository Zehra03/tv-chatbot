import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { Providers } from '@/app/providers'

/**
 * Sahte backend'i (MSW) yalnızca development'ta ve VITE_ENABLE_MSW !== 'false'
 * iken başlat. Worker dinamik import edilir ki production bundle'ına girmesin.
 */
async function enableMocking() {
  if (!import.meta.env.DEV || import.meta.env.VITE_ENABLE_MSW === 'false') return
  const { worker } = await import('@/mocks/browser')
  await worker.start({
    // Handler'ı olmayan /api istekleri sessizce backend'e sızmasın — konsola
    // uyarı düşsün (asset istekleri serbest). Not: hard refresh'te service
    // worker'ın tamamen baypas edilmesini bu yakalayamaz; o durumda apiClient
    // interceptor'ı HTML yanıtı ApiError'a çevirir.
    onUnhandledRequest(request, print) {
      if (new URL(request.url).pathname.startsWith('/api/')) print.warning()
    },
  })
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers />
    </StrictMode>,
  )
})
