import { useEffect } from 'react'
import { Provider, useStore } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { Toaster } from 'sonner'
import { authApi, TOKENS_REFRESHED_EVENT, UNAUTHORIZED_EVENT } from '@/api'
import { useAppDispatch } from '@/app/hooks'
import { LiquidGlassFilter } from '@/components/ui/button'
import { ThemeProvider, useTheme } from '@/app/theme'
import { store, type RootState } from '@/app/store'
import { router } from '@/app/router'
import { logout, tokensRefreshed, userRefreshed } from '@/features/auth/authSlice'

/**
 * Uygulama sarmalı: Redux (istemci state) + React Query (sunucu state) + Router.
 * main.tsx yalnızca <Providers /> render eder.
 * MotionConfig reducedMotion="user": tüm framer-motion animasyonları
 * prefers-reduced-motion tercihine uyar (a11y).
 */
const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
})

/**
 * Oturum bekçisi: (1) jetonlu bir istek 401 dönerse (client.ts olayı) oturumu
 * kapatır — ProtectedRoute kullanıcıyı /login'e düşürür; (2) açılışta saklı
 * jeton varsa GET /auth/me ile doğrular ve kullanıcı bilgisini tazeler.
 * Ağ hatasında oturuma dokunulmaz; gerçek 401 zaten olayı tetikler.
 */
function SessionManager() {
  const dispatch = useAppDispatch()
  const reduxStore = useStore<RootState>()

  useEffect(() => {
    const onUnauthorized = () => dispatch(logout())
    // client.ts sessiz refresh'i başardığında yeni jeton çiftini Redux'a + localStorage'a yaz.
    const onTokensRefreshed = (event: Event) => {
      const detail = (event as CustomEvent<{ token: string; refreshToken: string }>).detail
      if (detail) dispatch(tokensRefreshed(detail))
    }
    window.addEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
    window.addEventListener(TOKENS_REFRESHED_EVENT, onTokensRefreshed)
    return () => {
      window.removeEventListener(UNAUTHORIZED_EVENT, onUnauthorized)
      window.removeEventListener(TOKENS_REFRESHED_EVENT, onTokensRefreshed)
    }
  }, [dispatch])

  useEffect(() => {
    const { token, user } = reduxStore.getState().auth
    if (!token || user?.guest) return
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (!cancelled) dispatch(userRefreshed(me))
      })
      .catch(() => {
        /* 401 → UNAUTHORIZED_EVENT logout'u zaten tetikledi */
      })
    return () => {
      cancelled = true
    }
  }, [dispatch, reduxStore])

  return null
}

/**
 * Toaster <body>'ye portal olur, yani <html>'deki `.dark` sınıfını CSS ile değil
 * yalnızca kendi `theme` prop'uyla görür — bu yüzden ThemeProvider'ın içinde,
 * ayrı bir bileşen olarak okunur.
 */
function ThemedToaster() {
  const { resolvedTheme } = useTheme()
  // Marka toast'ları — inline role="alert" mesajlarının yerine değil, yanına.
  return <Toaster richColors position="top-center" theme={resolvedTheme} />
}

export function Providers() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <MotionConfig reducedMotion="user">
            <SessionManager />
            {/* Liquid glass butonların kırılım filtresi — tüm sayfalar için tek tanım. */}
            <LiquidGlassFilter />
            <RouterProvider router={router} />
            <ThemedToaster />
          </MotionConfig>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>
  )
}
