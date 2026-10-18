const TOKEN_KEY = 'shbean.accessToken';

// sessionStorage keeps the token scoped to the tab and cleared when it closes
export const getToken = (): string | null => sessionStorage.getItem(TOKEN_KEY);
export const setToken = (token: string): void => sessionStorage.setItem(TOKEN_KEY, token);
export const clearToken = (): void => sessionStorage.removeItem(TOKEN_KEY);

const apiBase = import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_URL || '';

export async function login(email: string, password: string): Promise<void> {
  const res = await fetch(`${apiBase}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) {
    throw new Error(res.status === 401 ? 'Invalid email or password' : 'Login failed');
  }
  const { accessToken } = (await res.json()) as { accessToken: string };
  setToken(accessToken);
}
