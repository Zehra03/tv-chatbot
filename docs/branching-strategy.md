# Branching Strategy

## Sabit Branchler

- main: Production-ready (uretim) branch — yalnizca yayina hazir kod.
- develop: Entegrasyon / gelistirme branch'i — gunluk gelistirme burada toplanir.

Tum feature ve fix calismalari `develop` uzerinde birlestirilir; `main` yalnizca release asamasinda guncellenir.

## Feature Branch Kurali

Feature branch'ler her zaman `develop`'tan acilir ve tamamlaninca `develop`'a geri merge edilir.

Adlandirma: `feature/<kapsam>/<kisa-aciklama>` (kapsam: backend | frontend | docs)

Ornek:

- feature/backend/chat-orchestrator
- feature/backend/flight-search
- feature/frontend/chat-ui
- feature/frontend/reservation-form

Hata duzeltmeleri icin: `fix/<kapsam>/<kisa-aciklama>`

## Merge Kurallari

1. Feature branch uzerinde kucuk ve anlamsal commitler at.
2. Unit/integration testleri localde gecir.
3. `develop` hedefli PR ac.
4. Reviewer olarak ekip arkadaslarini ve Copilot'u ekle.
5. En az bir onay alinmadan merge etme.
6. Release asamasinda `develop` -> `main` planli sekilde (tercihen release PR ile) alinir.
