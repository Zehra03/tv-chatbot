/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Backend base URL. MSW aktifken boş bırakılabilir. */
  readonly VITE_API_BASE_URL: string
  /** Sahte backend'i (MSW) aç/kapat. "false" → gerçek backend. */
  readonly VITE_ENABLE_MSW?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
