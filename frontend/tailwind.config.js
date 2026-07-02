import tailwindcssAnimate from 'tailwindcss-animate'

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: ['class'],
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    container: {
      center: true,
      // Mobilde 2rem kenar boşluğu 375px'in ~%17'sini yiyordu — kademeli ölçek.
      padding: { DEFAULT: '1rem', sm: '2rem' },
      screens: { '2xl': '1400px' },
    },
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', 'sans-serif'],
        // FloatingInput etiketleri `font-mono` kullanıyor — kontrollü sistem yığını.
        mono: ['ui-monospace', 'SFMono-Regular', 'Menlo', 'Consolas', 'monospace'],
      },
      colors: {
        // Fixed brand identity palette (does NOT theme-switch). Single source of
        // truth for the dark auth/hero surfaces — use `bg-brand-navy`,
        // `text-brand-ice`, `from-brand-blue`, etc. instead of raw hex.
        brand: {
          navy: '#0B234A', // deep background
          blue: '#2E8FFF', // primary action / smoke
          teal: '#17D6C3', // accent / focus
          iris: '#8B8CFF', // periwinkle highlight
          ice: '#A9E9FF', // light text / labels on dark
        },
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
        },
        secondary: {
          DEFAULT: 'hsl(var(--secondary))',
          foreground: 'hsl(var(--secondary-foreground))',
        },
        destructive: {
          DEFAULT: 'hsl(var(--destructive))',
          foreground: 'hsl(var(--destructive-foreground))',
        },
        muted: {
          DEFAULT: 'hsl(var(--muted))',
          foreground: 'hsl(var(--muted-foreground))',
        },
        accent: {
          DEFAULT: 'hsl(var(--accent))',
          foreground: 'hsl(var(--accent-foreground))',
        },
        popover: {
          DEFAULT: 'hsl(var(--popover))',
          foreground: 'hsl(var(--popover-foreground))',
        },
        card: {
          DEFAULT: 'hsl(var(--card))',
          foreground: 'hsl(var(--card-foreground))',
        },
      },
      borderRadius: {
        lg: 'var(--radius)',
        md: 'calc(var(--radius) - 2px)',
        sm: 'calc(var(--radius) - 4px)',
      },
      keyframes: {
        'accordion-down': {
          from: { height: '0' },
          to: { height: 'var(--radix-accordion-content-height)' },
        },
        'accordion-up': {
          from: { height: 'var(--radix-accordion-content-height)' },
          to: { height: '0' },
        },
        // Drifting blobs for BackgroundGradientAnimation.
        moveHorizontal: {
          '0%': { transform: 'translateX(-50%) translateY(-10%)' },
          '50%': { transform: 'translateX(50%) translateY(10%)' },
          '100%': { transform: 'translateX(-50%) translateY(-10%)' },
        },
        moveInCircle: {
          '0%': { transform: 'rotate(0deg)' },
          '50%': { transform: 'rotate(180deg)' },
          '100%': { transform: 'rotate(360deg)' },
        },
        moveVertical: {
          '0%': { transform: 'translateY(-50%)' },
          '50%': { transform: 'translateY(50%)' },
          '100%': { transform: 'translateY(-50%)' },
        },
        // Skeleton yükleme parıltısı (bg-[length:200%_100%] gradyanla kullanılır).
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
      },
      animation: {
        'accordion-down': 'accordion-down 0.2s ease-out',
        'accordion-up': 'accordion-up 0.2s ease-out',
        first: 'moveVertical 30s ease infinite',
        second: 'moveInCircle 20s reverse infinite',
        third: 'moveInCircle 40s linear infinite',
        fourth: 'moveHorizontal 40s ease infinite',
        fifth: 'moveInCircle 20s ease infinite',
        shimmer: 'shimmer 1.8s linear infinite',
      },
    },
  },
  plugins: [tailwindcssAnimate],
}
