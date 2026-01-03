package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.*;

import java.time.LocalDateTime;

public class VentaService {

    private Factura facturaActual;
    private final Configuracion configuracion;

    public VentaService(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    public void iniciarVenta() {
        facturaActual = new Factura();
        facturaActual.setFecha(LocalDateTime.now());
    }

    public void añadirProducto(Producto producto) {

        for (LineaFactura linea : facturaActual.getLineas()) {
            if (linea.getProductoId() != null &&
                    linea.getCodigoProducto().equals(producto.getCodigoBarra())) {

                linea.setCantidad(linea.getCantidad() + 1);
                facturaActual.recalcularTotales();
                return;
            }
        }

        LineaFactura nuevaLinea = new LineaFactura(
                null,
                null,
                producto.getId(),
                producto.getCodigoBarra(),
                producto.getNombre(),
                producto.getPrecioBase(),
                1,
                configuracion.getIvaGeneral()
        );

        facturaActual.añadirLinea(nuevaLinea);
    }

    public Factura finalizarVenta() {
        return facturaActual;
    }
}
