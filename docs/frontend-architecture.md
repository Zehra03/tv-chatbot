# Frontend Architecture — sanProject

AI Chatbot for hotel & flight search and controlled reservation over the **TourVisio API**.
This document describes the **frontend architecture only**. It is a design/planning document — no application code is created here yet.

> **Tech Stack:** Alternative 2 (Java/Spring Boot backend) · **React** frontend.

---

## 1. Overview

The frontend is a **chatbot-centric single-page application (SPA)**. The user talks to an AI assistant in natural language; the assistant understands the intent (hotel or flight), asks for any missing criteria, lists matching products as cards, and hands a selected product off to a **controlled reservation flow**.

Key principles:

- **The browser never calls the TourVisio API directly.** Every request goes through the team's backend. During frontend development the backend is simulated with **MSW (Mock Service Worker)**.
- **No secrets in the frontend.** AI and TourVisio API keys live only on the backend. The frontend knows nothing about them.
- **The chatbot never completes a booking on its own.** It only searches, lists and routes the user to the reservation screen, where the user explicitly confirms.

---

## 2. Tech Stack (frontend)

| Concern | Choice |
|---------|--------|
| Language | TypeScript |
| UI library | React 18 |
| Build tool | Vite |
| Routing | React Router |
| **Server state / cache** | **React Query (TanStack Query)** |
| **Client / UI state** | **Redux Toolkit** |
| Styling | Tailwind CSS |
| HTTP client | Axios |
| Mock API | **MSW (Mock Service Worker)** |
| Forms & validation | React Hook Form + Zod |
| Testing | Vitest + React Testing Library |

---

## 3. Screens / Routes

From the project document (§7.1 Frontend Layer):

| Route | Screen | Notes |
|-------|--------|-------|
| `/login` | Login / mock user | Mock authentication, sets the user in Redux |
| `/chat` | Chatbot (main) | Message thread + inline result cards + "Go to reservation" |
| `/hotels` | Hotel results | Dedicated, filterable list view |
| `/flights` | Flight results | Dedicated, filterable list view |
| `/reservation/new` | Reservation form | Product summary + guest/passenger + contact + confirm |
| `/reservations` | Reservation list | Number, type, date, guest, total, status, detail button |
| `/reservations/:id` | Reservation detail | Full reservation breakdown |

A **protected-route wrapper** redirects to `/login` when there is no mock user in state. `/chat` is the default landing route after login.

---

## 4. Folder Structure (feature-based)

```
frontend/src/
  app/            # Application wiring
    store.ts        # Redux Toolkit store
    router.tsx      # React Router routes + protected wrapper
    providers.tsx   # React Query + Redux + Router providers
  features/
    auth/           # mock login: slice, page, hooks
    chat/           # chat page, message list, composer, chatSlice, useChat
    hotels/         # hotel list, hotel card, filters, useHotelSearch
    flights/        # flight list, flight card, filters, useFlightSearch
    reservation/    # form, list, detail, draft slice, mutations
  components/      # shared UI: Button, Input, Card, Badge, Spinner, Layout, Modal
  api/            # axios client + endpoint modules (chat, hotel, flight, reservation)
  mocks/          # MSW handlers, browser worker, fixtures
  types/          # domain TypeScript types
  hooks/          # shared hooks
  utils/          # formatters (price, date), validators
  styles/         # tailwind base
  main.tsx
  App.tsx
```

Each **feature folder** is self-contained: its page(s), components, Redux slice (if any), and React Query hooks live together. Shared, presentation-only primitives live in `components/`.

---

## 5. State Management — the key design decision

The application has two kinds of state, and each has one clear owner.

### React Query owns all **server data**
Anything that comes from (or is sent to) the backend:

| Hook | Type | Endpoint |
|------|------|----------|
| `useSendMessage` | mutation | `POST /api/chat` |
| `useHotelSearch` | query (keyed by criteria) | `POST /api/hotels/search` |
| `useFlightSearch` | query (keyed by criteria) | `POST /api/flights/search` |
| `useCreateReservation` | mutation | `POST /api/reservations` |
| `useReservations` | query | `GET /api/reservations` |
| `useReservation(id)` | query | `GET /api/reservations/:id` |

React Query handles caching, loading/error states, retries and invalidation (e.g. creating a reservation invalidates the reservation list).

### Redux Toolkit owns **client / UI state only**

| Slice | Responsibility |
|-------|----------------|
| `authSlice` | Mock user / session |
| `chatSlice` | Message thread, active chat session id, **search criteria being progressively filled** by the chatbot (slot-filling), pending clarifying question |
| `reservationDraftSlice` | The product selected from chat/results that is being booked |
| `uiSlice` | Active filters, open modals, toasts |

> **Rule of thumb:** server cache → **React Query**; ephemeral UI & conversation state → **Redux**.

---

## 6. API Layer & Mocking (MSW)

### API client
`api/client.ts` exposes a single Axios instance:

- `baseURL` from `import.meta.env.VITE_API_BASE_URL`
- response interceptor to normalize errors
- **no API keys** — the AI and TourVisio credentials are backend-only

