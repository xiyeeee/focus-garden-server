import type { ApiErrorResponse, ApiResponse } from './task-types';
import type { CsrfVO, EmailCodeDTO, LoginDTO, RegisterDTO, ResetPasswordDTO, UserVO } from './auth-types';

export class GardenApiError extends Error {
  constructor(public readonly status: number, public readonly code: string,
    message: string, public readonly retryAfter: number | null) {
    super(message);
    this.name = 'GardenApiError';
  }
}

export const createGardenHttp = (baseUrl = '/api') => {
  let csrf: CsrfVO | null = null;
  const request = async <T>(path: string, method = 'GET', body?: object): Promise<T> => {
    const write = !['GET', 'HEAD', 'OPTIONS'].includes(method);
    if (write && csrf === null) await refreshCsrf();
    const response = await fetch(`${baseUrl.replace(/\/$/, '')}${path}`, {
      method, credentials: 'include', cache: 'no-store',
      headers: {
        Accept: 'application/json',
        ...(body ? { 'Content-Type': 'application/json' } : {}),
        ...(write && csrf ? { [csrf.headerName]: csrf.token } : {}),
      },
      ...(body ? { body: JSON.stringify(body) } : {}),
    });
    const payload: ApiResponse<T> | ApiErrorResponse | null = await response.json().catch(() => null);
    if (!response.ok || !payload || payload.code !== 'OK') {
      const retry = response.headers.get('Retry-After');
      if (response.status === 401 || payload?.code === 'CSRF_INVALID') csrf = null;
      throw new GardenApiError(response.status, payload?.code ?? 'HTTP_ERROR',
        payload?.message ?? '请求失败', retry === null ? null : Number(retry));
    }
    return payload.data;
  };
  const refreshCsrf = async (): Promise<CsrfVO> => {
    csrf = await request<CsrfVO>('/auth/csrf');
    return csrf;
  };
  return { request, refreshCsrf };
};
export type GardenHttp = ReturnType<typeof createGardenHttp>;

/** 与 task-client 共用同一个 http，确保登录后任务请求使用新 CSRF Token。 */
export const createAuthClient = (http: GardenHttp) => ({
  csrf: http.refreshCsrf,
  emailCode: (body: EmailCodeDTO) => http.request<null>('/auth/email-codes', 'POST', body),
  register: (body: RegisterDTO) => http.request<UserVO>('/auth/register', 'POST', body),
  me: () => http.request<UserVO>('/users/me'),
  login: async (body: LoginDTO) => {
    const user = await http.request<UserVO>('/auth/login', 'POST', body);
    await http.refreshCsrf();
    return user;
  },
  logout: async () => {
    await http.request<null>('/auth/logout', 'POST');
    await http.refreshCsrf();
  },
  resetPassword: async (body: ResetPasswordDTO) => {
    await http.request<null>('/auth/password/reset', 'POST', body);
    await http.refreshCsrf();
  },
});
