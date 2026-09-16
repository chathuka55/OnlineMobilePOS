'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FormEvent, useState } from 'react';
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

export default function SignupPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setLoading(true);
    try {
      await api.auth.signup({
        businessName: String(form.get('businessName') ?? ''),
        contactEmail: String(form.get('email') ?? ''),
        password: String(form.get('password') ?? ''),
        fullName: String(form.get('fullName') ?? ''),
        slug: String(form.get('slug') ?? '') || undefined,
        phone: String(form.get('phone') ?? '') || undefined,
      });
      toast({
        title: 'Workspace ready',
        description: 'Your Easy POS tenant was created.',
        variant: 'success',
      });
      router.replace('/dashboard');
    } catch (err) {
      const message = err instanceof ApiError ? err.message : 'Unable to create account';
      toast({ title: 'Signup failed', description: message, variant: 'destructive' });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="pos-mesh relative flex min-h-screen items-center justify-center px-4 py-10">
      <div className="from-navy/90 pointer-events-none absolute inset-x-0 top-0 h-72 bg-gradient-to-b to-transparent" />
      <Card className="animate-fade-up border-border/70 relative z-10 w-full max-w-lg shadow-xl">
        <CardHeader className="space-y-3">
          <div className="flex items-center gap-3">
            <div className="bg-navy text-primary flex h-11 w-11 items-center justify-center rounded-xl">
              <Boxes className="h-5 w-5" />
            </div>
            <div>
              <p className="text-primary text-xs font-semibold uppercase tracking-[0.18em]">
                Easy POS
              </p>
              <CardTitle>Create your workspace</CardTitle>
            </div>
          </div>
          <CardDescription>
            Spin up a tenant, admin user, and default outlet in minutes.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form className="grid gap-4 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="businessName">Business name</Label>
              <Input id="businessName" name="businessName" required autoFocus />
            </div>
            <div className="space-y-2">
              <Label htmlFor="fullName">Your name</Label>
              <Input id="fullName" name="fullName" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="phone">Phone</Label>
              <Input id="phone" name="phone" />
            </div>
            <div className="space-y-2 sm:col-span-2">
              <Label htmlFor="email">Work email</Label>
              <Input id="email" name="email" type="email" autoComplete="email" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="slug">Tenant slug</Label>
              <Input id="slug" name="slug" placeholder="acme-retail" />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input id="password" name="password" type="password" minLength={8} required />
            </div>
            <div className="sm:col-span-2">
              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? 'Creating…' : 'Create account'}
              </Button>
            </div>
          </form>
          <p className="text-muted-foreground mt-4 text-center text-sm">
            Already have access?{' '}
            <Link href="/login" className="text-navy hover:text-primary font-semibold">
              Sign in
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
