'use client';

import { useState, useEffect, useRef } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  CardContent,
  CardDescription,
  CardFooter,
  CardHeader,
  CardTitle,
  Input,
  Label,
  Tabs,
  TabsContent,
  TabsList,
  TabsTrigger,
  Spinner,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from '@possaas/ui';
import { PageHeader } from '@/components/page-header';
import { api, ApiError } from '@/lib/api';

type Outlet = {
  id: string;
  name: string;
  defaultOutlet: boolean;
  addressLine1: string | null;
  addressLine2: string | null;
  city: string | null;
  phonePrimary: string | null;
  email: string | null;
  receiptFooter: string | null;
  logoDataUrl: string | null;
};

type Tenant = {
  businessName: string;
  legalName: string | null;
  taxIdentifier: string | null;
  contactPhone: string | null;
  defaultCurrency: string;
};

export default function SettingsPage() {
  const queryClient = useQueryClient();
  const logoInputRef = useRef<HTMLInputElement>(null);

  const { data: tenant, isLoading: loadingTenant } = useQuery({
    queryKey: ['tenant'],
    queryFn: () => api.get<Tenant>('/api/v1/tenant'),
  });

  const { data: outlets, isLoading: loadingOutlets } = useQuery({
    queryKey: ['outlets'],
    queryFn: () => api.get<Outlet[]>('/api/v1/outlets'),
  });

  const outlet = outlets?.find((o) => o.defaultOutlet) ?? outlets?.[0];

  const [shopData, setShopData] = useState({
    businessName: '',
    taxIdentifier: '',
    phone: '',
    email: '',
    addressLine1: '',
    city: '',
  });

  const [receiptData, setReceiptData] = useState({
    receiptFooter: '',
    currency: 'LKR',
    logoDataUrl: null as string | null,
    logoLayout: 'SIDE' as 'SIDE' | 'CENTERED',
  });

  const { data: generalSettings } = useQuery({
    queryKey: ['settings', 'general'],
    queryFn: () => api.get<Record<string, string>>('/api/v1/settings'),
  });

  const [securityData, setSecurityData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  const [pinData, setPinData] = useState({ password: '', pin: '' });
  const [exporting, setExporting] = useState(false);

  useEffect(() => {
    if (tenant) {
      setShopData((prev) => ({
        ...prev,
        businessName: tenant.businessName || '',
        taxIdentifier: tenant.taxIdentifier || '',
        phone: tenant.contactPhone || '',
      }));
      setReceiptData((prev) => ({ ...prev, currency: tenant.defaultCurrency || 'LKR' }));
    }
  }, [tenant]);

  useEffect(() => {
    if (outlet) {
      setShopData((prev) => ({
        ...prev,
        email: outlet.email || '',
        addressLine1: outlet.addressLine1 || '',
        city: outlet.city || '',
      }));
      setReceiptData((prev) => ({
        ...prev,
        receiptFooter: outlet.receiptFooter || '',
        logoDataUrl: outlet.logoDataUrl,
      }));
    }
  }, [outlet]);

  useEffect(() => {
    if (generalSettings) {
      const layout = generalSettings['print.receipt.logoLayout'];
      setReceiptData((prev) => ({
        ...prev,
        logoLayout: layout === 'CENTERED' ? 'CENTERED' : 'SIDE',
      }));
    }
  }, [generalSettings]);

  const loading = loadingTenant || loadingOutlets;

  const updateShopMutation = useMutation({
    mutationFn: async () => {
      await api.put('/api/v1/tenant', {
        businessName: shopData.businessName,
        taxIdentifier: shopData.taxIdentifier,
        contactPhone: shopData.phone,
      });
      if (outlet) {
        await api.put(`/api/v1/outlets/${outlet.id}`, {
          name: outlet.name,
          addressLine1: shopData.addressLine1,
          addressLine2: outlet.addressLine2,
          city: shopData.city,
          phonePrimary: outlet.phonePrimary,
          email: shopData.email,
          receiptFooter: outlet.receiptFooter,
          logoDataUrl: outlet.logoDataUrl,
        });
      }
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant'] });
      queryClient.invalidateQueries({ queryKey: ['outlets'] });
      toast({ title: 'Shop profile updated' });
    },
    onError: (err) => {
      toast({
        title: 'Error updating shop profile',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const updateReceiptMutation = useMutation({
    mutationFn: async () => {
      await api.put('/api/v1/tenant', {
        businessName: shopData.businessName,
        taxIdentifier: shopData.taxIdentifier,
        contactPhone: shopData.phone,
        defaultCurrency: receiptData.currency,
      });
      if (outlet) {
        await api.put(`/api/v1/outlets/${outlet.id}`, {
          name: outlet.name,
          addressLine1: outlet.addressLine1,
          addressLine2: outlet.addressLine2,
          city: outlet.city,
          phonePrimary: outlet.phonePrimary,
          email: outlet.email,
          receiptFooter: receiptData.receiptFooter,
          logoDataUrl: receiptData.logoDataUrl,
        });
      }
      await api.put('/api/v1/settings', {
        'print.receipt.logoLayout': receiptData.logoLayout,
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant'] });
      queryClient.invalidateQueries({ queryKey: ['outlets'] });
      queryClient.invalidateQueries({ queryKey: ['settings', 'general'] });
      toast({ title: 'Receipt settings updated' });
    },
    onError: (err) => {
      toast({
        title: 'Error updating receipt settings',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const changePasswordMutation = useMutation({
    mutationFn: () =>
      api.post('/api/v1/users/me/password', {
        currentPassword: securityData.currentPassword,
        newPassword: securityData.newPassword,
      }),
    onSuccess: () => {
      setSecurityData({ currentPassword: '', newPassword: '', confirmPassword: '' });
      toast({ title: 'Password changed successfully' });
    },
    onError: (err) => {
      toast({
        title: 'Error changing password',
        description: err instanceof ApiError ? err.message : 'Incorrect current password',
        variant: 'destructive',
      });
    },
  });

  const setPinMutation = useMutation({
    mutationFn: () => api.auth.setPin(pinData),
    onSuccess: () => {
      toast({ title: pinData.pin ? 'Till PIN saved' : 'Till PIN disabled' });
      setPinData({ password: '', pin: '' });
    },
    onError: (err) => {
      toast({
        title: 'Error setting PIN',
        description: err instanceof ApiError ? err.message : 'Incorrect password',
        variant: 'destructive',
      });
    },
  });

  const handleSaveShop = () => updateShopMutation.mutate();
  const handleSaveReceipt = () => updateReceiptMutation.mutate();

  const handleSaveSecurity = () => {
    if (securityData.newPassword !== securityData.confirmPassword) {
      toast({ title: 'Passwords do not match', variant: 'destructive' });
      return;
    }
    changePasswordMutation.mutate();
  };

  const handleLogoFile = (file: File) => {
    if (file.size > 250_000) {
      toast({
        title: 'Image too large',
        description: 'Please use a logo under 250 KB.',
        variant: 'destructive',
      });
      return;
    }
    const reader = new FileReader();
    reader.onload = () => {
      setReceiptData((prev) => ({ ...prev, logoDataUrl: reader.result as string }));
    };
    reader.readAsDataURL(file);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="Tenant, outlet, tax, and receipt preferences." />

      {loading ? (
        <Spinner />
      ) : (
        <Tabs defaultValue="shop" className="space-y-4">
          <TabsList>
            <TabsTrigger value="shop">Shop Profile</TabsTrigger>
            <TabsTrigger value="receipt">Receipt</TabsTrigger>
            <TabsTrigger value="security">Security</TabsTrigger>
          </TabsList>

          <TabsContent value="shop">
            <Card>
              <CardHeader>
                <CardTitle>Shop Profile</CardTitle>
                <CardDescription>Basic information about your business.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="grid gap-4 md:grid-cols-2">
                  <div className="space-y-2">
                    <Label>Business Name</Label>
                    <Input
                      value={shopData.businessName}
                      onChange={(e) => setShopData({ ...shopData, businessName: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Phone</Label>
                    <Input
                      value={shopData.phone}
                      onChange={(e) => setShopData({ ...shopData, phone: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Email</Label>
                    <Input
                      type="email"
                      value={shopData.email}
                      onChange={(e) => setShopData({ ...shopData, email: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Address Line 1</Label>
                    <Input
                      value={shopData.addressLine1}
                      onChange={(e) => setShopData({ ...shopData, addressLine1: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>City</Label>
                    <Input
                      value={shopData.city}
                      onChange={(e) => setShopData({ ...shopData, city: e.target.value })}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>Tax Identifier</Label>
                    <Input
                      value={shopData.taxIdentifier}
                      onChange={(e) => setShopData({ ...shopData, taxIdentifier: e.target.value })}
                    />
                  </div>
                </div>
              </CardContent>
              <CardFooter>
                <Button onClick={handleSaveShop} disabled={updateShopMutation.isPending}>
                  {updateShopMutation.isPending ? <Spinner className="mr-2" /> : null}
                  Save Changes
                </Button>
              </CardFooter>
            </Card>
          </TabsContent>

          <TabsContent value="receipt">
            <Card>
              <CardHeader>
                <CardTitle>Receipt Settings</CardTitle>
                <CardDescription>Configure how your receipts look.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="max-w-md space-y-2">
                  <Label>Currency</Label>
                  <Select
                    value={receiptData.currency}
                    onValueChange={(val) => setReceiptData({ ...receiptData, currency: val })}
                  >
                    <SelectTrigger>
                      <SelectValue placeholder="Select currency" />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="LKR">LKR (Sri Lankan Rupee)</SelectItem>
                      <SelectItem value="USD">USD (US Dollar)</SelectItem>
                      <SelectItem value="EUR">EUR (Euro)</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-2">
                  <Label>Receipt Footer Message</Label>
                  <Input
                    value={receiptData.receiptFooter}
                    onChange={(e) =>
                      setReceiptData({ ...receiptData, receiptFooter: e.target.value })
                    }
                    placeholder="Thank you for your business!"
                  />
                </div>
                <div className="space-y-2">
                  <Label>Shop Logo</Label>
                  <div className="flex items-center gap-4">
                    {receiptData.logoDataUrl ? (
                      <img
                        src={receiptData.logoDataUrl}
                        alt="Shop logo"
                        className="h-16 w-16 rounded border object-contain"
                      />
                    ) : (
                      <div className="text-muted-foreground flex h-16 w-16 items-center justify-center rounded border text-xs">
                        No logo
                      </div>
                    )}
                    <input
                      ref={logoInputRef}
                      type="file"
                      accept="image/png,image/jpeg,image/webp"
                      className="hidden"
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) handleLogoFile(file);
                        e.target.value = '';
                      }}
                    />
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => logoInputRef.current?.click()}
                    >
                      Upload Logo
                    </Button>
                    {receiptData.logoDataUrl ? (
                      <Button
                        type="button"
                        variant="ghost"
                        onClick={() => setReceiptData({ ...receiptData, logoDataUrl: null })}
                      >
                        Remove
                      </Button>
                    ) : null}
                  </div>
                  <p className="text-muted-foreground text-xs">
                    Printed on receipts and invoice PDFs (80mm, half A4, and A4). Under 250 KB.
                  </p>
                </div>
                {receiptData.logoDataUrl ? (
                  <div className="space-y-2">
                    <Label>Logo Position</Label>
                    <Select
                      value={receiptData.logoLayout}
                      onValueChange={(v) =>
                        setReceiptData({ ...receiptData, logoLayout: v as 'SIDE' | 'CENTERED' })
                      }
                    >
                      <SelectTrigger className="max-w-xs">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="SIDE">Side (logo right of shop details)</SelectItem>
                        <SelectItem value="CENTERED">Centered (logo above shop details)</SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-muted-foreground text-xs">
                      With no logo uploaded, shop details are always centered.
                    </p>
                  </div>
                ) : null}
              </CardContent>
              <CardFooter>
                <Button onClick={handleSaveReceipt} disabled={updateReceiptMutation.isPending}>
                  {updateReceiptMutation.isPending ? <Spinner className="mr-2" /> : null}
                  Save Receipt Settings
                </Button>
              </CardFooter>
            </Card>
          </TabsContent>

          <TabsContent value="security">
            <Card>
              <CardHeader>
                <CardTitle>Data Backup</CardTitle>
                <CardDescription>
                  Download a full backup of your shop&apos;s data (items, customers, bills,
                  repairs, wholesale invoices and more) as a JSON file.
                </CardDescription>
              </CardHeader>
              <CardContent>
                <Button
                  variant="outline"
                  disabled={exporting}
                  onClick={async () => {
                    setExporting(true);
                    try {
                      const res = await fetch(`${api.baseUrl}/api/v1/data-export`, {
                        headers: { Authorization: `Bearer ${api.tokens.getAccessToken()}` },
                      });
                      if (!res.ok) throw new Error('Export failed');
                      const blob = await res.blob();
                      const url = URL.createObjectURL(blob);
                      const a = document.createElement('a');
                      a.href = url;
                      a.download = `shop-data-export-${new Date().toISOString().slice(0, 10)}.json`;
                      a.click();
                      URL.revokeObjectURL(url);
                    } catch {
                      toast({ title: 'Could not download backup', variant: 'destructive' });
                    } finally {
                      setExporting(false);
                    }
                  }}
                >
                  {exporting ? <Spinner className="mr-2" /> : null}
                  Download Backup
                </Button>
              </CardContent>
            </Card>

            <Card className="mt-6">
              <CardHeader>
                <CardTitle>Change Password</CardTitle>
                <CardDescription>Update your account password.</CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="max-w-sm space-y-2">
                  <Label>Current Password</Label>
                  <Input
                    type="password"
                    value={securityData.currentPassword}
                    onChange={(e) =>
                      setSecurityData({ ...securityData, currentPassword: e.target.value })
                    }
                  />
                </div>
                <div className="max-w-sm space-y-2">
                  <Label>New Password</Label>
                  <Input
                    type="password"
                    value={securityData.newPassword}
                    onChange={(e) =>
                      setSecurityData({ ...securityData, newPassword: e.target.value })
                    }
                  />
                </div>
                <div className="max-w-sm space-y-2">
                  <Label>Confirm New Password</Label>
                  <Input
                    type="password"
                    value={securityData.confirmPassword}
                    onChange={(e) =>
                      setSecurityData({ ...securityData, confirmPassword: e.target.value })
                    }
                  />
                </div>
              </CardContent>
              <CardFooter>
                <Button onClick={handleSaveSecurity} disabled={changePasswordMutation.isPending}>
                  {changePasswordMutation.isPending ? <Spinner className="mr-2" /> : null}
                  Change Password
                </Button>
              </CardFooter>
            </Card>

            <Card className="mt-6">
              <CardHeader>
                <CardTitle>Till Unlock PIN</CardTitle>
                <CardDescription>
                  A short PIN cashiers enter to resume a locked POS terminal, instead of the
                  full password. Leave the PIN field empty to disable it.
                </CardDescription>
              </CardHeader>
              <CardContent className="space-y-4">
                <div className="max-w-sm space-y-2">
                  <Label>Your Account Password</Label>
                  <Input
                    type="password"
                    value={pinData.password}
                    onChange={(e) => setPinData({ ...pinData, password: e.target.value })}
                    placeholder="Confirm it's you"
                  />
                </div>
                <div className="max-w-sm space-y-2">
                  <Label>New PIN (4 digits)</Label>
                  <Input
                    type="password"
                    maxLength={4}
                    value={pinData.pin}
                    onChange={(e) =>
                      setPinData({ ...pinData, pin: e.target.value.replace(/\D/g, '') })
                    }
                    placeholder="Leave empty to disable"
                    className="text-center text-xl tracking-[1em]"
                  />
                </div>
              </CardContent>
              <CardFooter>
                <Button
                  onClick={() => {
                    if (pinData.pin.length > 0 && pinData.pin.length !== 4) {
                      toast({ title: 'PIN must be 4 digits', variant: 'destructive' });
                      return;
                    }
                    if (!pinData.password) {
                      toast({ title: 'Enter your password to confirm', variant: 'destructive' });
                      return;
                    }
                    setPinMutation.mutate();
                  }}
                  disabled={setPinMutation.isPending}
                >
                  {setPinMutation.isPending ? <Spinner className="mr-2" /> : null}
                  Save PIN
                </Button>
              </CardFooter>
            </Card>
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}
