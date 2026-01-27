package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.*;

import java.sql.SQLException;
import java.time.LocalDateTime;

public class VentaService {

    private Factura facturaActual;
    private final Configuracion configuracion;
    private final FacturaService facturaService = new FacturaService();
    private final ClienteService clienteService = new ClienteService();


    public VentaService(Configuracion configuracion) {
        this.configuracion = configuracion;
    }

    public void iniciarVenta() {
        facturaActual = new Factura();
        facturaActual.setFechaEmision(LocalDateTime.now());

        // ⭐ El número se generará al FINALIZAR o GUARDAR para asegurar que sea el último real
        facturaActual.setNumeroFactura("PENDIENTE");

        // Asignar empleado por defecto
        Empleado empleado = Empleado.empleadoPorDefecto();
        facturaActual.setEmpleadoId(empleado.getId());
        facturaActual.setEmpleadoNombre(empleado.getNombre());

        // Asignar cliente por defecto
        Cliente cliente = clienteService.obtenerClientePorDefecto();
        facturaActual.setClienteId(cliente.getId());
        facturaActual.setClienteNombre(cliente.getNombre());
    }

    public void guardarVenta() throws SQLException {
        // ⭐ Antes de guardar, generamos el número real único
        String nuevoNumero = facturaService.generarSiguienteNumeroFactura();
        facturaActual.setNumeroFactura(nuevoNumero);

        // Guardamos usando el repositorio
        facturaService.guardarFactura(facturaActual);
    }

    public void asignarCliente(Cliente cliente) {
        if (facturaActual != null) {
            facturaActual.setClienteId(cliente.getId());
            facturaActual.setClienteNombre(cliente.getNombre());
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
        if (facturaActual == null) return clienteService.obtenerClientePorDefecto();

        Cliente cliente = new Cliente();
        cliente.setId(facturaActual.getClienteId());
        cliente.setNombre(facturaActual.getClienteNombre());
        return cliente;
    }
}
