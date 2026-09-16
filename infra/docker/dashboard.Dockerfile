# syntax=docker/dockerfile:1.7

FROM node:22-alpine AS deps
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
COPY package.json pnpm-workspace.yaml pnpm-lock.yaml* ./
COPY packages ./packages
COPY apps/admin-dashboard/package.json ./apps/admin-dashboard/
RUN pnpm install --frozen-lockfile || pnpm install

FROM node:22-alpine AS build
WORKDIR /app
RUN corepack enable && corepack prepare pnpm@9.15.0 --activate
COPY --from=deps /app/node_modules ./node_modules
COPY --from=deps /app/packages ./packages
COPY --from=deps /app/apps/admin-dashboard/node_modules ./apps/admin-dashboard/node_modules
COPY . .
ARG NEXT_PUBLIC_API_URL=https://api.example.com
ENV NEXT_PUBLIC_API_URL=$NEXT_PUBLIC_API_URL
ENV NEXT_TELEMETRY_DISABLED=1
RUN pnpm --filter @possaas/admin-dashboard build

FROM node:22-alpine AS runtime
WORKDIR /app
ENV NODE_ENV=production
ENV NEXT_TELEMETRY_DISABLED=1
RUN addgroup -S nextjs && adduser -S nextjs -G nextjs
COPY --from=build /app/apps/admin-dashboard/.next/standalone ./
COPY --from=build /app/apps/admin-dashboard/.next/static ./apps/admin-dashboard/.next/static
COPY --from=build /app/apps/admin-dashboard/public ./apps/admin-dashboard/public
USER nextjs
EXPOSE 3000
ENV PORT=3000
ENV HOSTNAME=0.0.0.0
CMD ["node", "apps/admin-dashboard/server.js"]
