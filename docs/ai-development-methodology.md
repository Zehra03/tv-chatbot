# AI Development Methodology

Bu dosya AI destekli kodlama araclarinin proje baglamini hizli anlamasi icindir.

## Proje Kimligi

- Proje: paxassist
- Firma: paximum.com
- Domain: seyahat chatbotu, otel/ucus arama, kontrollu rezervasyon

## Mimari Ilkeler

- Mimari: Modular Monolith
- Frontend dogrudan TourVisio veya AI provider'a gitmez
- Tanimli moduller:
  - RateLimiter
  - Guard
  - Orchestrator
  - AI/Intention
  - Hotel
  - Flight
  - Reservation

## Kritik Guvenlik Kurallari

- API key ve tokenlar sadece backend env'de tutulur
- Prompt/system talimatlari disariya sizdirilmaz
- Chatbot rezervasyonu tek basina finalize etmez
- Fiyat/musaitlik uydurulmaz, dis kaynaktan gelir

## Kod Uretirken Oncelikler

1. Mevcut modul sinirlarini koru
2. Kucuk, test edilebilir degisiklikler yap
3. DTO ve validation kullan
4. Hata yonetimini standartlastir
5. Test veya dogrulama adimi ekle

## Forbidden Patterns

- Frontend icinde secret tutmak
- Controller icinde karmasik is kurali yazmak
- Tek PR'da cok buyuk, bagimsiz degisiklikleri birlestirmek
- Testsiz merge
