/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_WSA_HOST?: string;
  readonly VITE_WSA_CLIENT_PORT?: string;
  readonly VITE_WSA_INGEST_PORT?: string;
  readonly VITE_WSA_ANALYTICS_PORT?: string;
  readonly VITE_WSA_ADMINER_PORT?: string;
  readonly VITE_WSA_KAFKA_UI_PORT?: string;
  readonly WSA_HOST?: string;
  readonly WSA_CLIENT_PORT?: string;
  readonly WSA_INGEST_PORT?: string;
  readonly WSA_ANALYTICS_PORT?: string;
  readonly WSA_ADMINER_PORT?: string;
  readonly WSA_KAFKA_UI_PORT?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
