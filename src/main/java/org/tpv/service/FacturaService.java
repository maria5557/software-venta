package org.tpv.service;

import org.tpv.domain.Factura;
import org.tpv.repository.FacturaRepository;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class FacturaService {

    private FacturaRepository facturaRepository;

    public FacturaService() {
        this.facturaRepository = new FacturaRepository();
    }

    /**
     * Obtiene todas las facturas ordenadas por fecha descendente
     */
    public List<Factura> obtenerTodas() throws SQLException {
        return facturaRepository.findAll();
    }

    /**
     * Busca facturas por rango de fechas
     * @param fechaInicio Fecha de inicio (puede ser null)
     * @param fechaFin Fecha de fin (puede ser null)
     * @return Lista de facturas en el rango especificado
     */
    public List<Factura> buscarPorFechas(LocalDateTime fechaInicio, LocalDateTime fechaFin) throws SQLException {
        return facturaRepository.findByFechaRango(fechaInicio, fechaFin);
    }

    /**
     * Busca facturas por nombre o DNI del cliente
     * @param busqueda Texto a buscar en nombre o DNI
     * @return Lista de facturas que coinciden con la búsqueda
     */
    public List<Factura> buscarPorCliente(String busqueda) throws SQLException {
        if (busqueda == null || busqueda.trim().isEmpty()) {
            throw new IllegalArgumentException("La búsqueda no puede estar vacía");
        }
        return facturaRepository.findByCliente(busqueda.trim());
    }

    /**
     * Obtiene una factura por su ID
     */
    public Factura obtenerPorId(Long id) throws SQLException {
        // Implementar cuando sea necesario
        throw new UnsupportedOperationException("Método no implementado aún");
    }

    public void guardarFactura(Factura factura) throws SQLException {
        facturaRepository.save(factura);
    }
}