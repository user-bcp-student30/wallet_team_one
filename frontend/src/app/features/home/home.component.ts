import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

/** Pantalla de inicio: resume el propósito del MVP (basado en la épica) y enlaza
 * a las 6 secciones funcionales de la aplicación. */
@Component({
  selector: 'app-home',
  standalone: true,
  imports: [RouterLink],
  template: `
    <section class="page">
      <h1>MVP de wallet en Perú para pagos cotidianos</h1>
      <p class="lead">
        Reducimos la fricción en el checkout y aceleramos el onboarding para comportamientos de
        pago frecuentes y de bajo monto, con confirmaciones claras, manejo confiable de estados y
        límites alineados a cumplimiento.
      </p>

      <div class="goals">
        <h2>Objetivos del trimestre</h2>
        <ul>
          <li>Reducir la caída en checkout de <strong>30% a 8%</strong>.</li>
          <li>Reducir el tiempo de onboarding de <strong>8 a 3 minutos</strong>.</li>
          <li>Mantener entre <strong>1,000 y 2,000</strong> usuarios activos mensuales (MAU).</li>
          <li>Lograr un costo de adquisición de cliente (CAC) de <strong>S/ 10</strong> por usuario nuevo.</li>
        </ul>
      </div>

      <h2>Flujos del MVP</h2>
      <div class="cards">
        <a class="card" routerLink="/onboarding">
          <h3>Onboarding</h3>
          <p>Wizard guiado de 4 pasos para activar tu wallet rápidamente.</p>
        </a>
        <a class="card" routerLink="/pagos/nuevo">
          <h3>Pago de bajo monto</h3>
          <p>Confirma destino y monto, y recibe un resultado terminal claro.</p>
        </a>
        <a class="card" routerLink="/pagos/historial">
          <h3>Historial de pagos</h3>
          <p>Consulta tus transacciones con referencia y estado.</p>
        </a>
        <a class="card" routerLink="/comercio">
          <h3>Dashboard de comercio</h3>
          <p>Genera QR de cobro y monitorea el estado de cada intento.</p>
        </a>
        <a class="card" routerLink="/soporte">
          <h3>Soporte</h3>
          <p>Gestiona casos abiertos, reversos y resoluciones.</p>
        </a>
        <a class="card" routerLink="/operaciones">
          <h3>Operaciones</h3>
          <p>Corre conciliación diaria y revisa incidencias.</p>
        </a>
      </div>
    </section>
  `,
  styles: [
    `
      .page {
        max-width: 960px;
        margin: 0 auto;
        padding: 2rem 1.5rem;
      }
      .lead {
        font-size: 1.05rem;
        color: #334;
        line-height: 1.5;
      }
      .goals ul {
        line-height: 1.8;
      }
      .cards {
        display: grid;
        grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
        gap: 1rem;
        margin-top: 1rem;
      }
      .card {
        display: block;
        padding: 1rem;
        border-radius: 8px;
        background: #fff;
        border: 1px solid #dde3ee;
        text-decoration: none;
        color: #1a1a2e;
        box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
        transition: box-shadow 0.15s ease;
      }
      .card:hover {
        box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
      }
      .card h3 {
        margin: 0 0 0.35rem 0;
        color: #0b2545;
      }
      .card p {
        margin: 0;
        font-size: 0.9rem;
        color: #556;
      }
    `
  ]
})
export class HomeComponent {}
