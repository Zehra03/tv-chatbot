import type { ReactNode } from 'react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'

/**
 * /design — the design playground.
 *
 * This is the screenshot target for the Playwright MCP visual-iteration loop.
 * Drop newly generated components (shadcn / 21st.dev Magic) into the
 * "Generated components" section below, then ask Claude to screenshot this page
 * and critique against a reference before moving the component into a feature.
 */

const SWATCHES: { name: string; className: string; text?: string }[] = [
  { name: 'primary', className: 'bg-primary', text: 'text-primary-foreground' },
  { name: 'secondary', className: 'bg-secondary', text: 'text-secondary-foreground' },
  { name: 'muted', className: 'bg-muted', text: 'text-muted-foreground' },
  { name: 'accent', className: 'bg-accent', text: 'text-accent-foreground' },
  { name: 'destructive', className: 'bg-destructive', text: 'text-destructive-foreground' },
  { name: 'card', className: 'bg-card border', text: 'text-card-foreground' },
]

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="space-y-4">
      <h2 className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
        {title}
      </h2>
      {children}
    </section>
  )
}

export default function Design() {
  return (
    <div className="min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="container flex items-center justify-between py-4">
          <Logo height={40} />
          <a href="#login" className="text-sm font-medium text-primary hover:underline">
            Login ekranı →
          </a>
        </div>
      </header>

      <main className="container space-y-12 py-10">
        <Section title="Resmî logo (logo.png — olduğu gibi)">
          <div className="flex flex-wrap items-end gap-8">
            {[40, 72, 140, 220].map((h) => (
              <div key={h} className="flex flex-col items-center gap-2">
                <Logo height={h} />
                <span className="text-xs text-muted-foreground">{h}px</span>
              </div>
            ))}
          </div>
        </Section>

        <Section title="Colors">
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-6">
            {SWATCHES.map((s) => (
              <div
                key={s.name}
                className={`flex h-20 items-end rounded-lg p-2 ${s.className} ${s.text ?? ''}`}
              >
                <span className="text-xs font-medium">{s.name}</span>
              </div>
            ))}
          </div>
        </Section>

        <Section title="Typography (Inter)">
          <div className="space-y-2">
            <p className="text-4xl font-extrabold tracking-tight">PaxAssist</p>
            <p className="text-2xl font-bold">Seyahat asistanınız</p>
            <p className="text-base">
              Otel ve uçuş aramalarını doğal dilde yapın; sonuçları listeleyin,
              kontrollü rezervasyona geçin.
            </p>
            <p className="text-sm text-muted-foreground">
              Yardımcı / ikincil metin — muted-foreground.
            </p>
          </div>
        </Section>

        <Section title="Generated components (drop zone)">
          <div className="space-y-6 rounded-xl border border-dashed p-8">
            <p className="text-center text-sm text-muted-foreground">
              shadcn / 21st.dev Magic ile üretilen bileşenleri buraya yerleştir,
              sonra Playwright ile screenshot al ve referansla karşılaştır.
            </p>
            <div className="flex flex-wrap items-center justify-center gap-3">
              <Button>Rezervasyona git</Button>
              <Button variant="secondary">Otelleri filtrele</Button>
              <Button variant="outline">Daha fazla göster</Button>
              <Button variant="ghost">İptal</Button>
              <Button variant="destructive">Sil</Button>
            </div>
          </div>
        </Section>
      </main>
    </div>
  )
}
