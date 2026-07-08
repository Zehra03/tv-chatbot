import { useState } from 'react'
import {
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

  return (
    <aside
      aria-label="Sohbet geçmişi"
      data-collapsed={collapsed}
      className={cn(
        'glass-card hidden shrink-0 flex-col gap-3 transition-[width] duration-300 md:flex',
        collapsed ? 'w-16 p-2' : 'w-64 p-3',
      )}
    >
      <div className={cn('flex items-center gap-2', collapsed && 'flex-col')}>
        <Button
          type="button"
          variant="outline"
          size="sm"
          className={cn(
            'gap-2',
            collapsed ? 'h-9 w-9 justify-center px-0' : 'w-full justify-start',
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
          className="h-9 w-9 shrink-0 text-brand-ice/60 hover:text-white"
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
          <p className="px-1 text-xs font-medium uppercase tracking-wide text-brand-ice/50">
            Geçmiş
          </p>

          <div className="-mr-1 flex-1 space-y-1 overflow-y-auto pr-1 [scrollbar-color:theme(colors.white/25%)_transparent] [scrollbar-width:thin]">
            {sessions.isPending && (
              <p className="px-2 py-1 text-sm text-brand-ice/60">Yükleniyor…</p>
            )}
            {sessions.isError && (
              <p className="px-2 py-1 text-sm text-brand-ice/60">Geçmiş yüklenemedi.</p>
            )}
            {sessions.data?.length === 0 && (
              <p className="px-2 py-1 text-sm text-brand-ice/60">Henüz sohbet yok.</p>
            )}
            {sessions.data?.map((session) => {
              const active = session.id === activeSessionId
              return (
                <div key={session.id} className="group relative">
                  <button
                    type="button"
                    aria-current={active ? 'true' : undefined}
                    onClick={() => loadSession.mutate(session.id)}
                    className={cn(
                      'w-full rounded-lg px-2.5 py-2 pr-8 text-left transition-colors',
                      active
                        ? 'bg-white/10 text-white'
                        : 'text-brand-ice/80 hover:bg-white/5 hover:text-white',
                    )}
                  >
                    <span className="block truncate text-sm font-medium">
                      {session.title ?? 'Yeni sohbet'}
                    </span>
                    <span className="block truncate text-xs text-brand-ice/50">
                      {formatDateTime(session.updatedAt)} · {session.messageCount} mesaj
                    </span>
                  </button>
                  <button
                    type="button"
                    aria-label={`Oturumu sil: ${session.title ?? session.id}`}
                    onClick={() => deleteSession.mutate(session.id)}
                    className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-brand-ice/40 opacity-0 transition-opacity hover:bg-white/10 hover:text-white focus-visible:opacity-100 group-hover:opacity-100"
                  >
                    <Trash2 className="h-3.5 w-3.5" aria-hidden />
                  </button>
                </div>
              )
            })}
          </div>
        </>
      )}
    </aside>
  )
}
