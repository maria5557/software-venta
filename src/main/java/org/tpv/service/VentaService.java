package org.tpv.service;

import org.tpv.config.Configuracion;
import org.tpv.domain.*;

import java.math.BigDecimal;
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
        facturaActual.setNumeroFactura("PENDIENTE");
        facturaActual.setMetodoPago("EFECTIVO");
        facturaActual.setEntregadoCliente(BigDecimal.ZERO);

        Empleado empleado = Empleado.empleadoPorDefecto();
        facturaActual.setEmpleadoId(empleado.getId());
        facturaActual.setEmpleadoNombre(empleado.getNombre());

        Cliente cliente = clienteService.obtenerClientePorDefecto();
        facturaActual.setClienteId(cliente.getId());
        facturaActual.setClienteNombre(cliente.getNombre());
    }

    public void guardarVenta() throws SQLException {
        String nuevoNumero = facturaService.generarSiguienteNumeroFactura();
        facturaActual.setNumeroFactura(nuevoNumero);
        facturaService.guardarFactura(facturaActual);
    }

    public void asignarCliente(Cliente cliente) {
        if (facturaActual != null) {
            facturaActual.setClienteId(cliente.getId());
            facturaActual.setClienteNombre(cliente.getNombre());
        }
    }

    /** "EFECTIVO" o "TARJETA" */
    public void setMetodoPago(String metodoPago) {
        if (facturaActual != null) facturaActual.setMetodoPago(metodoPago);
    }

    /** Importe en efectivo que entrega el cliente */
    public void setEntregadoCliente(BigDecimal importe) {
        if (facturaActual != null) facturaActual.setEntregadoCliente(importe);
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
        facturaActual.añadirLinea(new LineaFactura(
                null, null,
                producto.getId(),
                producto.getCodigoBarra(),
                producto.getNombre(),
                producto.getPrecioBase(),
                1,
                configuracion.getIvaGeneral()
        ));
    }

    public Factura finalizarVenta() {
        return facturaActual;
    }

    public Cliente getClienteActual() {
        if (facturaActual == null) return clienteService.obtenerClientePorDefecto();
        Cliente c = new Cliente();
        c.setId(facturaActual.getClienteId());
        c.setNombre(facturaActual.getClienteNombre());
        return c;
    }
}