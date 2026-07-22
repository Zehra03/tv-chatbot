graph TD
    %% =====================================================================
    %% PaxAssist — Kanonik Mimari (Modular Monolith)
    %% Değişiklik günlüğü (docs revizyonu):
    %%  - Guard artık Orchestrator'ın İLK iç adımı (LLM öncesi fail-fast); eski "gateway
    %%    öncesi mi / alt-adım mı" çelişkisi minimal-architecture.md ile hizalandı.
    %%  - JWT Auth katmanı + paylaşılan users tablosu eklendi (kod gerçeğini yansıtır).
    %%  - Rate limiter "kimliği doğrulanmış principal'a göre anahtarlar" notu eklendi.
    %%  - Orchestrator'a Evaluator-Optimizer (AI serbest-metin çıktısında guardrail döngüsü) eklendi.
    %%  - Runtime MCP Server (arama araçlarını yayınlar) eklendi — dev-time MCP'lerden farklıdır.
    %%  - LogDB kaldırıldı (V8): loglar veritabanında tutulmuyor. Log hedefi "yapılandırılmış
    %%    stdout": prod'da JSON (ECS) satırlar, platform toplar. Her istek requestId +
    %%    userId/guestId ile korele ediliyor; güvenlik olayları da aynı alanları taşıyor.
    %%  - Reservation "planlanan" olarak işaretlendi.
    %% =====================================================================
    %% --- STİL VE RENK TANIMLAMALARI ---
    classDef client fill:#eceff1,stroke:#37474f,stroke-width:2px;
    classDef gateway fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef orchestrator fill:#bbdefb,stroke:#0d47a1,stroke-width:2px;
    classDef business fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
    classDef cache fill:#ffe0b2,stroke:#f57c00,stroke-width:2px;
    classDef db fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef ext fill:#ffebee,stroke:#c62828,stroke-width:2px;
    classDef log fill:#f1f8e9,stroke:#558b2f,stroke-width:2px;
    classDef mcp fill:#ede7f6,stroke:#4527a0,stroke-width:2px;

    %% --- AKTÖRLER VE İSTEMCİLER ---
    User((Kullanıcı / Browser)):::client
    McpClient((Harici MCP Client <br> ör. Claude Desktop)):::client

    %% --- MODULAR MONOLITH SINIRI ---
    subgraph Modular_Monolith_Boundary [Modular Monolith Uygulama Sınırı]

        %% Giriş ve Güvenlik Katmanı
        RateLimiter[1. Rate Limiter <br> İstek sınırlandırma · kimliği doğrulanmış principal'a göre anahtarlar]:::gateway
        JwtAuth[2. JWT Auth Filtresi <br> Stateless Bearer · Spring Security]:::gateway

        %% Ana Yönetici + İç Adımlar
        Orchestrator[3. Chat Orchestrator <br> İnce koordinatör: guard → intent → route → persist]:::orchestrator
        Guard[3a. Guard <br> Orchestrator'ın İLK adımı · Regex / Prompt Injection / Küfür · LLM öncesi fail-fast]:::gateway
        AI_Engine[4. AI / Intention <br> Niyet analizi + slot çıkarımı]:::orchestrator
        Evaluator[4a. Evaluator-Optimizer <br> AI serbest-metninde üret→değerlendir→iyileştir · guardrail zorlaması]:::orchestrator

        %% İş Mantığı Modülleri
        subgraph Business_Modules [İş Mantığı Modülleri]
            HotelMod[Hotel Modülü]:::business
            FlightMod[Flight Modülü]:::business
            ReservationMod[Reservation Modülü <br> PLANLANAN · 0-token AI-dışı form]:::business
        end

        %% Önbellek + MCP + Log
        RedisCache[Redis Cache <br> TourVisio canlı sonuçları]:::cache
        McpServer[MCP Server -runtime- <br> Arama araçlarını yayınlar · SEARCH-ONLY, booking YOK]:::mcp
        LogMod[Loglama <br> ActivityLog -module/action/status- + korelasyon -requestId/userId/guestId- <br> prod'da JSON -ECS- stdout]:::log
    end

    %% --- VERİTABANI KATMANI ---
    subgraph Storage_Layer [Kalıcı Veri Katmanı]
        UsersDB[(users <br> Paylaşılan kimlik + rol)]:::db
        ChatDB[(Chat & Session DB <br> chat_sessions / chat_messages)]:::db
        ResDB[(Reservation DB <br> Nihai rezervasyon kayıtları)]:::db
    end

    %% --- DIŞ DÜNYA ---
    TourVisio((TourVisio API <br> Canlı entegrasyon)):::ext

    %% =======================================================
    %% 1. Güvenlik ve giriş akışı
    User -->|1. Doğal dilde mesaj| RateLimiter
    RateLimiter -->|2. Limit aşılmadıysa| JwtAuth
    JwtAuth -->|3. Kimlik doğrulandı| Orchestrator
    JwtAuth -.->|Kullanıcı/rol doğrula| UsersDB

    %% 2. Orchestrator iç akışı
    Orchestrator -->|3a. İLK adım: girişi denetle| Guard
    Guard -.->|Zararlı içerik → güvenli reddet yanıtı| User
    Orchestrator -->|4. Niyet + slot çıkar| AI_Engine
    AI_Engine -.->|Intent + çıkarılan kriterler| Orchestrator
    Orchestrator -->|4a. AI serbest-metin çıktısını denetle| Evaluator
    Orchestrator -->|Her mesajda durumu kaydet| ChatDB

    %% 3. Ürün arama ve önbellek
    Orchestrator -->|Otel arama| HotelMod
    Orchestrator -->|Uçuş arama| FlightMod
    HotelMod -->|Önbellekte var mı?| RedisCache
    FlightMod -->|Önbellekte var mı?| RedisCache
    HotelMod -->|Cache miss: canlı veri| TourVisio
    FlightMod -->|Cache miss: canlı veri| TourVisio
    HotelMod -.->|Filtrelenmiş sonuç kartları| Orchestrator
    FlightMod -.->|Filtrelenmiş sonuç kartları| Orchestrator
    Orchestrator -.->|Cevap + kartlar| User

    %% 4. Rezervasyon akışı (AI devre dışı, geleneksel form)
    User -->|Ürünü seç + formu doldur · 0 token güvenli alan| ReservationMod
    ReservationMod -->|Rezervasyonu tamamla / koltuk-oda bağla| TourVisio
    ReservationMod -->|Başarılı rezervasyonu kaydet| ResDB

    %% 5. MCP -runtime- arama araçlarını dışarı açar
    McpClient -->|MCP protokolü · JWT güvenliği arkasında| McpServer
    McpServer -->|Arama use-case'i| HotelMod
    McpServer -->|Arama use-case'i| FlightMod

    %% 6. Arka planda asenkron loglama (planlanan)
    Orchestrator -.->|Log olayı| LogMod
    HotelMod -.->|Log olayı| LogMod
    FlightMod -.->|Log olayı| LogMod
    ReservationMod -.->|Log olayı| LogMod
    %% Loglar bir veritabanına yazılmıyor (V8 ile logging şeması düşürüldü) ve ayrı bir log
    %% servisine gönderilmiyor: olaylar sürecin stdout'una akar, platform oradan toplar.
