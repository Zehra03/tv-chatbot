graph TD
    %% Stil Tanımlamaları
    classDef client fill:#eceff1,stroke:#37474f,stroke-width:2px;
    classDef orchestrator fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef coreMod fill:#fff3e0,stroke:#ef6c00,stroke-width:2px;
    classDef cache fill:#ffe0b2,stroke:#f57c00,stroke-width:2px;
    classDef db fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef extService fill:#ffebee,stroke:#c62828,stroke-width:2px;

    %% İstemci
    User((Kullanıcı / Browser)):::client

    subgraph Modular_Monolith [Modular Monolith Sınırı]
        RateLimiter[1. Rate Limiter]:::orchestrator
        Orchestrator[2. Orchestrator Modülü]:::orchestrator
        Guard[3. Guard Modülü]:::orchestrator
        Intention[4. AI / Intention Modülü]:::orchestrator

        %% İş Mantığı Modülleri
        Flight[Flight Modülü]:::coreMod
        Hotel[Hotel Modülü]:::coreMod
        Reservation[Reservation Modülü]:::coreMod

        %% Önbellek Katmanı (Senin Önerin 🚀)
        Cache[Redis / In-Memory Cache Katmanı <br> Canlı API Sonuçları Tutulur]:::cache
    end

    %% Yeni İzole Veritabanları
    subgraph Databases [Kalıcı Veritabanları]
        ChatDB[(Chat & Session DB <br> Geçmiş ve Oturum Durumu)]:::db
        ResDB[(Reservation DB <br> Kalıcı Rezervasyonlar)]:::db
    end

    TourVisio((TourVisio API)):::extService

    %% Akış Okları
    User -->|Mesaj| RateLimiter --> Orchestrator
    Orchestrator --> Guard
    Orchestrator --> Intention
    
    %% Oturum Kaydı
    Orchestrator -->|Her Mesajda Durumu Güncelle| ChatDB

    %% İş Modülleri ve Önbellek İlişkisi
    Orchestrator --> Flight
    Orchestrator --> Hotel
    
    Flight -->|Veri Yoksa İstek At| TourVisio
    Hotel -->|Veri Yoksa İstek At| TourVisio
    
    Flight -->|Sonuçları Yaz/Oku| Cache
    Hotel -->|Sonuçları Yaz/Oku| Cache

    %% Rezervasyon Akışı (AI Dışı Geleneksel)
    User -->|Rezervasyon Formu| Reservation
    Reservation --> ResDB
    Reservation --> TourVisio