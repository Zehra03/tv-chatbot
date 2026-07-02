import { Badge } from '@/components/ui/badge'
import { useAppSelector } from '@/app/hooks'

/**
 * Slot-filling göstergesi — chatbot'un o ana dek biriktirdiği arama
 * kriterlerini rozet olarak gösterir; kullanıcı hangi bilgilerin alındığını,
 * asistanın neyi sorduğunu görür. Kriter yokken hiç render edilmez.
 */
const FIELD_LABELS: Record<string, string> = {
  destination: 'Nereye',
  hotelName: 'Otel',
  checkIn: 'Giriş',
  checkOut: 'Çıkış',
  adults: 'Yetişkin',
  children: 'Çocuk',
  childAges: 'Çocuk yaşları',
  rooms: 'Oda',
  nationality: 'Uyruk',
  currency: 'Para birimi',
  origin: 'Nereden',
  departDate: 'Gidiş',
  returnDate: 'Dönüş',
  passengers: 'Yolcu',
  tripType: 'Yön',
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

  return (
    <div aria-label="Biriken arama kriterleri" className="flex flex-wrap items-center gap-2">
      <Badge>{INTENT_LABELS[criteria.intent]}</Badge>
      {entries.map(([key, value]) => (
        <Badge key={key} variant="secondary">
          {FIELD_LABELS[key] ?? key}: {VALUE_LABELS[String(value)] ?? String(value)}
        </Badge>
      ))}
    </div>
  )
}
