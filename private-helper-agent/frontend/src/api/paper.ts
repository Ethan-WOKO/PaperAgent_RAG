import http from './http';

export interface PaperTaskResponse {
  id: number;
  userId: number;
  title: string;
  sourceFilename: string | null;
  objectKey: string | null;
  finalObjectKey: string | null;
  status: string;
  targetLanguage: 'zh' | 'en';
  currentStage: string | null;
  errorMessage: string | null;
  scoreThreshold: number | null;
  maxRounds: number | null;
  innerMaxAttempts: number | null;
  literatureCount: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface PaperSseEvent {
  type: string;
  taskId: number;
  message: string;
  stage: string | null;
  timestamp: string;
}

export function createPaperTask(formData: FormData) {
  return http.post<PaperTaskResponse>('/paper/process', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}

export function getPaperTask(taskId: number) {
  return http.get<PaperTaskResponse>(`/paper/tasks/${taskId}`);
}

export function pausePaperTask(taskId: number) {
  return http.post(`/paper/tasks/${taskId}/pause`);
}

export function resumePaperTask(taskId: number) {
  return http.post(`/paper/tasks/${taskId}/resume`);
}

export function stopPaperTask(taskId: number) {
  return http.post(`/paper/tasks/${taskId}/stop`);
}

export function downloadPaperTask(taskId: number) {
  return http.get<Blob>(`/paper/tasks/${taskId}/download`, {
    responseType: 'blob',
  });
}
