import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'

/**
 * Tema durumu — "Gece Uçuşu" artık bir bölge kimliği değil, KOYU TEMANIN görünümü.
 *
 * Önceden yüzey rengi rotadan türerdi (app/zones.ts → Layout: `zone === 'ai'`);
 * tüm rotalar 'ai' işaretlendiği için uygulama fiilen tek renkti ve "AI devre dışı"
 * anlatısını AiOffBanner devraldı. Renk artık kullanıcı tercihinden gelir.
 *
 * `.dark` sınıfı <html>'e yazılır (Layout div'ine DEĞİL) — çünkü <body>'ye portal
 * olan katmanlar (ui/modal, chat/ResultsPanel, sonner) ve Layout dışı sayfalar
 * (login, landing, yazdırma voucher'ı) Layout'un altında değildir; sınıf orada
 * kalsaydı temayı göremezlerdi.
 */
export type Theme = 'light' | 'dark' | 'system'
export type ResolvedTheme = 'light' | 'dark'

/** localStorage anahtarı — mevcut 'pax-auth' ile aynı 'pax-' konvansiyonu. */
const STORAGE_KEY = 'pax-theme'

/**
 * Varsayılan AÇIK — düz (flat) marka kimliği açık-tema öncelikli.
 *
 * Uygulama artık temiz beyaz yüzeylerde açılıyor (Booking/Stripe dili); koyu tema
 * desteklenen ikincil seçenek olarak toggle'da kalır. Eski cam/koyu kimlik kaldırıldı.
 *
 * DİKKAT: index.html'deki FOUC script'i bu değeri AYNALAR — ikisi BİRLİKTE değişir.
 */
const DEFAULT_THEME: Theme = 'light'

/** Adres çubuğu/işletim sistemi çubuğu rengi (index.html'deki meta[name=theme-color]). */
const THEME_COLOR: Record<ResolvedTheme, string> = {
  dark: '#00243F', // brand.navy — derin lacivert kanvas (yeni palet)
  light: '#FFFFFF',
}

interface ThemeContextValue {
  theme: Theme
  resolvedTheme: ResolvedTheme
  setTheme: (t: Theme) => void
}

const ThemeContext = createContext<ThemeContextValue | null>(null)

const MEDIA = '(prefers-color-scheme: dark)'

function readStored(): Theme {
  try {
    const v = localStorage.getItem(STORAGE_KEY)
    return v === 'light' || v === 'dark' || v === 'system' ? v : DEFAULT_THEME
  } catch {
    // Gizli sekme / storage kapalı — tercih hatırlanmaz, uygulama yine çalışır.
    return DEFAULT_THEME
  }
}

function readSystem(): ResolvedTheme {
  return window.matchMedia?.(MEDIA).matches ? 'dark' : 'light'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStored)
  const [system, setSystem] = useState<ResolvedTheme>(readSystem)

  // İşletim sistemi teması değişince ('system' modda) anında izle.
  useEffect(() => {
    const mq = window.matchMedia?.(MEDIA)
    if (!mq) return
    const onChange = (e: MediaQueryListEvent) => setSystem(e.matches ? 'dark' : 'light')
    mq.addEventListener('change', onChange)
    return () => mq.removeEventListener('change', onChange)
  }, [])

  const resolvedTheme: ResolvedTheme = theme === 'system' ? system : theme

  // Tek yazma noktası — sınıfı ve adres çubuğu rengini <html>'e uygula.
  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolvedTheme === 'dark')
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', THEME_COLOR[resolvedTheme])
  }, [resolvedTheme])

  const setTheme = useCallback((t: Theme) => {
    setThemeState(t)
    try {
      localStorage.setItem(STORAGE_KEY, t)
    } catch {
      /* storage yoksa tercih bu oturumda yaşar */
    }
  }, [])

  const value = useMemo(
    () => ({ theme, resolvedTheme, setTheme }),
    [theme, resolvedTheme, setTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}

/**
 * Provider YOKSA throw ETMEZ, güvenli varsayılana düşer. Kasıtlı: birim testleri
 * sayfaları izole render ediyor (ThemeProvider ile sarmalamadan) — throw etmek
 * onlarca test dosyasını, üstelik tema ile ilgisiz gerekçeyle kırardı.
 * Uygulamada Provider her zaman app/providers.tsx'te mount edilir.
 */
export function useTheme(): ThemeContextValue {
  return (
    useContext(ThemeContext) ?? {
      theme: DEFAULT_THEME,
      resolvedTheme: DEFAULT_THEME === 'dark' ? 'dark' : 'light',
      setTheme: () => {},
    }
  )
}
