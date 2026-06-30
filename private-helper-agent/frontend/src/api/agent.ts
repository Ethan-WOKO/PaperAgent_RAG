import http from './http';

export interface AgentSessionResponse {
  id: number;
  userId: number;
  title: string;
  modelProvider: string;
  model: string;
  maxSteps: number;
  ragDisabled: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface AgentMessageResponse {
  id: number;
  sessionId: number;
  userId: number;
  role: string;
  content: string;
  toolCallsJson: string | null;
  paperTaskId: number | null;
  createdAt: string;
}

export function listSessions() {
  return http.get<AgentSessionResponse[]>('/agent/sessions');
}

export interface UpdateSessionPayload {
  title?: string;
  modelProvider?: string;
  model?: string;
  maxSteps?: number;
  ragDisabled?: boolean;
}

export function createSession(payload: { title?: string; modelProvider?: string; model?: string; maxSteps?: number; ragDisabled?: boolean }) {
  return http.post<AgentSessionResponse>('/agent/sessions', payload);
}

export function updateSession(sessionId: number, payload: UpdateSessionPayload) {
  return http.patch<AgentSessionResponse>(`/agent/sessions/${sessionId}`, payload);
}

export function deleteSession(sessionId: number) {
  return http.delete<void>(`/agent/sessions/${sessionId}`);
}

export function listMessages(sessionId: number) {
  return http.get<AgentMessageResponse[]>(`/agent/sessions/${sessionId}/messages`);
}
