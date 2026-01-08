package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.Producto;


import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ProductoRepository {

    public void save(Producto producto) throws SQLException {
        String sql = "INSERT INTO producto (codigo_barra, nombre, precio_base) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, producto.getCodigoBarra());
            ps.setString(2, producto.getNombre());
            ps.setBigDecimal(3, producto.getPrecioBase());
            ps.executeUpdate();

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


    public void update(Producto producto) throws SQLException {
        String sql = "UPDATE producto SET nombre = ?, precio_base = ? WHERE codigo_barra = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, producto.getNombre());
            ps.setBigDecimal(2, producto.getPrecioBase());
            ps.setString(3, producto.getCodigoBarra());

            int filasActualizadas = ps.executeUpdate();

            if (filasActualizadas > 0) {
                System.out.println("✓ Producto actualizado en BD: " + producto.getNombre());
            } else {
                System.out.println("⚠ No se encontró el producto para actualizar: " + producto.getCodigoBarra());
            }
        }
    }

    public List<Producto> findAll() throws SQLException {
        List<Producto> productos = new ArrayList<>();

        String sql = "SELECT * FROM producto ORDER BY nombre";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Producto producto = new Producto(
                        rs.getLong("id"),
                        rs.getString("codigo_barra"),
                        rs.getString("nombre"),
                        rs.getBigDecimal("precio_base")
                );
                productos.add(producto);
            }
        }

        return productos;
    }

    public List<Producto> findByNombre(String nombre) throws SQLException {
        List<Producto> productos = new ArrayList<>();

        String sql = "SELECT * FROM producto WHERE LOWER(nombre) LIKE ? ORDER BY nombre";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + nombre.toLowerCase() + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Producto producto = new Producto(
                            rs.getLong("id"),
                            rs.getString("codigo_barra"),
                            rs.getString("nombre"),
                            rs.getBigDecimal("precio_base")
                    );
                    productos.add(producto);
                }
            }
        }

        return productos;
    }

    public void delete(String codigoBarra) throws SQLException {
        String sql = "DELETE FROM producto WHERE codigo_barra = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, codigoBarra);

            int filas = ps.executeUpdate();

            if (filas > 0) {
                System.out.println("✓ Producto eliminado: " + codigoBarra);
            } else {
                System.out.println("⚠ No se encontró el producto para eliminar: " + codigoBarra);
            }
        }
    }
}