import { FormEvent, useState } from 'react';
import { ApiError, type User } from '@possaas/api-client';
import { Button, Card, CardContent, CardHeader, CardTitle, Input, Label, toast } from '@possaas/ui';
import { api } from '../lib/api';

export function LoginGate({ onAuthed }: { onAuthed: (user: User) => void }) {
  const [loading, setLoading] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setLoading(true);
    try {
      const tokens = await api.auth.login({
        emailOrUsername: String(form.get('email') ?? ''),
        password: String(form.get('password') ?? ''),
        tenantSlug: String(form.get('tenantSlug') ?? '')
          .trim()
          .toLowerCase(),
        deviceLabel: 'POS Terminal',
      });
      onAuthed(tokens.user);
      toast({ title: 'Terminal unlocked', variant: 'success' });
    } catch (err) {
      toast({
        title: 'Login failed',
        description: err instanceof ApiError ? err.message : 'Check credentials',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="bg-navy flex h-screen items-center justify-center px-4">
      <Card className="w-full max-w-md">
        <CardHeader>
          <p className="text-primary text-xs font-semibold uppercase tracking-[0.18em]">Easy POS</p>
          <CardTitle>Terminal sign-in</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="space-y-4" onSubmit={onSubmit}>
            <div className="space-y-2">
              <Label htmlFor="email">Email</Label>
              <Input id="email" name="email" required autoFocus />
            </div>
            <div className="space-y-2">
              <Label htmlFor="password">Password</Label>
              <Input id="password" name="password" type="password" required />
            </div>
            <div className="space-y-2">
              <Label htmlFor="tenantSlug">Shop code</Label>
              <Input
                id="tenantSlug"
                name="tenantSlug"
                required
                defaultValue={new URLSearchParams(window.location.search).get('shop') ?? ''}
              />
            </div>
            <Button className="w-full" disabled={loading}>
              {loading ? 'Signing in…' : 'Open terminal'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
