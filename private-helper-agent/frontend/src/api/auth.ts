import http from './http';

export interface AuthResponse {
  tokenType: string;
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
}

export interface UserMeResponse {
  id: number;
  username: string;
}

export function register(payload: { username: string; password: string; inviteCode?: string }) {
  return http.post<AuthResponse>('/auth/register', payload);
}

export function login(payload: { username: string; password: string }) {
  return http.post<AuthResponse>('/auth/login', payload);
}

export function me() {
  return http.get<UserMeResponse>('/users/me');
}
