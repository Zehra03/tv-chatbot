# Frontend — PaxAssist (CLAUDE.md)

Guidance for Claude Code when working in `frontend/`. The root `../CLAUDE.md` covers
the whole repo; this file covers the frontend app, its **design system**, and the
**logo / UI design workflow**. See `../docs/frontend-architecture.md` for the full spec.

## Stack

React 18 · TypeScript · Vite 5 · Tailwind CSS v3 · shadcn/ui (new-york, base color **blue**).
Path alias `@/*` → `src/*` (configured in `vite.config.ts` + `tsconfig*.json`).

```bash
npm run dev      # dev server at http://localhost:5173
npm run build    # tsc -b && vite build (CI runs this)
npm run preview  # serve the production build
```

> Node note: the installed Node is 20.11.1. `create-vite@latest` / Tailwind v4 / the newest
> CLIs require Node ≥ 20.19, so the project is pinned to **Vite 5 + Tailwind v3** on purpose.
> If a `shadcn`/`Magic` CLI errors with `styleText`/engine warnings, upgrade Node to ≥ 20.19.

## Design System (single source of truth)

Tokens live as HSL CSS variables in `src/index.css` and are wired into Tailwind in
`tailwind.config.js`. **Use semantic Tailwind classes, never raw hex** — e.g. `bg-primary`,
`text-muted-foreground`, `border`. This keeps light/dark + theming consistent.

**Visual language: flat, light-first (Booking/Stripe/Linear).** No glassmorphism, no heavy
gradients, no WebGL. Surfaces are solid `bg-card` + `border` + soft shadow (`shadow-soft`).
Default theme is **light** (`app/theme.tsx` `DEFAULT_THEME = 'light'`, mirrored in the
`index.html` FOUC script); dark is a supported secondary theme. Follow **60-30-10**: ~60%
neutral (white/`bg-background`), ~30% brand blue, ≤10% orange — orange is CTA-only.

- **Brand:** PaxAssist — travel chatbot. Fixed identity palette (does not theme-switch),
  defined in `tailwind.config.js` + mirrored in `lib/brand.ts`: `brand-navy #00243F`,
  `brand-blue #004E89`, `brand-steel #1A659E`, `brand-orange #FF6B35`, `brand-peach #F7C59F`,
  `brand-cream #EFEFD0`. No teal/iris/ice.
- **Primary:** `--primary` = `206 100% 27%` (`#004E89`, brand blue). Use `bg-primary` /
  `text-primary` / `fill-primary`.
- **Neutrals & semantic:** `background`, `foreground`, `muted`, `accent`, `card`,
  `secondary`, `destructive` (+ `destructive-emphasis` for readable error **text**),
  `success` (`#22C55E`), `warning` (`#F59E0B`) — all as `*-foreground` pairs.
- **Buttons** (`ui/button.tsx`, flat): `cta` = solid orange + navy text (the primary action —
  Search/Book/Continue/Pay/Confirm; navy-on-orange is 5.5:1, white-on-orange fails AA so we do
  NOT use white); `default` = neutral white+border; `secondary` = white + blue border + blue
  text; `ghost` = blue text; `outline`/`destructive`/`link` as named.
- **Typography:** Inter (loaded in `index.css`). Hierarchy via **size + weight**, not color.
- **Spacing:** 8px rhythm — Tailwind scale (`gap-2`=8px, `gap-4`=16px, `p-6`=24px).
- **Radius:** `--radius` = `0.75rem`. Buttons/inputs `rounded-lg` (12px), cards `rounded-2xl`
  (16px), dialogs `rounded-[1.25rem]` (20px), badges `rounded-full`.
- **Components:** shadcn/ui primitives in `src/components/ui/`; 21st.dev Magic for richer
  modern/animated pieces. Shared app primitives in `src/components/`.

**Scope every generation request to the system**, e.g. *"login formu üret: shadcn Button +
Input, gap-4, sadece primary renk, Inter"*. This constrains Magic/shadcn output and keeps
screens consistent.

## Design Playground

`src/pages/Design.tsx` (rendered by `App.tsx` at `#design`; `/` now lands on the login screen)
is the **screenshot target** for the Playwright MCP loop. It shows the logo at favicon→hero sizes, color swatches, typography,
and a **"Generated components" drop zone**. Put freshly generated components there, screenshot,
critique, then move the approved component into its feature folder.

## Logo workflow (AI concept → clean SVG)

`src/components/Logo.tsx` is a **placeholder** paper-plane mark (`size`, `withWordmark` props,
theme-aware `fill-primary` / `fill-primary-foreground`). To produce the real logo:

1. Generate 3–4 raster concepts via the image-gen MCP (travel/passenger + trust, blue).
2. Pick a direction.
3. Have Claude redraw it as a **clean SVG** inside `<g id="logo-mark">`, keeping the component
   API and semantic classes.
4. Refine via Playwright screenshots at both 16px and 96px.
5. Optimize: `npx svgo src/assets/logo.svg -o src/assets/logo.min.svg`. Export a favicon into
   `public/`.

## UI iteration loop

1. (Optional) Attach a reference image. **Windows:** clipboard paste from Snipping Tool can
   fail in Claude Code — pass a **file path** instead.
2. Generate with Magic / shadcn MCP (writes into `src/...`).
3. `npm run dev`, then Playwright MCP screenshots the playground.
4. "İstediğim bu [ref], elimizdeki bu [screenshot] — spacing/renk/hizalama düzelt." → iterate.
5. Approved → move into the feature folder (`features/<chat|hotels|flights|reservation>`).

## Guardrails (inherit from root CLAUDE.md)

- **No secrets in the frontend or git.** AI/TourVisio keys are backend-only. MCP API keys go in
  env vars referenced from `../.mcp.json` (`${VAR}`), never committed.
- The browser talks only to the team backend (`VITE_API_BASE_URL`); during dev, MSW mocks it.
- The chatbot never books — it searches/lists/routes to the controlled reservation form.
- Validate forms with Zod; never render unapproved/raw backend content.
