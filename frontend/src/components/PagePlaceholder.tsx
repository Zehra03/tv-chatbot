/**
 * Geçici sayfa yer tutucu — rota iskelesi çalışsın diye. İlgili epic kendi
 * gerçek sayfasını yazınca bu placeholder kaldırılır.
 */
interface PagePlaceholderProps {
  title: string
  route: string
}

export function PagePlaceholder({ title, route }: PagePlaceholderProps) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-2 bg-background text-foreground">
      <h1 className="text-2xl font-bold">{title}</h1>
      <p className="text-sm text-muted-foreground">
        <code>{route}</code> — yakında (placeholder)
      </p>
    </div>
  )
}
