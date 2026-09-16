'use client';

import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { api } from '@/lib/api';
import type { User } from '@possaas/api-client';

export function useRequireAuth() {
  const router = useRouter();
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    async function run() {
      const token = api.tokens.getAccessToken();
      if (!token) {
        router.replace('/login');
        return;
      }

      const cached = api.auth.getStoredUser();
      if (cached && !cancelled) setUser(cached);

      try {
        const me = await api.auth.me();
        if (!cancelled) {
          api.tokens.setUserJson(JSON.stringify(me));
          setUser(me);
        }
      } catch {
        if (!cancelled) {
          api.tokens.clear();
          router.replace('/login');
        }
      } finally {
        if (!cancelled) setLoading(false);
      }
    }

    void run();
    return () => {
      cancelled = true;
    };
  }, [router]);

  return { user, loading };
}
