/**
 * "Asistan yazıyor" göstergesi — cam balon içinde üç zıplayan nokta.
 * Kontrat: kök role="status" taşır, etiket ekran okuyucuya sr-only ile
 * duyurulur; noktalar dekoratiftir (aria-hidden). Animasyon CSS'tir ve
 * motion-reduce ile kapanır.
 */
export function TypingIndicator() {
  return (
    <div role="status" className="flex justify-start">
      <div className="glass-card flex items-center gap-1.5 rounded-bl-md px-4 py-3">
        <span className="sr-only">Asistan yazıyor…</span>
        {[0, 1, 2].map((i) => (
          <span
            key={i}
            aria-hidden="true"
            className="h-1.5 w-1.5 animate-bounce rounded-full bg-brand-ice/80 motion-reduce:animate-none"
            style={{ animationDelay: `${i * 0.15}s` }}
          />
        ))}
      </div>
    </div>
  )
}

export default TypingIndicator
