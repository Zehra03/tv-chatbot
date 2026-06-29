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
	- Frontend: `http://localhost:5173`
	- Backend: `http://localhost:8080`
	- Swagger: `http://localhost:8080/swagger-ui/index.html`

## Docker Uzerinden Test

Tum ekip uyeleri testleri Docker ile calistirmalidir.

- Tum testler: `make test-docker`
- Sadece backend: `make test-backend-docker`
- Sadece frontend: `make test-frontend-docker`

Makefile kullanmadan:

- `docker compose -f docker-compose.test.yml run --rm backend-test`
- `docker compose -f docker-compose.test.yml run --rm frontend-test`

## Lokal Gelistirme (Docker'siz)

### Backend

1. PostgreSQL calistir.
2. `backend/` altinda:
	- `./mvnw spring-boot:run` (wrapper eklenirse)
	- veya `mvn spring-boot:run`

### Frontend

1. `frontend/` altinda:
	- `npm install`
	- `npm run dev`

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

