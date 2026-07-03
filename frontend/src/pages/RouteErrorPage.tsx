import { isRouteErrorResponse, Link, useLocation, useRouteError } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { ErrorState } from '@/components/ErrorState'

const HOME_PATH = '/chat'

/**
 * Router hata sınırı — bir sayfa render sırasında patlarsa react-router'ın
 * İngilizce varsayılan ekranı yerine Türkçe, eyleme dönük bir kart gösterilir.
 * Layout'un altındaki pathless route'ta chrome (header/nav) korunur; kök
 * seviyedeki kopyası Layout'un kendisi patlarsa devreye girer.
 */
export function RouteErrorPage() {
  const error = useRouteError()
  const { pathname } = useLocation()
  const message = isRouteErrorResponse(error)
    ? `Sayfa yüklenemedi (${error.status}).`
    : error instanceof Error && error.message
      ? error.message
      : 'Beklenmeyen bir hata oluştu.'

  // Layout'un AnimatedOutlet'i outlet elemanını mount'ta dondurur; react-router
  // hata sınırı yalnız location DEĞİŞİNCE sıfırlandığından, hata zaten
  // HOME_PATH'teyken oraya soft <Link> sessiz bir no-op olur. Bu durumda tam
  // sayfa navigasyon (<a>) ile sınır garantili sıfırlanır.
  const needsHardNav = pathname === HOME_PATH

  return (
    <div className="mx-auto max-w-lg space-y-4 p-6">
      <h1 className="text-2xl font-bold">Bir şeyler ters gitti</h1>
      <ErrorState message={message} />
      <div className="flex flex-wrap gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => window.location.reload()}
        >
          Sayfayı yenile
        </Button>
        <Button asChild variant="outline" size="sm">
          {needsHardNav ? (
            <a href={HOME_PATH}>Sohbete dön</a>
          ) : (
            <Link to={HOME_PATH}>Sohbete dön</Link>
          )}
        </Button>
      </div>
    </div>
  )
}
