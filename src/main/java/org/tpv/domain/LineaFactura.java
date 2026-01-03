package org.tpv.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

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

    private BigDecimal precioUnitario; // sin IVA
    private int cantidad;
    private int ivaAplicado; // ej. 21

    public BigDecimal getSubtotalSinIva() {
        return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
    }

    public BigDecimal getImporteIva() {
        return getSubtotalSinIva()
                .multiply(BigDecimal.valueOf(ivaAplicado))
                .divide(BigDecimal.valueOf(100));
    }

    public BigDecimal getTotalConIva() {
        return getSubtotalSinIva().add(getImporteIva());
    }

}
