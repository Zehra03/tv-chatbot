import { useEffect, useState, type FormEvent } from 'react'
import { CheckCircle2, Eye, EyeOff } from 'lucide-react'
import { Modal } from '@/components/ui/modal'
import { FloatingInput } from '@/components/ui/floating-input'
import { authApi, type ApiError } from '@/api'

/**
 * "Şifremi unuttum" akışı — login sayfasından açılan hafif modal. E-posta bağlantısı
 * GÖNDERİLMEZ: kullanıcı e-postasını ve yeni şifresini girer, şifre aynı pop-up'tan
 * doğrudan değiştirilir (POST /api/v1/auth/reset-password). Bağlantılı akıştan farklı
 * olarak doğrudan sıfırlama, e-postanın kayıtlı olup olmadığını ele verir — kayıtsız
 * e-posta 404 döner ve kullanıcıya net geri bildirim gösterilir. Şifre burada saklanmaz;
 * doğrulama/kalıcılık tamamen backend'dedir.
 */
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
// Backend RegisterRequestDto/ResetPasswordRequestDto ile aynı kural (@Size(min = 8)).
const MIN_PASSWORD = 8

const primaryButton =
  'inline-flex items-center justify-center rounded-md bg-gradient-to-r from-brand-blue to-brand-teal px-4 py-2.5 text-sm font-semibold text-white transition hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal disabled:cursor-not-allowed disabled:opacity-60'

const ghostButton =
  'inline-flex items-center justify-center rounded-md px-4 py-2.5 text-sm font-medium text-brand-ice/70 transition hover:text-white focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-brand-teal disabled:opacity-60'

interface ForgotPasswordModalProps {
  open: boolean
  onClose: () => void
  /** Login formundaki e-posta ile önceden doldurmak için (opsiyonel). */
  defaultEmail?: string
}

export function ForgotPasswordModal({ open, onClose, defaultEmail = '' }: ForgotPasswordModalProps) {
  const [email, setEmail] = useState(defaultEmail)
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)

  // Modal her açıldığında formu sıfırla (ve login'deki e-postayla tazele).
  useEffect(() => {
    if (open) {
      setEmail(defaultEmail)
      setPassword('')
      setConfirmPassword('')
      setShowPassword(false)
      setError('')
      setDone(false)
      setSubmitting(false)
    }
  }, [open, defaultEmail])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (submitting) return
    const trimmed = email.trim()
    // noValidate ile native doğrulamayı kapattık — tek doğrulama kaynağı burası.
    if (!EMAIL_RE.test(trimmed)) {
      setError('Geçerli bir e-posta girin.')
      return
    }
    if (password.length < MIN_PASSWORD) {
      setError('Şifre en az 8 karakter olmalıdır.')
      return
    }
    if (password !== confirmPassword) {
      setError('Şifreler eşleşmiyor.')
      return
    }
    setSubmitting(true)
    setError('')
    try {
      await authApi.resetPassword({ email: trimmed, password })
      setDone(true)
    } catch (err) {
      const apiError = err as ApiError
      // Kayıtsız e-postayı Türkçe ve net göster; diğer hatalarda backend mesajını kullan.
      setError(
        apiError.code === 'EMAIL_NOT_FOUND'
          ? 'Bu e-posta ile kayıtlı bir hesap bulunamadı.'
          : apiError.message || 'İşlem başarısız — lütfen tekrar deneyin.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const eyeToggle = (
    <button
      type="button"
      onClick={() => setShowPassword((v) => !v)}
      className="absolute right-0 top-2 text-brand-ice/50 transition-colors hover:text-brand-teal"
      aria-label={showPassword ? 'Şifreyi gizle' : 'Şifreyi göster'}
    >
      {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
    </button>
  )

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Şifreni sıfırla"
      className="border-white/15 bg-brand-navy/95 text-white backdrop-blur-lg"
    >
      {done ? (
        <div className="space-y-4 text-center">
          <div
            aria-hidden="true"
            className="mx-auto flex h-12 w-12 items-center justify-center rounded-full bg-brand-teal/15 text-brand-teal"
          >
            <CheckCircle2 className="h-6 w-6" />
          </div>
          <p role="status" className="text-sm leading-relaxed text-brand-ice/80">
            <span className="font-medium text-white">{email.trim()}</span> için şifren güncellendi.
            Artık yeni şifrenle giriş yapabilirsin.
          </p>
          <button type="button" onClick={onClose} className={`${primaryButton} w-full`}>
            Giriş ekranına dön
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} noValidate className="space-y-6">
          <p className="text-sm leading-relaxed text-brand-ice/70">
            Hesabının e-posta adresini ve yeni şifreni gir; şifren hemen güncellensin.
          </p>
          <FloatingInput
            id="reset_email"
            label="E-posta"
            type="email"
            value={email}
            onChange={setEmail}
          />
          <FloatingInput
            id="reset_password"
            label="Yeni şifre"
            type={showPassword ? 'text' : 'password'}
            value={password}
            onChange={setPassword}
            trailing={eyeToggle}
          />
          <FloatingInput
            id="reset_confirm"
            label="Yeni şifre (Tekrar)"
            type={showPassword ? 'text' : 'password'}
            value={confirmPassword}
            onChange={setConfirmPassword}
          />
          {error && (
            // red-500 (#EF4444) = açık --destructive'in tam karşılığı; görünüm birebir
            // korunur. Bu modal <body>'ye portal olur ve artık <html>'deki `.dark`ı
            // görür — `text-destructive` koyu temada #7F1D1D'ye düşüp lacivert cam
            // üstünde okunmaz olurdu. (Bkz. LoginPage'deki aynı not.)
            <p role="alert" className="text-xs text-red-500">
              {error}
            </p>
          )}
          <div className="flex items-center gap-3">
            <button type="submit" disabled={submitting} className={`${primaryButton} flex-1`}>
              {submitting ? 'Güncelleniyor…' : 'Şifreyi güncelle'}
            </button>
            <button type="button" onClick={onClose} disabled={submitting} className={ghostButton}>
              Vazgeç
            </button>
          </div>
        </form>
      )}
    </Modal>
  )
}

export default ForgotPasswordModal
