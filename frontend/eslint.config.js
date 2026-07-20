import js from '@eslint/js'
import globals from 'globals'
import reactHooks from 'eslint-plugin-react-hooks'
import reactRefresh from 'eslint-plugin-react-refresh'
import tseslint from 'typescript-eslint'

export default tseslint.config(
  { ignores: ['dist'] },
  {
    extends: [js.configs.recommended, ...tseslint.configs.recommended],
    files: ['**/*.{ts,tsx}'],
    languageOptions: {
      ecmaVersion: 2020,
      globals: globals.browser,
    },
    plugins: {
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...reactHooks.configs.recommended.rules,
      'react-refresh/only-export-components': [
        'warn',
        { allowConstantExport: true },
      ],
    },
  },
  {
    // §1 token hijyeni: bileşenlerde ham hex renk yasak — semantic Tailwind sınıfı,
    // index.css token'ı veya brand-* kullanılmalı. Gerçek string/template literal'leri
    // yakalar (yorumlar AST literal'i olmadığından etkilenmez). Kaynak: docs/audit.md §1.
    files: ['src/**/*.{ts,tsx}'],
    // Zorunlu istisnalar: marka hex kaynağı, tema-color meta değeri, tasarım swatch
    // etiketleri ve GLSL shader (DarkVeil — `#define`/`#ifdef` direktifleri hex renk DEĞİL).
    ignores: [
      'src/lib/brand.ts',
      'src/app/theme.tsx',
      'src/pages/Design.tsx',
      'src/components/DarkVeil.tsx',
    ],
    rules: {
      'no-restricted-syntax': [
        'error',
        {
          selector: 'Literal[value=/#[0-9a-fA-F]{3,8}/]',
          message:
            'Ham hex renk yasak (§1) — semantic Tailwind sınıfı, index.css token veya brand-* kullan. Zorunlu istisna: lib/brand.ts.',
        },
        {
          selector: 'TemplateElement[value.raw=/#[0-9a-fA-F]{3,8}/]',
          message:
            'Ham hex renk yasak (§1) — semantic Tailwind sınıfı, index.css token veya brand-* kullan. Zorunlu istisna: lib/brand.ts.',
        },
      ],
    },
  },
)
