import { cn } from '@/lib/utils'

interface LogoProps {
  /** Pixel size of the square logo mark. */
  size?: number
  className?: string
  /** Render the "PaxAssist" wordmark next to the mark. */
  withWordmark?: boolean
}

/**
 * PaxAssist logo — PLACEHOLDER.
 *
 * This is a temporary, code-drawn mark (a paper plane = travel) so the app and
 * the /design playground have something to render. Replace the contents of
 * <g id="logo-mark"> with the final SVG produced by the logo workflow
 * (AI concept -> clean SVG -> SVGO). Keep the `size` / `withWordmark` API and
 * the `fill-primary` / `fill-primary-foreground` classes so it stays theme-aware.
 */
export function Logo({ size = 32, className, withWordmark = false }: LogoProps) {
  return (
    <span className={cn('inline-flex items-center gap-2', className)}>
      <svg
        width={size}
        height={size}
        viewBox="0 0 32 32"
        fill="none"
        role="img"
        aria-label="PaxAssist"
        xmlns="http://www.w3.org/2000/svg"
      >
        <g id="logo-mark">
          <rect width="32" height="32" rx="8" className="fill-primary" />
          <path
            d="M24 8L8 14.8l6.2 2.2 1.6 6.8L24 8z"
            className="fill-primary-foreground"
          />
          <path
            d="M14.2 17l9.8-9-8.2 11-1.6-2z"
            className="fill-primary-foreground"
            opacity={0.55}
          />
        </g>
      </svg>
      {withWordmark && (
        <span className="text-lg font-bold tracking-tight text-foreground">
          Pax<span className="text-primary">Assist</span>
        </span>
      )}
    </span>
  )
}

export default Logo
