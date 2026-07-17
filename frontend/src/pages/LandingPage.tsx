import { type ReactNode } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { motion, useReducedMotion } from 'framer-motion'
import {
  ArrowRight,
  ClipboardCheck,
  Hotel,
  MessagesSquare,
  Plane,
  Search,
  ShieldCheck,
  Sparkles,
  Star,
} from 'lucide-react'
import { Logo } from '@/components/Logo'
import { SplitText } from '@/components/SplitText'
import { Button } from '@/components/ui/button'
import { useAppSelector } from '@/app/hooks'

/**
 * Herkese açık tanıtım sayfası (`/`) — düz (flat), açık-tema öncelikli düzen:
 * prompt-hero → sonuç önizleme → nasıl çalışır → değer önerisi → CTA.
 * Yüzeyler dolu beyaz kart + yumuşak gölge (Booking/Stripe dili); tüm birincil
 * CTA'lar turuncu, mavi başlıklarla. Oturum yoksa ProtectedRoute /login'e düşürür.
 */

const EXAMPLE_QUERIES = [
  'Antalya’da 5 yıldızlı otel',
  'İstanbul → Amsterdam uçuş',
  'Kapadokya’da balayı oteli',
]

const STEPS = [
  {
    icon: MessagesSquare,
    title: 'Sohbette anlat',
    body: 'Nereye, ne zaman, kaç kişi — eksik kalan kriteri PaxAssist soruyla tamamlar, sen sadece konuşursun.',
  },
  {
    icon: Search,
    title: 'AI senin için arar',
    body: 'Toplanan kriterlerle oteller ve uçuşlar TourVisio üzerinde canlı aranır; sonuçlar karşılaştırmalı kartlarla listelenir.',
  },
  {
    icon: ClipboardCheck,
    title: 'Onay her zaman sende',
    body: 'Rezervasyon, yapay zekânın dokunmadığı ayrı bir formda tamamlanır — sohbet asla senin adına satın alma yapmaz.',
  },
]

const VALUE_PROPS = [
  {
    icon: Hotel,
    title: 'Canlı otel verisi',
    body: 'Fiyat ve müsaitlik tahmin değil: her sonuç TourVisio’dan anlık gelir, uydurma rakam yoktur.',
  },
  {
    icon: Plane,
    title: 'Uçuşlar aynı sohbette',
    body: 'Gidiş-dönüş, tek yön, esnek tarih — otelle aynı konuşmanın içinde, ayrı arama ekranı gerekmez.',
  },
  {
    icon: ShieldCheck,
    title: 'Kontrollü rezervasyon',
    body: 'Son adım tamamen sende: AI’sız form, açık onay. Ödeme ve kişisel bilgiler sohbete hiç girmez.',
  },
]

/** Scroll'a bağlı yumuşak giriş — reduced-motion'da yalnızca opacity. */
function Reveal({
  children,
  delay = 0,
  className,
}: {
  children: ReactNode
  delay?: number
  className?: string
}) {
  const reduced = useReducedMotion()
  return (
    <motion.div
      className={className}
      initial={{ opacity: 0, y: reduced ? 0 : 24 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true, margin: '-80px' }}
      transition={{ duration: 0.3, delay, ease: 'easeOut' }}
    >
      {children}
    </motion.div>
  )
}

/** Asistan balonundaki 3-nokta "yazıyor" nabzı (dekoratif). */
function TypingDots() {
  return (
    <span className="inline-flex items-center gap-1" aria-hidden="true">
      {[0, 150, 300].map((ms) => (
        <span
          key={ms}
          className="h-1.5 w-1.5 animate-pulse rounded-full bg-muted-foreground"
          style={{ animationDelay: `${ms}ms` }}
        />
      ))}
    </span>
  )
}

