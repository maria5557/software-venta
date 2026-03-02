package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.Cliente;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ClienteRepository {

    public Cliente findById(Long id) throws SQLException {
        String sql = "SELECT * FROM cliente WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Cliente(
                            rs.getLong("id"),
                            rs.getString("dni"),
                            rs.getString("nombre"),
                            rs.getString("telefono"),
                            rs.getString("direccion"),
                            rs.getString("email")
                    );
                }
            }
        }

        return null; // No encontrado
    }


    public void save(Cliente cliente) throws SQLException {
        String sql = "INSERT INTO cliente (dni, nombre, telefono, direccion, email) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            // Gestión de NULL para el DNI (y otros campos)
            if (cliente.getDni() == null || cliente.getDni().trim().isEmpty()) {
                ps.setNull(1, Types.VARCHAR);
            } else {
                ps.setString(1, cliente.getDni());
            }

            ps.setString(2, cliente.getNombre());
            ps.setString(3, cliente.getTelefono());
            ps.setString(4, cliente.getDireccion());
            ps.setString(5, cliente.getEmail());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    cliente.setId(rs.getLong(1));
                }
            }
        }
    }

    public Cliente findByDni(String dni) throws SQLException {
        String sql = "SELECT * FROM cliente WHERE dni = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, dni);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Cliente(
                            rs.getLong("id"),
                            rs.getString("dni"),
                            rs.getString("nombre"),
                            rs.getString("telefono"),
                            rs.getString("direccion"),
                            rs.getString("email")
                    );
                }
            }
        }
        return null;
    }

    public List<Cliente> findByNombre(String nombre) throws SQLException {
        List<Cliente> clientes = new ArrayList<>();
        String sql = "SELECT * FROM cliente WHERE LOWER(nombre) LIKE LOWER(?) ORDER BY nombre";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, "%" + nombre + "%");

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente cliente = new Cliente(
                            rs.getLong("id"),
                            rs.getString("dni"),
                            rs.getString("nombre"),
                            rs.getString("telefono"),
                            rs.getString("direccion"),
                            rs.getString("email")
                    );
                    clientes.add(cliente);
                }
            }
        }
        return clientes;
    }

    public void update(Cliente cliente) throws SQLException {
        String sql = "UPDATE cliente SET dni = ?, nombre = ?, telefono = ?, direccion = ?, email = ? WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, cliente.getDni());
            ps.setString(2, cliente.getNombre());
            ps.setString(3, cliente.getTelefono());
            ps.setString(4, cliente.getDireccion());
            ps.setString(5, cliente.getEmail());
            ps.setLong(6, cliente.getId());


            int filasActualizadas = ps.executeUpdate();

            if (filasActualizadas > 0) {
                System.out.println("✓ Cliente actualizado en BD: " + cliente.getNombre());
            } else {
                System.out.println("⚠ No se encontró el cliente para actualizar: " + cliente.getDni());
                System.out.println("El id proporcionado es: " + cliente.getId());
            }
        }
    }

    public void delete(Long id) throws SQLException {
        String sql = "DELETE FROM cliente WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);
            int filasEliminadas = ps.executeUpdate();

            if (filasEliminadas > 0) {
                System.out.println("✓ Cliente eliminado de BD: " + id);
            } else {
                System.out.println("⚠ No se encontró el cliente para eliminar: " + id);
            }
        }
    }

    public List<Cliente> findAll() throws SQLException {
        List<Cliente> clientes = new ArrayList<>();
        String sql = "SELECT * FROM cliente ORDER BY nombre";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Cliente cliente = new Cliente(
                        rs.getLong("id"),
                        rs.getString("dni"),
                        rs.getString("nombre"),
                        rs.getString("telefono"),
                        rs.getString("direccion"),
                        rs.getString("email")
                );
                clientes.add(cliente);
            }
        }

        return clientes;
    }

    public List<Cliente> findByNombreOrDni(String filtro) throws SQLException {
        List<Cliente> clientes = new ArrayList<>();

        String sql = "SELECT * FROM cliente " +
                "WHERE LOWER(nombre) LIKE LOWER(?) " +
                "   OR dni LIKE ? " +
                "ORDER BY nombre";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            String like = "%" + filtro + "%";

            ps.setString(1, like); // nombre
            ps.setString(2, like); // dni

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Cliente cliente = new Cliente(
                            rs.getLong("id"),
                            rs.getString("dni"),
                            rs.getString("nombre"),
                            rs.getString("telefono"),
                            rs.getString("direccion"),
                            rs.getString("email")
                    );
                    clientes.add(cliente);
                }
            }
        }

        return clientes;
    }

}