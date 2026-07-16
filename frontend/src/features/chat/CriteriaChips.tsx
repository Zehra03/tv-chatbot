import { motion } from 'framer-motion'
import { Badge } from '@/components/ui/badge'
import { useAppSelector } from '@/app/hooks'
import type { PartialCriteria } from '@/types'

/**
 * Slot-filling göstergesi — chatbot'un o ana dek biriktirdiği arama
 * kriterlerini rozet olarak gösterir; kullanıcı hangi bilgilerin alındığını,
 * asistanın neyi sorduğunu görür. Kriter yokken hiç render edilmez.
 */

/** İki niyette de aynı anlama gelen alanlar. */
const SHARED_LABELS: Record<string, string> = {
  nationality: 'Uyruk',
  currency: 'Para birimi',
}

/**
 * Etiketler niyete göre ayrışır: `destination` her iki kriter tipinde de var ama
 * farklı şeyi anlatıyor — otelde konaklanacak şehir, uçuşta varış noktası. Ortak
 * tek tablo otel aramasında "Nereye: Antalya" gibi yanlış bir rozet üretiyordu.
 */
const FIELD_LABELS: Record<PartialCriteria['intent'], Record<string, string>> = {
  hotel: {
    ...SHARED_LABELS,
    destination: 'Nerede / Şehir',
    hotelName: 'Otel',
    checkIn: 'Giriş',
    checkOut: 'Çıkış',
    nights: 'Gece',
    adults: 'Yetişkin',
    children: 'Çocuk',
    childAges: 'Çocuk yaşları',
    rooms: 'Oda',
  },
  flight: {
    ...SHARED_LABELS,
    origin: 'Nereden',
    destination: 'Nereye',
    departDate: 'Gidiş',
    returnDate: 'Dönüş',
    passengers: 'Yolcu',
    tripType: 'Yön',
  },
}

/**
 * Niyetin tablosunda olmayan bir alan için son çare. Yalnız `destination` iki tabloda da
 * var (ve niyet tablosu her zaman önce bakıldığı için buraya hiç düşmez); geri kalanı
 * çakışmaz. Böylece niyet beklenmedik gelse bile kullanıcı "adults: 2" gibi ham anahtar
 * değil, okunur bir etiket görür.
 */
const FALLBACK_LABELS: Record<string, string> = {
  ...FIELD_LABELS.hotel,
  ...FIELD_LABELS.flight,
}

const VALUE_LABELS: Record<string, string> = {
  one_way: 'Tek yön',
  round_trip: 'Gidiş-dönüş',
}

const INTENT_LABELS = { hotel: 'Otel araması', flight: 'Uçuş araması' } as const

function isFilled(value: unknown): boolean {
  if (value === undefined || value === null || value === '') return false
  if (Array.isArray(value)) return value.length > 0
  return true
}

export function CriteriaChips() {
  const criteria = useAppSelector((s) => s.chat.accumulatedCriteria)
  if (!criteria) return null

  const entries = Object.entries(criteria.criteria as Record<string, unknown>).filter(([, v]) =>
    isFilled(v),
  )
  const labels = FIELD_LABELS[criteria.intent] ?? {}

  return (
    <div
      role="group"
      aria-label="Biriken arama kriterleri"
      className="flex flex-wrap items-center gap-2"
    >
      <motion.div layout initial={{ opacity: 0, scale: 0.85 }} animate={{ opacity: 1, scale: 1 }}>
        <Badge variant="glassAccent">{INTENT_LABELS[criteria.intent]}</Badge>
      </motion.div>
      {entries.map(([key, value]) => (
        <motion.div
          key={key}
          layout
          initial={{ opacity: 0, scale: 0.85 }}
          animate={{ opacity: 1, scale: 1 }}
        >
          <Badge variant="glass">
            {labels[key] ?? FALLBACK_LABELS[key] ?? key}: {VALUE_LABELS[String(value)] ?? String(value)}
          </Badge>
        </motion.div>
      ))}
    </div>
  )
}
