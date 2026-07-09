import * as React from "react"
import { Slot, Slottable } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/* Liquid glass buton: ghost/link dışındaki variant'lar iki cam katmanı alır —
   kenar ışıkları (.btn-liquid-shadow) ve arkayı büken kırılım katmanı
   (.btn-liquid-refract, index.css). Kırılım filtresi (#pax-liquid-glass)
   <LiquidGlassFilter /> ile providers.tsx'te BİR KEZ mount edilir.
   Cam şeffaf olduğundan variant'lar renk yerine ince tonlarla ayrışır. */
const buttonVariants = cva(
  "relative inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-[transform,color,background-color,border-color,box-shadow] duration-300 focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "text-foreground hover:scale-[1.03] active:scale-[0.97]",
        destructive:
          "bg-destructive/15 text-destructive hover:scale-[1.03] hover:bg-destructive/25 active:scale-[0.97] dark:bg-destructive/25 dark:text-destructive-foreground dark:hover:bg-destructive/35",
        outline:
          "border border-input text-foreground hover:scale-[1.03] active:scale-[0.97] dark:border-white/15",
        secondary:
          "bg-secondary/50 text-secondary-foreground hover:scale-[1.03] hover:bg-secondary/70 active:scale-[0.97]",
        ghost: "text-foreground hover:bg-accent hover:text-accent-foreground",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-4 py-2",
        sm: "h-8 rounded-md px-3 text-xs",
        lg: "h-10 rounded-md px-6",
        xl: "h-12 rounded-md px-8",
        xxl: "h-14 rounded-md px-10",
        icon: "h-9 w-9",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, children, ...props }, ref) => {
    const resolvedVariant = variant ?? "default"
    // Ghost ve link bilinçli olarak "krom'suz": ikon/geri-dön eylemlerinde cam
    // kabarcığı gürültü olur.
    const glass = resolvedVariant !== "ghost" && resolvedVariant !== "link"
    const glassLayers = glass ? (
      <>
        <span aria-hidden="true" className="btn-liquid-shadow" />
        <span aria-hidden="true" className="btn-liquid-refract" />
      </>
    ) : null

    if (asChild) {
      // Slot çocuğun içeriğini saramaz; cam katmanları kardeş olarak eklenir.
      return (
        <Slot
          className={cn(buttonVariants({ variant, size, className }))}
          ref={ref}
          {...props}
        >
          <Slottable>{children}</Slottable>
          {glassLayers}
        </Slot>
      )
    }

    return (
      <button
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      >
        {glassLayers}
        <span className="pointer-events-none relative z-10 inline-flex items-center justify-center gap-2">
          {children}
        </span>
      </button>
    )
  }
)
Button.displayName = "Button"

/**
 * Kırılım filtresi — uygulama kökünde bir kez render edilir (providers.tsx).
 * Türbülans gürültüsü backdrop'u yer değiştirerek "sıvı cam" bükümü verir;
 * filtre yoksa butonlar bükümsüz düz cam olarak kalır (zarif düşüş).
 */
function LiquidGlassFilter() {
  return (
    <svg aria-hidden="true" className="absolute h-0 w-0">
      <defs>
        <filter
          id="pax-liquid-glass"
          x="0%"
          y="0%"
          width="100%"
          height="100%"
          colorInterpolationFilters="sRGB"
        >
          <feTurbulence
            type="fractalNoise"
            baseFrequency="0.05 0.05"
            numOctaves="1"
            seed="1"
            result="turbulence"
          />
          <feGaussianBlur in="turbulence" stdDeviation="2" result="blurredNoise" />
          <feDisplacementMap
            in="SourceGraphic"
            in2="blurredNoise"
            scale="70"
            xChannelSelector="R"
            yChannelSelector="B"
            result="displaced"
          />
          <feGaussianBlur in="displaced" stdDeviation="4" result="finalBlur" />
          <feComposite in="finalBlur" in2="finalBlur" operator="over" />
        </filter>
      </defs>
    </svg>
  )
}

export { Button, buttonVariants, LiquidGlassFilter }
