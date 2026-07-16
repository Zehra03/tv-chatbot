# PaxAssist – Postman

Backend endpoint'lerini elle test etmek için hazır Postman koleksiyonu.

## İçe aktarma (import)

1. Postman'i aç → sol üstten **Import**.
2. Bu iki dosyayı sürükle-bırak:
   - `PaxAssist.postman_collection.json` (koleksiyon)
   - `PaxAssist.local.postman_environment.json` (environment)
3. Sağ üstteki environment seçicisinden **PaxAssist - Local**'i seç.
   - `baseUrl` = `http://localhost:8081` (8080'i başka bir Apache tutuyor).

## Kullanım

1. **Auth → Register** çalıştır. Benzersiz bir e-posta üretir, `201` döner ve JWT'yi
   otomatik olarak `{{token}}` değişkenine yazar. Koleksiyon seviyesinde Bearer auth
   tanımlı olduğu için diğer tüm istekler bu token'ı kullanır.
   - Var olan bir hesapla girmek istersen `email`/`password` değişkenlerini düzenleyip
     **Auth → Login** çalıştır.
2. **Auth → Me** ile token'ı doğrula.
3. **Chat / Hotels / Flights** klasörlerindeki istekleri çalıştır.
   - Chat "yeni oturum" isteği dönen `sessionId`'yi yakalar; "devam", "get", "delete"
     istekleri onu kullanır.

Tüm istekler basit test scriptleri (durum kodu + token/sessionId yakalama) içerir;
**Send** yerine **Runner** ile Auth → Chat → Hotels → Flights sırasıyla da koşabilirsin.

## Bilinen durum (2026-07-07 itibarıyla, çalışan container)

| Endpoint | Sonuç | Not |
|---|---|---|
| `POST /api/v1/auth/register`, `login`, `GET /me`, `POST /logout` | ✅ çalışıyor | |
| `POST /api/v1/hotels/search` | ⚠️ 500 | Backend container Redis'e `localhost`'tan bağlanıyor |
| `POST /api/v1/flights/search` | ⚠️ 500 | Aynı Redis sorunu |
| `POST /api/v1/chat` | ⚠️ 500 | Geçerli AI anahtarı / çalışan Ollama yok (`AI yanıt üretemedi`) |

**Redis düzeltmesi:** `docker-compose.yml`'de `backend` servisinin `environment` bloğuna
`SPRING_DATA_REDIS_HOST: redis` eklenip `docker compose up -d --force-recreate backend`
ile yeniden başlatılınca hotel/flight çalışır. (Postgres zaten `SPRING_DATASOURCE_URL`
üzerinden `postgres` servis adıyla bağlandığı için çalışıyor; Redis için karşılığı eksik.)
