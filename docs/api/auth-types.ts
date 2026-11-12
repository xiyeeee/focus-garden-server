export type VerificationPurpose = 'REGISTER' | 'RESET_PASSWORD';
export interface EmailCodeDTO { email: string; purpose: VerificationPurpose }
export interface RegisterDTO { email: string; password: string; verificationCode: string; nickname?: string | null }
export interface LoginDTO { email: string; password: string }
export interface ResetPasswordDTO { email: string; verificationCode: string; newPassword: string }
export interface CsrfVO { headerName: 'X-CSRF-TOKEN'; token: string }
export interface UserVO {
  id: string;
  email: string;
  nickname: string;
  avatarUrl: string | null;
  lastLoginAt: string | null;
  createdAt: string;
  updatedAt: string;
}
