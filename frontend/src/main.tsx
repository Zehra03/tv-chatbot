import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import { Providers } from '@/app/providers'

/**
 * Uygulama HER ZAMAN gerçek backend'e konuşur (`VITE_API_BASE_URL`).
 *
 * Burada bir zamanlar MSW (sahte backend) önyüklemesi vardı; bir env anahtarıyla açılıyordu.
 * Kaldırıldı: tarayıcıda hiçbir ayarla sahte veri gösterilememeli. Bir admin panelinde bu
 * özellikle önemli — uydurma kullanıcı/rezervasyon listesi, gerçek olduğu sanılıp karar
 * verilebilecek bir şeydir.
 *
 * MSW projeden tümüyle gitmedi: `src/mocks/` hâlâ var ama YALNIZCA birim testlerinde
 * (`src/mocks/server.ts`, node tarafı) kullanılır. Orada sahte backend "gösterilen veri" değil,
 * testin girdisidir — testler bu sayede Postgres/TourVisio olmadan koşar.
 */
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Providers />
  </StrictMode>,
)
