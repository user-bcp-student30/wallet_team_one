// login.models.ts

/**
 * SUPUESTO PENDIENTE: Backend debe confirmar que el body actual de
 * POST /auth/login es JSON con estos nombres de campo.
 */
export interface LoginRequest {
  username: string;
  password: string;
}

/**
 * Basado en los elementos de respuesta exigidos por los criterios:
 * user.username y token.
 */
export interface LoginResponse {
  user: {
    username: string;
  };
  token: string;
}