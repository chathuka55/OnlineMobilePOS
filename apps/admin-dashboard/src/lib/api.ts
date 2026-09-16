'use client';

import { createPosApiClient } from '@possaas/api-client';

export { ApiError } from '@possaas/api-client';

const baseUrl = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080';

export const api = createPosApiClient({
  baseUrl,
  storageMode: 'localStorage',
  onUnauthorized: () => {
    if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
      window.location.href = '/login';
    }
  },
});

export function asList<T>(data: { content: T[] } | T[] | undefined | null): T[] {
  if (!data) return [];
  if (Array.isArray(data)) return data;
  return data.content ?? [];
}

export function money(value: number | string | null | undefined, currency = 'LKR') {
  const n = typeof value === 'string' ? Number(value) : (value ?? 0);
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
}
