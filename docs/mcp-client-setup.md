# MCP Client Kurulumu (Claude Desktop → PaxAssist arama araçları)

PaxAssist backend'i bir **MCP _server_**'dır: `searchHotels` ve `searchFlights` araçlarını
`spring-ai-starter-mcp-server-webmvc` ile **HTTP + SSE** üzerinden yayınlar
(`backend/.../mcp/HotelFlightTools.java`). Bu dokümandaki `_client_` bize ait değildir —
harici bir MCP istemcisi (ör. Claude Desktop) bu araçları çağırır.

## Neden bir köprü (bridge) gerekiyor?

Claude Desktop yalnızca **stdio** taşımasını (bir komut çalıştırıp stdin/stdout'tan konuşmak)
destekler. Bizim sunucumuz ise **SSE** yayınlıyor. Arasına `mcp-remote` köprüsünü koyuyoruz:
Claude Desktop `mcp-remote`'u stdio ile çalıştırır, `mcp-remote` de bizim SSE endpoint'imize
bağlanır.

```
Claude Desktop ──stdio──▶ npx mcp-remote ──HTTP/SSE──▶ http://localhost:8081/sse ──▶ MCP Server
```

## Endpoint'ler (spring-ai varsayılanları)

Kodda özel bir yol tanımlı değil, o yüzden spring-ai varsayılanları geçerli:

| Amaç | Yol |
|------|-----|
| SSE bağlantısı (client buraya bağlanır) | `GET /sse` |
| Mesaj kanalı | `POST /mcp/message` |

Port: `application.yml` varsayılanı **8080**, ancak bu makinede backend **8081**'de çalışıyor
(host 8080'i başka bir Apache tutuyor). Aşağıdaki örnekler 8081 kullanıyor — kendi portunuza göre
değiştirin.

## Önemli: Endpoint'ler JWT ister

`SecurityConfig` içinde `/sse` ve `/mcp/message` hiçbir `permitAll` listesinde değil; bunlar
`.anyRequest().authenticated()` altına düşer. Yani MCP istemcisi her isteğe geçerli bir
**`Authorization: Bearer <JWT>`** başlığı eklemek zorundadır. (MCP yüzeyi chat guard'ını atlar;
bu yüzden JWT tek erişim kontrolüdür — public açmayın.)

### 1. Adım — Token al

```bash
curl -s -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"YOUR_PASSWORD"}'
# Cevap: {"user":{...},"token":"eyJhbGciOi..."}  ← token alanını kopyalayın
```

Kullanıcınız yoksa önce `/api/v1/auth/register` ile oluşturun.

### 2. Adım — `claude_desktop_config.json`

Windows'ta dosya yolu: `%APPDATA%\Claude\claude_desktop_config.json`
(Claude Desktop → Settings → Developer → Edit Config ile de açılır).

```json
{
  "mcpServers": {
    "paxassist-search": {
      "command": "npx",
      "args": [
        "-y",
        "mcp-remote",
        "http://localhost:8081/sse",
        "--header",
        "Authorization:${AUTH_HEADER}"
      ],
      "env": {
        "AUTH_HEADER": "Bearer eyJhbGciOi...buraya-login-token'ı..."
      }
    }
  }
}
```

Neden `Authorization:${AUTH_HEADER}` ve token'ı `env`'de? `mcp-remote`'un bilinen bir sorunu var:
`--header` değerindeki **boşluk** argümanı ikiye böler. Değeri env değişkeninden enjekte edip
literal argümanda boşluk bırakmayınca (`Authorization:${AUTH_HEADER}`) bu sorun aşılır — çalışma
anında `Authorization:Bearer eyJ...` olur.

Gereksinim: makinede **Node.js** kurulu olmalı (`npx` için).

### 3. Adım — Claude Desktop'ı yeniden başlat

Tamamen kapatıp açın. Araç (🔨) menüsünde `paxassist-search` altında `searchHotels` ve
`searchFlights` görünmeli. "Antalya'da 5 gece 2 yetişkin otel ara" gibi bir istekte Claude bu
aracı çağırır; sonuçlar **yalnızca TourVisio'dan** gelir (uydurma yok, eksik alan varsa
`INCOMPLETE`).

## Bağlantıyı çıplak test etme (Claude Desktop'sız)

Sunucunun ayakta ve korumalı olduğunu doğrulamak için:

```bash
# Token'sız → 401 (JWT gerçekten gerekiyor)
curl -i http://localhost:8081/sse

# Token'lı → SSE akışı açılır (text/event-stream; "endpoint" event'i döner)
curl -i -H "Authorization: Bearer eyJ..." http://localhost:8081/sse
```

## Sadece yerel demo için alternatif (güvenliği zayıflatır)

JWT uğraşmadan hızlı bir demo isterseniz, `SecurityConfig`'in `permitAll` listesine geçici olarak
`"/sse"` ve `"/mcp/message"` ekleyip config'den `--header` satırlarını çıkarabilirsiniz.
**Yalnızca yerel makinede yapın**; üretime/paylaşılan ortama asla bu haliyle çıkmayın — MCP yüzeyi
chat guard'ını atladığı için kimliksiz erişim tüm arama use-case'lerini açığa çıkarır.
