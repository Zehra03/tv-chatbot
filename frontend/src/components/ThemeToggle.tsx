import { Monitor, Moon, Sun } from 'lucide-react'
import { useTheme, type Theme } from '@/app/theme'
import { cn } from '@/lib/utils'

/**
 * Tema seçici — Açık / Sistem / Koyu.
 *
 * Döngüsel tek düğme DEĞİL, 3 durumlu segment: döngü düğmesi mevcut durumu ve
 * sıradakini görünür kılmaz, ekran okuyucuya da "neyin neye döneceğini"
 * anlatamaz. radiogroup semantiği durumu doğrudan söyler.
 */
const OPTIONS: ReadonlyArray<readonly [Theme, typeof Sun, string]> = [
  ['light', Sun, 'Açık'],
  ['system', Monitor, 'Sistem'],
  ['dark', Moon, 'Koyu'],
]

export function ThemeToggle({ className }: { className?: string }) {
  const { theme, setTheme } = useTheme()

  return (
    <div
      role="radiogroup"
      aria-label="Tema"
      className={cn('glass-chip flex items-center gap-0.5 p-0.5', className)}
    >
      {OPTIONS.map(([value, Icon, label]) => (
        <button
          key={value}
          type="button"
          role="radio"
          aria-checked={theme === value}
          aria-label={label}
          title={label}
          onClick={() => setTheme(value)}
          className={cn(
            'rounded-full p-1.5 transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal',
            theme === value
              ? 'bg-brand-teal/20 text-current'
              : 'opacity-60 hover:opacity-100',
          )}
        >
          <Icon className="h-4 w-4" aria-hidden />
        </button>
      ))}
    </div>
  )
}

export default ThemeToggle
