/** HTTP 契约；ID 保持字符串，时间按 ISO 8601 UTC 字符串传输。 */
export type TaskId = string;
export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'COMPLETED';
export type TaskPlantType = 'fern' | 'daisy' | 'cactus' | 'tulip';
export type TaskSortBy = 'id' | 'createdAt' | 'updatedAt' | 'plannedFocusMinutes';

export interface ApiResponse<T> {
  code: 'OK';
  message: string;
  data: T;
}

export interface ApiErrorResponse {
  code: 'BAD_REQUEST' | 'NOT_FOUND' | 'METHOD_NOT_ALLOWED' | 'NOT_ACCEPTABLE'
    | 'UNSUPPORTED_MEDIA_TYPE' | 'INTERNAL_SERVER_ERROR' | 'UNAUTHORIZED' | 'FORBIDDEN'
    | 'INVALID_CREDENTIALS' | 'EMAIL_ALREADY_REGISTERED' | 'USERNAME_ALREADY_REGISTERED' | 'VERIFICATION_INVALID'
    | 'CSRF_INVALID' | 'RATE_LIMITED' | 'MAIL_UNAVAILABLE' | 'SERVICE_UNAVAILABLE';
  message: string;
  data: null;
}

export interface PageResponse<T> {
  page: number;
  size: number;
  total: number;
  records: T[];
}

export interface TaskCreateDTO {
  title: string;
  description?: string | null;
  plannedFocusMinutes: number;
  status: TaskStatus;
  plantType?: TaskPlantType | null;
}

/** PUT 完整替换；与新增字段相同，语义独立，后续可分别演进。 */
export interface TaskUpdateDTO {
  title: string;
  description?: string | null;
  plannedFocusMinutes: number;
  status: TaskStatus;
  plantType?: TaskPlantType | null;
}

export interface TaskQueryDTO {
  page?: number;
  size?: number;
  title?: string;
  status?: TaskStatus;
  sortBy?: TaskSortBy;
  direction?: 'asc' | 'desc';
}

export interface TaskVO {
  id: TaskId;
  title: string;
  description: string;
  plannedFocusMinutes: number;
  status: TaskStatus;
  plantType: TaskPlantType;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}
