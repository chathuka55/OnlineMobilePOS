'use client';

import { useState, useEffect } from 'react';
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

export default function SettingsPage() {
  const queryClient = useQueryClient();

  const { data: shopSettings, isLoading: loadingShop } = useQuery({
    queryKey: ['settings', 'shop'],
    queryFn: () => api.get<any>('/api/v1/settings/shop'),
  });

  const [shopData, setShopData] = useState({
    businessName: '',
    tagline: '',
    phone: '',
    email: '',
    addressLine1: '',
    city: '',
    taxIdentifier: '',
  });

  const [receiptData, setReceiptData] = useState({
    receiptFooter: '',
    showTaxBreakdown: false,
    currency: 'LKR',
  });

  const [securityData, setSecurityData] = useState({
    currentPassword: '',
    newPassword: '',
    confirmPassword: '',
  });

  useEffect(() => {
    if (shopSettings) {
      setShopData({
        businessName: shopSettings.businessName || '',
        tagline: shopSettings.tagline || '',
        phone: shopSettings.phone || '',
        email: shopSettings.email || '',
        addressLine1: shopSettings.addressLine1 || '',
        city: shopSettings.city || '',
        taxIdentifier: shopSettings.taxIdentifier || '',
      });
      setReceiptData({
        receiptFooter: shopSettings.receiptFooter || '',
        showTaxBreakdown: shopSettings.showTaxBreakdown || false,
        currency: shopSettings.currency || 'LKR',
      });
    }
  }, [shopSettings]);

  const updateShopMutation = useMutation({
    mutationFn: (data: any) => api.put('/api/v1/settings/shop', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'shop'] });
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
    mutationFn: (data: any) => api.put('/api/v1/settings/receipt', data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['settings', 'shop'] });
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
    mutationFn: (data: any) =>
      api.post('/api/v1/auth/change-password', {
        currentPassword: data.currentPassword,
        newPassword: data.newPassword,
      }),
    onSuccess: () => {
      setSecurityData({ currentPassword: '', newPassword: '', confirmPassword: '' });
      toast({ title: 'Password changed successfully' });
    },
    onError: (err) => {
      toast({
        title: 'Error changing password',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const handleSaveShop = () => updateShopMutation.mutate(shopData);
  const handleSaveReceipt = () => updateReceiptMutation.mutate(receiptData);

  const handleSaveSecurity = () => {
    if (securityData.newPassword !== securityData.confirmPassword) {
      toast({ title: 'Passwords do not match', variant: 'destructive' });
      return;
    }
    changePasswordMutation.mutate(securityData);
  };

  return (
    <div className="space-y-6">
      <PageHeader title="Settings" description="Tenant, outlet, tax, and receipt preferences." />

      {loadingShop ? (
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
                    <Label>Tagline</Label>
                    <Input
                      value={shopData.tagline}
                      onChange={(e) => setShopData({ ...shopData, tagline: e.target.value })}
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
                <div className="flex items-center space-x-2 pt-2">
                  <input
                    type="checkbox"
                    id="showTax"
                    checked={receiptData.showTaxBreakdown}
                    onChange={(e) =>
                      setReceiptData({ ...receiptData, showTaxBreakdown: e.target.checked })
                    }
                    className="rounded border-gray-300"
                  />
                  <Label htmlFor="showTax">Show tax breakdown on receipt</Label>
                </div>
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
                <CardTitle>Security</CardTitle>
                <CardDescription>Manage your account security.</CardDescription>
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
          </TabsContent>
        </Tabs>
      )}
    </div>
  );
}
