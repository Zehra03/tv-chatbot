package com.paximum.paxassist.ai;

public enum IntentType {
    HOTEL,              // otel arama
    FLIGHT,             // uçuş arama
    FILTER,             // mevcut sonuçları filtreleme
    CLEAR_FILTER,       // mevcut filtreleri kaldırma (örn: "tüm otelleri listele")
    SELECT,             // listeden ürün seçimi
    HOTEL_FACILITY_QA,  // belirli bir otelin olanak/pansiyon/tema sorusu ("ilk otelde havuz var mı") — gerçek detay verisiyle yanıtlanır
    DATE_ALTERNATIVES,  // sonuçsuz aramadan sonra "başka hangi tarihte müsait?" — boş tarih önerisi
    AMBIGUOUS,          // otel mi uçuş mu belirsiz — seçenekli kart ile netleştirilir
    GREETING,           // sadece selamlama ("merhaba") — kodda tespit edilir, sabit metinle yanıtlanır
    OTHER               // kapsam dışı
}
