import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * Rezervasyon akışı adım göstergesi — 1 Bilgiler · 2 Önizleme · 3 Sonuç.
 * Salt sunumsal: adım, ReservationFormPage'in mevcut durumundan türetilir
 * (state machine'e dokunulmaz). Aktif adım aria-current="step" taşır.
 */
const STEPS = ['Bilgiler', 'Önizleme', 'Sonuç'] as const

export function FormStepper({ current }: { current: 1 | 2 | 3 }) {
  return (
    <ol aria-label="Rezervasyon adımları" className="flex flex-wrap items-center gap-2">
      {STEPS.map((label, i) => {
        const step = i + 1
        const done = step < current
        const active = step === current
        return (
          <li
            key={label}
            aria-current={active ? 'step' : undefined}
            className="flex items-center gap-2"
          >
            <span
              className={cn(
                'flex h-6 w-6 items-center justify-center rounded-full text-xs font-semibold transition-colors',
                done && 'bg-primary text-primary-foreground',
                active && 'border-2 border-primary text-primary',
                !done && !active && 'border border-border text-muted-foreground',
              )}
            >
              {done ? <Check className="h-3.5 w-3.5" aria-hidden /> : step}
            </span>
            <span
              className={cn(
                'text-sm',
                active ? 'font-semibold' : 'text-muted-foreground',
              )}
            >
              {label}
            </span>
            {step < STEPS.length && <span className="h-px w-6 bg-border sm:w-10" aria-hidden />}
          </li>
        )
      })}
    </ol>
  )
}

export default FormStepper
