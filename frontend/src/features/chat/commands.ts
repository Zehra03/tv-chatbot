/**
 * Sohbet slash-komutları — TEK kaynak (composer + resolver ortak kullanır).
 *
 * Composer bu listeyi (ikon hariç) hem "/" yazınca açılan komut paletinde hem de
 * boş sohbetin hızlı-eylem kartlarında gösterir. `resolveCommand` ise ham girdiyi
 * (kart tıklaması ya da elle yazılan "/…") BACKEND'E GİTMEDEN önce yorumlar:
 *
 *  - Arama komutları doğal cümleye çevrilir → kullanıcı balonunda "/otel" değil
 *    "Otel aramak istiyorum" görünür ve intent doğru tetiklenir (mock/LLM ikisi
 *    de doğal cümleden otel/uçuş niyetini çıkarır).
 *  - "/rezervasyon" sohbete hiç mesaj yazmadan /reservations sayfasına yönlendirir.
 *  - Bilinmeyen "/xyz" girdilerinde baştaki "/" atılır; ham slash sözdizimi asla
 *    LLM'e sızmaz.
 *
 * Chatbot yalnızca arar/listeler/yönlendirir — booking'i kontrollü form yapar.
 */

export type CommandAction =
  | { type: 'search'; phrase: string }
  | { type: 'navigate'; to: string }

export interface ChatCommand {
  prefix: string
  label: string
  description: string
  action: CommandAction
}

export const CHAT_COMMANDS: ChatCommand[] = [
  {
    prefix: '/otel',
    label: 'Otel ara',
    description: 'Şehir ve tarihe göre otel bul',
    action: { type: 'search', phrase: 'Otel aramak istiyorum' },
  },
  {
    prefix: '/ucus',
    label: 'Uçuş ara',
    description: 'Kalkış ve varışa göre uçuş bul',
    action: { type: 'search', phrase: 'Uçuş aramak istiyorum' },
  },
  {
    prefix: '/rezervasyon',
    label: 'Rezervasyonlarım',
    description: 'Mevcut rezervasyonlarını görüntüle',
    action: { type: 'navigate', to: '/reservations' },
  },
  {
    prefix: '/oneri',
    label: 'Öneri al',
    description: 'Bütçene göre tatil önerisi al',
    action: { type: 'search', phrase: 'Bütçeme göre bir tatil önerisi almak istiyorum' },
  },
]

/**
 * resolveCommand'ın çıktısı:
 *  - `send`     → sohbete gönderilecek (doğal) metin; boş `text` "gönderme" demek.
 *  - `navigate` → sohbete yazmadan gidilecek istemci-içi rota.
 * `null` ise girdi bir komut değildir; olduğu gibi gönderilmelidir.
 */
export type ResolvedCommand =
  | { type: 'send'; text: string }
  | { type: 'navigate'; to: string }

/**
 * Ham composer girdisini komut olarak çözer. "/" ile başlamıyorsa `null`
 * (normal mesaj — çağıran olduğu gibi gönderir). Aksi hâlde ilk token komut
 * prefix'i ile eşleştirilir:
 *  - navigate komutu → `{ navigate, to }`
 *  - search komutu   → `{ send, doğal cümle (+ yazılan argümanlar) }`
 *  - bilinmeyen slash → baştaki "/" atılıp `{ send, düz metin }` (boşsa text='')
 */
export function resolveCommand(raw: string): ResolvedCommand | null {
  const trimmed = raw.trim()
  if (!trimmed.startsWith('/')) return null

  const spaceIdx = trimmed.search(/\s/)
  const token = (spaceIdx === -1 ? trimmed : trimmed.slice(0, spaceIdx)).toLowerCase()
  const rest = spaceIdx === -1 ? '' : trimmed.slice(spaceIdx + 1).trim()

  const cmd = CHAT_COMMANDS.find((c) => c.prefix === token)
  if (cmd?.action.type === 'navigate') return { type: 'navigate', to: cmd.action.to }
  if (cmd?.action.type === 'search') {
    return { type: 'send', text: rest ? `${cmd.action.phrase}. ${rest}` : cmd.action.phrase }
  }

  // Bilinmeyen komut: baştaki "/"leri at, kalanı düz metin olarak gönder.
  return { type: 'send', text: trimmed.replace(/^\/+/, '').trim() }
}
