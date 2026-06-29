# Contributing Guide

## Branch Kurallari

- prod: main
- entegrasyon / gelistirme: develop

Feature branchler her zaman `develop`'tan acilir.

## Gelistirme Prensipleri

- Kucuk parcalar halinde gelistir
- Her adimdan sonra test et
- Her mantikli adimda commit at
- PR aciklamasini net yaz
- PR oncesi testleri Docker uzerinden gecir

## PR Kurallari

- PR hedefi: feature/fix branch -> develop
- develop -> main yalnizca release asamasinda
- Reviewer olarak ekip arkadaslari ve Copilot eklenmeli
- En az bir onay alinmadan merge edilmemeli
- `make test-docker` sonucu basarili olmali

## Kod Standartlari

- Backend: temiz katman ayrimi, validasyon, hata yonetimi
- Frontend: component bazli yapi, API katmani ayri
- Secret bilgileri asla repoya ekleme
