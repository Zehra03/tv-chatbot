import { describe, expect, it } from 'vitest'
import { isValidElement, type ReactElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { routes } from '@/app/router'
import { Layout } from '@/components/Layout'
import { RouteErrorPage } from '@/pages/RouteErrorPage'

function elementType(el: React.ReactNode) {
  return isValidElement(el) ? (el as ReactElement).type : undefined
}

/**
 * Hata sınırı YERLEŞİM sözleşmesi (davranış RouteErrorPage.test'te):
 * - Kökte errorElement var → varsayılan İngilizce ekran hiçbir yerde çıkmaz.
 * - Layout'un ALTINDAKİ pathless route errorElement taşır → sayfa hatasında
 *   header/nav korunur. errorElement'i Layout route'unun kendisine taşımak
 *   (doğal görünen bir sadeleştirme) chrome'u hata kartıyla değiştirir — bu
 *   test o gerilemeyi yakalar.
 */
describe('router hata sınırı yerleşimi', () => {
  const root = routes[0]

  it('kök route RouteErrorPage errorElement taşır', () => {
    expect(elementType(root.errorElement)).toBe(RouteErrorPage)
  })

  it('Layout route değil, altındaki pathless route errorElement taşır', () => {
    const protectedWrapper = root.children!.find(
      (r) => !r.path && !r.index && r.children,
    ) as RouteObject
    const layoutRoute = protectedWrapper.children!.find(
      (r) => elementType(r.element) === Layout,
    ) as RouteObject
    expect(layoutRoute).toBeTruthy()
    // Layout route'unda errorElement OLMAMALI (olursa chrome kaybolur).
    expect(layoutRoute.errorElement).toBeUndefined()

    const innerBoundary = layoutRoute.children!.find((r) => r.errorElement) as RouteObject
    expect(elementType(innerBoundary.errorElement)).toBe(RouteErrorPage)
    // Sayfalar bu sınırın altında — /chat örnekleyici olarak.
    expect(innerBoundary.children!.some((r) => r.path === '/chat')).toBe(true)
  })
})
