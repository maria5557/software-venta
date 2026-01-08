package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class VentaService {

    private Factura facturaActual;
    private final Configuracion configuracion;
    private int contadorFacturas = 1;

    public VentaService(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    public void iniciarVenta() {
        facturaActual = new Factura();
        facturaActual.setFecha(LocalDateTime.now());

        // Generar número de factura
        String numeroFactura = generarNumeroFactura();
        facturaActual.setNumeroFactura(numeroFactura);

        // Asignar empleado por defecto
        Empleado empleado = Empleado.empleadoPorDefecto();
        facturaActual.setEmpleadoId(empleado.getId());
        facturaActual.setEmpleadoNombre(empleado.getNombre());

        // Asignar cliente por defecto
        Cliente cliente = Cliente.clientePorDefecto();
        facturaActual.setClienteId(cliente.getId());
        facturaActual.setClienteNombre(cliente.getNombre());
        facturaActual.setClienteDni(cliente.getDni());
    }

    private String generarNumeroFactura() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd");
        String fecha = LocalDateTime.now().format(formatter);
        String numero = String.format("%06d", contadorFacturas++);
        return fecha + "-" + numero;
    }

    public void asignarCliente(Cliente cliente) {
        if (facturaActual != null) {
            facturaActual.setClienteId(cliente.getId());
            facturaActual.setClienteNombre(cliente.getNombre());
            facturaActual.setClienteDni(cliente.getDni());
        }
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

    public Cliente getClienteActual() {
        if (facturaActual == null) return Cliente.clientePorDefecto();

        Cliente cliente = new Cliente();
        cliente.setId(facturaActual.getClienteId());
        cliente.setNombre(facturaActual.getClienteNombre());
        cliente.setDni(facturaActual.getClienteDni());
        return cliente;
    }
}