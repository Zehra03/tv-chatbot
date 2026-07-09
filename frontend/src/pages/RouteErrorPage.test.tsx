import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { RouteErrorPage } from '@/pages/RouteErrorPage'

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})

/** Render sırasında patlayan sahte sayfa — errorElement'i tetikler. */
function Boom(): never {
  throw new Error('patlama')
}

function renderAt(path: string) {
  // react-router yakaladığı hatayı console.error'a da yazar; test çıktısını
  // kirletmesin.
  vi.spyOn(console, 'error').mockImplementation(() => {})
  const router = createMemoryRouter(
    [
      { path: '/chat', element: <Boom />, errorElement: <RouteErrorPage /> },
      { path: '/hotels', element: <Boom />, errorElement: <RouteErrorPage /> },
    ],
    { initialEntries: [path] },
  )
  render(<RouterProvider router={router} />)
  return router
}

describe('RouteErrorPage', () => {
  it('render hatasında Türkçe mesaj + eylemler gösterir', async () => {
    renderAt('/hotels')

    expect(await screen.findByText('Bir şeyler ters gitti')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('patlama')
    expect(screen.getByRole('button', { name: 'Sayfayı yenile' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Sohbete dön' })).toBeTruthy()
  })

  it('başka sayfadaki hatada Sohbete dön soft navigasyonla /chat\'e gider', async () => {
    const user = userEvent.setup()
    const router = renderAt('/hotels')

    await screen.findByText('Bir şeyler ters gitti')
    await user.click(screen.getByRole('link', { name: 'Sohbete dön' }))
    expect(router.state.location.pathname).toBe('/chat')
  })

  it('hata /chat üzerindeyken Sohbete dön tam sayfa navigasyondur (soft Link değil)', async () => {
    // Donmuş AnimatedOutlet altında aynı-pathname soft Link hata sınırını
    // sıfırlayamaz; bileşen bu durumda düz <a> render etmelidir. jsdom gerçek
    // sayfa navigasyonu yapmadığından tıklama router state'ini DEĞİŞTİRMEMELİ.
    const user = userEvent.setup()
    const router = renderAt('/chat')

    await screen.findByText('Bir şeyler ters gitti')
    const before = router.state.location.key
    await user.click(screen.getByRole('link', { name: 'Sohbete dön' }))
    expect(router.state.location.key).toBe(before)
  })
})
