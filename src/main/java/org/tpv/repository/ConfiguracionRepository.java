package org.tpv.repository;

import org.tpv.config.Configuracion;
import org.tpv.database.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ConfiguracionRepository {

    /**
     * Obtiene la configuración de la base de datos.
     * Alias de obtenerConfiguracion para compatibilidad con FacturasController.
     */
    public Configuracion findFirst() throws SQLException {
        return obtenerConfiguracion();
    }

    /**
     * Obtiene la configuración de la base de datos (ID = 1)
     */
    public Configuracion obtenerConfiguracion() throws SQLException {
        String sql = "SELECT * FROM configuracion WHERE id = 1";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            if (rs.next()) {
                Configuracion config = new Configuracion();
                config.setIvaGeneral(rs.getInt("iva_general"));
                config.setNombreTienda(rs.getString("nombre_tienda"));
                config.setDireccion(rs.getString("direccion"));
                config.setCiudad(rs.getString("ciudad"));
                config.setCodigoPostal(rs.getString("codigo_postal"));
                config.setTelefono(rs.getString("telefono"));
                config.setCif(rs.getString("cif"));
                config.setNif(rs.getString("nif"));
                config.setEmail(rs.getString("email"));
                return config;
            }
        }

        // Si no existe, retornar configuración por defecto
        return new Configuracion();
    }

    /**
     * Actualiza la configuración en la base de datos
     */
    public void actualizarConfiguracion(Configuracion config) throws SQLException {
        String sql = """
            UPDATE configuracion SET
                iva_general = ?,
                nombre_tienda = ?,
                direccion = ?,
                ciudad = ?,
                codigo_postal = ?,
                telefono = ?,
                cif = ?,
                nif = ?,
                email = ?
            WHERE id = 1
        """;

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, config.getIvaGeneral());
            ps.setString(2, config.getNombreTienda());
            ps.setString(3, config.getDireccion());
            ps.setString(4, config.getCiudad());
            ps.setString(5, config.getCodigoPostal());
            ps.setString(6, config.getTelefono());
            ps.setString(7, config.getCif());
            ps.setString(8, config.getNif());
            ps.setString(9, config.getEmail());

            ps.executeUpdate();
            System.out.println("✓ Configuración actualizada en BD");
        }
    }
}
