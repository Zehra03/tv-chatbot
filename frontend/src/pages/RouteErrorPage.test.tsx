import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
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

describe('RouteErrorPage', () => {
  it('render hatasında Türkçe mesaj + eylemler gösterir', async () => {
    // react-router yakaladığı hatayı console.error'a da yazar; test çıktısını
    // kirletmesin.
    vi.spyOn(console, 'error').mockImplementation(() => {})

    const router = createMemoryRouter([
      { path: '/', element: <Boom />, errorElement: <RouteErrorPage /> },
    ])
    render(<RouterProvider router={router} />)

    expect(await screen.findByText('Bir şeyler ters gitti')).toBeTruthy()
    expect(screen.getByRole('alert').textContent).toContain('patlama')
    expect(screen.getByRole('button', { name: 'Tekrar dene' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'Sohbete dön' })).toBeTruthy()
  })
})
