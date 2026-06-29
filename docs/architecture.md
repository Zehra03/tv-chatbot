graph TD
    %% --- STİL VE RENK TANIMLAMALARI ---
    classDef client fill:#eceff1,stroke:#37474f,stroke-width:2px;
    classDef gateway fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef orchestrator fill:#bbdefb,stroke:#0d47a1,stroke-width:2px;
    classDef business fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
    classDef cache fill:#ffe0b2,stroke:#f57c00,stroke-width:2px;
    classDef db fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef ext fill:#ffebee,stroke:#c62828,stroke-width:2px;
    classDef log fill:#f1f8e9,stroke:#558b2f,stroke-width:2px;

    %% --- AKTÖRLER VE İSTEMCİ ---
    User((Kullanıcı / Browser)):::client

    %% --- MODULAR MONOLITH SINIRI ---
    subgraph Modular_Monolith_Boundary [Modular Monolith Uygulama Sınırı]
        
        %% Giriş ve Güvenlik Katmanı
        RateLimiter[1. Rate Limiter <br> İstek Sınırlandırma]:::gateway
        GuardMod[2. Guard Modülü <br> Statik Regex, Prompt Injection & Küfür Filtresi]:::gateway
        
        %% Ana Yönetici
        Orchestrator[3. Chat Orchestrator <br> Akış ve İş Mantığı Yönetimi]:::orchestrator
        
        %% Yapay Zeka Katmanı
        AI_Engine[4. AI / Intention Modülü <br> Niyet Analizi & Parametre Çıkarımı]:::orchestrator

        %% İş Mantığı Modülleri (Business Modules)
        subgraph Business_Modules [İş Mantığı Modülleri]
            FlightMod[Flight Modülü <br> Uçuş İşlemleri]:::business
            HotelMod[Hotel Modülü <br> Otel İşlemleri]:::business
            ReservationMod[Reservation Modülü <br> Rezervasyon Akışı]:::business
        end

        %% Önbellek Katmanı (Redis)
        RedisCache[Redis / In-Memory Cache <br> TourVisio Canlı Sonuçlar & Geçici Filtreleme]:::cache

        %% Loglama Modülü
        LogMod[Log Modülü <br> Asenkron Kuyruk Yapısı]:::log
    end

    %% --- VERİTABANI KATMANI ---
    subgraph Storage_Layer [Kalıcı Veri Katmanı]
        ChatDB[(Chat & Session DB <br> Konuşma Geçmişi ve Oturum Durumu)]:::db
        ResDB[(Reservation DB <br> Nihai Rezervasyon Kayıtları)]:::db
        LogDB[(Log DB <br> Sistem ve Hata Logları)]:::db
    end

    %% --- DIŞ DÜNYA / API KATMANI ---
    TourVisio((TourVisio API <br> Canlı Entegrasyon)):::ext

    %% =======================================================
    %% --- UÇTAN UCA İŞ AKIŞI OKLARI (END-TO-END FLOW) ---
    %% =======================================================

    %% 1. Güvenlik ve Giriş Akışı
    User -->|1. Doğal Dilde Mesaj Gönderir| RateLimiter
    RateLimiter -->|2. İstek Limiti Aşılmadıysa Geçir| GuardMod
    GuardMod -->|3. Güvenli / Temiz İçerik| Orchestrator
    GuardMod -.->|Zararlı İçerik / İstek Engellendi| User

    %% 2. Niyet ve Durum Yönetimi Akışı
    Orchestrator -->|4. Mesajı İletir| AI_Engine
    AI_Engine -.->|5. Niyet: Otel/Uçak + Çıkarılan Parametreler| Orchestrator
    Orchestrator -->|6. Mesajı ve Oturum Durumunu Kaydet| ChatDB

    %% 3. Ürün Arama ve Önbellek (Cache) Akışı
    Orchestrator -->|7a. Otel Arama Talebi| HotelMod
    Orchestrator -->|7b. Uçuş Arama Talebi| FlightMod

    HotelMod -->|8a. Önbellekte Veri Var mı?| RedisCache
    FlightMod -->|8b. Önbellekte Veri Var mı?| RedisCache

    HotelMod -->|9a. Cache Miss: Canlı Veri Çek| TourVisio
    FlightMod -->|9b. Cache Miss: Canlı Veri Çek| TourVisio

    TourVisio -.->|10a. Canlı Sonuçlar| HotelMod
    TourVisio -.->|11a. Canlı Sonuçlar| FlightMod

    HotelMod -->|12a. Gelen Sonuçları Önbelleğe Yaz| RedisCache
    FlightMod -->|12b. Gelen Sonuçları Önbelleğe Yaz| RedisCache

    %% 4. Filtreleme ve Sonuç Dönüşü
    HotelMod -.->|13a. Filtrelenmiş / Normalize Sonuçlar| Orchestrator
    FlightMod -.->|13b. Filtrelenmiş / Normalize Sonuçlar| Orchestrator
    Orchestrator -.->|14. Cevap ve Kartları Ekranda Göster| User

    %% 5. Rezervasyon Akışı (AI Tamamen Devre Dışı - Geleneksel UI Formu)
    User -->|15. Ürünü Seçer & Rezervasyon Formunu Doldur <br> 0 Token - Güvenli Alan| ReservationMod
    ReservationMod -->|16. Rezervasyonu Tamamla / Koltuk-Oda Bağla| TourVisio
    ReservationMod -->|17. Başarılı Rezervasyon Özetini Kaydet| ResDB

    %% 6. Arka Planda Asenkron (Non-Blocking) Loglama Akışı
    Orchestrator -.->|Log Olayı / Event| LogMod
    FlightMod -.->|Log Olayı / Event| LogMod
    HotelMod -.->|Log Olayı / Event| LogMod
    ReservationMod -.->|Log Olayı / Event| LogMod
    
    LogMod -->|Background Worker ile Toplu Yazma| LogDB