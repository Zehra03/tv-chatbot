import { MessageSquarePlus, Trash2 } from 'lucide-react'
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

/**
 * Sohbet geçmişi paneli — önceki oturumları listeler; tıklanınca oturum
 * thread'e yüklenir, çöp kutusu siler, "Yeni sohbet" durumu sıfırlar.
 * md altında gizlidir (dar ekranda thread'e yer bırakır).
 */
export function SessionSidebar() {
  const sessions = useChatSessions()
  const loadSession = useLoadSession()
  const deleteSession = useDeleteSession()
  const activeSessionId = useAppSelector((s) => s.chat.sessionId)
  const dispatch = useAppDispatch()

  return (
    <aside
      aria-label="Sohbet geçmişi"
      className="glass-card hidden w-64 shrink-0 flex-col gap-3 p-3 md:flex"
    >
      <Button
        type="button"
        variant="outline"
        size="sm"
        className="w-full justify-start gap-2"
        onClick={() => dispatch(chatReset())}
      >
        <MessageSquarePlus className="h-4 w-4" aria-hidden />
        Yeni sohbet
      </Button>

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
    </aside>
  )
}
