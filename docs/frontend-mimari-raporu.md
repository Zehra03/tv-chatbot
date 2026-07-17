# PaxAssist Frontend — Mimari Raporu

> Bu belge PaxAssist'in **frontend** tarafını baştan sona anlatır: hangi teknoloji neden
> seçildi, mimari katmanlar nasıl kuruldu, hangi **SOLID prensipleri** ve **tasarım
> desenleri** nerede uygulandı, ve tüm bunlar bir **müşteri sunumunda** hangi değer
> önermelerine dönüşür. Her teknik iddianın yanında koddaki kaynağına tıklanabilir bir
> bağlantı verilmiştir.
>
> Kapsam: yalnızca `frontend/`. Backend, mimarinin geri kalanı için `docs/architecture.md`
> ve `docs/frontend-architecture.md`'ye bakınız.

---

## İçindekiler

1. [Yönetici Özeti](#1-yönetici-özeti)
2. [Teknoloji Seçimleri ve Gerekçeleri](#2-teknoloji-seçimleri-ve-gerekçeleri)
3. [Katman Katman Mimari](#3-katman-katman-mimari)
4. [State Yönetimi Ayrımı](#4-state-yönetimi-ayrımı)
5. [API Katmanı ve Dayanıklılık](#5-api-katmanı-ve-dayanıklılık)
6. [SOLID Prensipleri (kavram + kod kanıtı)](#6-solid-prensipleri-kavram--kod-kanıtı)
7. [Tasarım Desenleri (kavram + kod kanıtı)](#7-tasarım-desenleri-kavram--kod-kanıtı)
8. [TypeScript Disiplini](#8-typescript-disiplini)
9. [Tasarım Sistemi ve Tema](#9-tasarım-sistemi-ve-tema)
10. [Test Stratejisi](#10-test-stratejisi)
11. [Erişilebilirlik ve UX Kalitesi](#11-erişilebilirlik-ve-ux-kalitesi)
12. [Müşteriye Sunum: Pazarlanacak Noktalar](#12-müşteriye-sunum-pazarlanacak-noktalar)

---

## 1. Yönetici Özeti

**PaxAssist**, yapay zekâ destekli bir otel & uçuş arama ve rezervasyon uygulamasıdır.
Frontend, kullanıcının bir **sohbet botuyla** konuşarak arama yaptığı, sonuçları listelediği
ve ardından **AI'sız, kontrollü bir rezervasyon formuna** yönlendirildiği bir Tek Sayfa
Uygulamasıdır (SPA).

**Tek cümlelik mimari kimlik:** React 18 + TypeScript üzerine kurulu, *sunucu state'i ile
istemci state'ini keskin biçimde ayıran*, tüm ağ trafiğini tek bir cephe (facade) katmanından
geçiren, tasarımı üç katmanlı bir token sistemiyle yöneten ve dört katmanlı bir test
piramidiyle korunan, **feature-first** (özellik öncelikli) bir frontend.

Öne çıkan dört mühendislik kararı:

- **Sunucu state (React Query) ↔ istemci state (Redux) ayrımı** — her verinin sahibi net;
  bu, önbellek tutarlılığı ve kiracı (kullanıcı) izolasyonu için tek bir doğru yer yaratır.
- **Tek API cephesi + MSW ile taklit backend** — bileşenler somut HTTP kütüphanesine değil,
  soyut bir `@/api` yüzeyine bağlı. Sahte backend'den gerçek backend'e geçiş **tek bir ortam
  değişkeniyle**, hiçbir bileşen değişmeden yapılır.
- **Üç katmanlı tasarım token'ları + WCAG-ölçülü kontrast** — renkler asla ham hex değil;
  hepsi anlamsal token'lardan akar. Açık/koyu tema, erişilebilirlik kontrast oranları
  ölçülerek tasarlandı.
- **Güvenlik-öncelikli sınırlar** — hiçbir API anahtarı frontend'de yok, sohbet botu asla
  rezervasyon yapmaz, aynı tarayıcıda ikinci kullanıcı birincinin verisini göremez.

Kod tabanının belirgin bir kalite sinyali: **neredeyse her dosya, alınan kararın *neden*ini
açıklayan bir Türkçe yorumla açılır** — çoğu zaman gerçek bir regresyonu, bir WCAG kontrast
oranını veya bir yarış (race condition) durumunu birebir belgeleyerek. Bu, olağanüstü yüksek
bir mühendislik disiplinidir.

---

## 2. Teknoloji Seçimleri ve Gerekçeleri

Tüm bağımlılıklar [frontend/package.json](../frontend/package.json)'da. Sürüm sabitlemeleri
rastgele değil: makinedeki Node **20.11.1** olduğundan, en yeni CLI'lar (Tailwind v4,
`create-vite@latest`) Node ≥ 20.19 istediği için proje **bilinçli olarak** Vite 5 + Tailwind
v3'e sabitlenmiştir ([frontend/CLAUDE.md](../frontend/CLAUDE.md)).

| Kütüphane | Rolü | Neden bu seçim? |
|---|---|---|
| **React 18** + **react-dom** | UI kütüphanesi | Olgun, geniş ekosistem; React 19 yerine bilinçli 18 (Node/araç uyumu). |
| **TypeScript 5.6** (strict) | Tip güvenliği | Backend sözleşmesini derleme zamanında yakalar; DTO'lar birebir tiplenir. |
| **Vite 5** | Derleyici / dev sunucu | Hızlı HMR; test yapılandırması da (Vitest) aynı config'te birleşir. |
| **Tailwind CSS v3** | Stil | Anlamsal token sınıflarıyla tutarlı tasarım; `darkMode: ['class']`. |
| **shadcn/ui** (CVA + Radix Slot) | UI primitifleri | Sahiplenilen (kopyalanan) bileşenler; varyant sistemi genişletilebilir. |
| **Redux Toolkit** + react-redux | **İstemci/UI state** | Oturum, sohbet, taslak, filtre gibi kalıcı olmayan istemci durumu. |
| **TanStack Query (React Query) 5** | **Sunucu state** | Arama sonuçları, rezervasyonlar, sohbet geçmişi — önbellek + geçersizleştirme. |
| **axios** | HTTP istemcisi | Tek instance + interceptor'larla token/oturum/hata yönetimi tek yerde. |
| **Zod** + **react-hook-form** | Form doğrulama | Rezervasyon formu sınırında şema-tabanlı doğrulama. |
| **framer-motion** | Animasyon | Sayfa geçişleri; `reducedMotion="user"` ile erişilebilirlik. |
| **sonner** | Toast bildirimleri | Marka temalı, tema-duyarlı geçici bildirimler. |
| **MSW 2** | Taklit backend | Aynı handler'lar hem tarayıcıda hem testte; gerçek backend'e sıfır-değişiklikle geçiş. |
| **Vitest 2** + RTL + user-event | Test | Vite ile birleşik; erişilebilirlik-öncelikli davranış testleri. |
| clsx + tailwind-merge + CVA | Sınıf birleştirme | `cn()` yardımcısıyla çakışan Tailwind sınıflarını güvenle birleştirir. |
| lucide-react, date-fns, react-day-picker | İkon / tarih | Hafif ikonlar; takvim/tarih seçici. |

**Derleme betikleri** ([package.json](../frontend/package.json)) sade ve konvansiyonel:
`dev` (vite), `build` (`tsc -b && vite build` — CI bunu koşar), `test` (`vitest run`),
`lint` (eslint). TypeScript **proje referanslarıyla** ayrılmış (`tsconfig.app.json` +
`tsconfig.node.json`) ve sertleştirilmiş: `strict`, `noUnusedLocals`, `noUnusedParameters`,
`noFallthroughCasesInSwitch`.

---

## 3. Katman Katman Mimari

### 3.1 Klasör felsefesi — feature-first (dikey dilim)

Kod, teknik türe göre (tüm bileşenler bir klasör, tüm hook'lar başka bir klasör) değil,
**özelliğe göre** (dikey dilim) organize edilir. Her özellik kendi slice'ını, hook'larını,
bileşenlerini, sayfalarını ve **yanına yerleştirilmiş testlerini** bir arada tutar.

```
frontend/src/
├─ main.tsx            # Giriş: MSW'yi koşullu başlat, <Providers/> render et
├─ index.css           # Tasarım token'ları (HSL CSS değişkenleri), Inter fontu
├─ app/                # Uygulama kompozisyonu ve kablolama
│   ├─ providers.tsx   # Sağlayıcı ağacı + SessionManager
│   ├─ router.tsx      # Rota ağacı + ProtectedRoute / RequireAccount
│   ├─ store.ts        # Redux configureStore
│   ├─ hooks.ts        # Tipli useAppDispatch / useAppSelector
│   └─ theme.tsx       # ThemeProvider (<html> üzerinde light/dark)
├─ api/                # Tek axios istemcisi + uç-başına modüller + barrel
├─ features/           # DİKEY DİLİMLER (ana konvansiyon)
│   ├─ auth/  chat/  hotels/  flights/  reservation/  profile/
│   └─ ui/            # Paylaşılan UI STATE'i (uiSlice, filterChips) — bileşen değil
├─ components/         # Özellikler arası paylaşılan bileşenler
│   ├─ ui/            # shadcn/ui + 21st.dev primitifleri
│   └─ *.tsx          # Layout, ThemeToggle, Logo, ErrorState, arka planlar…
├─ pages/              # Rota düzeyi, özelliğe ait olmayan sayfalar (Landing, Design, Error)
├─ lib/                # Yardımcılar + hook'lar (brand, apiErrorMessage, useDebouncedValue…)
├─ types/              # Paylaşılan alan tipleri (chat, product, reservation…) + barrel
├─ mocks/              # MSW (browser, server, handlers, chatEngine, fixtures)
└─ test/               # Vitest kurulumu + canlı-backend E2E
```

Konvansiyonlar: her yerde `@/*` yol takma adı (derin göreli import yok); testler ayrı bir
ağaçta değil kaynağın *yanında*; `features/ui/` ince bir ayrımdır — paylaşılan UI
*durumunu* tutar, paylaşılan *bileşenler* `components/`'te yaşar.

### 3.2 Bootstrap — açılış sırası ve nedeni

[main.tsx](../frontend/src/main.tsx) tek bir sıra dışı iş yapar: **render'dan önce MSW'yi
koşullu başlatır**. Taklit worker yalnızca geliştirmede ve `VITE_ENABLE_MSW !== 'false'` ise
devreye girer; dinamik `import` sayesinde üretim paketine hiç girmez. Bir de zekice bir
"sert yenileme (Ctrl+F5)" koruması vardır: service worker sayfayı kontrol etmiyorsa,
`sessionStorage` bayrağıyla sınırlanmış tek bir yeniden yükleme tetiklenir (döngüye girmeden).

`main.tsx` yalnızca `<Providers/>` render eder — tüm kompozisyon
[providers.tsx](../frontend/src/app/providers.tsx)'te toplanır. **Sağlayıcı sırası
dıştan içe** ve bu sıra bilinçlidir:

```
<Provider store={store}>                    // 1. Redux (istemci state)
  <QueryClientProvider client={queryClient}> // 2. React Query (sunucu state)
    <ThemeProvider>                          // 3. Tema bağlamı (light/dark)
      <MotionConfig reducedMotion="user">    // 4. framer-motion erişilebilirlik
        <SessionManager />                   // Oturum bekçisi (yalnız yan etki, null render)
        <LiquidGlassFilter />                // Paylaşılan SVG filtre tanımı (bir kez)
        <RouterProvider router={router} />   // Uygulama
        <ThemedToaster />                    // sonner toaster (portal)
```

`QueryClient`, `refetchOnWindowFocus: false` ve `retry: 1` ile yapılandırılmıştır. `MotionConfig`
en dışta değil ama tüm uygulamayı sarar: **tüm framer-motion animasyonları kullanıcının
`prefers-reduced-motion` tercihine uyar** — bu tek satır, uygulama genelinde bir erişilebilirlik
garantisidir.

### 3.3 Routing — iki katmanlı koruma modeli

[router.tsx](../frontend/src/app/router.tsx) veri-router (`createBrowserRouter`) kullanır ve
rota ağacını ayrıca `export` eder ki **hata sınırı yerleşimi test edilebilsin**. İki katmanlı
bir yetki modeli vardır:

- **`ProtectedRoute`** — hiç oturum yoksa `/login`'e yönlendirir; istenen adresi
  (`pathname + search`) `from` olarak taşır ki giriş sonrası kullanıcı geldiği yere dönebilsin.
- **`RequireAccount`** — sohbet/arama **misafire açıktır**, ama rezervasyon ve profil **gerçek
  hesap** ister (misafiri `account-required` sebebiyle `/login`'e düşürür). Bu, "kontrollü
  rezervasyon" ilkesinin rota düzeyindeki karşılığıdır.

Ağaç ayrıca **iç içe `errorElement`** kullanır: kök düzeyde tam-sayfa `RouteErrorPage`
(Login/Layout çökerse), Layout'un *içinde* ayrı bir hata sınırı (sayfa çökerse header/nav
korunur). Yazdırma voucher'ı (`/reservations/:id/print`) bilinçli olarak **Layout'un dışında,
kardeş bir dalda** durur — kabuk (header, `overflow-hidden`, gece-göğü yüzeyi) kâğıda taşınamaz.

---

## 4. State Yönetimi Ayrımı

Kod tabanı, yorumlarda `docs/frontend-architecture.md §5`'e atıfla tekrarlanan tek bir kuralı
uygular:

> **Redux = istemci / UI / kalıcı-olmayan state. React Query = sunucu state.**

Bu ayrım [store.ts](../frontend/src/app/store.ts)'te açıkça belgelenir ("yalnızca istemci/UI
state'i… Sunucu verisi React Query'e ait"). Redux dört slice tutar:

- **`authSlice`** — oturum (`user, token, refreshToken, guestId`), `localStorage`'a kalıcılaşır
  ve token'ları reducer içindeki yan etkilerle axios istemcisine aynalar
  (`setAuthToken/setRefreshToken/setGuestId`).
- **`uiSlice`** — otel/uçuş **filtreleri + sıralama**, aktif modal, toast. (Not: sunucu verisi
  React Query'de kalır; filtreler sunucudan gelen listeye *istemci tarafında* uygulanır.)
- **`chatSlice`** — sohbet thread'i, `sessionId`, ve konuşma değişince bayat yanıtları eleyen
  bir **`epoch`** sayacı.
- **`reservationDraftSlice`** — "ürün seç → rezervasyon formuna taşı" devri; `logout`/misafir
  sınırında temizlenir.

Sunucu state'i özellik-başına yerleştirilmiş React Query hook'larında yaşar:
`useHotelSearch`, `useFlightSearch`, `useReservations`, `useChatSessions`, mutation'lar
`useSendMessage`, `useConfirmReservation`, `useCancelReservation`.

**Pratikteki iki kritik desen:**

- **Query key'leri kimlik taşır + önbellek kimlik değişiminde temizlenir.** Aynı tarayıcıda
  bir kullanıcı çıkıp başka biri girerse, React Query önbelleği (gcTime 5 dk) sağ kalır ve
  ikinci kullanıcı birincinin rezervasyonlarını görebilirdi. Buna karşı **iki katmanlı savunma**
  var: (1) query key'leri kimlik gömer, (2) [providers.tsx](../frontend/src/app/providers.tsx)
  içindeki `SessionManager`, kimlik değişince `queryClient.clear()` çağırır. Bu, bir güvenlik
  (kiracı izolasyonu) kararıdır, performans değil.

- **İyimser güncelleme + epoch koruması.** [useSendMessage.ts](../frontend/src/features/chat/useSendMessage.ts)
  kullanıcı mesajını hemen (iyimser) thread'e yazar, mutation'ı atar; ama istek uçuştayken
  kullanıcı başka sohbete geçerse, `onMutate`'te bağlanan epoch ile güncel epoch farklıysa
  **gelen bayat yanıt yeni thread'e yazılmaz**:

  ```ts
  onMutate: () => ({ epoch: epochRef.current }),
  onSuccess: (response, _message, context) => {
    queryClient.invalidateQueries({ queryKey: CHAT_SESSIONS_KEY })
    if (context.epoch !== epochRef.current) return   // konuşma değiştiyse yok say
    dispatch(assistantReplied(response))
  },
  ```

Özetle: **sunucunun sahip olduğu her şey** (arama sonuçları, rezervasyonlar, sohbet geçmişi)
React Query tarafından getirilip önbelleklenir; **istemcinin sahip olduğu her şey** (oturum,
taslak seçim, filtreler, modal'lar, iyimser sohbet balonları) Redux'ta yaşar.

---

## 5. API Katmanı ve Dayanıklılık

Tasarım: **tek axios instance** üzerine kurulu, **uç-başına modüller** ve tek bir **barrel
cephesi**.

### 5.1 Tek istemci + interceptor'lar

[client.ts](../frontend/src/api/client.ts) tek bir `apiClient` kurar:

```ts
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '',   // MSW aktifken boş → aynı origin
  timeout: 60_000,                                     // sonsuz askıyı sınırlar
  headers: { 'Content-Type': 'application/json' },
})
```

- **Base URL** ortamdan gelir; MSW aktifken boş bırakılır, istekler aynı origin'e gider ve
  worker yakalar. **Buraya hiçbir API anahtarı konmaz** — AI/TourVisio kimlik bilgileri
  backend'dedir.
- **İstek interceptor'ı** — birbirini dışlayan yetki: oturum varsa `Authorization: Bearer`,
  yoksa misafir için `X-Guest-Id`. Token'lar opaktır; içeriği frontend'de yorumlanmaz.
- **Yanıt interceptor'ı** iki iş yapar:
  1. **Sessiz token yenileme** — token'lı, auth-dışı bir uçta 401 gelirse, `POST /auth/refresh`
     ile yeni bir çift alıp isteği **şeffafça tekrarlar**. **Tek-uçuş (single-flight)** güvenliği
     vardır: eşzamanlı 401'ler tek bir refresh çağrısını paylaşır — çünkü refresh token'ı
     tek-kullanımlıktır (rotation) ve paralel yenilemeler birbirini geçersiz kılardı. Refresh
     yoksa/başarısızsa `UNAUTHORIZED_EVENT` yayınlar.
  2. **Hata normalizasyonu** — her hata tek tip `ApiError { status, message, code }` olur;
     `code` ya `error` (auth kodları, ör. `EMAIL_ALREADY_EXISTS`) ya da `outcome`
     (rezervasyon kodları, ör. `PREVIEW_EXPIRED`) alanından gelir.

**60 saniyelik timeout gerekçesi belgelenmiştir:** buradaki uçların hepsi TourVisio ya da AI'a
vekillik eder; `timeout: 0` (varsayılan) olsaydı sunucu bağlantıyı kabul edip yanıt vermezse
promise hiç settle olmaz, kullanıcı **sonsuza kadar spinner** görürdü. Timeout `status: null`
üretir ve hata ekranına düşülebilir.

**Döngüsüz decoupling (bağımlılık tersine çevirme):** `client.ts` store'u hiç import etmez
(döngü olurdu). `SessionManager` ile iki `window` CustomEvent üzerinden konuşur:
`UNAUTHORIZED_EVENT` (`'pax:unauthorized'`) ve `TOKENS_REFRESHED_EVENT`. Yorum bunu birebir
belgeler: *"olay köprüsü katmanları ayrık tutar."*

### 5.2 Cephe (facade) ve uç modülleri

[index.ts](../frontend/src/api/index.ts) barrel'i `apiClient`, setter'ları, olay sabitlerini ve
her uç modülünü tek yüzeyde toplar. Tüketiciler `import { hotelApi, apiClient } from '@/api'`
yazar — asla doğrudan axios'a bağlanmazlar.

Uç modülleri ince, tipli sarmalayıcılardır ve backend sözleşmesini birebir aynalar:
`authApi`, `chatApi`, `hotelApi`, `flightApi`, ve en zengini
[reservationApi.ts](../frontend/src/api/reservationApi.ts) — durumsal iki adımlı akış
(`preview` → snapshot dondur, sonra `confirm` → TourVisio).

---

## 6. SOLID Prensipleri (kavram + kod kanıtı)

SOLID, nesne yönelimli tasarımın beş bakım-kolaylığı ilkesidir. Her birini önce kavram olarak,
sonra bu koddaki karşılığıyla açıklıyoruz.

### S — Single Responsibility (Tek Sorumluluk)

*Kavram:* Bir modülün değişmesi için tek bir nedeni olmalı; her birim tek bir işi yapar.

*Koddaki karşılığı:* En temel örnek, **sunucu state ↔ istemci state ayrımıdır** (§4): sohbetin
yükleme/hata durumu Redux'ta *değil* React Query'de tutulur ([chatSlice](../frontend/src/features/chat/chatSlice.ts)
bunu yorumda belirtir). Custom hook'ların her biri tek iş yapar: [useChatSessions.ts](../frontend/src/features/chat/useChatSessions.ts)
üç ayrı hook'a bölünür (listeleme sorgusu, yükleme mutation'ı, silme mutation'ı). Sunum
bileşenleri saftır: [ErrorState.tsx](../frontend/src/components/ErrorState.tsx),
[Composer.tsx](../frontend/src/features/chat/Composer.tsx) yalnızca prop alır, veri getirmez.

### O — Open/Closed (Açık/Kapalı)

*Kavram:* Bir birim genişletmeye açık, değiştirmeye kapalı olmalı; yeni davranış eklerken
mevcut kodu bozmamalısın.

*Koddaki karşılığı:* **CVA (class-variance-authority) varyant sistemi.**
[button.tsx](../frontend/src/components/ui/button.tsx) yedi varyant (`default | destructive |
outline | secondary | cta | ghost | link`) × altı boyut tanımlar. Yeni bir varyant eklemek,
mevcut çağrı yerlerini hiç değiştirmeden `variants` haritasına bir satır eklemektir:

```ts
const buttonVariants = cva("relative inline-flex … rounded-md text-sm font-medium …", {
  variants: {
    variant: { default: "…", destructive: "…", cta: "bg-brand-orange text-brand-navy …", … },
    size:    { default: "h-9 px-4 py-2", sm: "…", lg: "…", xl: "…", xxl: "…", icon: "h-9 w-9" },
  },
  defaultVariants: { variant: "default", size: "default" },
})
```

Aynı desen [badge.tsx](../frontend/src/components/ui/badge.tsx)'te de var. Tasarım token'ları
da (semantik CSS değişkenleri → Tailwind sınıfları) temayı bileşenlere dokunmadan genişletmeyi
sağlar.

### L — Liskov Substitution (Yerine Geçme)

*Kavram:* Bir alt tip, üst tipinin yerine sorunsuz geçebilmeli; sözleşmeyi bozmamalı.

*Koddaki karşılığı:* UI bileşenleri sardıkları **native DOM arayüzlerini genişletir**, böylece
sardıkları elemanın yerine geçebilirler:

```ts
export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,   // native <button> sözleşmesi
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}
```

`<Button>`, standart bir `<button>`'ın kabul ettiği her prop'u (`onClick`, `type`, `disabled`,
`aria-*`) kabul eder — çağıran için sürprizsiz ikamedir. `BadgeProps` de `HTMLAttributes<HTMLDivElement>`'i
genişletir.

### I — Interface Segregation (Arayüz Ayrımı)

*Kavram:* İstemciler kullanmadıkları arayüzlere bağımlı olmaya zorlanmamalı; küçük ve odaklı
arayüzler yeğlenmeli.

*Koddaki karşılığı:* Prop arayüzleri küçük ve amaca özeldir — dev bir "her şeyi kabul eden"
prop nesnesi yok. [ErrorState.tsx](../frontend/src/components/ErrorState.tsx):

```ts
interface ErrorStateProps { message: string; onRetry?: () => void; retrying?: boolean }
```

`ComposerProps` yalnız `{ onSend, disabled?, placeholder? }`, `ModalProps` yalnız gerektiği
kadar alan tutar. Bileşenler tam da ihtiyaç duydukları sözleşmeye bağlıdır.

### D — Dependency Inversion (Bağımlılığın Tersine Çevrilmesi)

*Kavram:* Üst düzey modüller alt düzey ayrıntılara değil, soyutlamalara bağlı olmalı.

*Koddaki karşılığı — kod tabanının en güçlü örneği:*

- **Tüm bileşen ve hook'lar `@/api` cephesine bağlıdır, asla doğrudan axios'a değil.** Hook'lar
  `import { chatApi } from '@/api'` yazar; somut HTTP kütüphanesini bilmezler.
- **MSW takası tek bir bileşeni bile değiştirmeden yapılır.** Sahte backend'den gerçek
  backend'e geçiş yalnızca ortam değişkenidir (`VITE_ENABLE_MSW=false`,
  `VITE_API_BASE_URL=…`). Bileşen/hook değişmez, çünkü hepsi `apiClient` soyutlamasından geçer.
- **Olay köprüsüyle döngüsüz decoupling** (§5.1): `client.ts` store'a değil, soyut bir `window`
  olayına yayın yapar; `SessionManager` dinler.

Bu üçü birlikte, klasik "üst düzey politika alt düzey ayrıntıdan bağımsızdır" tanımının ders
kitabı örneğidir.

---

## 7. Tasarım Desenleri (kavram + kod kanıtı)

| Desen | Ne işe yarar | Koddaki yeri |
|---|---|---|
| **Facade (Cephe)** | Bir alt sistemi tek, sade bir yüzeyin ardında toplar | [api/index.ts](../frontend/src/api/index.ts) — beş uç modülü + istemci tek import yüzeyi |
| **Adapter (Uyarlayıcı)** | Bir arayüzü tüketicinin beklediği başka bir arayüze çevirir | [reservationApi.ts](../frontend/src/api/reservationApi.ts) ham HTTP kodlarını (201/200/202) tipli birliğe çevirir; [apiErrorMessage.ts](../frontend/src/lib/apiErrorMessage.ts) `ApiError`'ı Türkçe kullanıcı metnine çevirir |
| **Provider (Sağlayıcı)** | Bağlamı ağaç boyunca dağıtır | [providers.tsx](../frontend/src/app/providers.tsx) iç içe sağlayıcılar; [theme.tsx](../frontend/src/app/theme.tsx) özel `ThemeProvider`/`useTheme` |
| **Custom Hooks** | Durumlu mantığı bileşenlerden ayırıp yeniden kullanır | `use*` tüm `features/*` ve `lib/*`'te |
| **Container / Presentational** | Veri/orkestrasyon ile saf sunumu ayırır | `ChatPage`/`HotelsPage` (container) ↔ `HotelCard`/`Composer`/`ErrorState` (saf) |
| **Interceptor / Chain** | İstek/yanıtı ortak bir zincirden geçirir | [client.ts](../frontend/src/api/client.ts) axios request+response interceptor'ları |
| **Single-flight / Mutex** | Eşzamanlı işleri tek bir uçuşta paylaştırır | `client.ts` `performTokenRefresh()` — eşzamanlı 401'ler tek refresh promise'i paylaşır |
| **Epoch / Generation Guard** | Bayat asenkron sonuçları eler | [useSendMessage.ts](../frontend/src/features/chat/useSendMessage.ts) + `chatSlice` `epoch` |
| **Route-guard wrapper** | Erişimi sarmalayıcı bileşenle kısıtlar | [router.tsx](../frontend/src/app/router.tsx) `ProtectedRoute` / `RequireAccount` |

**Adapter deseninin öne çıkan örneği** — çok-durumlu HTTP sonucunu tipe taşımak
([reservationApi.ts](../frontend/src/api/reservationApi.ts)):

```ts
export type ConfirmResult =
  | { kind: 'created'; reservation: ReservationSummary }        // 201 + özet
  | { kind: 'createdFallback'; outcome: OutcomeResponse }       // 201 ama özet okunamadı (nadir)
  | { kind: 'needsConfirmation'; confirmationToken: string; warnings: string[] }  // 200
  | { kind: 'pending'; outcome: OutcomeResponse }               // 202 COMMIT_OUTCOME_UNKNOWN
```

Bu, TourVisio yanıtsız kaldığında dönen **HTTP 202 "sonuç belirsiz"** durumunun yanlışlıkla
"başarı" sanılmasını *derleme zamanında* imkânsız kılar — çağıran her `kind`'ı ele almak
zorundadır. Kod tabanı HOC (higher-order component) veya render-props kullanmaz; bilinçli
olarak **hook + kompozisyon** (modern React deyimi) yeğlenir.

---

## 8. TypeScript Disiplini

- **Sertlik** ([tsconfig.app.json](../frontend/tsconfig.app.json)): `strict`, `noUnusedLocals`,
  `noUnusedParameters`, `noFallthroughCasesInSwitch`, `isolatedModules`.
- **Merkezî alan tipi katmanı** [src/types/](../frontend/src/types/) ve bir `index.ts` barrel'i;
  tüketiciler tek yerden import eder (`import type { HotelProduct } from '@/types'`).
- **DTO'lar backend sözleşmesini birebir aynalar** — hangi DB kolonuna/Java kaydına karşılık
  geldiği yorumlarla belgelenir (`IsoDate`, `CurrencyCode`, `CountryCode` gibi anlamsal
  primitifler; `ReservationDetail extends ReservationSummary` ile tip yeniden kullanımı).
- **Ayırt edici birlikler (discriminated unions)** — dilin öne çıkan kullanımı: `ResultCard`
  (`productType`'a göre), `PartialCriteria` (`intent`'e göre), ve §7'deki `ConfirmResult` /
  `CancelResult`. Bu birlikler, çok-durumlu backend yanıtlarını güvenle modelleyip yanlış
  yorumlamayı engeller.
- **Tipli Redux** — `RootState`/`AppDispatch`, `ReturnType` ile türetilir
  ([store.ts](../frontend/src/app/store.ts)); tipli hook'lar [hooks.ts](../frontend/src/app/hooks.ts)'te.

---

## 9. Tasarım Sistemi ve Tema

### 9.1 Üç katmanlı token mimarisi

Renkler asla ham hex olarak yazılmaz (`frontend/CLAUDE.md` kuralı: *"anlamsal Tailwind
sınıfları, asla ham hex"*). Üç katman vardır:

1. **Primitive marka paleti** (sabit, tema değiştirmez) — [tailwind.config.js](../frontend/tailwind.config.js)
   `colors.brand.*` ve [lib/brand.ts](../frontend/src/lib/brand.ts) `BRAND`, senkron tutulur.
   Altı renk, 206° mavi ailesi + turuncu (`navy #00243F`, `blue #004E89`, `steel #1A659E`,
   `orange #FF6B35`, `peach`, `cream`). `brand.ts` ayrıca `shade`/`tint`/`rgbTriplet` yardımcıları
   sunar ki tonlar yeni ham hex olmadan türetilsin.

2. **Anlamsal HSL token'ları** ([index.css](../frontend/src/index.css), `@layer base`) —
   `:root` (açık), `.dark` ve `.theme-light` (kaçış kapağı). Standart shadcn kümesi HSL üçlüsü
   olarak: `--background --foreground --card --primary --secondary --muted --accent --border
   --ring --radius`.

3. **Reçete token'ları** (düz CSS değişkenleri) — cam/alan yüzeyleri; alfaları temaya göre
   değişir ve reçetenin *içinde* sabittir (`--glass-bg` açıkta 0.65 / koyuda 0.10 gibi).

### 9.2 `destructive-emphasis` — ölçülü kontrast hikâyesi

Öne çıkan erişilebilirlik kararı: `--destructive` bir **zemin** rengidir (`bg-destructive` +
beyaz yazı için). Yazı olarak okunmaz — koyu temada lacivert üstünde **1.55:1**'e düşüyordu ve
uygulamadaki *her* hata mesajı boş bir kutu gibi görünüyordu. Çözüm, okunur hata *yazısı* için
ayrı bir token: **`--destructive-emphasis`** (açıkta koyu kırmızı `0 85% 40%`, koyuda açık
`0 85% 75%`). Değerler hem düz zeminde hem tint'lerde AA geçecek şekilde ölçüldü.
[ErrorState.tsx](../frontend/src/components/ErrorState.tsx) bunu birebir belgeler ve kullanır:

```tsx
<AlertTriangle className="… text-destructive-emphasis" aria-hidden />
<p role="alert" className="text-destructive-emphasis">{message}</p>
```

### 9.3 Tema mekaniği

- [theme.tsx](../frontend/src/app/theme.tsx) `ThemeProvider` + `useTheme()`; tema
  `'light' | 'dark' | 'system'`, `localStorage['pax-theme']`'e yazılır.
- Tek yazma noktası: bir effect `<html>` (belge kökü) üzerinde `.dark` sınıfını açar/kapar —
  Layout div'i değil, çünkü body'ye portal olan katmanlar (modal, ResultsPanel, sonner) var.
- **FOUC (renk sıçraması) koruması:** `index.html`'de senkron bir head betiği ilk boyamadan
  önce aynı sözleşmeyle `.dark`'ı ekler.
- **`ThemeToggle`** üç durumlu (Açık/Sistem/Koyu) bir `role="radiogroup"` segmentidir —
  döngüsel bir buton değil; bu, ekran okuyucu için bilinçli bir erişilebilirlik seçimidir.

### 9.4 Performans — bilinçli sadeleştirme

Donma/kasma nedeniyle **WebGL/GSAP tabanlı efektler kaldırıldı**: animasyonlu gradyan arka plan
([NightSkyBackground.tsx](../frontend/src/components/NightSkyBackground.tsx)) **statik çok
katmanlı radyal gradyanla** değiştirildi (kaydırmada her kareyi yeniden rasterize etmiyor).
Butonun SVG kırılım katmanı da Chromium'da bozuk rasterizasyon ürettiği için zarif biçimde
devre dışı bırakıldı; kenar-ışığı gölgesi korundu. Kaydırılan sonuç panelinde iç içe
`backdrop-filter` katmanları bastırıldı. Tüm bu kararlar kodda yorumlarla gerekçelendirilmiştir.

---

## 10. Test Stratejisi

**39 test dosyası** (Vitest + RTL + user-event + MSW), kaynağın yanına yerleştirilmiş.
Yapılandırma [vite.config.ts](../frontend/vite.config.ts)'te (jsdom, 10s timeout — animasyon
ağırlıklı render'lar için). Paylaşılan kurulum [src/test/setup.ts](../frontend/src/test/setup.ts)
`apiClient.defaults.baseURL`'i boşa zorlar (testler dev'in `.env`'inden bağımsız MSW'ye
düşsün) ve jsdom'un eksik `window.matchMedia`'sını taklit eder (framer-motion için).

**Dört katmanlı piramit:**

1. **Saf birim / mantık** — `isSearchTurn`, `buildDraft`, `apiErrorMessage`, `filterChips`,
   Zod şema testi, slice testleri, hook testleri (`useDelayedFlag`, `useProgressiveReveal`).
2. **Bileşen / davranış (RTL)** — temsilci örnek `HotelCard.test.tsx`: gerçek bir Redux store
   + `MemoryRouter` üzerinden render eder, `userEvent` ile etkileşir ve **rol-tabanlı
   sorgularla** (`getByRole('button', {name:/seç/i})`) davranışı doğrular.
3. **Entegrasyon (interceptor sözleşmesi)** — [client.test.ts](../frontend/src/api/client.test.ts)
   gerçek axios istemcisini MSW node sunucusuna karşı koşturur: SPA-fallback HTML→`ApiError`
   koruması, 204 geçişi, hata normalizasyonu, sessiz-refresh + `UNAUTHORIZED_EVENT` akışı.
4. **Gerçek E2E (canlı backend)** — [realAuth.e2e.test.ts](../frontend/src/test/realAuth.e2e.test.ts):
   **MSW yok**, gerçek `authApi → apiClient → canlı backend` zincirini koşar. `BACKEND_E2E_URL`
   ortamı veya (git-yoksayılan) `e2e.local.json` ile açılır; `describe.skipIf` sayesinde normal
   CI etkilenmez. Kayıt/çift-409/giriş/refresh-rotation/me/logout senaryolarını gerçek token
   rotation doğrulamalarıyla kapsar.

**Felsefe:** iç modüller değil, **ağ sınırı** taklit edilir (MSW). Böylece aynı bileşen/hook
kodu testte, dev'de ve üretimde koşar; gözlemlenebilir davranış erişilebilir roller üzerinden
test edilir; birim testler hermetiktir; canlı-backend sözleşmesi ayrı, opt-in bir katman olarak
tutulur.

---

## 11. Erişilebilirlik ve UX Kalitesi

53 dosyada 364 erişilebilirlik izi. Somut kanıtlar:

- **Azaltılmış hareket:** `<MotionConfig reducedMotion="user">` — tüm framer-motion
  animasyonları `prefers-reduced-motion`'a uyar; CSS animasyonları ayrıca `@media
  (prefers-reduced-motion: reduce)` ile korunur.
- **Canlı bölgeler / uyarılar:** `ErrorState` `role="alert"`; `Composer` karakter sayacı
  `aria-live="polite"` (yalnız limite yakın duyurulur); `LoadingState` `role="status"`.
- **Odak yönetimi:** [modal.tsx](../frontend/src/components/ui/modal.tsx) açılışta
  `document.activeElement`'i saklar, dialog'a odaklanır ve **kapanışta odağı açan öğeye geri
  verir** — bir klavye-kullanıcı regresyonunu düzelttiği belgelenmiştir; `role="dialog"`,
  `aria-modal`, Escape-ile-kapat.
- **Semantik HTML + etiket bağlama:** `htmlFor`/`useId` ile label bağlama, `<h1>/<h2>`
  başlıklar, dekoratif ikonlar `aria-hidden`.
- **Ölçülü kontrast token'ları:** §9.2'deki `destructive-emphasis` ve butonun ölçülü CTA
  oranları (turuncu/lacivert 5.5:1) — niceliksel, WCAG-farkında tasarım.

---

## 12. Müşteriye Sunum: Pazarlanacak Noktalar

Bu bölüm teknik gerçekleri **müşteri değerine** çevirir. Sunumda "özellik → müşteri faydası"
biçiminde kullanılabilir.

### 🔒 Güvenlik ve Uyum

- **Hiçbir API anahtarı tarayıcıda yok.** AI ve TourVisio kimlik bilgileri yalnız backend'de;
  tarayıcı yalnız kendi ekibimizin backend'iyle konuşur. → *Müşteri faydası: sızıntı riski
  mimari olarak ortadan kaldırılmış; sırlar git'e veya istemciye asla düşmez.*
- **Sohbet botu asla rezervasyon yapmaz.** AI yalnız arar, listeler ve kullanıcıyı AI'sız,
  onaylı bir forma yönlendirir ("0 token kontrollü yol"). → *Müşteri faydası: yanlış/istem-dışı
  rezervasyon riski yok; her booking'i kullanıcı açıkça onaylar.*
- **Kiracı (kullanıcı) izolasyonu iki katmanlı savunmayla korunur.** Aynı tarayıcıda ikinci
  kullanıcı, birincinin rezervasyon/geçmiş verisini göremez. → *Müşteri faydası: paylaşımlı
  cihazlarda bile veri gizliliği.*

### ✅ Güvenilirlik ve Doğruluk

- **Asla uydurma fiyat/uygunluk yok.** Fiyat ve müsaitlik yalnız TourVisio'dan gelir; taklit
  veriler dahi "MOCK" olarak işaretlenir. → *Müşteri faydası: gösterilen her fiyat gerçek
  kaynaklıdır.*
- **Belirsiz sonuçlar başarı sanılmaz.** Sağlayıcı yanıtsız kalırsa (HTTP 202) sistem bunu
  "belirsiz" olarak modeller ve kullanıcıya asla yanlış "başarılı/başarısız" demez. →
  *Müşteri faydası: rezervasyon durumu konusunda dürüst, güvenilir geri bildirim.*
- **Dayanıklı hata ve zaman aşımı yönetimi.** 60s timeout sonsuz spinner'ı engeller; sessiz
  token yenileme oturumu şeffafça korur; her hata tutarlı, okunur bir mesaja dönüşür. →
  *Müşteri faydası: takılmayan, kendini toparlayan bir arayüz.*

### ♿ Erişilebilirlik (WCAG)

- **Ölçülmüş kontrast, azaltılmış hareket desteği, tam klavye/ekran-okuyucu uyumu.** Renkler
  WCAG AA oranları hesaplanarak seçildi; animasyonlar kullanıcı tercihine uyar. → *Müşteri
  faydası: daha geniş kitle, kamu/kurumsal erişilebilirlik gerekliliklerine uyum.*

### ⚡ Geliştirme Hızı ve Bakım Kolaylığı

- **Feature-first mimari + üç katmanlı token sistemi.** Yeni bir özellik izole bir dilime
  eklenir; marka/tema tek yerden değişir. → *Müşteri faydası: yeni özellikler hızlı ve düşük
  riskle eklenir; marka güncellemesi dakikalar sürer.*
- **MSW ile backend'siz geliştirme.** Frontend, gerçek backend hazır olmadan tam taklit bir
  sözleşmeye karşı geliştirilir; geçiş tek ayarla, kod değişmeden. → *Müşteri faydası: paralel
  ekip çalışması, daha kısa teslim süresi.*
- **Dört katmanlı test piramidi + strict TypeScript.** Regresyonlar CI'da yakalanır; tipler
  backend sözleşmesini derleme zamanında doğrular. → *Müşteri faydası: daha az canlı hata,
  daha güvenli sürümler.*

### 🎨 Kullanıcı Deneyimi

- **Açık/koyu tema (sistem + manuel), akıcı animasyonlar, misafir akışı.** Kullanıcı hesap
  açmadan arama yapabilir; giriş sonrası kaldığı yerden devam eder. → *Müşteri faydası: düşük
  sürtünmeli ilk deneyim, modern ve kişiselleştirilebilir arayüz.*
- **Performans için bilinçli sadeleştirme.** Kasmaya yol açan ağır efektler ölçülüp hafif
  CSS alternatifleriyle değiştirildi. → *Müşteri faydası: düşük donanımda bile akıcı deneyim.*

---

*Bu rapor, kod tabanının salt-okunur incelemesinden üretilmiştir. Atıfların tümü yazım anındaki
kaynağa göre doğrulanmıştır; kod evrildikçe dosya/satır referansları güncellenmelidir.*
