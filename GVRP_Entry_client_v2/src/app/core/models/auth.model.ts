import {User} from '@core/models/user.model';

export interface LoginRequest {
  branch_name: string;
  username: string;
  password: string;
}

export interface AuthResponse {
  access_token: string;
  user_id: number;
  username: string;
  role: string;
  branch_id: number;
}

export interface SessionData {
  token: string;
  user: User;
  branchId: number;
}
