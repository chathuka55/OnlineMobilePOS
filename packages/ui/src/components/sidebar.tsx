import * as React from 'react';
import { cn } from '../lib/cn';

export function SidebarShell({ className, children, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <aside
      className={cn(
        'bg-sidebar text-sidebar-foreground flex h-full w-64 shrink-0 flex-col',
        className,
      )}
      {...props}
    >
      {children}
    </aside>
  );
}

export function SidebarBrand({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn('border-sidebar-border flex items-center gap-3 border-b px-5 py-5', className)}
      {...props}
    >
      {children}
    </div>
  );
}

export function SidebarNav({ className, children, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <nav className={cn('flex-1 space-y-1 overflow-y-auto px-3 py-4', className)} {...props}>
      {children}
    </nav>
  );
}

export interface SidebarNavItemProps extends React.AnchorHTMLAttributes<HTMLAnchorElement> {
  active?: boolean;
  icon?: React.ReactNode;
  asChild?: boolean;
}

export function SidebarNavItem({
  className,
  active,
  icon,
  children,
  ...props
}: SidebarNavItemProps) {
  return (
    <a
      className={cn(
        'flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-colors',
        active
          ? 'bg-[hsl(var(--sidebar-accent)/0.18)] text-[hsl(var(--sidebar-accent))]'
          : 'text-sidebar-foreground/75 hover:text-sidebar-foreground hover:bg-[hsl(var(--sidebar-muted))]',
        className,
      )}
      {...props}
    >
      {icon ? <span className="[&_svg]:h-4 [&_svg]:w-4">{icon}</span> : null}
      <span>{children}</span>
    </a>
  );
}

export function SidebarFooter({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('border-sidebar-border border-t p-4', className)} {...props}>
      {children}
    </div>
  );
}

export function AppShell({ className, children, ...props }: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={cn('bg-background flex min-h-screen w-full', className)} {...props}>
      {children}
    </div>
  );
}

export function AppMain({ className, children, ...props }: React.HTMLAttributes<HTMLElement>) {
  return (
    <main className={cn('pos-mesh flex min-h-screen flex-1 flex-col', className)} {...props}>
      {children}
    </main>
  );
}
