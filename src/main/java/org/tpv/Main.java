package org.tpv;

import org.tpv.config.Configuracion;
import org.tpv.domain.Factura;
import org.tpv.domain.Producto;
import org.tpv.service.ProductoService;
import org.tpv.service.VentaService;

import java.math.BigDecimal;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    public static void main(String[] args) {
        Configuracion config = new Configuracion(21);
        ProductoService productoService = new ProductoService();
        VentaService ventaService = new VentaService(config);

        Producto p1 = productoService.crearProducto("123", "Leche", new BigDecimal("1.20"));
        Producto p2 = productoService.crearProducto("456", "Pan", new BigDecimal("0.80"));

        ventaService.iniciarVenta();
        ventaService.añadirProducto(p1);
        ventaService.añadirProducto(p1);
        ventaService.añadirProducto(p2);

        Factura factura = ventaService.finalizarVenta();

        System.out.println("Total sin IVA: " + factura.getTotalSinIva());
        System.out.println("IVA: " + factura.getTotalIva());
        System.out.println("Total con IVA: " + factura.getTotalConIva());
    }
}