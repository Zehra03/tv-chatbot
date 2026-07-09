import React from 'react'

/**
 * FloatingInput — koyu (gece uçuşu) yüzeyler için yüzen etiketli input.
 * Login/register alanlarının görsel dilini taşır: alt çizgi + odakta teal
 * glow-bar (`.input-glow-bar`, index.css) ve grubu sağa iten odak animasyonu
 * (`.form-group-animated`). Açık temadaki formlar için shadcn `Input` kullanın.
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
    <div className="relative z-0 form-group-animated">
      <input
        type={type}
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={`block py-2.5 px-0 w-full text-sm text-white bg-transparent border-0 border-b-2 border-brand-ice/30 appearance-none focus:outline-none focus:ring-0 focus:border-brand-teal peer${trailing ? ' pr-8' : ''}`}
        placeholder=" "
        required
      />
      <label
        htmlFor={id}
        className="absolute text-xs text-brand-ice/60 duration-300 transform -translate-y-6 scale-75 top-3 -z-10 origin-[0] peer-focus:left-0 peer-focus:text-brand-teal peer-placeholder-shown:scale-100 peer-placeholder-shown:translate-y-0 peer-focus:scale-75 peer-focus:-translate-y-6 uppercase tracking-wider font-mono"
      >
        {label}
      </label>
      <div className="input-glow-bar"></div>
      {trailing}
    </div>
  )
}

export default FloatingInput
