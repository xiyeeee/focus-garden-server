import type { PageResponse, TaskCreateDTO, TaskId, TaskQueryDTO, TaskUpdateDTO, TaskVO } from './task-types';
import type { GardenHttp } from './auth-client';

/** 与 createAuthClient 共用 http，统一 Cookie 与 CSRF。 */
export const createTaskClient = (http: GardenHttp) => {
  const taskPath = (id: TaskId) => `/tasks/${encodeURIComponent(id)}`;
  return {
    create: (body: TaskCreateDTO) => http.request<TaskVO>('/tasks', 'POST', body),
    list: (query: TaskQueryDTO = {}) => {
      const search = new URLSearchParams();
      Object.entries(query).forEach(([key, value]) => {
        if (value !== undefined) search.set(key, String(value));
      });
      return http.request<PageResponse<TaskVO>>(`/tasks?${search.toString()}`);
    },
    get: (id: TaskId) => http.request<TaskVO>(taskPath(id)),
    update: (id: TaskId, body: TaskUpdateDTO) => http.request<TaskVO>(taskPath(id), 'PUT', body),
    delete: (id: TaskId) => http.request<null>(taskPath(id), 'DELETE'),
    complete: (id: TaskId) => http.request<TaskVO>(`${taskPath(id)}/complete`, 'POST'),
  };
};
