import { useEffect, useState, type MouseEvent as ReactMouseEvent } from 'react'
import './GooeyNav.css'

/**
 * GooeyNav — pill navigasyon (reactbits.dev GooeyNav'ından türedi; tıklamadaki
 * parçacık patlaması + goo filtresi sahibin isteğiyle kaldırıldı, aktif öğeyi
 * saran beyaz pill ve yumuşak geçişi kaldı). SPA navigasyonu onNavigate ile
 * router'a bağlanır (<a href> tam sayfa yüklemesi yapmaz); aktif öğe rota
 * değişimini izleyen kontrollü `activeIndex` ile senkron kalır.
 */
export interface GooeyNavItem {
  label: string
  href: string
}

interface GooeyNavProps {
  items: GooeyNavItem[]
  /** Dışarıdan senkronlanan aktif öğe — -1 hiçbirini işaretlemez (ör. /profile). */
  activeIndex?: number
  /** SPA yönlendirmesi — verilmezse <a href> varsayılan davranışı kalır. */
  onNavigate?: (href: string) => void
  initialActiveIndex?: number
}

export function GooeyNav({
  items,
  activeIndex,
  onNavigate,
  initialActiveIndex = 0,
}: GooeyNavProps) {
  const [active, setActive] = useState(activeIndex ?? initialActiveIndex)

  // Rota başka yoldan değişirse (logo, profil linki, geri tuşu) pill'i taşı.
  useEffect(() => {
    if (activeIndex != null) setActive(activeIndex)
  }, [activeIndex])

  const handleClick = (e: ReactMouseEvent<HTMLAnchorElement>, index: number, href: string) => {
    // SPA: tam sayfa yükleme yerine router'a devret (chat state'i yaşasın).
    if (onNavigate) e.preventDefault()
    setActive(index)
    onNavigate?.(href)
  }

  return (
    <div className="gooey-nav-container">
      <nav aria-label="Ana navigasyon">
        <ul>
          {items.map((item, index) => (
            <li key={item.href} className={active === index ? 'active' : ''}>
              <a
                href={item.href}
                aria-current={active === index ? 'page' : undefined}
                onClick={(e) => handleClick(e, index, item.href)}
              >
                {item.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
    </div>
  )
}

export default GooeyNav
