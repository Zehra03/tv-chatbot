import { isRouteErrorResponse, Link, useRouteError } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'

/**
 * Router hata sınırı — bir sayfa render sırasında patlarsa react-router'ın
 * İngilizce varsayılan ekranı yerine Türkçe, eyleme dönük bir kart gösterilir.
 * Layout'un altındaki pathless route'ta chrome (header/nav) korunur; kök
 * seviyedeki kopyası Layout'un kendisi patlarsa devreye girer.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  const message = isRouteErrorResponse(error)
    ? `Sayfa yüklenemedi (${error.status}).`
    : error instanceof Error && error.message
      ? error.message
      : 'Beklenmeyen bir hata oluştu.'

  return (
    <div className="mx-auto max-w-lg space-y-4 p-6">
      <h1 className="text-2xl font-bold">Bir şeyler ters gitti</h1>
      <ErrorState message={message} onRetry={() => window.location.reload()} />
      <Button asChild variant="outline" size="sm">
        <Link to="/chat">Sohbete dön</Link>
      </Button>
    </div>
  )
}
