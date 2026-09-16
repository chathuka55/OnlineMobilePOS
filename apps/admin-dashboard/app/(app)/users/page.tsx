'use client';

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  Button,
  Card,
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  Input,
  Label,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
  Badge,
  Spinner,
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
  toast,
} from '@possaas/ui';
import { PageHeader } from '@/components/page-header';
import { api, asList, ApiError } from '@/lib/api';
import { Edit, UserPlus, Power, PowerOff } from 'lucide-react';

interface StaffUser {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  status: 'ACTIVE' | 'SUSPENDED';
  createdAt: string;
}

export default function UsersPage() {
  const queryClient = useQueryClient();
  const [isDialogOpen, setIsDialogOpen] = useState(false);
  const [editingUser, setEditingUser] = useState<StaffUser | null>(null);

  const [formData, setFormData] = useState({
    fullName: '',
    email: '',
    password: '',
    roles: 'CASHIER',
  });

  const { data: users, isLoading } = useQuery({
    queryKey: ['users'],
    queryFn: () => api.get<StaffUser[]>('/api/v1/users', { size: 100 }),
  });

  const createMutation = useMutation({
    mutationFn: (data: any) =>
      api.post<StaffUser>('/api/v1/users', {
        ...data,
        roles: [data.roles],
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setIsDialogOpen(false);
      toast({ title: 'User created successfully' });
    },
    onError: (err) => {
      toast({
        title: 'Error creating user',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const updateMutation = useMutation({
    mutationFn: (data: any) =>
      api.put<StaffUser>(`/api/v1/users/${editingUser?.id}`, {
        fullName: data.fullName,
        email: data.email,
        roles: [data.roles],
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      setIsDialogOpen(false);
      toast({ title: 'User updated successfully' });
    },
    onError: (err) => {
      toast({
        title: 'Error updating user',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const suspendActivateMutation = useMutation({
    mutationFn: ({ id, action }: { id: string; action: 'suspend' | 'activate' }) =>
      api.post(`/api/v1/users/${id}/${action}`, {}),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['users'] });
      toast({ title: 'User status updated successfully' });
    },
    onError: (err) => {
      toast({
        title: 'Error updating status',
        description: err instanceof ApiError ? err.message : 'Unknown error',
        variant: 'destructive',
      });
    },
  });

  const handleOpenDialog = (user?: StaffUser) => {
    if (user) {
      setEditingUser(user);
      setFormData({
        fullName: user.fullName,
        email: user.email,
        password: '',
        roles: user.roles?.[0] || 'CASHIER',
      });
    } else {
      setEditingUser(null);
      setFormData({ fullName: '', email: '', password: '', roles: 'CASHIER' });
    }
    setIsDialogOpen(true);
  };

  const handleSave = () => {
    if (editingUser) {
      updateMutation.mutate(formData);
    } else {
      createMutation.mutate(formData);
    }
  };

  const toggleStatus = (user: StaffUser) => {
    suspendActivateMutation.mutate({
      id: user.id,
      action: user.status === 'ACTIVE' ? 'suspend' : 'activate',
    });
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Users"
        description="Invite staff, assign roles, and manage access."
        actions={
          <Button onClick={() => handleOpenDialog()}>
            <UserPlus className="mr-2 h-4 w-4" />
            New User
          </Button>
        }
      />

      <Card>
        {isLoading ? (
          <div className="flex justify-center p-8">
            <Spinner />
          </div>
        ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Full Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Roles</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {asList(users).map((user: StaffUser) => (
                <TableRow key={user.id}>
                  <TableCell className="font-medium">{user.fullName}</TableCell>
                  <TableCell>{user.email}</TableCell>
                  <TableCell>
                    <div className="flex flex-wrap gap-1">
                      {user.roles?.map((r) => (
                        <Badge key={r} variant="outline">
                          {r}
                        </Badge>
                      ))}
                    </div>
                  </TableCell>
                  <TableCell>
                    <Badge variant={user.status === 'ACTIVE' ? 'default' : 'destructive'}>
                      {user.status}
                    </Badge>
                  </TableCell>
                  <TableCell>{new Date(user.createdAt).toLocaleDateString()}</TableCell>
                  <TableCell className="space-x-2 text-right">
                    <Button variant="ghost" size="sm" onClick={() => handleOpenDialog(user)}>
                      <Edit className="h-4 w-4" />
                    </Button>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => toggleStatus(user)}
                      title={user.status === 'ACTIVE' ? 'Suspend' : 'Activate'}
                    >
                      {user.status === 'ACTIVE' ? (
                        <PowerOff className="h-4 w-4 text-red-500" />
                      ) : (
                        <Power className="h-4 w-4 text-green-500" />
                      )}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {asList(users).length === 0 && (
                <TableRow>
                  <TableCell colSpan={6} className="text-muted-foreground py-8 text-center">
                    No users found.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        )}
      </Card>

      <Dialog open={isDialogOpen} onOpenChange={setIsDialogOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editingUser ? 'Edit User' : 'New User'}</DialogTitle>
            <DialogDescription>
              {editingUser ? 'Update the user details below.' : 'Create a new user account.'}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4 py-4">
            <div className="space-y-2">
              <Label>Full Name</Label>
              <Input
                value={formData.fullName}
                onChange={(e) => setFormData({ ...formData, fullName: e.target.value })}
              />
            </div>
            <div className="space-y-2">
              <Label>Email</Label>
              <Input
                type="email"
                value={formData.email}
                onChange={(e) => setFormData({ ...formData, email: e.target.value })}
              />
            </div>
            {!editingUser && (
              <div className="space-y-2">
                <Label>Password</Label>
                <Input
                  type="password"
                  value={formData.password}
                  onChange={(e) => setFormData({ ...formData, password: e.target.value })}
                />
              </div>
            )}
            <div className="space-y-2">
              <Label>Role</Label>
              <Select
                value={formData.roles}
                onValueChange={(val) => setFormData({ ...formData, roles: val })}
              >
                <SelectTrigger>
                  <SelectValue placeholder="Select role" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="ADMIN">Admin</SelectItem>
                  <SelectItem value="MANAGER">Manager</SelectItem>
                  <SelectItem value="CASHIER">Cashier</SelectItem>
                  <SelectItem value="TECHNICIAN">Technician</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setIsDialogOpen(false)}>
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              disabled={createMutation.isPending || updateMutation.isPending}
            >
              {createMutation.isPending || updateMutation.isPending ? (
                <Spinner className="mr-2" />
              ) : null}
              Save
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
