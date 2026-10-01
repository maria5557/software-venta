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

    // Información del empleado
    private Long empleadoId;
    private String empleadoNombre;

    private List<LineaFactura> lineas = new ArrayList<>();

    private BigDecimal totalSinIva = BigDecimal.ZERO;
    private BigDecimal totalIva = BigDecimal.ZERO;
    private BigDecimal totalConIva = BigDecimal.ZERO;
    private String metodoPago;
    private BigDecimal entregadoCliente;

    // Ciclo de vida: las facturas NUNCA se borran físicamente, se anulan.
    // El número de factura queda reservado para siempre (correlación sin huecos).
    private EstadoFactura estado = EstadoFactura.EMITIDA;
    private String motivoAnulacion;
    private LocalDateTime fechaAnulacion;
    private String anuladaPor;

    // Última modificación (también se usa como control de concurrencia optimista)
    private LocalDateTime fechaModificacion;

    public boolean isAnulada() {
        return estado == EstadoFactura.ANULADA;
    }

    /**
     * Copia profunda: permite editar en un diálogo sin tocar el objeto
     * que se muestra en la tabla hasta que el usuario confirme.
     */
    public Factura copiar() {
        Factura c = new Factura();
        c.id = id;
        c.numeroFactura = numeroFactura;
        c.fechaEmision = fechaEmision;
        c.clienteId = clienteId;
        c.clienteNombre = clienteNombre;
        c.empleadoId = empleadoId;
        c.empleadoNombre = empleadoNombre;
        c.metodoPago = metodoPago;
        c.entregadoCliente = entregadoCliente;
        c.estado = estado;
        c.motivoAnulacion = motivoAnulacion;
        c.fechaAnulacion = fechaAnulacion;
        c.anuladaPor = anuladaPor;
        c.fechaModificacion = fechaModificacion;
        for (LineaFactura l : lineas) {
            c.lineas.add(l.copiar());
        }
        c.recalcularTotales();
        return c;
    }



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