package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.Empleado;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class EmpleadoRepository {
    /**
     * Busca un empleado por su ID.
     */
    public Empleado findById(Long id) throws SQLException {
        String sql = "SELECT * FROM empleado WHERE id = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new Empleado(
                            rs.getLong("id"),
                            rs.getString("nombre"),
                            rs.getString("usuario"),
                            rs.getString("password")
                    );
                }
            }
        }

        return null; // No encontrado
    }
}
