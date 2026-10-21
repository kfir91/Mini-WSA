# WSA client frontend

React + TypeScript (Vite) SPA for the Mini WSA **`client`** service (`:8084`).

Maven `frontend-maven-plugin` builds this app into `../src/main/resources/static/` (gitignored). Spring serves that folder and proxies `/v1/**` + `/demo/**` to analytics.

## Scripts

```bash
yarn install --ignore-engines
yarn dev      # Vite — proxies /v1 and /demo → analytics (ports from VITE_WSA_*)
yarn build    # emits ../src/main/resources/static/ (empties that folder first)
yarn test     # Vitest (e.g. config helpers)
```

Skip the Maven frontend step with `-Dfrontend.skip=true` when `static/` is already generated.

## Layout

| Path | Role |
|---|---|
| `src/config/` | `appConfig` (ports/paths) + `navItems` |
| `src/i18n/` | `react-i18next` locales |
| `src/layout/` | Shared shell: header + sidebar + content outlet |
| `src/pages/` | Route bodies (overview, apis, alerts, rate, infra, stats) |
| `src/components/` | Reusable UI (`ui/`) + feature widgets |
| `src/hooks/` | Data/UI hooks |
| `src/api/` | HTTP + types |
| `src/styles/` | SCSS tokens + generic layout classes |
| `public/` | Copied as-is into `static/` |

## Conventions

- No hardcoded ports/URLs in components — use `appConfig` / `apiLinks`.
- No hardcoded user-facing strings — use `t('…')`.
- Same-origin relative API paths in production; Vite proxy in dev.
