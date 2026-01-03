package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.Factura;
import org.tpv.domain.Producto;

public class VentaService {
    private Factura facturaActual;
    private Configuracion configuracion;

    public void iniciarVenta();
    public void añadirProducto(Producto producto);
    public Factura finalizarVenta();


}
