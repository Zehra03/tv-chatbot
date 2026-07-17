import { useState } from 'react'
import {
  MessageSquare,
  MessageSquarePlus,
  PanelLeftClose,
  PanelLeftOpen,
  Trash2,
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { useAppDispatch, useAppSelector } from '@/app/hooks'
import { chatReset } from '@/features/chat/chatSlice'
import {
  useChatSessions,
  useDeleteSession,
  useLoadSession,
} from '@/features/chat/useChatSessions'
import { formatDateTime } from '@/utils/format'
import { cn } from '@/lib/utils'

// v2: varsayılan AÇIK'tan KAPALI'ya döndü. Eski anahtar, eski varsayılana göre
// yazılmış tercihleri taşıyor — onu okumak yeni varsayılanı sessizce ezerdi
// (eskiden açık gezen herkes gene açık açılırdı). Sürüm atlayarak temiz başlıyoruz.
const COLLAPSE_KEY = 'pax.chat.sidebar-collapsed.v2'

/**
 * Saklı daraltma tercihini oku. Varsayılan KAPALI ray: geçmiş, kullanıcı
 * isteyene kadar thread'den yer çalmaz. Yalnızca açık bırakıldığı açıkça
 * kaydedilmişse ('0') genişlemiş başlar; kalıcılık iyileştirmedir, gereklilik değil.
 */
function loadCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) !== '0'
  } catch {
    return true
  }
}

function persistCollapsed(collapsed: boolean) {
  try {
    localStorage.setItem(COLLAPSE_KEY, collapsed ? '1' : '0')
  } catch {
    /* sessizce geç */
  }
}

/**
 * Sohbet geçmişi paneli — önceki oturumları listeler; tıklanınca oturum
 * thread'e yüklenir, çöp kutusu siler, "Yeni sohbet" durumu sıfırlar.
 *
 * Yerleşim Gemini/ChatGPT deseni: sol kenara ve header'ın hemen altına TAM
 * dayalı, varsayılan olarak KAPALI ince ikon-rayı; panel düğmesiyle açılınca
 * geçmiş listesi görünür. Tercih localStorage'da saklanır ki yenilemede korunsun.
 * Ray her genişlikte durur — mobilde açılınca thread'i sıkıştırmak yerine
 * üstüne biner (karartmaya ya da düğmeye tıklayınca kapanır).
 *
 * Görsel dil navbar (GooeyNav) ile uyumlu: AKTİF oturum, üst navigasyondaki
 * gibi beyaz pill + lacivert metindir; pasif satırlar brand-ice tonunda, teal
 * aksanlı. Cam yüzey (glass-card) header ve diğer panellerle aynı reçete.
 */
