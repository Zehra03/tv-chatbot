import { Search } from 'lucide-react'

/**
 * Asistanın "meşgul" göstergesi — cam balon içinde üç zıplayan nokta.
 * Kontrat: kök role="status" taşır; animasyon CSS'tir ve motion-reduce ile kapanır.
 *
 * İki durum (PPMO K24):
 * - varsayılan: "Asistan yazıyor…" (sr-only) + noktalar — kısa/anlık yanıtlar için.
 * - `searching`: arama saniyelerce sürerken görünür "Arıyorum…" etiketi + arama
 *   simgesi. Çağıran taraf bunu isteğin süresine bağlar (bkz. useDelayedFlag),
 *   böylece hızlı takip soruları etiket değiştirmez, yalnız gerçek arama gösterir.
 */
export function TypingIndicator({ searching = false }: { searching?: boolean }) {
  const dots = (
    <span className="flex items-center gap-1.5" aria-hidden="true">
      {[0, 1, 2].map((i) => (
        <span
          key={i}
          className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground/80 motion-reduce:animate-none"
          style={{ animationDelay: `${i * 0.15}s` }}
        />
      ))}
    </span>
  )

  if (searching) {
    return (
      <div role="status" className="flex justify-start">
        <div className="glass-card flex items-center gap-2 rounded-bl-md px-4 py-3">
          <Search
            className="h-4 w-4 shrink-0 animate-pulse text-primary motion-reduce:animate-none"
            aria-hidden="true"
          />
          {/* Görünür metin → role="status" ekran okuyucuya kendisi duyurur (sr-only gereksiz). */}
          <span className="text-sm text-muted-foreground">Arıyorum…</span>
          {dots}
        </div>
      </div>
    )
  }

  return (
    <div role="status" className="flex justify-start">
      <div className="glass-card flex items-center gap-1.5 rounded-bl-md px-4 py-3">
        <span className="sr-only">Asistan yazıyor…</span>
        {dots}
      </div>
    </div>
  )
}

export default TypingIndicator
