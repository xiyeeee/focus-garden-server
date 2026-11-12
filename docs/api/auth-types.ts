export type VerificationPurpose = 'REGISTER' | 'RESET_PASSWORD';
export interface EmailCodeDTO { email: string; purpose: VerificationPurpose }

export interface PasswordRegistrationFields {
  password: string;
  nickname?: string | null;
}
/** 注册方式二选一；用户名注册不发送邮箱验证码。 */
export type RegisterDTO = PasswordRegistrationFields & (
  | { username: string; email?: never; verificationCode?: never }
  | { email: string; verificationCode: string; username?: never }
);
/** 两个身份字段二选一，不同时发送。 */
export type LoginDTO = { password: string } & (
  | { username: string; email?: never }
  | { email: string; username?: never }
);
export interface ResetPasswordDTO { email: string; verificationCode: string; newPassword: string }
export interface CsrfVO { headerName: 'X-CSRF-TOKEN'; token: string }
export interface UserVO {
  id: string;
  email: string | null;
  username: string | null;
  nickname: string;
  avatarUrl: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}
