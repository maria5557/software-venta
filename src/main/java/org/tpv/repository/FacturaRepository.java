package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class FacturaRepository {

    public List<Factura> findAll() throws SQLException {
        List<Factura> facturas = new ArrayList<>();
        String sql = "SELECT * FROM factura ORDER BY fecha_emision DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Factura factura = mapearFactura(rs);
                //cargarLineasFactura(factura, conn);
                facturas.add(factura);
            }
        }

        return facturas;
    }

    public List<Factura> findByFechaRango(LocalDateTime fechaInicio, LocalDateTime fechaFin) throws SQLException {
        List<Factura> facturas = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM factura WHERE 1=1");

        if (fechaInicio != null) {
            sql.append(" AND fecha_emision >= ?");
        }
        if (fechaFin != null) {
            sql.append(" AND fecha_emision <= ?");
        }
        sql.append(" ORDER BY fecha_emision DESC");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (fechaInicio != null) {
                ps.setTimestamp(paramIndex++, Timestamp.valueOf(fechaInicio));
            }
            if (fechaFin != null) {
                ps.setTimestamp(paramIndex++, Timestamp.valueOf(fechaFin));
            }

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Factura factura = mapearFactura(rs);
                    // cargarLineasFactura(factura, conn);
                    facturas.add(factura);
                }
            }
        }

        return facturas;
    }

    public List<Factura> findByCliente(String busqueda) throws SQLException {
        List<Factura> facturas = new ArrayList<>();
        String sql = """
            SELECT f.* FROM factura f
            LEFT JOIN cliente c ON f.cliente_dni = c.dni
            WHERE LOWER(c.nombre) LIKE LOWER(?) 
               OR LOWER(c.dni) LIKE LOWER(?)
               OR LOWER(f.cliente_nombre) LIKE LOWER(?)
            ORDER BY f.fecha_emision DESC
            """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String busquedaParam = "%" + busqueda + "%";
            ps.setString(1, busquedaParam);
            ps.setString(2, busquedaParam);
            ps.setString(3, busquedaParam);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Factura factura = mapearFactura(rs);
                    // cargarLineasFactura(factura, conn);
                    facturas.add(factura);
                }
            }
        }

        return facturas;
    }

    private Factura mapearFactura(ResultSet rs) throws SQLException {
        Factura factura = new Factura();
        factura.setId(rs.getLong("id"));
        factura.setNumeroFactura(rs.getString("numero_factura"));
        factura.setFecha(rs.getTimestamp("fecha_emision").toLocalDateTime());
        factura.setClienteDni(rs.getString("cliente_dni"));
        factura.setClienteNombre(rs.getString("cliente_nombre"));
        factura.setTotalSinIva(rs.getBigDecimal("total_sin_iva"));
        factura.setTotalIva(rs.getBigDecimal("total_iva"));
        factura.setTotalConIva(rs.getBigDecimal("total_con_iva"));
        return factura;
    }
/*
    private void cargarLineasFactura(Factura factura, Connection conn) throws SQLException {
        String sql = "SELECT * FROM linea_factura WHERE factura_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, factura.getId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LineaFactura linea = new LineaFactura(
                            rs.getString("codigo_producto"),
                            rs.getString("nombre_producto"),
                            rs.getInt("cantidad"),
                            rs.getBigDecimal("precio_unitario"),
                            rs.getInt("porcentaje_iva"),
                            rs.getInt("descuento")
                    );
                    factura.añadirLinea(linea);
                }
            }
        }

    }
 */


}