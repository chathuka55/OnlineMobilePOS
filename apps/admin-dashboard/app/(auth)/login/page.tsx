'use client';

import Link from 'next/link';
import { useRouter, useSearchParams } from 'next/navigation';
import { FormEvent, Suspense, useEffect, useState } from 'react';
import { Boxes } from 'lucide-react';
import { ApiError } from '@possaas/api-client';
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
  Input,
  Label,
  toast,
} from '@possaas/ui';
import { api } from '@/lib/api';

export default function LoginPage() {
  return (
    <Suspense fallback={null}>
      <LoginForm />
    </Suspense>
  );
}

function LoginForm() {
  const router = useRouter();
  const params = useSearchParams();
  const linkedShop = (params.get('shop') ?? '').trim().toLowerCase();
  const [loading, setLoading] = useState(false);
  const [shopName, setShopName] = useState<string | null>(null);
  const [shopMissing, setShopMissing] = useState(false);

  useEffect(() => {
    if (!linkedShop) return;
    let cancelled = false;
    api.auth
      .shopInfo(linkedShop)
      .then((info) => !cancelled && setShopName(info.businessName))
      .catch(() => !cancelled && setShopMissing(true));
    return () => {
      cancelled = true;
    };
  }, [linkedShop]);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setLoading(true);
    try {
      await api.auth.login({
        emailOrUsername: String(form.get('email') ?? ''),
        password: String(form.get('password') ?? ''),
        tenantSlug: linkedShop || String(form.get('tenantSlug') ?? '').trim().toLowerCase(),
      });
      toast({ title: 'Welcome back', description: 'Signed in successfully.', variant: 'success' });
      router.replace('/dashboard');
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to sign in';
      toast({ title: 'Login failed', description: message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="pos-mesh relative flex min-h-screen items-center justify-center px-4">
      <div className="from-navy/90 pointer-events-none absolute inset-x-0 top-0 h-72 bg-gradient-to-b to-transparent" />
      <Card className="animate-fade-up border-border/70 relative z-10 w-full max-w-md shadow-xl">
        <CardHeader className="space-y-3">
          <div className="flex items-center gap-3">
            <div className="bg-navy text-primary flex h-11 w-11 items-center justify-center rounded-xl">
              <Boxes className="h-5 w-5" />
            </div>
            <div>
              <p className="text-primary text-xs font-semibold uppercase tracking-[0.18em]">
                Easy POS
              </p>
              <CardTitle>{shopName ?? 'Sign in'}</CardTitle>
            </div>
          </div>
          <CardDescription>
            {shopMissing
              ? 'This shop link is not valid. Check the address you were given.'
              : 'Sign in to your shop.'}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="email">Email or username</Label>
              <Input
                id="email"
                name="email"
                type="text"
                autoComplete="username"
                required
                autoFocus
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                name="password"
                type="password"
                autoComplete="current-password"
                required
              />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenantSlug">Shop code</Label>
              <Input
                id="tenantSlug"
                name="tenantSlug"
                placeholder="acme-retail"
                required={!linkedShop}
                defaultValue={linkedShop}
                readOnly={!!linkedShop}
              />
            </div>
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
          <p className="text-muted-foreground mt-4 text-center text-sm">
            New business?{' '}
            <Link href="/signup" className="text-navy hover:text-primary font-semibold">
              Create an account
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
