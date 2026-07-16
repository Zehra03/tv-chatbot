# paxassist

AI destekli otel ve ucus arama/rezervasyon uygulamasi.

Bu repo, SAN TSG staj kapsamindaki gereksinimlere gore Spring Boot + PostgreSQL + React kullanilarak, modular monolith yaklasimi ile hazirlanmis proje iskeletidir.

## Teknoloji Yigini

- Backend: Java 21, Spring Boot 3, Spring Data JPA, Flyway, PostgreSQL
- Frontend: React 18, TypeScript, Vite
- Orkestrasyon: Docker Compose

## Klasor Yapisi

- `backend/`: Modular monolith backend
- `frontend/`: React istemci
- `docs/`: Mimari, surec, AI gelistirme metodolojisi dokumanlari

## Hizli Baslangic

1. Ornek ortam dosyasini kullan:
	- `.env.example` icerigini kontrol et
	- gerekiyorsa `.env` icinde degerleri guncelle
2. Konteynerleri calistir:
	- `docker compose up --build`
3. Uygulamalar:
	- Backend: `http://localhost:8080`
	- Swagger: `http://localhost:8080/swagger-ui/index.html`
	- Frontend compose'da su an devre disi — lokal calistir (asagida):
	  `http://localhost:5173`

## Testler

> Not: `docker-compose.test.yml` henuz repoda yok; Makefile'daki
> `test-docker` hedefleri o dosya eklenene kadar calismaz. Testleri
> asagidaki komutlarla calistirin (CI de ayni komutlari kullanir).

- Backend (calisan Postgres + Redis gerekir):
	- `docker compose up -d postgres redis`
	- `cd backend && mvn -B test`
- Frontend (Vitest + React Testing Library + MSW; servis gerekmez):
	- `cd frontend && npm run test`

## Lokal Gelistirme (Docker'siz)

### Backend

1. Bagimliliklar: `docker compose up -d postgres redis`
	- Ollama calismiyorsa `.env` icinde `SPRING_PROFILES_ACTIVE=dev,mock-ai` kullan.
2. `backend/` altinda: `mvn spring-boot:run` (Maven wrapper henuz yok)

### Frontend

1. `frontend/` altinda ornek env'i kopyala: `cp .env.example .env`
	- Varsayilan: `VITE_ENABLE_MSW=true` ve `VITE_API_BASE_URL` bos —
	  tum istekler MSW (Mock Service Worker) sahte backend'i tarafindan
	  karsilanir; backend calistirmadan gelistirebilirsin.
2. `npm install` ve `npm run dev` → `http://localhost:5173`
3. Gercek backend'e gecis (kod degisikligi gerekmez, kontrat ayni):
	- `.env` icinde `VITE_ENABLE_MSW=false`
	- `VITE_API_BASE_URL=http://localhost:8080` (backend calisiyor olmali)
4. Diger komutlar: `npm run test` (Vitest) · `npm run lint` · `npm run build`

## Branch Stratejisi

- Uretim: `main`
- Entegrasyon / gelistirme: `develop`

Feature branch'ler `develop`'tan acilir, `develop`'a PR edilir. Adlandirma ornekleri:

- `feature/frontend/chat-ui`
- `feature/backend/hotel-search`
- `feature/backend/ai-intention`

Detayli surec: `docs/development-workflow.md` ve `docs/branching-strategy.md`.

## Guvenlik Notlari

- Secret ve API key degerlerini commit etme.
- Browser dogrudan TourVisio veya AI provider'a gitmemeli.
- Tum dis servis cagri mantigi backend'de kalmali.

## Kabul Kriterleri Odagi

Bu iskelet, staj dokumanindaki su basliklari hizli ilerletmek icin hazirlandi:

- Chat niyet analizi + eksik bilgi tamamlama
- Otel/ucus arama ve filtreleme
- Rezervasyon akisi (chat disi kontrollu form)
- Rezervasyon listeleme ve detay
- Dokumantasyon, test ve demo disiplini

