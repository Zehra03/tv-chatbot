import {
  useEffect,
  useRef,
  useState,
  type KeyboardEvent as ReactKeyboardEvent,
  type MouseEvent as ReactMouseEvent,
} from 'react'
import './GooeyNav.css'

/**
 * GooeyNav — parçacıklı "akışkan" navigasyon.
 * Kaynak: https://reactbits.dev (DavidHDev/react-bits, GooeyNav) — PaxAssist
 * uyarlaması: TypeScript, SPA navigasyonu (onNavigate ile router'a bağlanır;
 * <a href> tam sayfa yüklemesi yapmaz), rota değişimini izleyen kontrollü
 * `activeIndex` ve marka renkli parçacıklar (GooeyNav.css --color-1..4).
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
  animationTime?: number
  particleCount?: number
  particleDistances?: [number, number]
  particleR?: number
  timeVariance?: number
  colors?: number[]
  initialActiveIndex?: number
}

interface Particle {
  start: [number, number]
  end: [number, number]
  time: number
  scale: number
  color: number
  rotate: number
}

export function GooeyNav({
  items,
  activeIndex,
  onNavigate,
  animationTime = 600,
  particleCount = 15,
  particleDistances = [90, 10],
  particleR = 100,
  timeVariance = 300,
  colors = [1, 2, 3, 1, 2, 3, 1, 4],
  initialActiveIndex = 0,
}: GooeyNavProps) {
  const containerRef = useRef<HTMLDivElement>(null)
  const navRef = useRef<HTMLUListElement>(null)
  const filterRef = useRef<HTMLSpanElement>(null)
  const textRef = useRef<HTMLSpanElement>(null)
  const [active, setActive] = useState(activeIndex ?? initialActiveIndex)

  // Rota başka yoldan değişirse (logo, profil linki, geri tuşu) pill'i taşı.
  useEffect(() => {
    if (activeIndex != null) setActive(activeIndex)
  }, [activeIndex])

  const noise = (n = 1) => n / 2 - Math.random() * n

  const getXY = (distance: number, pointIndex: number, totalPoints: number): [number, number] => {
    const angle = ((360 + noise(8)) / totalPoints) * pointIndex * (Math.PI / 180)
    return [distance * Math.cos(angle), distance * Math.sin(angle)]
  }

  const createParticle = (i: number, t: number, d: [number, number], r: number): Particle => {
    const rotate = noise(r / 10)
    return {
      start: getXY(d[0], particleCount - i, particleCount),
      end: getXY(d[1] + noise(7), particleCount - i, particleCount),
      time: t,
      scale: 1 + noise(0.2),
      color: colors[Math.floor(Math.random() * colors.length)],
      rotate: rotate > 0 ? (rotate + r / 20) * 10 : (rotate - r / 20) * 10,
    }
  }

  const makeParticles = (element: HTMLElement) => {
    const d = particleDistances
    const r = particleR
    const bubbleTime = animationTime * 2 + timeVariance
    element.style.setProperty('--time', `${bubbleTime}ms`)

    for (let i = 0; i < particleCount; i++) {
      const t = animationTime * 2 + noise(timeVariance * 2)
      const p = createParticle(i, t, d, r)
      element.classList.remove('active')

      setTimeout(() => {
        const particle = document.createElement('span')
        const point = document.createElement('span')
        particle.classList.add('particle')
        particle.style.setProperty('--start-x', `${p.start[0]}px`)
        particle.style.setProperty('--start-y', `${p.start[1]}px`)
        particle.style.setProperty('--end-x', `${p.end[0]}px`)
        particle.style.setProperty('--end-y', `${p.end[1]}px`)
        particle.style.setProperty('--time', `${p.time}ms`)
        particle.style.setProperty('--scale', `${p.scale}`)
        particle.style.setProperty('--color', `var(--color-${p.color}, white)`)
        particle.style.setProperty('--rotate', `${p.rotate}deg`)

        point.classList.add('point')
        particle.appendChild(point)
        element.appendChild(particle)
        requestAnimationFrame(() => {
          element.classList.add('active')
        })
        setTimeout(() => {
          try {
            element.removeChild(particle)
          } catch {
            // parçacık zaten kaldırılmış — sorun değil
          }
        }, t)
      }, 30)
    }
  }

  const updateEffectPosition = (element: HTMLElement) => {
    if (!containerRef.current || !filterRef.current || !textRef.current) return
    const containerRect = containerRef.current.getBoundingClientRect()
    const pos = element.getBoundingClientRect()

    const styles = {
      left: `${pos.x - containerRect.x}px`,
      top: `${pos.y - containerRect.y}px`,
      width: `${pos.width}px`,
      height: `${pos.height}px`,
    }
    Object.assign(filterRef.current.style, styles)
    Object.assign(textRef.current.style, styles)
    textRef.current.innerText = element.innerText
  }

  /** Pill + parçacık patlaması — yalnızca farklı bir öğeye geçerken. */
  const fireEffects = (liEl: HTMLElement) => {
    updateEffectPosition(liEl)
    if (filterRef.current) {
      const particles = filterRef.current.querySelectorAll('.particle')
      particles.forEach((p) => filterRef.current?.removeChild(p))
      makeParticles(filterRef.current)
    }
    if (textRef.current) {
      textRef.current.classList.remove('active')
      void textRef.current.offsetWidth
      textRef.current.classList.add('active')
    }
  }

  const handleClick = (e: ReactMouseEvent<HTMLAnchorElement>, index: number, href: string) => {
    // SPA: tam sayfa yükleme yerine router'a devret (chat state'i yaşasın).
    if (onNavigate) e.preventDefault()
    if (active !== index) {
      setActive(index)
      const liEl = e.currentTarget.parentElement
      if (liEl) fireEffects(liEl)
    }
    onNavigate?.(href)
  }

  const handleKeyDown = (e: ReactKeyboardEvent<HTMLAnchorElement>, index: number, href: string) => {
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault()
      if (active !== index) {
        setActive(index)
        const liEl = e.currentTarget.parentElement
        if (liEl) fireEffects(liEl)
      }
      onNavigate?.(href)
    }
  }

  useEffect(() => {
    if (!navRef.current || !containerRef.current) return
    const liEls = navRef.current.querySelectorAll('li')

    if (active >= 0 && liEls[active]) {
      updateEffectPosition(liEls[active])
      textRef.current?.classList.add('active')
      filterRef.current?.classList.add('active')
    } else {
      // Aktif öğe yok (ör. /profile) — pill ve metni gizle.
      textRef.current?.classList.remove('active')
      filterRef.current?.classList.remove('active')
      if (textRef.current) textRef.current.innerText = ''
      for (const ref of [filterRef, textRef]) {
        if (ref.current) Object.assign(ref.current.style, { width: '0px', height: '0px' })
      }
    }

    // jsdom'da ResizeObserver yok — testlerde sessizce atlanır.
    if (typeof ResizeObserver === 'undefined') return
    const resizeObserver = new ResizeObserver(() => {
      const currentActiveLi = navRef.current?.querySelectorAll('li')[active]
      if (currentActiveLi) updateEffectPosition(currentActiveLi)
    })
    resizeObserver.observe(containerRef.current)
    return () => resizeObserver.disconnect()
  }, [active])

  return (
    <div className="gooey-nav-container" ref={containerRef}>
      <nav aria-label="Ana navigasyon">
        <ul ref={navRef}>
          {items.map((item, index) => (
            <li key={item.href} className={active === index ? 'active' : ''}>
              <a
                href={item.href}
                aria-current={active === index ? 'page' : undefined}
                onClick={(e) => handleClick(e, index, item.href)}
                onKeyDown={(e) => handleKeyDown(e, index, item.href)}
              >
                {item.label}
              </a>
            </li>
          ))}
        </ul>
      </nav>
      <span className="effect filter" ref={filterRef} />
      <span className="effect text" ref={textRef} />
    </div>
  )
}

export default GooeyNav
