import { Provider } from 'react-redux'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { RouterProvider } from 'react-router-dom'
import { MotionConfig } from 'framer-motion'
import { store } from '@/app/store'
import { router } from '@/app/router'

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

export function Providers() {
  return (
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <MotionConfig reducedMotion="user">
          <RouterProvider router={router} />
        </MotionConfig>
      </QueryClientProvider>
    </Provider>
  )
}
