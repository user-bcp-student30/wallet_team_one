// login.service.spec.ts

import { TestBed } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { LoginService } from './login.service';
import { LoginRequest, LoginResponse } from './login.models';

describe('LoginService', () => {
  let service: LoginService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [LoginService],
    });

    service = TestBed.inject(LoginService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('envía las credenciales a POST /auth/login y recibe la respuesta tipada', () => {
    const credentials: LoginRequest = {
      username: 'student',
      password: 'Workshop123!',
    };
    const response: LoginResponse = {
      user: { username: 'student' },
      token: 'token-de-prueba',
    };

    service.login(credentials).subscribe((result) => {
      expect(result.user.username).toBe('student');
      expect(result.token).toBe('token-de-prueba');
    });

    const request = httpMock.expectOne('/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual(credentials);
    request.flush(response);
  });

  [
    { username: "' OR '1'='1", password: 'cualquier-valor' },
    { username: 'student', password: "' OR '1'='1" },
  ].forEach((credentials) => {
    it('transmite literalmente el valor recibido, sin probar la seguridad del servidor', () => {
      service.login(credentials).subscribe({
        error: (error) => {
          expect(error.status).toBe(401);
        },
      });

      const request = httpMock.expectOne('/auth/login');
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(credentials);
      request.flush({}, { status: 401, statusText: 'Unauthorized' });
    });
  });
});