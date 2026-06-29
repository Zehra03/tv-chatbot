# Development Workflow

Bu dokuman, PaxAssist gelistirme ekibinin ortak calisma metodolojisini tanimlar.

## Calisma Modeli

- Her gelistirici tek bir feature'a odaklanir.
- Her feature branch'i kisa omurlu olur.
- Kucuk adimlarla gelistirme yapilir.
- Her adim test edilip commitlenir.

## Standart Akis

1. Gorev sec ve acceptance criteria netlestir.
2. Ilgili base branch'ten feature branch ac.
3. Kucuk bir degisiklik yap.
4. Testleri calistir.
5. Anlamli commit at.
6. Sonraki kucuk degisiklige gec.
7. Feature tamamlaninca PR ac ve review al.

## Commit Mesaji Formati

Onerilen format:

- feat(module): kisa aciklama
- fix(module): kisa aciklama
- test(module): kisa aciklama
- docs(scope): kisa aciklama
- refactor(module): kisa aciklama

Ornek:

- feat(chat): add conversation state persistence
- fix(guard): block prompt-injection phrase variants

## Definition of Done

Bir feature tamamlandi sayilmasi icin:

- Acceptance criteria saglanmis olmali
- Ilgili testler yazilmis/guncellenmis olmali
- Lint/build/test adimlari basarili olmali
- PR aciklamasi net olmali
- En az bir reviewer onayi alinmali
