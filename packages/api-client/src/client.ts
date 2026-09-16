import {
  ApiError,
  type ApiErrorBody,
  type Bill,
  type BillSummary,
  type Cart,
  type CartLineRequest,
  type CheckoutRequest,
  type CreateCartRequest,
  type Customer,
  type CustomerRequest,
  type Item,
  type ItemRequest,
  type Category,
  type CategoryRequest,
  type CreateRefundRequest,
  type Grn,
  type GrnCreateRequest,
  type LoginRequest,
  type Outlet,
  type Page,
  type RefreshRequest,
  type Refund,
  type SignupRequest,
  type Supplier,
  type SupplierRequest,
  type Tenant,
  type TokenResponse,
  type User,
  type UUID,
} from './types';
import { createTokenStorage, type TokenStorage, type TokenStorageMode } from './token-storage';

export interface PosApiClientOptions {
  baseUrl?: string;
  tokenStorage?: TokenStorage;
  storageMode?: TokenStorageMode;
  getHeaders?: () => Record<string, string>;
  onUnauthorized?: () => void;
}

type Query = Record<string, string | number | boolean | undefined | null>;

function buildUrl(baseUrl: string, path: string, query?: Query): string {
  const normalizedBase = baseUrl.replace(/\/$/, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  const url = new URL(`${normalizedBase}${normalizedPath}`);
  if (query) {
    Object.entries(query).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }
  return url.toString();
}

export function createPosApiClient(options: PosApiClientOptions = {}) {
  const baseUrl = options.baseUrl ?? 'http://localhost:8080';
  const tokens = options.tokenStorage ?? createTokenStorage(options.storageMode ?? 'localStorage');

  let refreshPromise: Promise<string | null> | null = null;

  async function parseError(response: Response): Promise<ApiError> {
    let body: ApiErrorBody | undefined;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = undefined;
    }
    const message = body?.message || body?.error || response.statusText || 'Request failed';
    return new ApiError(response.status, message, body);
  }

  async function refreshAccessToken(): Promise<string | null> {
    const refreshToken = tokens.getRefreshToken();
    if (!refreshToken) return null;
    const response = await fetch(buildUrl(baseUrl, '/api/v1/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      body: JSON.stringify({ refreshToken } satisfies RefreshRequest),
    });
    if (!response.ok) {
      tokens.clear();
      options.onUnauthorized?.();
      return null;
    }
    const data = (await response.json()) as TokenResponse;
    tokens.setTokens(data.accessToken, data.refreshToken);
    tokens.setUserJson(JSON.stringify(data.user));
    return data.accessToken;
  }

  async function request<T>(
    method: string,
    path: string,
    init?: {
      body?: unknown;
      query?: Query;
      auth?: boolean;
      headers?: Record<string, string>;
    },
  ): Promise<T> {
    const auth = init?.auth !== false;
    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...options.getHeaders?.(),
      ...init?.headers,
    };

    if (init?.body !== undefined) {
      headers['Content-Type'] = 'application/json';
    }

    if (auth) {
      const access = tokens.getAccessToken();
      if (access) headers.Authorization = `Bearer ${access}`;
    }

    let response = await fetch(buildUrl(baseUrl, path, init?.query), {
      method,
      headers,
      body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
    });

    if (response.status === 401 && auth) {
      if (!refreshPromise) {
        refreshPromise = refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
      }
      const newAccess = await refreshPromise;
      if (newAccess) {
        headers.Authorization = `Bearer ${newAccess}`;
        response = await fetch(buildUrl(baseUrl, path, init?.query), {
          method,
          headers,
          body: init?.body !== undefined ? JSON.stringify(init.body) : undefined,
        });
      }
    }

    if (response.status === 204) {
      return undefined as T;
    }

    if (!response.ok) {
      throw await parseError(response);
    }

    const text = await response.text();
    if (!text) return undefined as T;
    return JSON.parse(text) as T;
  }

  const get = <T>(path: string, query?: Query, auth = true) =>
    request<T>('GET', path, { query, auth });
  const post = <T>(path: string, body?: unknown, auth = true) =>
    request<T>('POST', path, { body, auth });
  const put = <T>(path: string, body?: unknown, auth = true) =>
    request<T>('PUT', path, { body, auth });
  const patch = <T>(path: string, body?: unknown, auth = true) =>
    request<T>('PATCH', path, { body, auth });
  const del = <T>(path: string, auth = true) => request<T>('DELETE', path, { auth });

  function persistAuth(data: TokenResponse) {
    tokens.setTokens(data.accessToken, data.refreshToken);
    tokens.setUserJson(JSON.stringify(data.user));
    return data;
  }

  return {
    baseUrl,
    tokens,
    get,
    post,
    put,
    patch,
    delete: del,

    auth: {
      async login(payload: LoginRequest) {
        const data = await post<TokenResponse>('/api/v1/auth/login', payload, false);
        return persistAuth(data);
      },
      async signup(payload: SignupRequest) {
        const data = await post<TokenResponse>('/api/v1/auth/signup', payload, false);
        return persistAuth(data);
      },
      async refresh(payload?: RefreshRequest) {
        const refreshToken = payload?.refreshToken ?? tokens.getRefreshToken();
        if (!refreshToken) throw new ApiError(401, 'Missing refresh token');
        const data = await post<TokenResponse>('/api/v1/auth/refresh', { refreshToken }, false);
        return persistAuth(data);
      },
      me() {
        return get<User>('/api/v1/auth/me');
      },
      async logout() {
        const refreshToken = tokens.getRefreshToken();
        try {
          await post('/api/v1/auth/logout', { refreshToken });
        } finally {
          tokens.clear();
        }
      },
      getStoredUser(): User | null {
        const raw = tokens.getUserJson();
        if (!raw) return null;
        try {
          return JSON.parse(raw) as User;
        } catch {
          return null;
        }
      },
      setPin(payload: { password: string; pin: string }) {
        return post<{ message: string }>('/api/v1/users/me/pin', payload);
      },
      verifyPin(payload: { pin: string }) {
        return post<{ message: string }>('/api/v1/users/me/pin/verify', payload);
      },
    },

    shop: {
      tenant() {
        return get<Tenant>('/api/v1/tenant');
      },
      outlets() {
        return get<Outlet[]>('/api/v1/outlets');
      },
    },

    categories: {
      list(activeOnly?: boolean) {
        return get<Category[]>('/api/v1/categories', { activeOnly });
      },
      create(payload: CategoryRequest) {
        return post<Category>('/api/v1/categories', payload);
      },
      update(id: UUID, payload: CategoryRequest) {
        return put<Category>(`/api/v1/categories/${id}`, payload);
      },
    },

    suppliers: {
      list(params?: { q?: string; page?: number; size?: number }) {
        return get<Page<Supplier> | Supplier[]>('/api/v1/suppliers', params);
      },
      get(id: UUID) {
        return get<Supplier>(`/api/v1/suppliers/${id}`);
      },
      create(payload: SupplierRequest) {
        return post<Supplier>('/api/v1/suppliers', payload);
      },
      update(id: UUID, payload: SupplierRequest) {
        return put<Supplier>(`/api/v1/suppliers/${id}`, payload);
      },
    },

    grns: {
      list(params?: { page?: number; size?: number }) {
        return get<Page<Grn> | Grn[]>('/api/v1/grns', params);
      },
      get(id: UUID) {
        return get<Grn>(`/api/v1/grns/${id}`);
      },
      create(payload: GrnCreateRequest) {
        return post<Grn>('/api/v1/grns', payload);
      },
    },

    items: {
      list(params?: { q?: string; page?: number; size?: number; active?: boolean }) {
        return get<Page<Item> | Item[]>('/api/v1/items', params);
      },
      get(id: UUID) {
        return get<Item>(`/api/v1/items/${id}`);
      },
      byBarcode(barcode: string) {
        return get<Item>(`/api/v1/items/by-barcode/${encodeURIComponent(barcode)}`);
      },
      create(payload: ItemRequest) {
        return post<Item>('/api/v1/items', payload);
      },
      update(id: UUID, payload: ItemRequest) {
        return put<Item>(`/api/v1/items/${id}`, payload);
      },
      remove(id: UUID) {
        return del<void>(`/api/v1/items/${id}`);
      },
      adjustStock(id: UUID, delta: number | string, reason?: string) {
        return post<Item>(`/api/v1/items/${id}/adjust-stock`, { delta, reason });
      },
      lowStock() {
        return get<Item[]>('/api/v1/items/low-stock');
      },
    },

    customers: {
      list(params?: { q?: string; page?: number; size?: number; active?: boolean }) {
        return get<Page<Customer> | Customer[]>('/api/v1/customers', params);
      },
      get(id: UUID) {
        return get<Customer>(`/api/v1/customers/${id}`);
      },
      create(payload: CustomerRequest) {
        return post<Customer>('/api/v1/customers', payload);
      },
      update(id: UUID, payload: CustomerRequest) {
        return put<Customer>(`/api/v1/customers/${id}`, payload);
      },
      remove(id: UUID) {
        return del<void>(`/api/v1/customers/${id}`);
      },
    },

    bills: {
      list(params?: { page?: number; size?: number; status?: string; q?: string }) {
        return get<Page<BillSummary> | BillSummary[]>('/api/v1/bills', params);
      },
      get(billId: UUID) {
        return get<Bill>(`/api/v1/bills/${billId}`);
      },
      checkout(payload: CheckoutRequest) {
        return post<Bill>('/api/v1/bills/checkout', payload);
      },
      void(billId: UUID, reason?: string) {
        return post<Bill>(`/api/v1/bills/${billId}/void`, { reason });
      },
    },

    refunds: {
      create(payload: CreateRefundRequest) {
        return post<Refund>('/api/v1/refunds', payload);
      },
      get(id: UUID) {
        return get<Refund>(`/api/v1/refunds/${id}`);
      },
    },

    carts: {
      list(params?: { status?: string }) {
        return get<Cart[]>('/api/v1/carts', params);
      },
      get(cartId: UUID) {
        return get<Cart>(`/api/v1/carts/${cartId}`);
      },
      create(payload: CreateCartRequest = {}) {
        return post<Cart>('/api/v1/carts', payload);
      },
      update(cartId: UUID, payload: CreateCartRequest) {
        return put<Cart>(`/api/v1/carts/${cartId}`, payload);
      },
      addLine(cartId: UUID, payload: CartLineRequest) {
        return post<Cart>(`/api/v1/carts/${cartId}/lines`, payload);
      },
      updateLine(cartId: UUID, lineId: UUID, payload: CartLineRequest) {
        return put<Cart>(`/api/v1/carts/${cartId}/lines/${lineId}`, payload);
      },
      removeLine(cartId: UUID, lineId: UUID) {
        return del<Cart>(`/api/v1/carts/${cartId}/lines/${lineId}`);
      },
      hold(cartId: UUID, label?: string) {
        return post<Cart>(`/api/v1/carts/${cartId}/hold`, { label });
      },
      resume(cartId: UUID) {
        return post<Cart>(`/api/v1/carts/${cartId}/resume`);
      },
      abandon(cartId: UUID) {
        return post<Cart>(`/api/v1/carts/${cartId}/abandon`);
      },
    },
  };
}

export type PosApiClient = ReturnType<typeof createPosApiClient>;
