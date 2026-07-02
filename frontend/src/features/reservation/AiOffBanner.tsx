import { ShieldCheck } from 'lucide-react'

/**
 * "AI devre dışı" bandı — kontrollü bölgenin anlatısını ekranda söyler:
 * chatbot yalnızca arar/listeler, booking'i bu AI'sız form yapar
 * (docs/architecture.md "0 token" yolu). Bilinçli olarak sade ve açık temalı.
 */
export function AiOffBanner() {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-primary/20 bg-primary/5 p-4">
      <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden />
      <p className="text-sm">
        <span className="font-semibold">Bu adımda yapay zekâ devre dışı</span> — rezervasyonu siz
        kontrol ediyor ve siz onaylıyorsunuz.
      </p>
    </div>
  )
}

export default AiOffBanner
