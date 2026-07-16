import { useState, type ReactNode } from 'react'
import { Logo } from '@/components/Logo'
import { Button } from '@/components/ui/button'
import { FloatingInput } from '@/components/ui/floating-input'
import { AnimatedAIChat } from '@/components/ui/animated-ai-chat'

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
  const [demoInput, setDemoInput] = useState('')

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

        <Section title="Gece Uçuşu — koyu yüzey (AI bölgesi)">
          {/* 'dark': Layout AI bölgesinde token'ları koyuya çevirir; playground
              aynı koşulda render etsin ki buton/kart önizlemeleri gerçeğe uysun. */}
          <div className="dark space-y-8 rounded-3xl bg-brand-navy p-8">
            <div className="space-y-1">
              <h3 className="text-2xl font-bold tracking-tight text-gradient-brand">
                Gece uçuşuna hoş geldin
              </h3>
              <p className="text-sm text-brand-ice/70">
                Chat ve arama sonuçları bu yüzeyi kullanır; rezervasyon bilinçli olarak açık kalır.
              </p>
            </div>

            <div className="glass-card max-w-md p-5">
              <p className="text-sm font-semibold text-white">.glass-card reçetesi</p>
              <p className="mt-1 text-xs text-white/70">
                rounded-2xl · border-white/15 · bg-white/10 · backdrop-blur-md
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <span className="glass-chip text-brand-ice">Antalya</span>
              <span className="glass-chip text-brand-ice">2 misafir</span>
              <span className="glass-chip border-brand-teal/40 bg-brand-teal/15 text-white">
                otel araması
              </span>
            </div>

            <div className="max-w-sm">
              <FloatingInput
                id="design_floating"
                label="Nereye gidiyorsun?"
                value={demoInput}
                onChange={setDemoInput}
              />
            </div>

            <div className="flex flex-wrap items-center gap-3">
              <button
                type="button"
                className="bg-white px-6 py-3 text-sm font-bold uppercase tracking-widest text-brand-navy transition-all duration-300 hover:tracking-[0.3em] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal"
              >
                Birincil eylem
              </button>
              <button
                type="button"
                className="rounded-xl border border-brand-ice/30 bg-white/5 px-6 py-3 text-sm font-semibold text-brand-ice transition-all duration-300 hover:border-brand-teal hover:bg-white/10 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal"
              >
                İkincil eylem
              </button>
            </div>

            {/* Ortak Button — koyu yüzeyde liquid glass variant önizlemesi. */}
            <div className="flex flex-wrap items-center gap-3">
              <Button>Rezervasyona git</Button>
              <Button variant="secondary">Otelleri filtrele</Button>
              <Button variant="outline">Daha fazla göster</Button>
              <Button variant="ghost">İptal</Button>
              <Button variant="destructive">Sil</Button>
            </div>

            {/* Koyu drop zone — Magic/shadcn üretimi bileşenler önce buraya. */}
            <div className="rounded-xl border border-dashed border-white/20 p-8">
              <p className="text-center text-sm text-brand-ice/60">
                Koyu yüzey drop zone — üretilen bileşeni buraya koy, screenshot al, kritik et.
              </p>
            </div>
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

            {/* Hero arka planı — statik CSS gradyan (eski WebGL DarkVeil kaldırıldı). */}
            <div className="relative h-80 overflow-hidden rounded-xl border">
              <div className="absolute inset-0 bg-gradient-to-b from-[#1a1040] via-brand-navy to-brand-navy" />
              <div className="absolute inset-0 bg-[radial-gradient(60%_50%_at_50%_0%,theme(colors.brand.teal/12%),transparent_70%)]" />
              {/* dark: koyu görsel üstündeki buton/metin token'ları koyu bölgeyle uysun. */}
              <div className="dark absolute inset-0 flex flex-col items-center justify-center gap-2">
                <p className="text-3xl font-semibold text-white">Gece uçuşuna hazır mısın?</p>
                <p className="text-sm text-white/70">Statik gradyan hero — hafif ve akıcı</p>
                <Button className="mt-2">Uçuş ara</Button>
              </div>
            </div>

            {/* Deneme: 21st.dev Animated AI Chat (jatin-yadav05) — chat arayüzü adayı */}
            <div className="h-[640px] overflow-hidden rounded-3xl bg-brand-navy">
              <AnimatedAIChat />
            </div>
          </div>
        </Section>
      </main>
    </div>
  )
}
