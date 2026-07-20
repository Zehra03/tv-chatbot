import { afterEach, describe, expect, it } from 'vitest'
import { cleanup, render, screen } from '@testing-library/react'
import { Input } from '@/components/ui/input'
import { fieldClass, darkFieldClass, heroFieldClass } from '@/lib/field-styles'

afterEach(cleanup)

/**
 * Alan sınıfının Input'un KENDİ varsayılanlarını gerçekten ezdiğinin kanıtı.
 *
 * Regresyon: `fieldClass` bir Tailwind utility'si değil düz bir sınıftı (`pax-field`).
 * tailwind-merge yalnız tanıdığı utility'leri eleyebildiğinden Input'un tabanındaki
 * `border-input` / `bg-transparent` elementte KALIYOR ve `@layer utilities`'te oldukları
 * için `@layer base`'deki reçeteyi eziyordu. Koyu temada kenarlık `--field-border` yerine
 * `--input` ile boyanıyor, lacivert kartın üstünde 1.26:1 ile görünmez oluyordu.
 *
 * Sınıf LİSTESİ üzerinden bakılır, alt-dize üzerinden değil: Input'ta `file:bg-transparent`
 * de var ve `toContain('bg-transparent')` ona da takılıp yanlış sonuç verir.
 */
function classesOf(label: string): string[] {
  return screen.getByLabelText(label).className.split(/\s+/).filter(Boolean)
}

function renderField() {
  render(<Input aria-label="Ad" className={fieldClass} />)
  return classesOf('Ad')
}

describe('fieldClass — Input varsayılanlarını ezer', () => {
  it('token kenarlığını taşır, border-input’u TAŞIMAZ', () => {
    const cls = renderField()
    expect(cls).toContain('border-[color:var(--field-border)]')
    // Regresyonda burası patlar: border-input elenmeyip kenarlığı --input ile boyuyordu.
    expect(cls).not.toContain('border-input')
  })

  it('token zeminini taşır, bg-transparent elenir (file:bg-transparent kalır — o ayrı varyant)', () => {
    const cls = renderField()
    expect(cls).toContain('bg-[color:var(--field-bg)]')
    expect(cls).not.toContain('bg-transparent')
    expect(cls).toContain('file:bg-transparent')
  })

  it('yazı + placeholder token’ları Input’un varsayılanını eler', () => {
    const cls = renderField()
    expect(cls).toContain('text-[color:var(--field-fg)]')
    expect(cls).toContain('placeholder:text-[color:var(--field-placeholder)]')
    expect(cls).not.toContain('placeholder:text-muted-foreground')
  })

  it('kenarlık GENİŞLİĞİ korunur — yalnız rengi değişiyor', () => {
    // `border` (border-width: 1px) elenirse renk verilse de çizgi hiç çizilmez.
    expect(renderField()).toContain('border')
  })

  it('darkFieldClass fieldClass ile aynı (deprecated takma ad)', () => {
    expect(darkFieldClass).toBe(fieldClass)
  })

  it('heroFieldClass temadan bağımsız kalır — token kullanmaz', () => {
    // Hero alanları fotoğrafın üstünde duruyor; zemini tema değil görsel belirliyor.
    expect(heroFieldClass).toContain('bg-white')
    expect(heroFieldClass).not.toContain('var(--field-')
  })
})
