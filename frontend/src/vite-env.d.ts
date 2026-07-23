/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL — uygulama her zaman gerçek backend'e konuşur. */
  readonly VITE_API_BASE_URL: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
