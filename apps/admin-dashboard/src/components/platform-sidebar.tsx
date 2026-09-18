'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import {
  Badge,
  Button,
  SidebarBrand,
  SidebarFooter,
  SidebarNav,
  SidebarShell,
  cn,
} from '@possaas/ui';
import { LayoutDashboard, LogOut, ShieldCheck, Store, Tag } from 'lucide-react';
import { api } from '@/lib/api';

const nav = [
  { href: '/platform', label: 'Overview', icon: LayoutDashboard },
  { href: '/platform/shops', label: 'Shops', icon: Store },
  { href: '/platform/plans', label: 'Plans', icon: Tag },
];

export function PlatformSidebar() {
  const pathname = usePathname();
  const router = useRouter();
  const user = api.auth.getStoredUser();

  async function logout() {
    try {
      await api.auth.logout();
    } catch {
      api.tokens.clear();
    }
    router.replace('/login');
  }

  return (
    <SidebarShell className="sticky top-0 hidden h-screen lg:flex">
      <SidebarBrand>
        <div className="bg-primary text-primary-foreground flex h-10 w-10 items-center justify-center rounded-xl shadow-sm">
          <ShieldCheck className="h-5 w-5" />
        </div>
        <div>
          <p className="text-base font-bold tracking-tight text-white">Easy POS</p>
          <p className="text-xs text-white/55">Service Provider</p>
        </div>
      </SidebarBrand>

      <SidebarNav>
        {nav.map((item) => {
          const Icon = item.icon;
          const active = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={cn(
                'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
                active
                  ? 'bg-[hsl(var(--sidebar-accent)/0.18)] text-[hsl(var(--sidebar-accent))]'
                  : 'text-sidebar-foreground/75 hover:text-sidebar-foreground hover:bg-[hsl(var(--sidebar-muted))]',
              )}
            >
              <Icon className="h-4 w-4" />
              <span>{item.label}</span>
            </Link>
          );
        })}
      </SidebarNav>

      <SidebarFooter>
        <div className="mb-3 rounded-lg bg-[hsl(var(--sidebar-muted))] px-3 py-2.5">
          <p className="truncate text-sm font-medium text-white">{user?.fullName ?? 'Admin'}</p>
          <p className="truncate text-xs text-white/50">{user?.email ?? '—'}</p>
          <Badge variant="secondary" className="mt-2 bg-white/10 text-white/80">
            Platform Admin
          </Badge>
        </div>
        <Button
          variant="ghost"
          className="w-full justify-start text-white/70 hover:bg-white/10 hover:text-white"
          onClick={logout}
        >
          <LogOut className="h-4 w-4" />
          Sign out
        </Button>
      </SidebarFooter>
    </SidebarShell>
  );
}
