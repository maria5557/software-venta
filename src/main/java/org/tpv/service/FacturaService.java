package org.tpv.service;

import org.tpv.config.SesionUsuario;
import org.tpv.domain.EstadoFactura;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;
import org.tpv.domain.RegistroAuditoria;
import org.tpv.repository.FacturaRepository;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class FacturaService {

    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
    private static final int MAX_REINTENTOS_NUMERACION = 5;

    private final FacturaRepository facturaRepository;

    public FacturaService() {
        this.facturaRepository = new FacturaRepository();
    }

    // ------------------------------------------------------------------
    // CONSULTAS
    // ------------------------------------------------------------------

    /**
     * Obtiene todas las facturas ordenadas por fecha descendente
     * (incluidas las anuladas; la capa de UI decide si mostrarlas).
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
        if (id == null) {
            throw new IllegalArgumentException("El id de la factura es obligatorio");
        }
        return facturaRepository.findById(id);
    }

    public List<RegistroAuditoria> obtenerHistorial(Long facturaId) throws SQLException {
        return facturaRepository.findAuditoria(facturaId);
    }

    // ------------------------------------------------------------------
    // NUMERACIÓN
    // ------------------------------------------------------------------

    /**
     * Genera el siguiente número de factura secuencial único.
     * Formato: AÑO-000001 (ej: 2026-000001).
     * <p>
     * Se calcula como (máximo secuencial del año) + 1 sobre TODAS las facturas,
     * anuladas incluidas. Como las facturas nunca se borran físicamente, un número
     * no se reutiliza jamás y la serie queda siempre correlativa y sin huecos
     * "silenciosos": una factura anulada sigue ocupando su número.
     */
    public synchronized String generarSiguienteNumeroFactura() throws SQLException {
        int anio = LocalDateTime.now().getYear();
        int siguiente = facturaRepository.findMaxSecuencialDelAnio(anio) + 1;
        return String.format("%d-%06d", anio, siguiente);
    }

    public void guardarFactura(Factura factura) throws SQLException {
        boolean numeroAutomatico = factura.getNumeroFactura() == null
                || factura.getNumeroFactura().isEmpty()
                || "PENDIENTE".equals(factura.getNumeroFactura());

        if (factura.getMetodoPago() == null || factura.getMetodoPago().isEmpty()) {
            factura.setMetodoPago("EFECTIVO");
        }
        if (factura.getEntregadoCliente() == null) {
            factura.setEntregadoCliente(BigDecimal.ZERO);
        }

        // Si dos puestos generan el mismo número a la vez, el índice único de la BD
        // rechaza el segundo y aquí se reintenta con el siguiente número libre.
        for (int intento = 1; ; intento++) {
            if (numeroAutomatico) {
                factura.setNumeroFactura(generarSiguienteNumeroFactura());
            }
            try {
                facturaRepository.guardar(factura);
                return;
            } catch (SQLException e) {
                boolean duplicado = e.getMessage() != null && e.getMessage().contains("UNIQUE");
                if (!(numeroAutomatico && duplicado && intento < MAX_REINTENTOS_NUMERACION)) {
                    throw e;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // MODIFICACIÓN
    // ------------------------------------------------------------------

    /**
     * Valida y guarda los cambios de una factura. El número de factura no se
     * modifica nunca. Registra en el historial qué cambió, quién y por qué.
     *
     * @param editada factura con los nuevos valores (debe conservar id, número y
     *                fechaModificacion tal y como se cargaron)
     * @param motivo  motivo del cambio (opcional; puede ser null o vacío)
     */
    public void actualizarFactura(Factura editada, String motivo) throws SQLException {
        if (editada == null || editada.getId() == null) {
            throw new IllegalArgumentException("Factura no válida");
        }
        validarContenido(editada);

        Factura original = facturaRepository.findById(editada.getId());
        if (original == null) {
            throw new IllegalArgumentException("La factura ya no existe");
        }
        if (original.isAnulada()) {
            throw new IllegalStateException("No se puede modificar una factura anulada. Restáurala primero.");
        }
        if (!Objects.equals(original.getFechaModificacion(), editada.getFechaModificacion())) {
            throw new FacturaRepository.ConflictoConcurrenciaException(
                    "La factura ha sido modificada desde otro puesto mientras la editabas. "
                            + "Recarga la lista y vuelve a intentarlo.");
        }

        // El número NO es editable: se fuerza el original por seguridad.
        editada.setNumeroFactura(original.getNumeroFactura());
        editada.setEmpleadoId(original.getEmpleadoId());
        editada.setEmpleadoNombre(original.getEmpleadoNombre());
        editada.recalcularTotales();

        String detalle = describirCambios(original, editada);
        if (detalle.isEmpty()) {
            throw new IllegalArgumentException("No se ha realizado ningún cambio");
        }

        RegistroAuditoria auditoria = new RegistroAuditoria(null, original.getId(), original.getNumeroFactura(),
                RegistroAuditoria.ACCION_MODIFICACION, detalle, normalizarMotivo(motivo), usuarioActual(), null);

        facturaRepository.actualizar(editada, original.getFechaModificacion(), auditoria);
    }

    /**
     * Avisos (no bloqueantes) cuando la nueva fecha rompe la coherencia de la serie:
     * distinto año al del número, o fuera del orden cronológico de las facturas vecinas.
     */
    public List<String> comprobarCoherenciaFecha(String numeroFactura, LocalDateTime fechaOriginal,
                                                 LocalDateTime fechaNueva) throws SQLException {
        List<String> avisos = new ArrayList<>();
        if (fechaNueva == null || fechaNueva.equals(fechaOriginal)) {
            return avisos;
        }

        try {
            int anioNumero = Integer.parseInt(numeroFactura.substring(0, 4));
            if (fechaNueva.getYear() != anioNumero) {
                avisos.add("El año de la nueva fecha (" + fechaNueva.getYear() + ") no coincide con el año del "
                        + "número de factura (" + anioNumero + ").");
            }
        } catch (RuntimeException ignored) {
            // número con formato no estándar: no se puede comprobar el año
        }

        LocalDateTime anterior = facturaRepository.findFechaVecinaAnterior(numeroFactura);
        if (anterior != null && fechaNueva.isBefore(anterior)) {
            avisos.add("La nueva fecha es anterior a la de la factura previa de la serie ("
                    + anterior.format(FORMATO_FECHA) + "). La numeración dejaría de seguir el orden cronológico.");
        }
        LocalDateTime posterior = facturaRepository.findFechaVecinaPosterior(numeroFactura);
        if (posterior != null && fechaNueva.isAfter(posterior)) {
            avisos.add("La nueva fecha es posterior a la de la factura siguiente de la serie ("
                    + posterior.format(FORMATO_FECHA) + "). La numeración dejaría de seguir el orden cronológico.");
        }
        return avisos;
    }

    /**
     * Validaciones de negocio. Lanza IllegalArgumentException con un mensaje
     * apto para mostrar al usuario.
     */
    public void validarContenido(Factura f) {
        if (f.getFechaEmision() == null) {
            throw new IllegalArgumentException("La fecha es obligatoria");
        }
        if (f.getFechaEmision().isAfter(LocalDateTime.now().plusMinutes(1))) {
            throw new IllegalArgumentException("La fecha de la factura no puede ser futura");
        }
        if (f.getLineas() == null || f.getLineas().isEmpty()) {
            throw new IllegalArgumentException("La factura debe tener al menos una línea. "
                    + "Si quieres invalidarla, utiliza la opción Anular.");
        }
        int n = 1;
        for (LineaFactura l : f.getLineas()) {
            String ref = "Línea " + n++ + ": ";
            if (l.getNombreProducto() == null || l.getNombreProducto().isBlank()) {
                throw new IllegalArgumentException(ref + "el nombre del artículo es obligatorio");
            }
            if (l.getCantidad() <= 0) {
                throw new IllegalArgumentException(ref + "la cantidad debe ser mayor que 0");
            }
            if (l.getPrecioUnitario() == null || l.getPrecioUnitario().signum() < 0) {
                throw new IllegalArgumentException(ref + "el precio no puede ser negativo");
            }
            if (l.getDescuento() < 0 || l.getDescuento() > 100) {
                throw new IllegalArgumentException(ref + "el descuento debe estar entre 0 y 100");
            }
            if (l.getIvaAplicado() < 0 || l.getIvaAplicado() > 100) {
                throw new IllegalArgumentException(ref + "el IVA debe estar entre 0 y 100");
            }
        }
        f.recalcularTotales();

        String metodo = f.getMetodoPago();
        if (!"EFECTIVO".equals(metodo) && !"TARJETA".equals(metodo)) {
            throw new IllegalArgumentException("El método de pago debe ser EFECTIVO o TARJETA");
        }
        if ("TARJETA".equals(metodo)) {
            f.setEntregadoCliente(BigDecimal.ZERO);
        } else {
            if (f.getEntregadoCliente() == null || f.getEntregadoCliente().compareTo(f.getTotalConIva()) < 0) {
                throw new IllegalArgumentException("El importe entregado en efectivo no puede ser inferior al total ("
                        + String.format("%.2f €", f.getTotalConIva()) + ")");
            }
        }
    }

    // ------------------------------------------------------------------
    // ANULACIÓN / RESTAURACIÓN
    // ------------------------------------------------------------------

    /**
     * Anula una factura (borrado lógico). La factura conserva su número, deja de
     * computar en los totales y queda registrada en el historial.
     */
    public void anularFactura(Long facturaId, String motivo) throws SQLException {
        String motivoLimpio = normalizarMotivo(motivo);
        Factura f = obtenerPorId(facturaId);
        if (f == null) {
            throw new IllegalArgumentException("La factura ya no existe");
        }
        if (f.isAnulada()) {
            throw new IllegalStateException("La factura " + f.getNumeroFactura() + " ya está anulada");
        }
        String usuario = usuarioActual();
        RegistroAuditoria auditoria = new RegistroAuditoria(null, f.getId(), f.getNumeroFactura(),
                RegistroAuditoria.ACCION_ANULACION,
                "Factura anulada. Importe: " + String.format("%.2f €", f.getTotalConIva()),
                motivoLimpio, usuario, null);
        facturaRepository.anular(f.getId(), motivoLimpio, usuario, auditoria);
    }

    /** Deshace una anulación realizada por error. */
    public void restaurarFactura(Long facturaId, String motivo) throws SQLException {
        Factura f = obtenerPorId(facturaId);
        if (f == null) {
            throw new IllegalArgumentException("La factura ya no existe");
        }
        if (f.getEstado() != EstadoFactura.ANULADA) {
            throw new IllegalStateException("La factura " + f.getNumeroFactura() + " no está anulada");
        }
        String usuario = usuarioActual();
        RegistroAuditoria auditoria = new RegistroAuditoria(null, f.getId(), f.getNumeroFactura(),
                RegistroAuditoria.ACCION_RESTAURACION,
                "Factura restaurada (anulada anteriormente por " + f.getAnuladaPor()
                        + (f.getMotivoAnulacion() != null ? ": " + f.getMotivoAnulacion() : " sin indicar motivo")
                        + ")",
                normalizarMotivo(motivo), usuario, null);
        facturaRepository.restaurar(f.getId(), usuario, auditoria);
    }

    // ------------------------------------------------------------------
    // UTILIDADES
    // ------------------------------------------------------------------

    /** El motivo es opcional: un texto vacío o en blanco se guarda como null. */
    private String normalizarMotivo(String motivo) {
        if (motivo == null) {
            return null;
        }
        String limpio = motivo.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String usuarioActual() {
        return SesionUsuario.getEmpleadoActivo() != null
                ? SesionUsuario.getEmpleadoActivo().getNombre()
                : "Admin";
    }

    /** Descripción legible de las diferencias entre dos versiones de una factura. */
    private String describirCambios(Factura antes, Factura despues) {
        List<String> cambios = new ArrayList<>();

        if (!antes.getFechaEmision().equals(despues.getFechaEmision())) {
            cambios.add("Fecha: " + antes.getFechaEmision().format(FORMATO_FECHA)
                    + " → " + despues.getFechaEmision().format(FORMATO_FECHA));
        }
        if (!Objects.equals(antes.getClienteId(), despues.getClienteId())
                || !Objects.equals(antes.getClienteNombre(), despues.getClienteNombre())) {
            cambios.add("Cliente: " + nvl(antes.getClienteNombre()) + " → " + nvl(despues.getClienteNombre()));
        }
        if (!Objects.equals(antes.getMetodoPago(), despues.getMetodoPago())) {
            cambios.add("Método de pago: " + nvl(antes.getMetodoPago()) + " → " + nvl(despues.getMetodoPago()));
        }
        if (distintoImporte(antes.getEntregadoCliente(), despues.getEntregadoCliente())) {
            cambios.add("Entregado: " + dinero(antes.getEntregadoCliente()) + " → " + dinero(despues.getEntregadoCliente()));
        }

        // Líneas
        Map<Long, LineaFactura> lineasAntes = new HashMap<>();
        for (LineaFactura l : antes.getLineas()) lineasAntes.put(l.getId(), l);
        Set<Long> conservadas = new HashSet<>();

        for (LineaFactura nueva : despues.getLineas()) {
            LineaFactura vieja = nueva.getId() != null ? lineasAntes.get(nueva.getId()) : null;
            if (vieja == null) {
                cambios.add("Línea añadida: " + nueva.getNombreProducto() + " x" + nueva.getCantidad()
                        + " a " + dinero(nueva.getPrecioUnitario()));
                continue;
            }
            conservadas.add(vieja.getId());
            List<String> d = new ArrayList<>();
            if (!Objects.equals(vieja.getNombreProducto(), nueva.getNombreProducto()))
                d.add("nombre '" + nvl(vieja.getNombreProducto()) + "' → '" + nvl(nueva.getNombreProducto()) + "'");
            if (vieja.getCantidad() != nueva.getCantidad())
                d.add("cantidad " + vieja.getCantidad() + " → " + nueva.getCantidad());
            if (distintoImporte(vieja.getPrecioUnitario(), nueva.getPrecioUnitario()))
                d.add("precio " + dinero(vieja.getPrecioUnitario()) + " → " + dinero(nueva.getPrecioUnitario()));
            if (vieja.getDescuento() != nueva.getDescuento())
                d.add("dto " + vieja.getDescuento() + "% → " + nueva.getDescuento() + "%");
            if (vieja.getIvaAplicado() != nueva.getIvaAplicado())
                d.add("IVA " + vieja.getIvaAplicado() + "% → " + nueva.getIvaAplicado() + "%");
            if (!d.isEmpty()) {
                cambios.add("Línea '" + nvl(vieja.getNombreProducto()) + "': " + String.join(", ", d));
            }
        }
        for (LineaFactura vieja : antes.getLineas()) {
            if (!conservadas.contains(vieja.getId())) {
                cambios.add("Línea eliminada: " + vieja.getNombreProducto() + " x" + vieja.getCantidad());
            }
        }

        if (!cambios.isEmpty() && distintoImporte(antes.getTotalConIva(), despues.getTotalConIva())) {
            cambios.add("TOTAL: " + dinero(antes.getTotalConIva()) + " → " + dinero(despues.getTotalConIva()));
        }
        return String.join("\n", cambios);
    }

    private static boolean distintoImporte(BigDecimal a, BigDecimal b) {
        BigDecimal x = a == null ? BigDecimal.ZERO : a;
        BigDecimal y = b == null ? BigDecimal.ZERO : b;
        return x.compareTo(y) != 0;
    }

    private static String dinero(BigDecimal v) {
        return String.format("%.2f €", v == null ? BigDecimal.ZERO : v);
    }

    private static String nvl(String s) {
        return s == null || s.isBlank() ? "(vacío)" : s;
    }
}
