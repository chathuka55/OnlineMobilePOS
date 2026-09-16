# Easy POS · POS SaaS

Multi-tenant point-of-sale platform: Spring Boot API, Next.js admin dashboard, and a Vite + Tauri POS terminal.

## Prerequisites

| Tool              | Version                               |
| ----------------- | ------------------------------------- |
| JDK               | **21**                                |
| Node.js           | **20+**                               |
| pnpm              | **9+** (`corepack enable`)            |
| Docker Desktop    | for Postgres, Redis, MinIO            |
| Rust + Tauri deps | only for `tauri:dev` / desktop builds |

## Repository layout

```
apps/
  admin-dashboard/     # @possaas/admin-dashboard — Next.js 15 admin
  pos-terminal/        # @possaas/pos-terminal — Vite + Tauri POS UI
packages/
  ui/                  # @possaas/ui — design system (navy + violet)
  api-client/          # @possaas/api-client — typed fetch client
  tsconfig/            # shared TypeScript configs
  eslint-config/       # shared ESLint configs
backend/               # Spring Boot modules
infra/docker/          # compose.dev.yml
scripts/
  fetch-openapi.mjs    # dump OpenAPI → packages/api-client/openapi.json
```

## Quick start

### 1. Install frontend deps

```bash
pnpm install
```

### 2. Start infrastructure

```bash
pnpm infra:up
```

Brings up Postgres, Redis, and MinIO (see `infra/docker/compose.dev.yml` and `.env`).

### 3. Run the API

```bash
pnpm api:dev
```

API defaults to `http://localhost:8080`.

Optional OpenAPI snapshot:

```bash
pnpm api:openapi
```

### 4. Run the admin dashboard

```bash
pnpm dev:dashboard
```

Open [http://localhost:3000](http://localhost:3000).  
Set `NEXT_PUBLIC_API_URL` if the API is not on `localhost:8080`.

### 5. Run the POS terminal

Browser / Vite:

```bash
pnpm dev:pos
```

Desktop (Tauri 2):

```bash
pnpm --filter @possaas/pos-terminal tauri:dev
```

Set `VITE_API_URL` if needed.

## Brand / UI

Design tokens live in `packages/ui/styles/globals.css`:

- Primary violet `#8989FD`
- Navy header `#192A56`
- Background `#F5F7FA`
- Success / danger / info accents
- Font: **DM Sans** (fallback Source Sans 3)

## Scripts (root)

| Script                         | Purpose                 |
| ------------------------------ | ----------------------- |
| `pnpm dev:dashboard`           | Next.js admin           |
| `pnpm dev:pos`                 | Vite POS terminal       |
| `pnpm infra:up` / `infra:down` | Docker services         |
| `pnpm api:dev`                 | Spring Boot             |
| `pnpm api:openapi`             | Fetch OpenAPI JSON      |
| `pnpm build`                   | Build all packages/apps |

## Auth

Both frontends store `accessToken` + `refreshToken` in `localStorage` via `@possaas/api-client` and refresh on `401`.

## Production deploy (Ubuntu VPS)

1. Copy `.env.example` → `/opt/possaas/.env` and set strong secrets.
2. Place TLS certs in `infra/nginx/certs/fullchain.pem` + `privkey.pem`.
3. On first boot: `docker compose --env-file .env -f infra/docker/compose.prod.yml up -d`
4. Pushing to `main` runs [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml): builds API + dashboard images to GHCR, then SSHes to the VPS and rolls them.

Daily Postgres dumps land in MinIO under `s3://$S3_BUCKET/backups/` via `infra/scripts/backup-postgres.sh`.

## Observability

Actuator already exposes Prometheus metrics at `/internal/actuator/prometheus`.

Local overlay:

```bash
pnpm infra:obs
```

- Prometheus → http://localhost:9090
- Grafana → http://localhost:3001 (admin / admin)

Production stack includes Prometheus + Grafana in `compose.prod.yml` with the API overview dashboard under `infra/observability/grafana/dashboards/`.

## Gradle wrapper

`gradlew` / `gradlew.bat` are checked in. If `gradle/wrapper/gradle-wrapper.jar` is missing on a fresh machine, generate it with a local JDK 21 install:

```bash
gradle wrapper --gradle-version 8.12
```
