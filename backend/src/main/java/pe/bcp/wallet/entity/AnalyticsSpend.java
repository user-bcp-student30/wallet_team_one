package pe.bcp.wallet.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Gasto de adquisición registrado manualmente por mes (simplificación MVP,
 * no hay sistema de marketing real detrás). Se usa para calcular CAC.
 */
@Entity
@Table(name = "analytics_spend")
public class AnalyticsSpend extends PanacheEntityBase {

    @Id
    @Column(name = "spend_month")
    public String month;

    @Column(name = "amount_pen", nullable = false)
    public double amountPen;
}
