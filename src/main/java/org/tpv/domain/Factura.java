package org.tpv.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class Factura {

    private Long id;
    private String numeroFactura;
    private LocalDateTime fechaEmision;

    // Información del cliente
    private Long clienteId;
    private String clienteNombre;
    private String clienteDni;

    // Información del empleado
    private Long empleadoId;
    private String empleadoNombre;

    private List<LineaFactura> lineas = new ArrayList<>();

    private BigDecimal totalSinIva = BigDecimal.ZERO;
    private BigDecimal totalIva = BigDecimal.ZERO;
    private BigDecimal totalConIva = BigDecimal.ZERO;

    public void añadirLinea(LineaFactura linea) {
        lineas.add(linea);
        recalcularTotales();
    }

    public void recalcularTotales() {
        totalSinIva = BigDecimal.ZERO;
        totalIva = BigDecimal.ZERO;
        totalConIva = BigDecimal.ZERO;

        for (LineaFactura linea : lineas) {
            totalSinIva = totalSinIva.add(linea.getSubtotalSinIva());
            totalIva = totalIva.add(linea.getImporteIva());
            totalConIva = totalConIva.add(linea.getTotalConIva());
        }
    }
}