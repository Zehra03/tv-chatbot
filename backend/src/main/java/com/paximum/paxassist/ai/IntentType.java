package com.paximum.paxassist.ai;

public enum IntentType {
    HOTEL,              // otel arama
    FLIGHT,             // uçuş arama
    FILTER,             // mevcut sonuçları filtreleme
    SELECT,             // listeden ürün seçimi
    DATE_ALTERNATIVES,  // sonuçsuz aramadan sonra "başka hangi tarihte müsait?" — boş tarih önerisi
    AMBIGUOUS,          // otel mi uçuş mu belirsiz — seçenekli kart ile netleştirilir
    OTHER               // kapsam dışı / selamlama
}
