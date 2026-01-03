package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.Producto;

import java.sql.*;

public class ProductoRepository {

    public void save(Producto producto) throws SQLException {
        String sql = "INSERT INTO producto (codigo_barra, nombre, precio_base) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, producto.getCodigoBarra());
            ps.setString(2, producto.getNombre());
            ps.setBigDecimal(3, producto.getPrecioBase());
            ps.executeUpdate();

            // Recuperar id generado
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    producto.setId(rs.getLong(1));
                }
            }
        }
    }


    public Producto findByCodigo(String codigoBarra) throws SQLException {
        String sql = "SELECT * FROM producto WHERE codigo_barra = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codigoBarra);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Producto(
                            rs.getLong("id"),
                            rs.getString("codigo_barra"),
                            rs.getString("nombre"),
                            rs.getBigDecimal("precio_base")
                    );
                }
            }
        }
        return null;
    }
}
