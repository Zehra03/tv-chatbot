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
    // uyarı düşsün (asset istekleri serbest).
    onUnhandledRequest(request, print) {
      if (new URL(request.url).pathname.startsWith('/api/')) print.warning()
    },
  })

  // Hard refresh (Ctrl+F5) sayfayı service worker kontrolü DIŞINDA yükler:
  // worker.start() başarılı görünse de istekler mock'a uğramadan Vite SPA
  // fallback'ine düşer (200 + text/html → ApiError). Tek seferlik normal
  // reload kontrolü geri kazandırır; sessionStorage bayrağı döngüyü önler.
  const controlled = Boolean(navigator.serviceWorker?.controller)
  if (!controlled && !sessionStorage.getItem('pax-msw-reload')) {
    sessionStorage.setItem('pax-msw-reload', '1')
    window.location.reload()
  } else if (controlled) {
    sessionStorage.removeItem('pax-msw-reload')
  }
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <Providers />
    </StrictMode>,
  )
})
