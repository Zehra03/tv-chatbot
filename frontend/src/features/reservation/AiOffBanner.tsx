import { ShieldCheck } from 'lucide-react'

/**
 * "AI devre dışı" bandı — kontrollü akışın anlatısını ekranda söyler:
 * chatbot yalnızca arar/listeler, booking'i bu AI'sız form yapar
 * (docs/architecture.md "0 token" yolu). Tüm yüzey koyuya geçtiğinden anlatı
 * artık yüzey rengiyle değil bu teal cam bantla taşınır.
 */
export function AiOffBanner() {
  return (
    <div className="flex items-start gap-3 rounded-xl border border-primary/30 bg-primary/10 p-4 text-foreground">
      <ShieldCheck className="mt-0.5 h-5 w-5 shrink-0 text-primary" aria-hidden />
      <p className="text-sm">
        <span className="font-semibold">Bu adımda yapay zekâ devre dışı</span> — rezervasyonu siz
        kontrol ediyor ve siz onaylıyorsunuz.
      </p>
    </div>
  )
}

export default AiOffBanner
