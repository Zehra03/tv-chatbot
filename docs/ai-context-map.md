# AI Context Map

## Moduller ve Sorumluluklar

- chat: mesaj alma, session bazli kayit
- orchestrator: chat akis yonetimi ve modul routing
- ai: niyet cikarimi ve follow-up soru mantigi
- guard: prompt injection ve kotu niyetli giris filtresi
- ratelimiter: API istek limitleme
- hotel: otel arama use-case
- flight: ucus arama use-case
- reservation: rezervasyon olusturma/listeleme

## Branch Eslestirme

Tum branch'ler `develop`'tan acilir:

- Backend module degisiklikleri: feature/backend/*
- Frontend UI degisiklikleri: feature/frontend/*
- Mimari/dokuman: feature/docs/* veya chore/*
