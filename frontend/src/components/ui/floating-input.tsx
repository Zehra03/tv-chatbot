import React from 'react'

/**
 * FloatingInput — yüzen etiketli, alt-çizgili düz input (Stripe/Linear dili).
 * Tema-duyarlı token'lar: alt çizgi `border-input`, odakta `border-primary` ve
 * etiket `text-primary`. Cam/glow yok. Basit formlarda shadcn `Input` da kullanılabilir.
 */
interface FloatingInputProps {
  id: string
  label: string
  value: string
  onChange: (value: string) => void
  type?: string
  trailing?: React.ReactNode
}

export function FloatingInput({
  id,
  label,
  value,
  onChange,
  type = 'text',
  trailing,
}: FloatingInputProps) {
  return (
    <div className="relative z-0">
      <input
        type={type}
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`block py-2.5 px-0 w-full text-sm text-foreground bg-transparent border-0 border-b-2 border-input appearance-none focus:outline-none focus:ring-0 focus:border-primary transition-colors peer${trailing ? ' pr-8' : ''}`}
        placeholder=" "
        required
      />
      <label
        htmlFor={id}
        className="absolute text-xs text-muted-foreground duration-300 transform -translate-y-6 scale-75 top-3 -z-10 origin-[0] peer-focus:left-0 peer-focus:text-primary peer-placeholder-shown:scale-100 peer-placeholder-shown:translate-y-0 peer-focus:scale-75 peer-focus:-translate-y-6 uppercase tracking-wider font-mono"
      >
        {label}
      </label>
      {trailing}
    </div>
  )
}

export default FloatingInput