export function SessionSidebar() {
  const sessions = useChatSessions()
  const loadSession = useLoadSession()
  const deleteSession = useDeleteSession()
  const activeSessionId = useAppSelector((s) => s.chat.sessionId)
  const dispatch = useAppDispatch()
  const [collapsed, setCollapsed] = useState(loadCollapsed)

  const toggleCollapsed = () =>
    setCollapsed((prev) => {
      const next = !prev
      persistCollapsed(next)
      return next
    })

  // Boş oturumları gizle — henüz mesaj yazılmamış sohbetler geçmişte görünmesin.
  const visibleSessions = sessions.data?.filter((s) => s.messageCount > 0)
  const count = visibleSessions?.length ?? 0

  return (
    <>
      {/* Mobilde açık panelin arkasını karart — dışına tıklayınca kapanır.
          md+'da panel akışta durduğu için karartma gerekmez. */}
      {!collapsed && (
        <div
          className="absolute inset-0 z-30 bg-black/50 md:hidden"
          onClick={toggleCollapsed}
          aria-hidden
        />
      )}
      <aside
        aria-label="Sohbet geçmişi"
        data-collapsed={collapsed}
        className={cn(
          // Sol kenara tam dayalı ray: glass-card'ın yuvarlak köşesi ve sol/üst/alt
          // kenarı ezilir, yalnız thread'den ayıran sağ hairline kalır.
          'glass-card z-40 flex shrink-0 flex-col overflow-hidden rounded-none border-y-0 border-l-0 transition-[width] duration-300',
          collapsed ? 'w-14 p-2 sm:w-16' : 'w-64 p-2.5',
          // Mobilde açıkken thread'i ezmesin diye üstüne biner; md+'da akışa döner.
          // Üste binerken cam yüzey şeffaf kalırsa altındaki metin okunur, o yüzden
          // opak zemin (token: koyuda navy, açıkta beyaz); md+'da glass-card'ın
          // kendi camına geri döner.
          !collapsed &&
            'absolute inset-y-0 left-0 bg-background/95 md:relative md:bg-foreground/10',
        )}
      >
        {/* Üst eylem satırı — Yeni sohbet (pill) + panel daraltma. */}
        <div className={cn('flex items-center gap-2', collapsed && 'flex-col')}>
          <Button
            type="button"
            variant="outline"
            size="sm"
            className={cn(
              'gap-2 rounded-full border-foreground/15 text-muted-foreground transition-colors hover:border-brand-teal/50 hover:text-foreground',
              collapsed ? 'h-9 w-9 justify-center px-0' : 'h-9 w-full justify-start px-3',
            )}
            onClick={() => dispatch(chatReset())}
            aria-label="Yeni sohbet"
            title="Yeni sohbet"
          >
            <MessageSquarePlus className="h-4 w-4" aria-hidden />
            {!collapsed && 'Yeni sohbet'}
          </Button>
          <Button
            type="button"
            variant="ghost"
            size="icon"
            className="h-9 w-9 shrink-0 rounded-full text-muted-foreground hover:bg-foreground/10 hover:text-foreground"
            onClick={toggleCollapsed}
            aria-expanded={!collapsed}
            aria-label={
              collapsed ? 'Geçmiş panelini genişlet' : 'Geçmiş panelini daralt'
            }
            title={collapsed ? 'Genişlet' : 'Daralt'}
          >
            {collapsed ? (
              <PanelLeftOpen className="h-4 w-4" aria-hidden />
            ) : (
              <PanelLeftClose className="h-4 w-4" aria-hidden />
            )}
          </Button>
        </div>

        {!collapsed && (
          <>
            {/* Hairline ayraç — header'ın border-b'siyle aynı ton, panele yapı verir. */}
            <div className="my-2.5 h-px bg-foreground/10" aria-hidden />

            <div className="flex items-center justify-between px-1.5 pb-1.5">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Geçmiş
              </p>
              {count > 0 && (
                <span className="rounded-full bg-foreground/10 px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-muted-foreground">
                  {count}
                </span>
              )}
            </div>

            <ul
              className="-mr-1.5 flex-1 space-y-0.5 overflow-y-auto pr-1.5 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]"
            >
              {sessions.isPending && (
                <li className="px-2 py-1 text-sm text-muted-foreground">Yükleniyor…</li>
              )}
              {sessions.isError && (
                <li className="px-2 py-1 text-sm text-muted-foreground">Geçmiş yüklenemedi.</li>
              )}
              {visibleSessions?.length === 0 && (
                <li className="flex flex-col items-center gap-1.5 px-2 py-6 text-center">
                  <MessageSquare className="h-5 w-5 text-muted-foreground" aria-hidden />
                  <span className="text-sm text-muted-foreground">Henüz sohbet yok.</span>
                </li>
              )}
              {visibleSessions?.map((session) => {
                const active = session.id === activeSessionId
                return (
                  <li key={session.id} className="group relative">
                    <button
                      type="button"
                      aria-current={active ? 'true' : undefined}
                      onClick={() => loadSession.mutate(session.id)}
                      className={cn(
                        'flex w-full items-start gap-2.5 rounded-xl px-2.5 py-2 pr-9 text-left transition-all duration-200',
                        // Aktif = navbar pill dili: zıt kapsül (koyuda beyaz/lacivert,
                        // açıkta lacivert/beyaz) + yumuşak mavi parıltı.
                        // Pasif = ikincil metin, hafif hover.
                        active
                          ? 'bg-foreground text-background shadow-[0_2px_14px_theme(colors.brand.blue/25%)]'
                          : 'text-muted-foreground hover:bg-foreground/[0.06] hover:text-foreground',
                      )}
                    >
                      <MessageSquare
                        aria-hidden
                        className={cn(
                          'mt-0.5 h-4 w-4 shrink-0 transition-colors',
                          active
                            ? 'text-brand-blue'
                            : 'text-muted-foreground group-hover:text-muted-foreground',
                        )}
                      />
                      <span className="min-w-0 flex-1">
                        <span className="block truncate text-sm font-medium">
                          {session.title ?? 'Yeni sohbet'}
                        </span>
                        <span
                          className={cn(
                            'mt-0.5 block truncate text-xs',
                            // Aktif pill zıt yüzey → üstündeki ikincil yazı da `background`.
                            active ? 'text-background/55' : 'text-muted-foreground',
                          )}
                        >
                          {formatDateTime(session.updatedAt)} · {session.messageCount} mesaj
                        </span>
                      </span>
                    </button>
                    <button
                      type="button"
                      aria-label={`Oturumu sil: ${session.title ?? session.id}`}
                      onClick={() => deleteSession.mutate(session.id)}
                      className={cn(
                        'absolute right-1.5 top-1/2 -translate-y-1/2 rounded-lg p-1.5 opacity-0 transition-all focus-visible:opacity-100 group-hover:opacity-100',
                        // Aktif pill zıt yüzey (bg-foreground) — üstündeki ikon da zıt
                        // renk almalı: koyuda lacivert, açıkta beyaz. Yani `background`.
                        active
                          ? 'text-background/40 hover:bg-background/10 hover:text-destructive'
                          : 'text-muted-foreground hover:bg-foreground/10 hover:text-foreground',
                      )}
                    >
                      <Trash2 className="h-3.5 w-3.5" aria-hidden />
                    </button>
                  </li>
                )
              })}
            </ul>
          </>
        )}
      </aside>
    </>
  )
}
