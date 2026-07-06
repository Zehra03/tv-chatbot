import { useEffect, useRef, useState, type CSSProperties, type RefObject } from 'react'
import { gsap } from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import { SplitText as GSAPSplitText } from 'gsap/SplitText'
import { useGSAP } from '@gsap/react'

gsap.registerPlugin(ScrollTrigger, GSAPSplitText, useGSAP)

/**
 * SplitText — metni harf/kelime/satıra bölüp kademeli giriş animasyonu verir.
 * Kaynak: https://reactbits.dev (DavidHDev/react-bits, SplitText) — PaxAssist
 * uyarlaması: TypeScript + jsdom koruması (document.fonts testte yok).
 * DİKKAT: metin ayrı span'lara bölündüğünden RTL getByText tam dizeyi bulamaz —
 * testlerde sorgulanan metinlere UYGULAMAYIN (başlık/karşılama gibi salt
 * sunumsal yazılar için).
 */
interface SplitTextProps {
  text: string
  className?: string
  /** Harfler arası gecikme (ms). */
  delay?: number
  duration?: number
  ease?: string
  splitType?: 'chars' | 'words' | 'lines' | 'words, chars'
  from?: gsap.TweenVars
  to?: gsap.TweenVars
  threshold?: number
  rootMargin?: string
  textAlign?: CSSProperties['textAlign']
  tag?: 'p' | 'h1' | 'h2' | 'h3' | 'span' | 'div'
  onLetterAnimationComplete?: () => void
  /** bg-clip:text (gradyan) metinlerde false verin: harf span'larına transform/
   * katman binince Chromium ebeveynin clip boyamasını kırar, harfler görünmez
   * olur — yalnız opacity animasyonu güvenlidir. */
  force3D?: boolean
}

/** SplitText örneğini element üzerinde taşımak için (yeniden bölmede revert). */
type SplitElement = HTMLElement & { _rbsplitInstance?: GSAPSplitText | null }

export function SplitText({
  text,
  className = '',
  delay = 50,
  duration = 1.25,
  ease = 'power3.out',
  splitType = 'chars',
  from = { opacity: 0, y: 40 },
  to = { opacity: 1, y: 0 },
  threshold = 0.1,
  rootMargin = '-100px',
  textAlign = 'center',
  tag: Tag = 'p',
  onLetterAnimationComplete,
  force3D = true,
}: SplitTextProps) {
  const ref = useRef<SplitElement>(null)
  const animationCompletedRef = useRef(false)
  const onCompleteRef = useRef(onLetterAnimationComplete)
  const [fontsLoaded, setFontsLoaded] = useState(false)

  useEffect(() => {
    onCompleteRef.current = onLetterAnimationComplete
  }, [onLetterAnimationComplete])

  useEffect(() => {
    // jsdom document.fonts sağlamaz — testte bekletmeden hazır say.
    const fonts = typeof document !== 'undefined' ? document.fonts : undefined
    if (!fonts) {
      setFontsLoaded(true)
    } else if (fonts.status === 'loaded') {
      setFontsLoaded(true)
    } else {
      fonts.ready.then(() => setFontsLoaded(true))
    }
  }, [])

  useGSAP(
    () => {
      // Test modunda (jsdom) bölme/animasyon atlanır: GSAP jsdom'u yavaşlatıp
      // paralel yükte yalancı timeout üretiyor; ayrıca RTL metni tam dize
      // olarak sorgulayabilsin diye düz render kalır.
      if (import.meta.env.MODE === 'test') return
      if (!ref.current || !text || !fontsLoaded) return
      // Animasyon bittiyse (once) yeniden bölme/oynatma yapma.
      if (animationCompletedRef.current) return
      const el = ref.current

      if (el._rbsplitInstance) {
        try {
          el._rbsplitInstance.revert()
        } catch {
          /* noop */
        }
        el._rbsplitInstance = null
      }

      const startPct = (1 - threshold) * 100
      const marginMatch = /^(-?\d+(?:\.\d+)?)(px|em|rem|%)?$/.exec(rootMargin)
      const marginValue = marginMatch ? parseFloat(marginMatch[1]) : 0
      const marginUnit = marginMatch ? marginMatch[2] || 'px' : 'px'
      const sign =
        marginValue === 0
          ? ''
          : marginValue < 0
            ? `-=${Math.abs(marginValue)}${marginUnit}`
            : `+=${marginValue}${marginUnit}`
      const start = `top ${startPct}%${sign}`

      let targets: Element[] | undefined
      const assignTargets = (self: GSAPSplitText) => {
        if (splitType.includes('chars') && self.chars.length) targets = self.chars
        if (!targets && splitType.includes('words') && self.words.length) targets = self.words
        if (!targets && splitType.includes('lines') && self.lines.length) targets = self.lines
        if (!targets) targets = self.chars || self.words || self.lines
      }

      const splitInstance = new GSAPSplitText(el, {
        type: splitType,
        smartWrap: true,
        autoSplit: splitType === 'lines',
        linesClass: 'split-line',
        wordsClass: 'split-word',
        charsClass: 'split-char',
        reduceWhiteSpace: false,
        onSplit: (self) => {
          assignTargets(self)
          return gsap.fromTo(
            targets ?? [],
            { ...from },
            {
              ...to,
              duration,
              ease,
              stagger: delay / 1000,
              scrollTrigger: {
                trigger: el,
                start,
                once: true,
                fastScrollEnd: true,
                anticipatePin: 0.4,
              },
              onComplete: () => {
                animationCompletedRef.current = true
                onCompleteRef.current?.()
              },
              willChange: force3D ? 'transform, opacity' : 'opacity',
              force3D,
            },
          )
        },
      })

      el._rbsplitInstance = splitInstance

      return () => {
        ScrollTrigger.getAll().forEach((st) => {
          if (st.trigger === el) st.kill()
        })
        try {
          splitInstance.revert()
        } catch {
          /* noop */
        }
        el._rbsplitInstance = null
      }
    },
    {
      dependencies: [
        text,
        delay,
        duration,
        ease,
        splitType,
        JSON.stringify(from),
        JSON.stringify(to),
        threshold,
        rootMargin,
        fontsLoaded,
        force3D,
      ],
      scope: ref,
    },
  )

  const style: CSSProperties = {
    textAlign,
    overflow: 'hidden',
    display: 'inline-block',
    whiteSpace: 'normal',
    wordWrap: 'break-word',
    willChange: 'transform, opacity',
  }

  // Tag birleşimi ('p'|'h1'|…) ref tipini daraltamıyor — ElementType üzerinden
  // render edilir, ref güvenli biçimde genel HTMLElement olarak bağlanır.
  const Component = Tag as 'p'
  return (
    <Component
      ref={ref as RefObject<HTMLParagraphElement>}
      style={style}
      className={`split-parent ${className}`}
    >
      {text}
    </Component>
  )
}

export default SplitText
