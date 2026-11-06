import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive } from '@angular/router';

/** Barra de navegación superior con enlaces a las 6 secciones del MVP. */
@Component({
  selector: 'app-nav-bar',
  standalone: true,
  imports: [RouterLink, RouterLinkActive],
  template: `
    <nav class="nav-bar">
      <a class="brand" routerLink="/">Wallet Perú MVP</a>
      <div class="links">
        <a routerLink="/onboarding" routerLinkActive="active">Onboarding</a>
        <a routerLink="/pagos/nuevo" routerLinkActive="active">Pagar</a>
        <a routerLink="/pagos/historial" routerLinkActive="active">Historial</a>
        <a routerLink="/comercio" routerLinkActive="active">Comercio</a>
        <a routerLink="/soporte" routerLinkActive="active">Soporte</a>
        <a routerLink="/operaciones" routerLinkActive="active">Operaciones</a>
      </div>
    </nav>
  `,
  styles: [
    `
      .nav-bar {
        display: flex;
        align-items: center;
        justify-content: space-between;
        padding: 0.75rem 1.5rem;
        background: #0b2545;
        color: #fff;
        flex-wrap: wrap;
        gap: 0.5rem;
      }
      .brand {
        font-weight: 700;
        color: #fff;
        text-decoration: none;
        font-size: 1.1rem;
      }
      .links {
        display: flex;
        gap: 1rem;
        flex-wrap: wrap;
      }
      .links a {
        color: #cfe0ff;
        text-decoration: none;
        padding: 0.3rem 0.5rem;
        border-radius: 4px;
        font-size: 0.95rem;
      }
      .links a.active,
      .links a:hover {
        background: #13417a;
        color: #fff;
      }
    `
  ]
})
export class NavBarComponent {}
