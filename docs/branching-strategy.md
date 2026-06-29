# Branching Strategy

## Sabit Branchler

- main: Production-ready branch
- backend: Backend integration branch
- frontend: Frontend integration branch

## Feature Branch Kurali

Feature branchler her zaman ilgili entegrasyon branchinden acilir.

- Backend icin: backend branchinden ac
- Frontend icin: frontend branchinden ac

Ornek adlandirma:

- feature/backend/chat-orchestrator
- feature/backend/flight-search
- feature/frontend/chat-ui
- feature/frontend/reservation-form

## Merge Kurallari

1. Feature branch uzerinde kucuk ve anlamsal commitler at.
2. Unit/integration testleri localde gecir.
3. Hedef branch icin PR ac:
   - backend feature -> backend
   - frontend feature -> frontend
4. Reviewer olarak ekip arkadaslarini ve Copilot'u ekle.
5. Onay sonrasi merge et.
6. Sprint/release asamasinda backend/frontend branchleri main'e planli sekilde alin.
