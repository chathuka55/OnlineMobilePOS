import { createPosApiClient } from '@possaas/api-client';

export const api = createPosApiClient({
  baseUrl: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  storageMode: 'localStorage',
});

export function money(value: number | string | null | undefined, currency = 'LKR') {
  const n = typeof value === 'string' ? Number(value) : (value ?? 0);
  return new Intl.NumberFormat(undefined, {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
  }).format(Number.isFinite(n) ? n : 0);
}
