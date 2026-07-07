graph TD
    %% =====================================================================
    %% PaxAssist — SADELEŞTİRİLMİŞ görünüm.
    %% Kanonik/tam mimari için bkz. architecture.md (JWT auth + users, Log modülü + LogDB,
    %% MCP Server, Evaluator-Optimizer dahil). Burada guard/intent, Orchestrator'ın İÇ
    %% adımları olarak gösterilir (guard = ilk adım, LLM öncesi fail-fast) — iki diyagram
    %% bu konuda artık hizalıdır.
    %% =====================================================================
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
        Orchestrator[2. Orchestrator Modülü <br> guard → intent → route → persist]:::orchestrator
        Guard[Guard <br> Orchestrator'ın ilk adımı · LLM öncesi fail-fast]:::orchestrator
        Intention[AI / Intention Modülü <br> Niyet + slot çıkarımı]:::orchestrator

        %% İş Mantığı Modülleri
        Flight[Flight Modülü]:::coreMod
        Hotel[Hotel Modülü]:::coreMod
        Reservation[Reservation Modülü <br> PLANLANAN · 0-token AI-dışı form]:::coreMod

        %% Önbellek Katmanı
        Cache[Redis / In-Memory Cache Katmanı <br> Canlı API Sonuçları Tutulur]:::cache
    end

    %% İzole Veritabanları (bu görünümde users + LogDB gösterilmez — bkz. architecture.md)
    subgraph Databases [Kalıcı Veritabanları]
        ChatDB[(Chat & Session DB <br> Geçmiş ve Oturum Durumu)]:::db
        ResDB[(Reservation DB <br> Kalıcı Rezervasyonlar)]:::db
    end

    TourVisio((TourVisio API)):::extService

    %% Akış Okları
    User -->|Mesaj| RateLimiter --> Orchestrator
    Orchestrator -->|1. ilk adım| Guard
    Orchestrator -->|2. niyet + slot| Intention

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
    User -->|Rezervasyon Formu · 0 token| Reservation
    Reservation --> ResDB
    Reservation --> TourVisio
