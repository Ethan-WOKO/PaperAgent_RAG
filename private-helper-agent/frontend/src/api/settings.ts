import http from './http';

export interface UserSettingsResponse {
  defaultProvider: string;
  deepseekApiKeyConfigured: boolean;
  glmApiKeyConfigured: boolean;
  githubPatConfigured: boolean;
  deepseekModel: string;
  glmModel: string;
  deepseekTemperature: number;
  maxSteps: number;
  ragDefaultEnabled: boolean;
  filesystemRoots: string[];
  disabledSkills: string[];
  updatedAt: string | null;
}

export interface UserSettingsRequest {
  defaultProvider: string;
  deepseekApiKey?: string;
  glmApiKey?: string;
  githubPat?: string;
  deepseekModel: string;
  glmModel: string;
  deepseekTemperature: number;
  maxSteps: number;
  ragDefaultEnabled: boolean;
  filesystemRoots: string[];
  disabledSkills: string[];
}

export function getSettings() {
  return http.get<UserSettingsResponse>('/settings');
}

export function updateSettings(payload: UserSettingsRequest) {
  return http.put<UserSettingsResponse>('/settings', payload);
}
