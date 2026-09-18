'use client';

import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';
import { ShieldCheck } from 'lucide-react';
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

export default function PlatformLoginPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setLoading(true);
    try {
      await api.auth.platformLogin({
        emailOrUsername: String(form.get('email') ?? ''),
        password: String(form.get('password') ?? ''),
      });
      router.replace('/platform');
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to sign in';
      toast({ title: 'Sign-in failed', description: message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="pos-mesh relative flex min-h-screen items-center justify-center px-4">
      <Card className="animate-fade-up relative z-10 w-full max-w-md shadow-xl">
        <CardHeader className="space-y-3">
          <div className="flex items-center gap-3">
            <div className="bg-navy text-primary flex h-11 w-11 items-center justify-center rounded-xl">
              <ShieldCheck className="h-5 w-5" />
            </div>
            <div>
              <p className="text-primary text-xs font-semibold uppercase tracking-[0.18em]">
                Easy POS
              </p>
              <CardTitle>Service provider sign in</CardTitle>
            </div>
          </div>
          <CardDescription>Platform administrators only.</CardDescription>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" name="email" type="text" autoComplete="username" required autoFocus />
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
            <Button type="submit" className="w-full" disabled={loading}>
              {loading ? 'Signing in…' : 'Sign in'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
