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

    public List<Factura> buscarPorFechas(LocalDateTime fechaInicio, LocalDateTime fechaFin) throws SQLException {
        return facturaRepository.findByFechaRango(fechaInicio, fechaFin);
    }

    public List<Factura> buscarPorCliente(String busqueda) throws SQLException {
        if (busqueda == null || busqueda.trim().isEmpty()) {
            throw new IllegalArgumentException("La búsqueda no puede estar vacía");
        }
        return facturaRepository.findByCliente(busqueda.trim());
    }

    public Factura obtenerPorId(Long id) throws SQLException {
        // Implementar cuando sea necesario
        throw new UnsupportedOperationException("Método no implementado aún");
    }

    /**
     * Genera el siguiente número de factura secuencial único.
     * Formato: AÑO-000001 (ej: 2026-000001)
     */
    public synchronized String generarSiguienteNumeroFactura() throws SQLException {
        String ultimoNumero = facturaRepository.findLastNumeroFactura();
        int añoActual = LocalDateTime.now().getYear();
        int siguienteSecuencial = 1;

        if (ultimoNumero != null && ultimoNumero.contains("-")) {
            try {
                String[] partes = ultimoNumero.split("-");
                int añoUltimo = Integer.parseInt(partes[0]);
                int secuencialUltimo = Integer.parseInt(partes[1]);

                if (añoActual == añoUltimo) {
                    siguienteSecuencial = secuencialUltimo + 1;
                }
                // Si el año ha cambiado, el secuencial vuelve a 1 (opcional, según normativa)
            } catch (NumberFormatException e) {
                // Si el formato no es el esperado, empezamos de 1
            }
        }

        return String.format("%d-%06d", añoActual, siguienteSecuencial);
    }

    public void guardarFactura(Factura factura) throws SQLException {
        // Aseguramos que la factura tenga un número único antes de guardar
        if (factura.getNumeroFactura() == null || factura.getNumeroFactura().isEmpty()) {
            factura.setNumeroFactura(generarSiguienteNumeroFactura());
        }
        facturaRepository.save(factura);
    }
}
