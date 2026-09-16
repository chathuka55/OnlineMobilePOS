'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Spinner } from '@possaas/ui';
import { api } from '@/lib/api';

export default function HomePage() {
  const router = useRouter();

  useEffect(() => {
    const token = api.tokens.getAccessToken();
    router.replace(token ? '/dashboard' : '/login');
  }, [router]);

  return (
    <div className="pos-mesh flex min-h-screen items-center justify-center">
      <Spinner size="lg" />
    </div>
  );
}