export function LandingPage() {
  const navigate = useNavigate()
  const user = useAppSelector((s) => s.auth.user)

  // Gerçek (kayıtlı) oturum açıkken tanıtım sayfası atlanır; kullanıcı doğrudan
  // uygulamaya (sohbete) düşer. Misafir istisna: landing herkese açık bir pazarlama
  // sayfası olduğundan misafir oturumu buraya erişebilmeli, yönlendirilmez.
  if (user && !user.guest) return <Navigate to="/chat" replace />

  return (
    <div className="min-h-[100dvh] bg-background font-sans text-foreground">
      {/* ——— Hero: prompt-input odaklı ——— */}
      <section className="relative flex min-h-[100dvh] flex-col overflow-hidden">
        {/* Düz açık zemin — üstte fark edilir edilmez mavi ferahlık, WebGL/gradyan yok. */}
        <div
          className="absolute inset-0 bg-gradient-to-b from-primary/[0.04] to-background"
          aria-hidden="true"
        />

        <header className="relative z-10 mx-auto flex w-full max-w-6xl items-center justify-between px-4 py-5 sm:px-8">
          <Link
            to="/"
            className="rounded-lg focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
            aria-label="PaxAssist ana sayfa"
          >
            {/* Responsive boyut: sarmalayıcı span'lar görünürlüğü tutar — display
                sınıfını Logo'ya GEÇİRMEK auto varyantın dark:block/dark:hidden
                geçişiyle çakışır (sm:block base `hidden`i ezip koyu logoyu açıkta gösterir). */}
            <span className="sm:hidden">
              <Logo height={26} />
            </span>
            <span className="hidden sm:block">
              <Logo height={38} />
            </span>
          </Link>
          <nav className="flex items-center gap-2">
            <Button asChild variant="ghost" className="h-11 px-4">
              <Link to="/login">Giriş yap</Link>
            </Button>
            <Button asChild variant="cta" className="h-11 px-5">
              <Link to="/chat">Sohbete başla</Link>
            </Button>
          </nav>
        </header>

        <div className="relative z-10 mx-auto flex w-full max-w-4xl flex-1 flex-col items-center justify-center px-4 pb-24 pt-8 text-center">
          <span className="glass-chip mb-6 inline-flex items-center gap-1.5">
            <Sparkles className="h-3.5 w-3.5 text-primary" aria-hidden="true" />
            AI destekli seyahat asistanı
          </span>

          <h1 className="text-4xl font-bold leading-tight tracking-tight sm:text-6xl">
            <SplitText
              text="Tatilini anlat,"
              tag="span"
              className="block text-foreground"
              splitType="chars"
              delay={35}
              duration={0.8}
            />
            <span className="block text-primary">gerisini PaxAssist halletsin.</span>
          </h1>

          <p className="mt-6 max-w-2xl text-base leading-relaxed text-muted-foreground sm:text-lg">
            Otel ve uçuş aramayı sohbete çevirir: kriterlerini konuşarak toplar, canlı
            sonuçları listeler. Rezervasyonun son sözü ise her zaman sende.
          </p>

          {/* Sahte prompt çubuğu — tıklanınca gerçek sohbete götürür. */}
          <button
            type="button"
            onClick={() => navigate('/chat')}
            aria-label="Sohbete başla ve aramanı yaz"
            className="group mt-10 flex w-full max-w-2xl cursor-pointer items-center gap-3 rounded-full border border-input bg-card p-2 pl-5 text-left shadow-soft transition-colors duration-200 hover:border-primary/60 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          >
            <Sparkles className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
            <span className="flex-1 truncate text-sm text-muted-foreground sm:text-base">
              Eylülde Antalya’da denize sıfır, her şey dahil bir otel arıyorum…
            </span>
            <span
              className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full bg-brand-orange transition-transform duration-200 group-hover:scale-105"
              aria-hidden="true"
            >
              <ArrowRight className="h-5 w-5 text-brand-navy" />
            </span>
          </button>

          <ul className="mt-5 flex flex-wrap items-center justify-center gap-2">
            {EXAMPLE_QUERIES.map((q) => (
              <li key={q}>
                <button
                  type="button"
                  onClick={() => navigate('/chat')}
                  className="glass-chip min-h-[44px] cursor-pointer px-4 text-muted-foreground transition-colors duration-200 hover:border-primary/50 hover:text-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                >
                  {q}
                </button>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* ——— Sonuç önizleme: göster, anlatma ——— */}
      <section className="relative py-20 sm:py-24" aria-labelledby="preview-heading">
        <div className="mx-auto grid w-full max-w-6xl items-center gap-12 px-4 sm:px-8 lg:grid-cols-2">
          <Reveal>
            <h2 id="preview-heading" className="text-3xl font-bold tracking-tight sm:text-4xl">
              Aramayı <span className="text-primary">konuşma</span> gibi yap
            </h2>
            <p className="mt-4 max-w-md leading-relaxed text-muted-foreground">
              Form doldurmak yok. İsteğini yaz; PaxAssist eksik bilgiyi sorar, kriterler
              tamamlanınca canlı sonuçları kartlar hâlinde önüne getirir.
            </p>
            <Button asChild variant="cta" className="mt-8 h-11 px-6">
              <Link to="/chat">
                Kendin dene
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </Button>
          </Reveal>

          <Reveal delay={0.05}>
            <div
              className="glass-card space-y-4 p-5 sm:p-6"
              role="img"
              aria-label="Örnek sohbet akışı: kullanıcı otel istiyor, asistan örnek sonuç kartı listeliyor"
            >
              <div className="flex justify-end">
                <p className="max-w-[85%] rounded-2xl rounded-br-md bg-primary px-4 py-2.5 text-sm text-primary-foreground">
                  Eylülde Antalya’da denize sıfır bir otel arıyorum, 2 yetişkin.
                </p>
              </div>
              <div className="flex justify-start">
                <div className="max-w-[85%] space-y-3 rounded-2xl rounded-bl-md bg-muted px-4 py-3 text-sm text-foreground">
                  <TypingDots />
                  <p>Denize sıfır 12 otel buldum. Öne çıkan seçenek:</p>
                  <div className="rounded-xl border border-border bg-card p-3.5">
                    <div className="flex items-start gap-3">
                      <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                        <Hotel className="h-5 w-5 text-primary" aria-hidden="true" />
                      </span>
                      <div className="min-w-0 flex-1">
                        <p className="truncate font-medium text-foreground">Örnek Sahil Oteli · Lara</p>
                        <span className="mt-1 flex gap-0.5" aria-label="5 yıldız">
                          {Array.from({ length: 5 }).map((_, i) => (
                            <Star
                              key={i}
                              className="h-3.5 w-3.5 fill-warning text-warning"
                              aria-hidden="true"
                            />
                          ))}
                        </span>
                        {/* Fiyat kasıtlı olarak iskelet: gerçek rakam yalnızca
                           TourVisio'dan gelir, tanıtımda uydurulmaz. */}
                        <span className="mt-2 flex items-center gap-2" aria-hidden="true">
                          <span className="h-3 w-20 animate-pulse rounded bg-muted" />
                          <span className="h-3 w-12 animate-pulse rounded bg-muted" />
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <p className="text-center text-xs text-muted-foreground">
                Örnek görünüm — fiyat ve müsaitlik canlıda TourVisio’dan gelir.
              </p>
            </div>
          </Reveal>
        </div>
      </section>

      {/* ——— Nasıl çalışır ——— */}
      <section className="relative py-20 sm:py-24" aria-labelledby="steps-heading">
        <div className="mx-auto w-full max-w-6xl px-4 sm:px-8">
          <Reveal className="text-center">
            <h2 id="steps-heading" className="text-3xl font-bold tracking-tight sm:text-4xl">
              Üç adımda <span className="text-primary">planla</span>
            </h2>
          </Reveal>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {STEPS.map((step, i) => (
              <Reveal key={step.title} delay={i * 0.05}>
                <article className="glass-card h-full p-6">
                  <div className="flex items-center gap-3">
                    <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-primary/10">
                      <step.icon className="h-5 w-5 text-primary" aria-hidden="true" />
                    </span>
                    <span className="text-3xl font-bold text-primary/15" aria-hidden="true">
                      {String(i + 1).padStart(2, '0')}
                    </span>
                  </div>
                  <h3 className="mt-4 text-lg font-semibold text-foreground">{step.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{step.body}</p>
                </article>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ——— Değer önerileri ——— */}
      <section className="relative py-20 sm:py-24" aria-labelledby="values-heading">
        <div className="mx-auto w-full max-w-6xl px-4 sm:px-8">
          <Reveal className="text-center">
            <h2 id="values-heading" className="text-3xl font-bold tracking-tight sm:text-4xl">
              Neden <span className="text-primary">PaxAssist?</span>
            </h2>
          </Reveal>
          <div className="mt-12 grid gap-5 md:grid-cols-3">
            {VALUE_PROPS.map((v, i) => (
              <Reveal key={v.title} delay={i * 0.05}>
                <article className="glass-card h-full p-6 text-center">
                  <span className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-primary/10">
                    <v.icon className="h-6 w-6 text-primary" aria-hidden="true" />
                  </span>
                  <h3 className="mt-4 text-lg font-semibold text-foreground">{v.title}</h3>
                  <p className="mt-2 text-sm leading-relaxed text-muted-foreground">{v.body}</p>
                </article>
              </Reveal>
            ))}
          </div>
        </div>
      </section>

      {/* ——— Kapanış CTA + footer ——— */}
      <section className="relative py-20 sm:py-28" aria-labelledby="cta-heading">
        <Reveal className="mx-auto w-full max-w-3xl px-4 text-center sm:px-8">
          <h2 id="cta-heading" className="text-3xl font-bold tracking-tight sm:text-5xl">
            Bir sonraki tatilin <span className="text-primary">bir sohbet uzağında.</span>
          </h2>
          <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
            <Button asChild variant="cta" className="h-12 px-8 text-base">
              <Link to="/chat">
                Sohbete başla
                <ArrowRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            </Button>
            <Button asChild variant="ghost" className="h-12 px-6 text-base">
              <Link to="/login">Hesabın var mı? Giriş yap</Link>
            </Button>
          </div>
        </Reveal>
      </section>

      <footer className="border-t border-border py-8">
        <p className="mx-auto max-w-6xl px-4 text-center text-xs leading-relaxed text-muted-foreground sm:px-8">
          PaxAssist — Fiyat ve müsaitlik verileri TourVisio’dan canlı alınır.
        </p>
      </footer>
    </div>
  )
}

export default LandingPage
