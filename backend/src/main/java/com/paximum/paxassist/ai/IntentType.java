package com.paximum.paxassist.ai;

public enum IntentType {
    HOTEL,              // otel arama
    FLIGHT,             // uçuş arama
    FILTER,             // mevcut sonuçları filtreleme
    SELECT,             // listeden ürün seçimi
    DATE_ALTERNATIVES,  // sonuçsuz aramadan sonra "başka hangi tarihte müsait?" — boş tarih önerisi
    AMBIGUOUS,          // otel mi uçuş mu belirsiz — seçenekli kart ile netleştirilir
    SMALLTALK,          // selamlama / sohbet (merhaba, teşekkürler, iyi günler) — GERÇEK kapsam dışı DEĞİL,
                        // out-of-scope streak'ine SAYILMAZ (guard yalnızca OTHER'ı sayar)
    OTHER,              // gerçek kapsam dışı: bilgi/servis soruları (yorum, iptal, olanaklar) + prompt injection
    UNKNOWN             // niyet çıkarımı başarısız (LLM parse hatası) — LLM'in hatası kullanıcıya fatura edilmez;
                        // OTHER değildir, dolayısıyla streak'e sayılmaz. LLM bu değeri ASLA üretmez (yalnızca kod üretir).
}
