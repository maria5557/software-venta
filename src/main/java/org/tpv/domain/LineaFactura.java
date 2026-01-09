package org.tpv.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LineaFactura {
    private Long id;
    private Long facturaId;
    private Long productoId;

    private String codigoProducto;
    private String nombreProducto;

    private BigDecimal precioUnitario; // CON IVA incluido
    private int cantidad;
    private int ivaAplicado; // ej. 21
    private int descuento = 0; // porcentaje de descuento (0-100)

    // Constructor sin descuento (para compatibilidad)
    public LineaFactura(Long id, Long facturaId, Long productoId,
                        String codigoProducto, String nombreProducto,
                        BigDecimal precioUnitario, int cantidad, int ivaAplicado) {
        this.id = id;
        this.facturaId = facturaId;
        this.productoId = productoId;
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.precioUnitario = precioUnitario;
        this.cantidad = cantidad;
        this.ivaAplicado = ivaAplicado;
        this.descuento = 0;
    }

    /**
     * Calcula el precio con IVA después de aplicar el descuento
     */
    public BigDecimal getPrecioConDescuento() {
        if (descuento == 0) {
            return precioUnitario;
        }

        BigDecimal factorDescuento = BigDecimal.valueOf(100 - descuento)
                .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);

        return precioUnitario.multiply(factorDescuento)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Extrae el precio sin IVA a partir del precio con IVA (tras aplicar descuento)
     */
    public BigDecimal getPrecioSinIva() {
        BigDecimal precioConIvaYDescuento = getPrecioConDescuento();

        // Fórmula: precioSinIva = precioConIva / (1 + iva/100)
        BigDecimal divisor = BigDecimal.ONE.add(
                BigDecimal.valueOf(ivaAplicado).divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
        );

        return precioConIvaYDescuento.divide(divisor, 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el subtotal sin IVA (precio sin IVA * cantidad)
     */
    public BigDecimal getSubtotalSinIva() {
        return getPrecioSinIva().multiply(BigDecimal.valueOf(cantidad))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el importe del IVA sobre el subtotal sin IVA
     */
    public BigDecimal getImporteIva() {
        return getSubtotalSinIva()
                .multiply(BigDecimal.valueOf(ivaAplicado))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el total con IVA (subtotal sin IVA + importe IVA)
     */
    public BigDecimal getTotalConIva() {
        return getSubtotalSinIva().add(getImporteIva())
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Calcula el importe total del descuento aplicado
     */
    public BigDecimal getImporteDescuento() {
        if (descuento == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal precioOriginalTotal = precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        BigDecimal precioConDescuentoTotal = getPrecioConDescuento().multiply(BigDecimal.valueOf(cantidad));

        return precioOriginalTotal.subtract(precioConDescuentoTotal)
                .setScale(2, RoundingMode.HALF_UP);
    }
}