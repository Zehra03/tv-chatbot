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

const COLLAPSE_KEY = 'pax.chat.sidebar-collapsed'

/** Saklı daraltma tercihini oku — kalıcılık iyileştirmedir, gereklilik değil. */
function loadCollapsed(): boolean {
  try {
    return localStorage.getItem(COLLAPSE_KEY) === '1'
  } catch {
    return false
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
 * md altında gizlidir (dar ekranda thread'e yer bırakır). Başlıktaki panel
 * düğmesiyle ince ikon-rayına daraltılıp genişletilebilir; tercih
 * localStorage'da saklanır ki sayfa yenilemede korunsun.
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
    <aside
      aria-label="Sohbet geçmişi"
      data-collapsed={collapsed}
      className={cn(
        'glass-card hidden shrink-0 flex-col overflow-hidden transition-[width] duration-300 md:flex',
        collapsed ? 'w-16 p-2' : 'w-64 p-2.5',
      )}
    >
      {/* Üst eylem satırı — Yeni sohbet (pill) + panel daraltma. */}
      <div className={cn('flex items-center gap-2', collapsed && 'flex-col')}>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className={cn(
            'gap-2 rounded-full border-white/15 text-brand-ice transition-colors hover:border-brand-teal/50 hover:text-white',
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
          className="h-9 w-9 shrink-0 rounded-full text-brand-ice/60 hover:bg-white/10 hover:text-white"
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
          <div className="my-2.5 h-px bg-white/10" aria-hidden />

          <div className="flex items-center justify-between px-1.5 pb-1.5">
            <p className="text-[11px] font-semibold uppercase tracking-wider text-brand-ice/50">
              Geçmiş
            </p>
            {count > 0 && (
              <span className="rounded-full bg-white/10 px-1.5 py-0.5 text-[10px] font-medium tabular-nums text-brand-ice/60">
                {count}
              </span>
            )}
          </div>

          <ul
            className="-mr-1.5 flex-1 space-y-0.5 overflow-y-auto pr-1.5 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]"
          >
            {sessions.isPending && (
              <li className="px-2 py-1 text-sm text-brand-ice/60">Yükleniyor…</li>
            )}
            {sessions.isError && (
              <li className="px-2 py-1 text-sm text-brand-ice/60">Geçmiş yüklenemedi.</li>
            )}
            {visibleSessions?.length === 0 && (
              <li className="flex flex-col items-center gap-1.5 px-2 py-6 text-center">
                <MessageSquare className="h-5 w-5 text-brand-ice/30" aria-hidden />
                <span className="text-sm text-brand-ice/55">Henüz sohbet yok.</span>
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
                      // Aktif = navbar pill dili: beyaz kapsül + lacivert metin,
                      // yumuşak mavi parıltı. Pasif = brand-ice, hafif hover.
                      active
                        ? 'bg-white text-brand-navy shadow-[0_2px_14px_theme(colors.brand.blue/25%)]'
                        : 'text-brand-ice/75 hover:bg-white/[0.06] hover:text-white',
                    )}
                  >
                    <MessageSquare
                      aria-hidden
                      className={cn(
                        'mt-0.5 h-4 w-4 shrink-0 transition-colors',
                        active
                          ? 'text-brand-blue'
                          : 'text-brand-ice/40 group-hover:text-brand-ice/70',
                      )}
                    />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">
                        {session.title ?? 'Yeni sohbet'}
                      </span>
                      <span
                        className={cn(
                          'mt-0.5 block truncate text-xs',
                          active ? 'text-brand-navy/55' : 'text-brand-ice/45',
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
                      active
                        ? 'text-brand-navy/40 hover:bg-brand-navy/10 hover:text-destructive'
                        : 'text-brand-ice/40 hover:bg-white/10 hover:text-white',
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
  )
}
