'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AppMain, AppShell, Spinner } from '@possaas/ui';
import { PlatformSidebar } from '@/components/platform-sidebar';
import { useRequireAuth } from '@/hooks/use-require-auth';

export default function PlatformLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useRequireAuth();
  const router = useRouter();

  useEffect(() => {
    if (!loading && user && !user.platformAdmin) {
      router.replace('/dashboard');
    }
  }, [loading, user, router]);

  if (loading || !user?.platformAdmin) {
    return (
      <div className="pos-mesh flex min-h-screen items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <AppShell>
      <PlatformSidebar />
      <AppMain>
        <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </AppMain>
    </AppShell>
  );
}
