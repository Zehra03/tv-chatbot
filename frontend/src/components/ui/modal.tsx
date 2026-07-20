import { type ReactNode, useEffect, useId, useRef } from 'react'
import { createPortal } from 'react-dom'
import { cn } from '@/lib/utils'

interface ModalProps {
  open: boolean
  onClose: () => void
  title?: string
  children: ReactNode
  className?: string
}

/**
 * Hafif modal — portal ile <body>'ye render edilir, overlay tıklaması ve Escape
 * ile kapanır. Açılışta odak diyaloğa taşınır; başlık aria-labelledby ile
 * bağlanır. Not: tam odak-tuzağı (focus trap) yok; karmaşık akışlar için
 * ileride Radix Dialog'a yükseltilebilir.
 */
export function Modal({ open, onClose, title, children, className }: ModalProps) {
  const dialogRef = useRef<HTMLDivElement>(null)
  const titleId = useId()

  useEffect(() => {
    if (!open) return
    // Açan öğeyi sakla ve KAPANIŞTA ona geri dön. Eskiden odak diyaloğa taşınıyor ama geri
    // verilmiyordu: modal unmount olunca odak <body>'ye düşüyor, klavye kullanıcısı
    // ("Şifremi unuttum?" ile açan kişi) belgenin en başına atılıyor ve tüm sayfayı yeniden
    // Tab'lamak zorunda kalıyordu.
    const opener = document.activeElement as HTMLElement | null
    dialogRef.current?.focus()
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('keydown', onKey)
      // Öğe hâlâ belgedeyse odağı iade et (kapanışta DOM'dan çıkmış olabilir).
      if (opener?.isConnected) opener.focus()
    }
  }, [open, onClose])

  if (!open) return null

  return createPortal(
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      <div className="absolute inset-0 bg-black/50" onClick={onClose} aria-hidden />
      <div
        ref={dialogRef}
        role="dialog"
        aria-modal="true"
        tabIndex={-1}
        {...(title ? { 'aria-labelledby': titleId } : { 'aria-label': 'İletişim kutusu' })}
        className={cn(
          'relative z-10 w-full max-w-lg rounded-lg border bg-card p-6 text-card-foreground shadow-lg focus-visible:outline-none',
          className,
        )}
      >
        {title && (
          <h2 id={titleId} className="mb-4 text-lg font-semibold">
            {title}
          </h2>
        )}
        {children}
      </div>
    </div>,
    document.body,
  )
}
