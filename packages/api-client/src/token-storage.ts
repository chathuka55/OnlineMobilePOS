const ACCESS_KEY = 'possaas.accessToken';
const REFRESH_KEY = 'possaas.refreshToken';
const USER_KEY = 'possaas.user';

export type TokenStorageMode = 'localStorage' | 'sessionStorage' | 'memory';

type MemoryStore = {
  accessToken?: string;
  refreshToken?: string;
  user?: string;
};

const memory: MemoryStore = {};

function getStore(mode: TokenStorageMode): Storage | null {
  if (typeof window === 'undefined') return null;
  if (mode === 'localStorage') return window.localStorage;
  if (mode === 'sessionStorage') return window.sessionStorage;
  return null;
}

export interface TokenStorage {
  getAccessToken(): string | null;
  getRefreshToken(): string | null;
  setTokens(accessToken: string, refreshToken: string): void;
  setAccessToken(accessToken: string): void;
  clear(): void;
  getUserJson(): string | null;
  setUserJson(userJson: string): void;
}

export function createTokenStorage(mode: TokenStorageMode = 'localStorage'): TokenStorage {
  return {
    getAccessToken() {
      const store = getStore(mode);
      if (!store) return memory.accessToken ?? null;
      return store.getItem(ACCESS_KEY);
    },
    getRefreshToken() {
      const store = getStore(mode);
      if (!store) return memory.refreshToken ?? null;
      return store.getItem(REFRESH_KEY);
    },
    setTokens(accessToken: string, refreshToken: string) {
      const store = getStore(mode);
      if (!store) {
        memory.accessToken = accessToken;
        memory.refreshToken = refreshToken;
        return;
      }
      store.setItem(ACCESS_KEY, accessToken);
      store.setItem(REFRESH_KEY, refreshToken);
    },
    setAccessToken(accessToken: string) {
      const store = getStore(mode);
      if (!store) {
        memory.accessToken = accessToken;
        return;
      }
      store.setItem(ACCESS_KEY, accessToken);
    },
    clear() {
      const store = getStore(mode);
      if (!store) {
        delete memory.accessToken;
        delete memory.refreshToken;
        delete memory.user;
        return;
      }
      store.removeItem(ACCESS_KEY);
      store.removeItem(REFRESH_KEY);
      store.removeItem(USER_KEY);
    },
    getUserJson() {
      const store = getStore(mode);
      if (!store) return memory.user ?? null;
      return store.getItem(USER_KEY);
    },
    setUserJson(userJson: string) {
      const store = getStore(mode);
      if (!store) {
        memory.user = userJson;
        return;
      }
      store.setItem(USER_KEY, userJson);
    },
  };
}

export const defaultTokenStorage = createTokenStorage('localStorage');
