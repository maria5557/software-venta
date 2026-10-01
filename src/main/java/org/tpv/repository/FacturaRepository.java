package org.tpv.repository;

import org.tpv.database.DatabaseManager;
import org.tpv.domain.EstadoFactura;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.RegistroAuditoria;

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

    /**
     * Devuelve el mayor secuencial usado en un año (0 si aún no hay facturas).
     * Incluye las facturas ANULADAS: su número queda reservado para siempre.
     * No depende del orden de los ids ni de las fechas (que ahora son editables).
     */
    public int findMaxSecuencialDelAnio(int anio) throws SQLException {
        String sql = "SELECT MAX(CAST(substr(numero_factura, 6) AS INTEGER)) FROM factura "
                + "WHERE numero_factura LIKE ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, anio + "-%");
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1); // NULL -> 0
                }
            }
        }
        return 0;
    }

    public Factura findById(Long id) throws SQLException {
        String sql = "SELECT * FROM factura WHERE id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Factura factura = mapearFactura(rs);
                    cargarLineasFactura(factura, conn);
                    return factura;
                }
            }
        }
        return null;
    }

    /**
     * Fecha de emisión de la factura inmediatamente anterior en la numeración
     * del mismo año (o null si no existe). Se usa para avisar si al editar una
     * fecha se rompe el orden cronológico de la serie.
     */
    public LocalDateTime findFechaVecinaAnterior(String numeroFactura) throws SQLException {
        return findFechaVecina(numeroFactura, true);
    }

    public LocalDateTime findFechaVecinaPosterior(String numeroFactura) throws SQLException {
        return findFechaVecina(numeroFactura, false);
    }

    private LocalDateTime findFechaVecina(String numeroFactura, boolean anterior) throws SQLException {
        if (numeroFactura == null || numeroFactura.length() < 5) return null;
        String prefijo = numeroFactura.substring(0, 5); // "2026-"
        String sql = "SELECT fechaEmision FROM factura WHERE numero_factura LIKE ? AND numero_factura "
                + (anterior ? "< ? ORDER BY numero_factura DESC" : "> ? ORDER BY numero_factura ASC")
                + " LIMIT 1";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, prefijo + "%");
            ps.setString(2, numeroFactura);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return LocalDateTime.parse(rs.getString(1));
                }
            }
        }
        return null;
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
        SELECT f.* 
        FROM factura f
        LEFT JOIN cliente c ON f.cliente_id = c.id
        WHERE LOWER(c.nombre) LIKE LOWER(?) 
           OR LOWER(COALESCE(c.dni, '')) LIKE LOWER(?) 
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
        factura.setFechaEmision(LocalDateTime.parse(rs.getString("fechaEmision")));

        // Cliente
        long clienteId = rs.getLong("cliente_id");
        factura.setClienteId(rs.wasNull() ? null : clienteId);
        factura.setClienteNombre(rs.getString("cliente_nombre"));

        factura.setEmpleadoId(rs.getObject("empleado_id") != null ? rs.getLong("empleado_id") : null);
        factura.setEmpleadoNombre(rs.getString("empleado_nombre"));

        // Totales y pago
        factura.setTotalSinIva(rs.getBigDecimal("total_sin_iva"));
        factura.setTotalIva(rs.getBigDecimal("total_iva"));
        factura.setTotalConIva(rs.getBigDecimal("total_con_iva"));
        factura.setMetodoPago(rs.getString("metodo_pago"));
        factura.setEntregadoCliente(rs.getBigDecimal("entregado_cliente"));

        // Ciclo de vida
        factura.setEstado(EstadoFactura.desdeTexto(rs.getString("estado")));
        factura.setMotivoAnulacion(rs.getString("motivo_anulacion"));
        factura.setAnuladaPor(rs.getString("anulada_por"));
        factura.setFechaAnulacion(parseFecha(rs.getString("fecha_anulacion")));
        factura.setFechaModificacion(parseFecha(rs.getString("fecha_modificacion")));

        return factura;
    }

    private static LocalDateTime parseFecha(String valor) {
        return (valor == null || valor.isBlank()) ? null : LocalDateTime.parse(valor);
    }

    private void cargarLineasFactura(Factura factura, Connection conn) throws SQLException {
        String sql = "SELECT * FROM linea_factura WHERE factura_id = ? ORDER BY id";

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


    public void guardar(Factura factura) throws SQLException {
        String sqlFactura = """
        INSERT INTO factura (
            numero_factura,
            fechaEmision,
            total_sin_iva, 
            total_iva,   
            total_con_iva,                 
            cliente_id,                 
            cliente_nombre,
            empleado_id,
            empleado_nombre,
            metodo_pago,
            entregado_cliente                 
        ) VALUES (?, ?, ?, ?, ?, ?, ?,?,?,?,?)
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement ps = conn.prepareStatement(
                    sqlFactura, Statement.RETURN_GENERATED_KEYS)) {

                // 2. Mapeo correcto y ordenado de los 9 parámetros
                ps.setString(1, factura.getNumeroFactura());
                ps.setString(2, factura.getFechaEmision().toString());
                ps.setBigDecimal(3, factura.getTotalSinIva());
                ps.setBigDecimal(4, factura.getTotalIva());
                ps.setBigDecimal(5, factura.getTotalConIva());
                if (factura.getClienteId() != null) {
                    ps.setLong(6, factura.getClienteId());
                } else {
                    ps.setNull(6, Types.INTEGER);
                }
                ps.setString(7, factura.getClienteNombre());
                // Protección para evitar NullPointerException con Long
                if (factura.getEmpleadoId() != null) {
                    ps.setLong(8, factura.getEmpleadoId());
                } else {
                    ps.setNull(8, java.sql.Types.INTEGER);
                }
                ps.setString(9,factura.getEmpleadoNombre());
                ps.setString(10, factura.getMetodoPago());
                ps.setBigDecimal(11, factura.getEntregadoCliente());


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
                if (linea.getProductoId() != null) {
                    ps.setLong(2, linea.getProductoId());
                } else {
                    ps.setNull(2, Types.INTEGER);
                }
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

    // ------------------------------------------------------------------
    // MODIFICACIÓN, ANULACIÓN Y AUDITORÍA
    // ------------------------------------------------------------------

    /**
     * Actualiza una factura (cabecera + líneas) y registra la auditoría en la
     * MISMA transacción. El número de factura NO se toca nunca.
     * Usa control de concurrencia optimista: si otro puesto modificó la factura
     * desde que se cargó, no se pisa y se lanza {@link ConflictoConcurrenciaException}.
     */
    public void actualizar(Factura factura, LocalDateTime fechaModificacionOriginal,
                           RegistroAuditoria auditoria) throws SQLException {
        String sql = """
        UPDATE factura SET
            fechaEmision = ?,
            total_sin_iva = ?,
            total_iva = ?,
            total_con_iva = ?,
            cliente_id = ?,
            cliente_nombre = ?,
            metodo_pago = ?,
            entregado_cliente = ?,
            fecha_modificacion = ?
        WHERE id = ?
          AND estado = 'EMITIDA'
          AND COALESCE(fecha_modificacion, '') = ?
        """;

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDateTime ahora = LocalDateTime.now();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, factura.getFechaEmision().toString());
                    ps.setBigDecimal(2, factura.getTotalSinIva());
                    ps.setBigDecimal(3, factura.getTotalIva());
                    ps.setBigDecimal(4, factura.getTotalConIva());
                    if (factura.getClienteId() != null) {
                        ps.setLong(5, factura.getClienteId());
                    } else {
                        ps.setNull(5, Types.INTEGER);
                    }
                    ps.setString(6, factura.getClienteNombre());
                    ps.setString(7, factura.getMetodoPago());
                    ps.setBigDecimal(8, factura.getEntregadoCliente());
                    ps.setString(9, ahora.toString());
                    ps.setLong(10, factura.getId());
                    ps.setString(11, fechaModificacionOriginal == null ? "" : fechaModificacionOriginal.toString());

                    if (ps.executeUpdate() == 0) {
                        throw new ConflictoConcurrenciaException(
                                "La factura ha sido modificada o anulada desde otro puesto mientras la editabas. "
                                        + "Recarga la lista y vuelve a intentarlo.");
                    }
                }

                // Reemplazo de líneas (nada referencia el id de una línea)
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM linea_factura WHERE factura_id = ?")) {
                    del.setLong(1, factura.getId());
                    del.executeUpdate();
                }
                guardarLineasFactura(factura, conn);

                insertarAuditoria(conn, auditoria, ahora);
                conn.commit();
                factura.setFechaModificacion(ahora);
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /**
     * Anulación lógica: la fila y su número se conservan. Solo se puede anular
     * una factura EMITIDA.
     */
    public void anular(Long facturaId, String motivo, String usuario,
                       RegistroAuditoria auditoria) throws SQLException {
        String sql = """
        UPDATE factura SET
            estado = 'ANULADA',
            motivo_anulacion = ?,
            fecha_anulacion = ?,
            anulada_por = ?,
            fecha_modificacion = ?
        WHERE id = ? AND estado = 'EMITIDA'
        """;
        cambiarEstado(sql, facturaId, motivo, usuario, auditoria, "La factura ya estaba anulada.");
    }

    /**
     * Revierte una anulación (por ejemplo, si se anuló por error).
     * El número de factura es el mismo, por lo que la serie no se altera.
     */
    public void restaurar(Long facturaId, String usuario, RegistroAuditoria auditoria) throws SQLException {
        String sql = """
        UPDATE factura SET
            estado = 'EMITIDA',
            motivo_anulacion = NULL,
            fecha_anulacion = NULL,
            anulada_por = NULL,
            fecha_modificacion = ?
        WHERE id = ? AND estado = 'ANULADA'
        """;
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDateTime ahora = LocalDateTime.now();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, ahora.toString());
                    ps.setLong(2, facturaId);
                    if (ps.executeUpdate() == 0) {
                        throw new ConflictoConcurrenciaException("La factura no está anulada (¿otro puesto la restauró ya?).");
                    }
                }
                insertarAuditoria(conn, auditoria, ahora);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void cambiarEstado(String sql, Long facturaId, String motivo, String usuario,
                               RegistroAuditoria auditoria, String mensajeConflicto) throws SQLException {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                LocalDateTime ahora = LocalDateTime.now();
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, motivo);
                    ps.setString(2, ahora.toString());
                    ps.setString(3, usuario);
                    ps.setString(4, ahora.toString());
                    ps.setLong(5, facturaId);
                    if (ps.executeUpdate() == 0) {
                        throw new ConflictoConcurrenciaException(mensajeConflicto);
                    }
                }
                insertarAuditoria(conn, auditoria, ahora);
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void insertarAuditoria(Connection conn, RegistroAuditoria a, LocalDateTime fecha) throws SQLException {
        String sql = """
        INSERT INTO factura_auditoria (factura_id, numero_factura, accion, detalle, motivo, usuario, fecha)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, a.getFacturaId());
            ps.setString(2, a.getNumeroFactura());
            ps.setString(3, a.getAccion());
            ps.setString(4, a.getDetalle());
            ps.setString(5, a.getMotivo());
            ps.setString(6, a.getUsuario());
            ps.setString(7, fecha.toString());
            ps.executeUpdate();
        }
    }

    public List<RegistroAuditoria> findAuditoria(Long facturaId) throws SQLException {
        List<RegistroAuditoria> registros = new ArrayList<>();
        String sql = "SELECT * FROM factura_auditoria WHERE factura_id = ? ORDER BY fecha DESC, id DESC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, facturaId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    registros.add(new RegistroAuditoria(
                            rs.getLong("id"),
                            rs.getLong("factura_id"),
                            rs.getString("numero_factura"),
                            rs.getString("accion"),
                            rs.getString("detalle"),
                            rs.getString("motivo"),
                            rs.getString("usuario"),
                            LocalDateTime.parse(rs.getString("fecha"))));
                }
            }
        }
        return registros;
    }

    /** El registro cambió desde otro puesto entre la carga y el guardado. */
    public static class ConflictoConcurrenciaException extends SQLException {
        public ConflictoConcurrenciaException(String mensaje) {
            super(mensaje);
        }
    }
}
