'use client';

import { AppMain, AppShell, Spinner } from '@possaas/ui';
import { AppSidebar } from '@/components/app-sidebar';
import { useRequireAuth } from '@/hooks/use-require-auth';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const { loading } = useRequireAuth();

  if (loading) {
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
        <div className="mx-auto w-full max-w-7xl px-4 py-6 sm:px-6 lg:px-8">{children}</div>
      </AppMain>
    </AppShell>
  );
}
