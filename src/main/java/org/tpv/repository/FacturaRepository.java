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
        String sql = "SELECT * FROM factura ORDER BY fechaEmision DESC";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Factura factura = mapearFactura(rs);
                cargarLineasFactura(factura, conn);
                facturas.add(factura);
            }
        }

        return facturas;
    }

    public List<Factura> findByFechaRango(LocalDateTime fechaInicio, LocalDateTime fechaFin) throws SQLException {
        List<Factura> facturas = new ArrayList<>();
        StringBuilder sql = new StringBuilder("SELECT * FROM factura WHERE 1=1");

        if (fechaInicio != null) {
            sql.append(" AND fechaEmision >= ?");
        }
        if (fechaFin != null) {
            sql.append(" AND fechaEmision <= ?");
        }
        sql.append(" ORDER BY fechaEmision DESC");

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            int paramIndex = 1;
            if (fechaInicio != null) {
                ps.setString(paramIndex++, fechaInicio.toString());
            }
            if (fechaFin != null) {
                ps.setString(paramIndex++, fechaFin.toString());
            }


            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Factura factura = mapearFactura(rs);
                    cargarLineasFactura(factura, conn);
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
            ORDER BY f.fechaEmision DESC
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
                    cargarLineasFactura(factura, conn);
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
        factura.setFechaEmision(
                LocalDateTime.parse(rs.getString("fechaEmision"))
        );        factura.setClienteDni(rs.getString("cliente_dni"));
        factura.setClienteNombre(rs.getString("cliente_nombre"));
        factura.setTotalSinIva(rs.getBigDecimal("total_sin_iva"));
        factura.setTotalIva(rs.getBigDecimal("total_iva"));
        factura.setTotalConIva(rs.getBigDecimal("total_con_iva"));
        return factura;
    }

    private void cargarLineasFactura(Factura factura, Connection conn) throws SQLException {
        String sql = "SELECT * FROM linea_factura WHERE factura_id = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, factura.getId());

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    LineaFactura linea = new LineaFactura(
                            rs.getLong("id"),
                            rs.getLong("factura_id"),
                            rs.getLong("producto_id"),
                            rs.getString("codigo_producto"),
                            rs.getString("nombre_producto"),
                            rs.getBigDecimal("precio_unitario"),
                            rs.getInt("cantidad"),
                            rs.getInt("iva_aplicado")
                    );
                    linea.setDescuento(rs.getInt("descuento"));
                    factura.añadirLinea(linea);
                }
            }
        }
    }


    public void save(Factura factura) throws SQLException {
        String sqlFactura = """
        INSERT INTO factura (
            numero_factura,
            fechaEmision,
            cliente_dni,
            cliente_nombre,
            total_sin_iva,
            total_iva,
            total_con_iva
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    sqlFactura, Statement.RETURN_GENERATED_KEYS)) {

                ps.setString(1, factura.getNumeroFactura());
                ps.setString(2, factura.getFechaEmision().toString());
                ps.setString(3, factura.getClienteDni());
                ps.setString(4, factura.getClienteNombre());
                ps.setBigDecimal(5, factura.getTotalSinIva());
                ps.setBigDecimal(6, factura.getTotalIva());
                ps.setBigDecimal(7, factura.getTotalConIva());

                ps.executeUpdate();

                // Obtener ID generado
                ResultSet keys = ps.getGeneratedKeys();
                if (keys.next()) {
                    factura.setId(keys.getLong(1));
                }
            }

            // Guardar líneas
            guardarLineasFactura(factura, conn);

            conn.commit();
        }
    }

    private void guardarLineasFactura(Factura factura, Connection conn) throws SQLException {
        String sqlLinea = """
        INSERT INTO linea_factura (
            factura_id,
            producto_id,
            codigo_producto,
            nombre_producto,
            precio_unitario,
            cantidad,
            iva_aplicado,
            descuento
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement ps = conn.prepareStatement(sqlLinea)) {
            for (LineaFactura linea : factura.getLineas()) {
                ps.setLong(1, factura.getId());
                ps.setLong(2, linea.getProductoId());
                ps.setString(3, linea.getCodigoProducto());
                ps.setString(4, linea.getNombreProducto());
                ps.setBigDecimal(5, linea.getPrecioUnitario());
                ps.setInt(6, linea.getCantidad());
                ps.setInt(7, linea.getIvaAplicado());
                ps.setInt(8, linea.getDescuento());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }


}