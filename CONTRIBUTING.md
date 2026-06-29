# Contributing Guide

## Branch Kurallari

- prod: main
- frontend integration: frontend
- backend integration: backend

Feature branchler her zaman ilgili entegrasyon branchinden acilir.

## Gelistirme Prensipleri

- Kucuk parcalar halinde gelistir
- Her adimdan sonra test et
- Her mantikli adimda commit at
- PR aciklamasini net yaz
- PR oncesi testleri Docker uzerinden gecir

## PR Kurallari

- PR hedefi:
  - backend feature -> backend
  - frontend feature -> frontend
- Reviewer olarak ekip arkadaslari ve Copilot eklenmeli
- En az bir onay alinmadan merge edilmemeli
- `make test-docker` sonucu basarili olmali

## Kod Standartlari

- Backend: temiz katman ayrimi, validasyon, hata yonetimi
- Frontend: component bazli yapi, API katmani ayri
- Secret bilgileri asla repoya ekleme
