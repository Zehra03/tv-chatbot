/**
 * Ortak domain primitifleri. Kaynak: V1 Flyway şeması
 * (backend/src/main/resources/db/migration/V1__initial_schema.sql) ve backend
 * record'ları (ör. hotel/HotelProduct.java). Fiyat DB'de düz `numeric + char(3)`
 * taşınır — ayrı bir `Money` sarmalayıcı YOKTUR.
 */

/** Ürün tipi. DB: reservations.product_type CHECK ('hotel','flight'). */
export type ProductType = 'hotel' | 'flight'

/** ISO 8601 gün (YYYY-MM-DD). DB: `date` sütunları (check_in, check_out, reservation_date). */
export type IsoDate = string

/** ISO 8601 instant (YYYY-MM-DDTHH:mm:ssZ). DB: `timestamptz` (depart_time, created_at…). */
export type IsoDateTime = string

/** ISO 4217 para birimi. DB: currency char(3) (ör. "EUR"). */
export type CurrencyCode = string

/** ISO-3166 alpha-2 ülke kodu. DB: nationality varchar(2) (ör. "TR"). */
export type CountryCode = string
