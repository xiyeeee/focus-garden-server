import type {
  ApiErrorResponse, ApiResponse, PageResponse, TaskCreateDTO, TaskId,
  TaskQueryDTO, TaskUpdateDTO, TaskVO,
} from './task-types';

export class TaskApiError extends Error {
  constructor(public readonly status: number, public readonly code: string, message: string) {
    super(message);
    this.name = 'TaskApiError';
  }
}

/** baseUrl 使用 /api（同源代理），或 http://localhost:8080/api（CORS）。 */
export const createTaskClient = (baseUrl = '/api') => {
  const request = async <T>(path: string, method = 'GET', body?: object): Promise<T> => {
    const response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
      method,
      headers: { Accept: 'application/json', ...(body ? { 'Content-Type': 'application/json' } : {}) },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    if (!response.ok) {
      const error: ApiErrorResponse | null = await response.json().catch(() => null);
      throw new TaskApiError(response.status, error?.code ?? 'HTTP_ERROR', error?.message ?? '请求失败');
    }
    const result: ApiResponse<T> = await response.json();
    return result.data;
  };
  const taskPath = (id: TaskId) => `/tasks/${encodeURIComponent(id)}`;

  return {
    create: (body: TaskCreateDTO) => request<TaskVO>('/tasks', 'POST', body),
    list: (query: TaskQueryDTO = {}) => {
      const search = new URLSearchParams();
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined) search.set(key, String(value));
      });
      return request<PageResponse<TaskVO>>(`/tasks?${search.toString()}`);
    },
    get: (id: TaskId) => request<TaskVO>(taskPath(id)),
    update: (id: TaskId, body: TaskUpdateDTO) => request<TaskVO>(taskPath(id), 'PUT', body),
    delete: (id: TaskId) => request<null>(taskPath(id), 'DELETE'),
    complete: (id: TaskId) => request<TaskVO>(`${taskPath(id)}/complete`, 'POST'),
  };
};
