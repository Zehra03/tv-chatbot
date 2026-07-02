import React, { useEffect, useRef, useState } from 'react'
import { cn } from '@/lib/utils'

/**
 * BackgroundGradientAnimation — an animated, gooey field of drifting radial
 * gradients (Aceternity-style). Colors are passed as `r, g, b` strings and wired
 * through CSS variables on <body>; the blobs animate via the `first…fifth`
 * keyframes defined in `tailwind.config.js`.
 *
 * Decorative only. Render content as children (or absolutely positioned over it).
 */
interface BackgroundGradientAnimationProps {
  gradientBackgroundStart?: string
  gradientBackgroundEnd?: string
  firstColor?: string
  secondColor?: string
  thirdColor?: string
  fourthColor?: string
  fifthColor?: string
  pointerColor?: string
  size?: string
  blendingValue?: string
  children?: React.ReactNode
  className?: string
  interactive?: boolean
  containerClassName?: string
}

export function BackgroundGradientAnimation({
  gradientBackgroundStart = 'rgb(219, 234, 254)',
  gradientBackgroundEnd = 'rgb(204, 251, 241)',
  firstColor = '37, 99, 235',
  secondColor = '59, 130, 246',
  thirdColor = '96, 165, 250',
  fourthColor = '20, 184, 166',
  fifthColor = '45, 212, 191',
  pointerColor = '59, 130, 246',
  size = '80%',
  blendingValue = 'hard-light',
  children,
  className,
  interactive = true,
  containerClassName,
}: BackgroundGradientAnimationProps) {
  const interactiveRef = useRef<HTMLDivElement>(null)
  const [curX, setCurX] = useState(0)
  const [curY, setCurY] = useState(0)
  const [tgX, setTgX] = useState(0)
  const [tgY, setTgY] = useState(0)

  useEffect(() => {
    const body = document.body.style
    body.setProperty('--gradient-background-start', gradientBackgroundStart)
    body.setProperty('--gradient-background-end', gradientBackgroundEnd)
    body.setProperty('--first-color', firstColor)
    body.setProperty('--second-color', secondColor)
    body.setProperty('--third-color', thirdColor)
    body.setProperty('--fourth-color', fourthColor)
    body.setProperty('--fifth-color', fifthColor)
    body.setProperty('--pointer-color', pointerColor)
    body.setProperty('--size', size)
    body.setProperty('--blending-value', blendingValue)
  }, [
    gradientBackgroundStart,
    gradientBackgroundEnd,
    firstColor,
    secondColor,
    thirdColor,
    fourthColor,
    fifthColor,
    pointerColor,
    size,
    blendingValue,
  ])

  useEffect(() => {
    function move() {
      if (!interactiveRef.current) return
      setCurX(curX + (tgX - curX) / 20)
      setCurY(curY + (tgY - curY) / 20)
      interactiveRef.current.style.transform = `translate(${Math.round(curX)}px, ${Math.round(curY)}px)`
    }
    move()
    // Smooth-follow stepper: intentionally keyed to the target only.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tgX, tgY])

  const handleMouseMove = (event: React.MouseEvent<HTMLDivElement>) => {
    if (!interactiveRef.current) return
    const rect = interactiveRef.current.getBoundingClientRect()
    setTgX(event.clientX - rect.left)
    setTgY(event.clientY - rect.top)
  }

  const [isSafari, setIsSafari] = useState(false)
  useEffect(() => {
    setIsSafari(/^((?!chrome|android).)*safari/i.test(navigator.userAgent))
  }, [])

  return (
    <div
      className={cn(
        'h-screen w-screen relative overflow-hidden top-0 left-0 bg-[linear-gradient(40deg,var(--gradient-background-start),var(--gradient-background-end))]',
        containerClassName,
      )}
    >
      <svg className="hidden">
        <defs>
          <filter id="blurMe">
            <feGaussianBlur in="SourceGraphic" stdDeviation="10" result="blur" />
            <feColorMatrix
              in="blur"
              mode="matrix"
              values="1 0 0 0 0  0 1 0 0 0  0 0 1 0 0  0 0 0 18 -8"
              result="goo"
            />
            <feBlend in="SourceGraphic" in2="goo" />
          </filter>
        </defs>
      </svg>
      <div className={cn('', className)}>{children}</div>
      <div
        className={cn(
          'gradients-container h-full w-full blur-lg',
          isSafari ? 'blur-2xl' : '[filter:url(#blurMe)_blur(40px)]',
        )}
      >
        <div
          className={cn(
            'absolute [background:radial-gradient(circle_at_center,_rgba(var(--first-color),_0.8)_0,_rgba(var(--first-color),_0)_50%)_no-repeat]',
            '[mix-blend-mode:var(--blending-value)] w-[var(--size)] h-[var(--size)] top-[calc(50%-var(--size)/2)] left-[calc(50%-var(--size)/2)]',
            '[transform-origin:center_center]',
            'animate-first',
            'opacity-100',
          )}
        ></div>
        <div
          className={cn(
            'absolute [background:radial-gradient(circle_at_center,_rgba(var(--second-color),_0.8)_0,_rgba(var(--second-color),_0)_50%)_no-repeat]',
            '[mix-blend-mode:var(--blending-value)] w-[var(--size)] h-[var(--size)] top-[calc(50%-var(--size)/2)] left-[calc(50%-var(--size)/2)]',
            '[transform-origin:calc(50%-400px)]',
            'animate-second',
            'opacity-100',
          )}
        ></div>
        <div
          className={cn(
            'absolute [background:radial-gradient(circle_at_center,_rgba(var(--third-color),_0.8)_0,_rgba(var(--third-color),_0)_50%)_no-repeat]',
            '[mix-blend-mode:var(--blending-value)] w-[var(--size)] h-[var(--size)] top-[calc(50%-var(--size)/2)] left-[calc(50%-var(--size)/2)]',
            '[transform-origin:calc(50%+400px)]',
            'animate-third',
            'opacity-100',
          )}
        ></div>
        <div
          className={cn(
            'absolute [background:radial-gradient(circle_at_center,_rgba(var(--fourth-color),_0.8)_0,_rgba(var(--fourth-color),_0)_50%)_no-repeat]',
            '[mix-blend-mode:var(--blending-value)] w-[var(--size)] h-[var(--size)] top-[calc(50%-var(--size)/2)] left-[calc(50%-var(--size)/2)]',
            '[transform-origin:calc(50%-200px)]',
            'animate-fourth',
            'opacity-70',
          )}
        ></div>
        <div
          className={cn(
            'absolute [background:radial-gradient(circle_at_center,_rgba(var(--fifth-color),_0.8)_0,_rgba(var(--fifth-color),_0)_50%)_no-repeat]',
            '[mix-blend-mode:var(--blending-value)] w-[var(--size)] h-[var(--size)] top-[calc(50%-var(--size)/2)] left-[calc(50%-var(--size)/2)]',
            '[transform-origin:calc(50%-800px)_calc(50%+800px)]',
            'animate-fifth',
            'opacity-100',
          )}
        ></div>

        {interactive && (
          <div
            ref={interactiveRef}
            onMouseMove={handleMouseMove}
            className={cn(
              'absolute [background:radial-gradient(circle_at_center,_rgba(var(--pointer-color),_0.8)_0,_rgba(var(--pointer-color),_0)_50%)_no-repeat]',
              '[mix-blend-mode:var(--blending-value)] w-full h-full -top-1/2 -left-1/2',
              'opacity-70',
            )}
          ></div>
        )}
      </div>
    </div>
  )
}

export default BackgroundGradientAnimation
