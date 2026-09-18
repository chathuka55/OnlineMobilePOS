'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { AppMain, AppShell, Spinner } from '@possaas/ui';
import { AppSidebar } from '@/components/app-sidebar';
import { ImpersonationBanner } from '@/components/impersonation-banner';
import { useRequireAuth } from '@/hooks/use-require-auth';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { user, loading } = useRequireAuth();
  const router = useRouter();

  useEffect(() => {
    // A platform admin has no tenant, so every tenant-scoped page here would
    // just be empty/broken for them - send them to their own dashboard instead.
    if (!loading && user?.platformAdmin) {
      router.replace('/platform');
    }
  }, [loading, user, router]);

  if (loading || user?.platformAdmin) {
    return (
      <div className="pos-mesh flex min-h-screen items-center justify-center">
        <Spinner size="lg" />
      </div>
    );
  }

  return (
    <AppShell>
      <AppSidebar />
      <AppMain>
        <ImpersonationBanner />
        <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </AppMain>
    </AppShell>
  );
}
