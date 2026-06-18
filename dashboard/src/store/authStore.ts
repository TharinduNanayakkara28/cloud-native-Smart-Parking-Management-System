import { create } from 'zustand';
import { persist } from 'zustand/middleware';

interface Operator {
  id: string;
  name: string;
  email: string;
}

interface AuthState {
  accessToken: string | null;
  refreshToken: string | null;
  operator: Operator | null;
  setTokens: (access: string, refresh: string) => void;
  setOperator: (op: Operator) => void;
  logout: () => void;
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      operator: null,
      setTokens: (accessToken, refreshToken) =>
        set({ accessToken, refreshToken }),
      setOperator: (operator) => set({ operator }),
      logout: () =>
        set({ accessToken: null, refreshToken: null, operator: null }),
    }),
    { name: 'sp-auth' }
  )
);
