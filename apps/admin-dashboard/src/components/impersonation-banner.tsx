'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button } from '@possaas/ui';
import { api } from '@/lib/api';

export const IMPERSONATOR_KEY = 'possaas.impersonator';

export function stashPlatformSession() {
  try {
    sessionStorage.setItem(
      IMPERSONATOR_KEY,
      JSON.stringify({
        access: api.tokens.getAccessToken(),
        refresh: api.tokens.getRefreshToken(),
        user: api.tokens.getUserJson(),
      }),
    );
  } catch {
    /* sessionStorage unavailable */
  }
}

export function ImpersonationBanner() {
  const router = useRouter();
  const [active, setActive] = useState(false);

  useEffect(() => {
    try {
      setActive(!!sessionStorage.getItem(IMPERSONATOR_KEY));
    } catch {
      setActive(false);
    }
  }, []);

  if (!active) return null;

  function exit() {
    try {
      const saved = JSON.parse(sessionStorage.getItem(IMPERSONATOR_KEY) ?? 'null');
      sessionStorage.removeItem(IMPERSONATOR_KEY);
      if (saved?.access && saved?.refresh) {
        api.tokens.setTokens(saved.access, saved.refresh);
        api.tokens.setUserJson(saved.user ?? '');
        router.replace('/platform/shops');
        return;
      }
    } catch {
      /* fall through */
    }
    api.tokens.clear();
    router.replace('/platform/login');
  }

  return (
    <div className="flex items-center justify-between gap-3 bg-amber-500 px-4 py-2 text-sm font-medium text-black">
      <span>You are viewing this shop as a service provider.</span>
      <Button size="sm" variant="outline" className="bg-white" onClick={exit}>
        Exit to platform
      </Button>
    </div>
  );
}
