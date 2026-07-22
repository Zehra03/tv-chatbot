# 🏨 PaxAssist API Documentation

> AI-assisted hotel & flight search backend service.

**Version:** `v1`  
**Base URL:** `/api/v1`

---

## 📌 İçindekiler
- [Authentication (Kimlik Doğrulama)](#1-authentication-kimlik-doğrulama)
- [Hotels (Otel İşlemleri)](#2-hotels-otel-i̇şlemleri)
- [Flights (Uçuş İşlemleri)](#3-flights-uçuş-i̇şlemleri)
- [Reservations (Rezervasyon İşlemleri)](#4-reservations-rezervasyon-i̇şlemleri)
- [Chat (AI Sohbet)](#5-chat-ai-sohbet)

---

## 1. Authentication (Kimlik Doğrulama)

### `POST` /auth/register
* **Açıklama:** Yeni kullanıcı kaydı oluşturur.
* **Yanıt:** `200 OK`

### `POST` /auth/login
* **Açıklama:** Kullanıcı girişi yapar.
* **Yanıt:** `200 OK`

### `POST` /auth/logout
* **Açıklama:** Oturumu kapatır.
* **Yanıt:** `200 OK`

### `POST` /auth/refresh
* **Açıklama:** Access token yeniler.
* **Yanıt:** `200 OK`

### `POST` /auth/reset-password
* **Açıklama:** Şifre sıfırlama talebi gönderir.
* **Yanıt:** `200 OK`

### `GET` | `PATCH` /auth/me
* **Açıklama:** Mevcut oturum açmış kullanıcının profil bilgilerini getirir (`GET`) veya günceller (`PATCH`).
* **Yanıt:** `200 OK`

---

## 2. Hotels (Otel İşlemleri)

### `POST` /hotels/search
* **Açıklama:** Otel arama ve filtreleme işlemleri.
* **Yanıt:** `200 OK`

### `GET` /hotels/locations
* **Açıklama:** Otel lokasyon önerilerini getirir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `q` | `string` | Query | Yes |
* **Yanıt:** `200 OK`

### `GET` /hotels/{id}/details
* **Açıklama:** Otel detay bilgilerini getirir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `id` | `string` | Path | Yes |
  | `ownerProvider` | `integer` | Query | Yes |
  | `boardType` | `string` | Query | No |
* **Yanıt:** `200 OK`

---

## 3. Flights (Uçuş İşlemleri)

### `POST` /flights/search
* **Açıklama:** Uçuş arama ve filtreleme işlemleri.
* **Yanıt:** `200 OK`

### `GET` /flights/locations
* **Açıklama:** Havalimanı / lokasyon önerilerini getirir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `q` | `string` | Query | Yes |
  | `direction` | `string` | Query | No |
* **Yanıt:** `200 OK`

---

## 4. Reservations (Rezervasyon İşlemleri)

### `GET` /reservations
* **Açıklama:** Kullanıcının rezervasyonlarını listeler.
* **Yanıt:** `200 OK`

### `POST` /reservations
* **Açıklama:** Yeni bir rezervasyon oluşturur.
* **Yanıt:** `200 OK`

### `POST` /reservations/preview
* **Açıklama:** Rezervasyon önizlemesi sunar *(TourVisio'ya iletilmez, salt okunur)*.
* **Yanıt:** `200 OK`

### `GET` /reservations/{id}
* **Açıklama:** Belirli bir rezervasyonun detaylarını getirir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `id` | `long` | Path | Yes |
* **Yanıt:** `200 OK`

### `PATCH` /reservations/{id}/cancel
* **Açıklama:** Rezervasyonu iptal eder.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `id` | `long` | Path | Yes |
* **Yanıt:** `200 OK`

---

## 5. Chat (AI Sohbet)

### `POST` /chat
* **Açıklama:** AI asistan ile yeni bir sohbet başlatır veya mesaj gönderir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `X-Guest-Id` | `string` | Header | No |
* **Yanıt:** `200 OK`

### `GET` /chat/sessions
* **Açıklama:** Geçmiş sohbet oturumlarını listeler.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `X-Guest-Id` | `string` | Header | No |
* **Yanıt:** `200 OK`

### `GET` /chat/{sessionId}
* **Açıklama:** Belirli bir sohbet oturumunun mesaj geçmişini getirir.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `sessionId` | `string` | Path | Yes |
  | `X-Guest-Id` | `string` | Header | No |
* **Yanıt:** `200 OK`

### `DELETE` /chat/{sessionId}
* **Açıklama:** Sohbet oturumunu siler.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `sessionId` | `string` | Path | Yes |
  | `X-Guest-Id` | `string` | Header | No |
* **Yanıt:** `200 OK`

---

## 6. Admin Paneli

### `GET` /admin/dashboard/stats
* **Açıklama:** Sistemin toplam kullanıcı, rezervasyon ve gelir istatistiklerini getirir. (Sadece `ROLE_ADMIN` yetkisine sahip kullanıcılar erişebilir).
* **Yanıt:** `200 OK`

### `GET` /admin/users
* **Açıklama:** Sistemdeki tüm kullanıcıları paginated (sayfalamalı) olarak getirir.
* **Yanıt:** `200 OK`

### `GET` /admin/reservations
* **Açıklama:** Sistemdeki tüm rezervasyonları paginated olarak listeler.
* **Yanıt:** `200 OK`

### `PUT` /admin/reservations/{id}/status
* **Açıklama:** Admin yetkisiyle belirtilen rezervasyonu iptal eder.
* **Parametreler:**
  | Parametre | Tipi | Konum | Zorunlu mu? |
  | :--- | :--- | :--- | :---: |
  | `id` | `long` | Path | Yes |
* **Yanıt:** `200 OK` / `409 CONFLICT`