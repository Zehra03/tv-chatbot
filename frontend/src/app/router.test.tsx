import { describe, expect, it } from 'vitest'
import { isValidElement, type ReactElement } from 'react'
import type { RouteObject } from 'react-router-dom'
import { routes } from '@/app/router'
import { Layout } from '@/components/Layout'
import { RouteErrorPage } from '@/pages/RouteErrorPage'
import { ReservationPrintPage } from '@/features/reservation/ReservationPrintPage'

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

/**
 * Misafir erişim YERLEŞİMİ: sohbet/arama misafire açık (doğrudan çocuk), rezervasyon/profil
 * ise ek bir pathless "hesap-guard" (RequireAccount) sarmalının altında. Bu ayrımı bozmak
 * (ör. rezervasyonu doğrudan çocuk yapmak) misafire rezervasyon açardı — bu test onu yakalar.
 */
describe('misafir erişim yerleşimi', () => {
  const root = routes[0]

  function innerBoundary() {
    const protectedWrapper = root.children!.find(
      (r) => !r.path && !r.index && r.children,
    ) as RouteObject
    const layoutRoute = protectedWrapper.children!.find(
      (r) => elementType(r.element) === Layout,
    ) as RouteObject
    return layoutRoute.children!.find((r) => r.errorElement) as RouteObject
  }

  it('sohbet/arama misafire açık: doğrudan çocuk rotalar', () => {
    const directPaths = innerBoundary()
      .children!.filter((r) => r.path)
      .map((r) => r.path)
    expect(directPaths).toEqual(expect.arrayContaining(['/chat', '/hotels', '/flights']))
    // Rezervasyon/profil doğrudan DEĞİL (bir guard sarmalının altında).
    expect(directPaths).not.toContain('/reservations')
    expect(directPaths).not.toContain('/profile')
  })

  it('rezervasyon ve profil ayrı bir hesap-guard sarmalının altında', () => {
    const accountGuard = innerBoundary().children!.find(
      (r) => !r.path && !r.index && r.children,
    ) as RouteObject
    expect(accountGuard).toBeTruthy()
    const guardedPaths = accountGuard.children!.map((r) => r.path)
    expect(guardedPaths).toEqual(
      expect.arrayContaining(['/reservation/new', '/reservations', '/reservations/:id', '/profile']),
    )
  })
})

/**
 * Yazdırma voucher'ı YERLEŞİMİ: Layout'un DIŞINDA ama hesap-guard'ın ALTINDA.
 * Layout'un altına taşımak (doğal görünen bir "diğer rezervasyon sayfaları gibi olsun"
 * sadeleştirmesi) kâğıda header/nav'ı ve h-screen overflow-hidden kabuğunu sokar —
 * voucher tek sayfaya kırpılır. Guard'ın dışına almak ise rezervasyonu misafire açar.
 */
describe('yazdırma voucher rotası yerleşimi', () => {
  const root = routes[0]

  const protectedWrapper = root.children!.find(
    (r) => !r.path && !r.index && r.children,
  ) as RouteObject

  function printBranch() {
    return protectedWrapper.children!.find((r) =>
      r.children?.some((c) => c.path === '/reservations/:id/print'),
    ) as RouteObject
  }

  it('voucher Layout kabuğunun dışında', () => {
    const layoutRoute = protectedWrapper.children!.find(
      (r) => elementType(r.element) === Layout,
    ) as RouteObject
    // Layout ağacının hiçbir yerinde print rotası olmamalı.
    const insideLayout = JSON.stringify(
      layoutRoute.children,
      (k, v) => (k === 'element' || k === 'errorElement' ? undefined : v),
    ).includes('/reservations/:id/print')
    expect(insideLayout).toBe(false)

    // …ama korumalı sarmalın altında, kendi dalında duruyor.
    const branch = printBranch()
    expect(branch).toBeTruthy()
    expect(elementType(branch.children![0].element)).toBe(ReservationPrintPage)
  })

  it('voucher hesap gerektiren bir guard sarmalının altında (misafire kapalı)', () => {
    const branch = printBranch()
    // Guard, /reservations/:id ile aynı sarmal bileşeni (RequireAccount) olmalı.
    const layoutRoute = protectedWrapper.children!.find(
      (r) => elementType(r.element) === Layout,
    ) as RouteObject
    const innerBoundary = layoutRoute.children!.find((r) => r.errorElement) as RouteObject
    const accountGuard = innerBoundary.children!.find(
      (r) => !r.path && !r.index && r.children,
    ) as RouteObject

    expect(branch.element).toBeTruthy()
    expect(elementType(branch.element)).toBe(elementType(accountGuard.element))
  })
})
