import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';

/** Prefer VITE_* override, then root WSA_*, then default. */
function pick(
  viteEnv: Record<string, string>,
  rootEnv: Record<string, string>,
  viteKey: string,
  wsaKey: string,
  fallback: string,
): string {
  return viteEnv[viteKey] || rootEnv[viteKey] || rootEnv[wsaKey] || fallback;
}

export default defineConfig(({ mode }) => {
  const frontendDir = path.resolve(__dirname);
  const repoRoot = path.resolve(frontendDir, '../..');
  const viteEnv = { ...loadEnv(mode, repoRoot, 'VITE_'), ...loadEnv(mode, frontendDir, 'VITE_') };
  const rootEnv = loadEnv(mode, repoRoot, '');

  const host = pick(viteEnv, rootEnv, 'VITE_WSA_HOST', 'WSA_HOST', 'localhost');
  const analyticsPort = pick(viteEnv, rootEnv, 'VITE_WSA_ANALYTICS_PORT', 'WSA_ANALYTICS_PORT', '8080');
  const clientPort = pick(viteEnv, rootEnv, 'VITE_WSA_CLIENT_PORT', 'WSA_CLIENT_PORT', '8084');
  const vitePort = Number(viteEnv.VITE_DEV_PORT || rootEnv.VITE_DEV_PORT || 5173);
  const analyticsOrigin = `http://${host}:${analyticsPort}`;
  const clientOrigin = `http://${host}:${clientPort}`;

  return {
    plugins: [react()],
    // Root .env is canonical (WSA_* / optional VITE_*).
    envDir: repoRoot,
    // Expose both prefixes so appConfig can prefer VITE_ then fall back to WSA_.
    envPrefix: ['VITE_', 'WSA_'],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src'),
      },
    },
    server: {
      port: vitePort,
      proxy: {
        '/v1': analyticsOrigin,
        '/demo': analyticsOrigin,
        '/actuator': clientOrigin,
      },
    },
    build: {
      outDir: path.resolve(__dirname, '../src/main/resources/static'),
      emptyOutDir: true,
      sourcemap: false,
    },
    test: {
      environment: 'jsdom',
      globals: true,
    },
  };
});