Endpoint modules (`api/chatApi.ts`, `hotelApi.ts`, `flightApi.ts`, `reservationApi.ts`) mirror the backend contract:

```
POST /api/chat
POST /api/hotels/search
POST /api/flights/search
POST /api/reservations
GET  /api/reservations
GET  /api/reservations/:id
```

### Mock Service Worker
`mocks/handlers.ts` intercepts those exact endpoints and returns fixture data from `mocks/fixtures/`. The chat handler simulates intent extraction and slot-filling (it replies with either a clarifying question or a set of result cards). MSW is started only in development, guarded in `main.tsx`.

**Swap path to the real backend:** disable the MSW worker and point `VITE_API_BASE_URL` at the backend. **No component or hook code changes** — the contract is identical.

---

## 7. Data Models (`types/`)

Aligned to the backend domain layer (project document §7.4).

| Type | Key fields |
|------|-----------|
| `HotelSearchCriteria` | location/hotelName, checkIn, checkOut, adults, children + ages, nationality, currency, rooms, *(optional: stars, board type, price range, region, sort)* |
| `FlightSearchCriteria` | from, to, departDate, passengers, currency, oneWay/roundTrip, *(optional: returnDate, nonstop, airline, time range, baggage)* |
| `HotelProduct` | id, name, region, stars, price, boardType, availability |
| `FlightProduct` | id, airline, departTime, arriveTime, stops, baggage, price |
| `Passenger` | firstName, lastName, contact info |
| `Reservation` | id/number, productType (hotel/flight), date, guest/passenger, total, status |
| `ChatSession` | id, messages, accumulated criteria |
| `ChatMessage` | role (user/assistant), content, optional result cards |

---

## 8. Chat Flow

```
User types message
      │
      ▼
useSendMessage (mutation → POST /api/chat)
      │
      ├─ missing criteria ──► assistant asks a clarifying question
      │                        (chatSlice stores partial criteria + pending question)
      │
      └─ criteria complete ──► backend(mock) returns result cards
                               (rendered inline in the chat thread)
                                      │
                                      ▼
                          user clicks "Select" on a card
                                      │
                                      ▼
            reservationDraftSlice ← selected product
                                      │
                                      ▼
                       navigate to /reservation/new
```

The chatbot **only** searches, lists and routes — it never books. `chatSlice` accumulates the conversation and the progressively-filled search criteria.

---

## 9. Reservation Flow

```
reservationDraft (selected product)
      ▼
Reservation form  (React Hook Form + Zod validation)
   - product summary
   - guest / passenger info
   - contact info
      ▼
Preview + explicit confirmation checkbox
      ▼
useCreateReservation (mutation → POST /api/reservations)
      ▼
Success / failure result screen
      ▼
Reservation list refreshes (React Query invalidation)
      ▼
Reservation detail (/reservations/:id)
```

The explicit confirmation checkbox enforces the "controlled reservation" rule.

---

## 10. Cross-cutting Concerns

### Security & privacy (frontend slice of project doc §13)
- AI / TourVisio API keys are **never** in the frontend or committed to git.
- All TourVisio access is backend-only; the browser talks only to the team backend.
- Input validation on every form (Zod schemas).
- No personal data written to logs.
- Prompt-injection protection is handled server-side; the frontend only renders backend-approved content.

### Styling
Tailwind CSS utility classes with a small set of shared component primitives (`Button`, `Input`, `Card`, `Badge`, `Spinner`, `Modal`, `Layout`). Responsive chat layout (message thread + composer).

The visual language is **flat and light-first** (Booking/Stripe/Linear): no glassmorphism, no heavy gradients, no WebGL. Two color systems coexist, both consumed as Tailwind classes (never raw hex in components): the **semantic shadcn tokens** (`primary`, `muted`, `card`, `background`, `success`, `warning`, `destructive`/`destructive-emphasis`, … — HSL CSS variables, theme-aware) for all surfaces, and a **fixed brand palette** (`brand-navy/blue/steel/orange/peach/cream`, defined in `tailwind.config.js`) used mainly for the orange `cta` button and accents. Default theme is **light**; dark is a supported secondary theme. Surfaces are solid `bg-card` + `border` + `shadow-soft`; the primary CTA is solid orange with navy text (white-on-orange fails WCAG AA). See `frontend/CLAUDE.md` for the palette and design-system rules.

### Environment variables
| Variable | Purpose |
|----------|---------|
| `VITE_API_BASE_URL` | Backend base URL (or unused while MSW is active) |

No secrets are exposed to the client.

### Testing strategy
Component tests with React Testing Library backed by MSW. Example targets: chat composer, hotel card, reservation form validation.

### Suggested build order (for when implementation starts)
1. Scaffold (Vite + React + TS, Tailwind)
2. `types/`
3. `api/` + MSW handlers & fixtures
4. Layout + routing + protected wrapper
5. Auth (mock login)
6. Chat feature
7. Hotel & flight results
8. Reservation flow (form → list → detail)

---

## Out of Scope (this document)
Backend, AI prompt design, and the C#/Vue/MySQL stack alternatives are not covered here. This document is frontend architecture only and contains no application code.
