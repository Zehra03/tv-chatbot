import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

/* Düz (flat) SaaS butonu — cam/kırılım katmanı YOK (Booking/Stripe dili).
   Varyantlar renk + kenarlıkla ayrışır; geçişler 150–250ms. 60-30-10: turuncu
   yalnız `cta` (birincil eylem) — çıplak <Button> nötr kalır. */
const buttonVariants = cva(
  "relative inline-flex cursor-pointer items-center justify-center gap-2 whitespace-nowrap rounded-lg text-sm font-medium transition-[transform,color,background-color,border-color,box-shadow] duration-200 focus-visible:outline-none focus-visible:ring-[3px] focus-visible:ring-ring/50 disabled:pointer-events-none disabled:opacity-50 [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        // Nötr çıplak buton: beyaz yüzey + kenarlık. Turuncuya DÖNMEZ (60-30-10 korunur).
        default:
          "border border-input bg-card text-foreground shadow-soft hover:bg-muted active:scale-[0.98]",
        // Hata eylemi: dolu kırmızı + beyaz yazı (#DC2626 üstünde 4.9:1 ✓).
        destructive:
          "bg-destructive text-destructive-foreground shadow-soft hover:bg-destructive/90 active:scale-[0.98]",
        // İkincil (brief): beyaz zemin + mavi kenarlık + mavi yazı.
        outline:
          "border border-input bg-transparent text-foreground hover:bg-muted active:scale-[0.98]",
        secondary:
          "border border-primary bg-transparent text-primary hover:bg-primary/5 active:scale-[0.98]",
        // Birincil eylem CTA'sı (Ara, Rezervasyon Yap, Devam, Öde, Onayla): turuncu DOLU.
        // Beyaz yazı turuncuda 2.84:1 (AA'yı GEÇMEZ); lacivert (brand.navy) 5.5:1 ✓.
        // Hover'da turuncu KOYULAŞIR (açık şeftali yazıyı düşürürdü). İki temada da aynı.
        cta:
          "bg-brand-orange text-brand-navy shadow-soft hover:bg-brand-orange-hover active:scale-[0.98]",
        // Ghost (brief): saydam + mavi yazı.
        ghost: "text-primary hover:bg-primary/10 active:scale-[0.98]",
        link: "text-primary underline-offset-4 hover:underline",
      },
      size: {
        default: "h-9 px-4 py-2",
        sm: "h-8 rounded-lg px-3 text-xs",
        lg: "h-10 px-6",
        xl: "h-12 px-8",
        xxl: "h-14 px-10",
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
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button"
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
