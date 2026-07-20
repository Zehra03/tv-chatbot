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

// Sabit marka kimliği paleti (tema değiştirmez). CTA turuncusunun yazısı DAİMA lacivert
// (beyaz turuncuda 2.84:1, AA'yı geçmez; lacivert 5.5:1).
const BRAND_SWATCHES: { name: string; className: string; text: string }[] = [
  { name: 'navy #00243F', className: 'bg-brand-navy', text: 'text-white' },
  { name: 'blue #004E89', className: 'bg-brand-blue', text: 'text-white' },
  { name: 'steel #1A659E', className: 'bg-brand-steel', text: 'text-white' },
  { name: 'orange / cta #FF6B35', className: 'bg-brand-orange', text: 'text-brand-navy' },
  { name: 'peach #F7C59F', className: 'bg-brand-peach', text: 'text-brand-navy' },
  { name: 'cream #EFEFD0', className: 'bg-brand-cream', text: 'text-brand-navy' },
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
    // theme-light: playground'un gövdesi HER ZAMAN açık kalır ki açık swatch'ların
    // ve token örneklerinin anlamı olsun (`.dark` artık <html>'de — app/theme.tsx).
    // Aşağıdaki "Gece Uçuşu" bölümü kendi `dark` sarmalıyla koyuyu geri getirir:
    // CSS değişkenleri kalıtımla akar, en yakın ata kazanır.
    <div className="theme-light min-h-screen bg-background text-foreground">
      <header className="border-b">
        <div className="container flex items-center justify-between py-4">
          {/* variant="light": playground gövdesi HER ZAMAN açık (theme-light), ama
              o sınıf yalnız CSS değişkenlerini çevirir — `dark:` varyantı hâlâ
              <html>.dark'a bakar, yani 'auto' burada yanlış varyantı seçerdi. */}
          <Logo height={28} variant="light" />
          <a href="#login" className="text-sm font-medium text-primary hover:underline">
            Login ekranı →
          </a>
        </div>
      </header>

      <main className="container space-y-12 py-10">
        <Section title="Resmî logo (logo-wordmark.png — ikonsuz wordmark)">
          <div className="flex flex-wrap items-end gap-8">
            {[20, 28, 44, 72].map((h) => (
              <div key={h} className="flex flex-col items-center gap-2">
                <Logo height={h} variant="light" />
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
          <p className="mt-4 text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Marka paleti (sabit)
          </p>
          <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 md:grid-cols-6">
            {BRAND_SWATCHES.map((s) => (
              <div
                key={s.name}
                className={`flex h-20 items-end rounded-lg p-2 ${s.className} ${s.text}`}
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

        <Section title="Koyu tema — ikincil yüzey önizlemesi">
          {/* 'dark': token'ları koyuya çevirir; playground koyu temayı da önizlesin
              ki buton/kart görünümleri gerçeğe uysun (koyu artık ikincil tema). */}
          <div className="dark space-y-8 rounded-3xl bg-background p-8">
            <div className="space-y-1">
              <h3 className="text-2xl font-bold tracking-tight text-primary">
                Koyu temaya hoş geldin
              </h3>
              <p className="text-sm text-muted-foreground">
                Chat ve arama sonuçları bu yüzeyi de destekler; açık tema varsayılandır.
              </p>
            </div>

            <div className="glass-card max-w-md p-5">
              <p className="text-sm font-semibold text-foreground">.glass-card reçetesi (düz)</p>
              <p className="mt-1 text-xs text-muted-foreground">
                rounded-2xl · border · bg-card · shadow-soft — cam/blur yok
              </p>
            </div>

            <div className="flex flex-wrap gap-2">
              <span className="glass-chip">Antalya</span>
              <span className="glass-chip">2 misafir</span>
              <span className="glass-chip border-primary/30 bg-primary/10 text-primary">
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

            {/* Düz buton varyantları — birincil (turuncu cta) + nötr/ikincil. */}
            <div className="flex flex-wrap items-center gap-3">
              <Button variant="cta">Ara</Button>
              <Button>Rezervasyona git</Button>
              <Button variant="secondary">Otelleri filtrele</Button>
              <Button variant="outline">Daha fazla göster</Button>
              <Button variant="ghost">İptal</Button>
              <Button variant="destructive">Sil</Button>
            </div>

            {/* Koyu drop zone — Magic/shadcn üretimi bileşenler önce buraya. */}
            <div className="rounded-xl border border-dashed border-border p-8">
              <p className="text-center text-sm text-muted-foreground">
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
              <Button variant="cta">Ara</Button>
              <Button>Rezervasyona git</Button>
              <Button variant="secondary">Otelleri filtrele</Button>
              <Button variant="outline">Daha fazla göster</Button>
              <Button variant="ghost">İptal</Button>
              <Button variant="destructive">Sil</Button>
            </div>

            {/* Düz açık hero önizlemesi — dolu yüzey + yumuşak gölge, gradyan/WebGL yok. */}
            <div className="relative flex h-80 flex-col items-center justify-center gap-2 overflow-hidden rounded-xl border bg-card shadow-soft">
              <p className="text-3xl font-semibold text-foreground">Tatiline hazır mısın?</p>
              <p className="text-sm text-muted-foreground">Düz, hafif ve akıcı hero</p>
              <Button variant="cta" className="mt-2">Uçuş ara</Button>
            </div>

            {/* Deneme: 21st.dev Animated AI Chat (jatin-yadav05) — chat arayüzü adayı */}
            <div className="h-[640px] overflow-hidden rounded-3xl border bg-card">
              <AnimatedAIChat />
            </div>
          </div>
        </Section>
      </main>
    </div>
  )
}
