package org.tpv.service;

import javafx.scene.control.Alert;
import javafx.scene.control.ChoiceDialog;
import org.tpv.config.Configuracion;
import org.tpv.domain.Factura;
import org.tpv.domain.LineaFactura;

import javax.print.*;
import java.awt.*;
import java.awt.print.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Servicio de impresión de tickets v4.
 * - Cabecera tabla: CNT (izq.) | ART. (indentado sin solaparse) | TOTAL (der.)
 * - Efectivo: imprime entrega del cliente y cambio bajo el total.
 * - Tarjeta: imprime "TARJETA" con el importe bajo el total.
 */
public class ImpresoraService {

    private final Configuracion config;
    private Factura facturaActual;

    private static final double ANCHO_PAPEL_PUNTOS = 138.0;
    private static final int MARGEN_IZQUIERDO  = 5;
    private static final int MARGEN_DERECHO    = 2;
    private static final int ANCHO_IMPRIMIBLE  = (int)(ANCHO_PAPEL_PUNTOS - MARGEN_IZQUIERDO - MARGEN_DERECHO);
    private static final int MAX_CHARS_PER_LINE = 38;

    // Ancho fijo reservado para la columna CNT (en puntos de impresora)
    // Se calcula dinámicamente en print() usando la métrica real de la fuente,
    // pero definimos un margen mínimo en caracteres para la cabecera.
    private static final int COL_CANT_CHARS = 5; // "CNT" + 2 espacios de separación

    public ImpresoraService(Configuracion config) {
        this.config = config;
    }

    public String[] obtenerImpresorasDisponibles() {
        PrintService[] ps = PrintServiceLookup.lookupPrintServices(null, null);
        String[] nombres = new String[ps.length];
        for (int i = 0; i < ps.length; i++) nombres[i] = ps[i].getName();
        return nombres;
    }

    private PrintService buscarImpresora(String nombre) {
        for (PrintService s : PrintServiceLookup.lookupPrintServices(null, null))
            if (s.getName().equalsIgnoreCase(nombre)) return s;
        return null;
    }

    public void imprimirTicket(Factura factura, String nombreImpresora) throws Exception {
        this.facturaActual = factura;

        PrintService ps;
        if (nombreImpresora != null && !nombreImpresora.isEmpty()) {
            ps = buscarImpresora(nombreImpresora);
            if (ps == null) throw new Exception("Impresora no encontrada: " + nombreImpresora);
        } else {
            ps = PrintServiceLookup.lookupDefaultPrintService();
            if (ps == null) throw new Exception("No hay impresora por defecto");
        }

        PrinterJob job = PrinterJob.getPrinterJob();
        job.setPrintService(ps);

        PageFormat pf   = job.defaultPage();
        Paper      paper = pf.getPaper();
        paper.setSize(ANCHO_PAPEL_PUNTOS, 1500);
        paper.setImageableArea(MARGEN_IZQUIERDO, 0, ANCHO_IMPRIMIBLE, 1500);
        pf.setPaper(paper);
        pf.setOrientation(PageFormat.PORTRAIT);

        job.setPrintable(new TicketPrintable(), pf);
        try { job.print(); }
        catch (PrinterException e) { throw new Exception("Error al imprimir: " + e.getMessage(), e); }
    }

    // =========================================================================
    private class TicketPrintable implements Printable {

        @Override
        public int print(Graphics graphics, PageFormat pageFormat, int pageIndex) throws PrinterException {
            if (pageIndex > 0) return NO_SUCH_PAGE;

            Graphics2D g2d = (Graphics2D) graphics;
            g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,       RenderingHints.VALUE_ANTIALIAS_ON);
            g2d.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,  RenderingHints.VALUE_FRACTIONALMETRICS_ON);

            int x     = (int) pageFormat.getImageableX();
            int y     = 15;
            int width = (int) pageFormat.getImageableWidth();

            Font fontNormal = new Font("SansSerif", Font.PLAIN, 9);
            Font fontTitle  = new Font("SansSerif", Font.PLAIN, 11);
            Font fontSmall  = new Font("SansSerif", Font.PLAIN, 8);

            g2d.setColor(Color.BLACK);

            // Calculamos el ancho real de la columna CNT usando la fuente normal
            g2d.setFont(fontNormal);
            // Reservamos el ancho de "CNT" + un espacio extra de separación
            int colCantWidth = g2d.getFontMetrics().stringWidth("CNT") + 6;

            try {
                // ===== ENCABEZADO =====

                // Nombre de la tienda → GRANDE
                g2d.setFont(fontTitle);
                y = drawText(g2d, config.getNombreTienda(), x, y, width, "center");

                // Ciudad
                g2d.setFont(fontSmall);
                y = drawText(g2d, "FIAZ AHMED", x, y, width, "center");

                // Dirección
                y = drawText(g2d, config.getDireccion(), x, y, width, "center");

                // Código postal + provincia
                y = drawText(g2d, config.getCodigoPostal() + " " + config.getCiudad(), x, y, width, "center");

                // NIF/CIF (si existe)
                if (config.getNif() != null && !config.getNif().isEmpty()) {
                    y = drawText(g2d, "NIF/CIF: " + config.getNif(), x, y, width, "center");
                }

                // Teléfono
                y = drawText(g2d, "Tel: " + config.getTelefono(), x, y, width, "center");


                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ===== INFO FACTURA =====
                g2d.setFont(fontNormal);
                y = drawText(g2d, "Factura Simplificada", x, y, width, "left");
                if (facturaActual.getNumeroFactura() != null)
                    y = drawText(g2d, "Nº: " + facturaActual.getNumeroFactura(), x, y, width, "left");

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                y = drawText(g2d, "Fecha: " + facturaActual.getFechaEmision().format(fmt), x, y, width, "left");

                String nombreCliente = (facturaActual.getClienteNombre() != null)
                        ? facturaActual.getClienteNombre() : "AL CONTADO";
                for (String l : dividirTexto("Cliente: " + nombreCliente, 25))
                    y = drawText(g2d, l, x, y, width, "left");

                String nombreEmpleado = (facturaActual.getEmpleadoNombre() != null)
                        ? facturaActual.getEmpleadoNombre() : "Admin";

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ===== CABECERA DE TABLA =====
                // CNT ocupa colCantWidth píxeles; ART. arranca justo después; TOTAL alineado a la derecha.
                // Se dibujan en la misma línea Y sin solaparse.
                g2d.setFont(fontNormal);
                int lineH = g2d.getFontMetrics().getHeight();

                g2d.drawString("CNT", (float) x, (float) y);
                g2d.drawString("ART.", (float)(x + colCantWidth), (float) y);
                // TOTAL alineado a la derecha
                int totalHeaderW = g2d.getFontMetrics().stringWidth("TOTAL");
                g2d.drawString("TOTAL", (float)(x + width - totalHeaderW), (float) y);
                y += lineH;

                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);

                // ===== PRODUCTOS =====
                int totalArticulos  = 0;
                int maxNombreWidth  = width - colCantWidth;

                for (LineaFactura linea : facturaActual.getLineas()) {
                    y += 2;
                    g2d.setFont(fontNormal);

                    List<String> lineasNombre = dividirTexto(linea.getNombreProducto(), 24);
                    String cantStr     = linea.getCantidad() + "x";
                    String precioUnit  = String.format("%.2f", linea.getPrecioUnitario());
                    String subtotalStr = String.format("%.2f", linea.getTotalConIva());
                    totalArticulos    += linea.getCantidad();

                    // Línea 1: cantidad + primera línea del nombre (misma Y, sin solaparse)
                    g2d.drawString(cantStr, (float) x, (float) y);
                    g2d.drawString(lineasNombre.get(0), (float)(x + colCantWidth), (float) y);
                    y += g2d.getFontMetrics().getHeight();

                    // Líneas adicionales del nombre
                    for (int i = 1; i < lineasNombre.size(); i++)
                        y = drawText(g2d, lineasNombre.get(i), x + colCantWidth, y, maxNombreWidth, "left");

                    // Línea 2: precio unitario (izq. indentado) + total (der.)
                    String precioInfo = (linea.getDescuento() > 0)
                            ? String.format("PVP: %.2f (-%d%%)", linea.getPrecioUnitario(), linea.getDescuento())
                            : String.format("PVP: %s/ud", precioUnit);

                    g2d.setFont(fontSmall);
                    drawText(g2d, precioInfo, x + colCantWidth, y, maxNombreWidth, "left");
                    drawText(g2d, subtotalStr, x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();
                    g2d.setFont(fontNormal);

                    y += 1;
                }

                y += 4;
                y = drawLine(g2d, "-", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                // ===== TOTALES =====
                g2d.setFont(fontNormal);
                y = drawTotalRow(g2d, "Base imponible:", facturaActual.getTotalSinIva(), x, y, width, fontNormal);
                y = drawTotalRow(g2d, "IVA " + config.getIvaGeneral() + "%:", facturaActual.getTotalIva(), x, y, width, fontNormal);

                y += 2;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 4;

                y = drawTotalRow(g2d, "TOTAL:", facturaActual.getTotalConIva(), x, y, width, fontNormal);
                y += 3;

                // ===== MÉTODO DE PAGO =====
                String metodoPago = (facturaActual.getMetodoPago() != null)
                        ? facturaActual.getMetodoPago().toUpperCase() : "EFECTIVO";

                if (metodoPago.equals("TARJETA")) {
                    // TARJETA: etiqueta a la izquierda, importe a la derecha
                    g2d.setFont(fontNormal);
                    drawText(g2d, "TARJETA:", x, y, width, "left");
                    drawText(g2d, String.format("%.2f", facturaActual.getTotalConIva()), x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();
                } else {
                    // EFECTIVO: entrega del cliente y cambio
                    BigDecimal entregado = facturaActual.getEntregadoCliente() != null
                            ? facturaActual.getEntregadoCliente() : BigDecimal.ZERO;
                    BigDecimal cambio = entregado.subtract(facturaActual.getTotalConIva())
                            .setScale(2, RoundingMode.HALF_UP);

                    g2d.setFont(fontNormal);
                    drawText(g2d, "EFECTIVO:", x, y, width, "left");
                    drawText(g2d, String.format("%.2f", entregado), x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();

                    /*
                    drawText(g2d, "Entrega:", x, y, width, "left");
                    drawText(g2d, String.format("%.2f", entregado), x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();
                     */
                    drawText(g2d, "Cambio:", x, y, width, "left");
                    drawText(g2d, String.format("%.2f", cambio), x, y, width, "right");
                    y += g2d.getFontMetrics().getHeight();
                }

                y += 4;
                y = drawLine(g2d, "=", x, y, width, MAX_CHARS_PER_LINE);
                y += 6;

                // ===== Nº ARTÍCULOS =====
                g2d.setFont(fontNormal);
                drawText(g2d, "Nº Artículos:", x, y, width, "left");
                drawText(g2d, String.valueOf(totalArticulos), x, y, width, "right");
                y += g2d.getFontMetrics().getHeight();
                y += 4;

                // ===== ATENDIDO POR =====
                g2d.setFont(fontSmall);
                y = drawText(g2d, "Atendido por: " + nombreEmpleado, x, y, width, "left");
                y += 14;

                // ===== PIE =====
                g2d.setFont(fontNormal);
                y = drawText(g2d, "Gracias por su compra", x, y, width, "center");
                y += 6;
                g2d.setFont(fontSmall);
                y = drawText(g2d, "Visítanos en:", x, y, width, "center");
                y = drawText(g2d, "www.almadenashop.com", x, y, width, "center");

            } catch (Exception e) {
                throw new PrinterException(e.getMessage());
            }

            return PAGE_EXISTS;
        }

        // ------------------------------------------------------------------ helpers

        private int drawText(Graphics2D g2d, String text, int x, int y, int width, String align) {
            if (text == null || text.isEmpty()) return y;
            FontMetrics fm = g2d.getFontMetrics();
            int tw = fm.stringWidth(text);
            int sx = x;
            if ("center".equalsIgnoreCase(align))     sx = x + (width - tw) / 2;
            else if ("right".equalsIgnoreCase(align)) sx = x + width - tw;
            g2d.drawString(text, (float) sx, (float) y);
            return "right".equalsIgnoreCase(align) ? y : y + fm.getHeight();
        }

        private List<String> dividirTexto(String texto, int max) {
            List<String> res = new ArrayList<>();
            if (texto == null) return res;
            if (texto.length() <= max) { res.add(texto); return res; }
            String[] words = texto.split(" ");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (sb.length() + w.length() + 1 <= max) { if (sb.length() > 0) sb.append(" "); sb.append(w); }
                else { if (sb.length() > 0) res.add(sb.toString()); sb = new StringBuilder(w); }
            }
            if (sb.length() > 0) res.add(sb.toString());
            return res;
        }

        private int drawTotalRow(Graphics2D g2d, String label, BigDecimal value,
                                 int x, int y, int width, Font font) {
            g2d.setFont(font);
            drawText(g2d, label, x, y, width, "left");
            drawText(g2d, String.format("%.2f", value), x, y, width, "right");
            return y + g2d.getFontMetrics().getHeight();
        }

        private int drawLine(Graphics2D g2d, String c, int x, int y, int width, int n) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < n; i++) sb.append(c);
            return drawText(g2d, sb.toString(), x, y, width, "left");
        }
    }

    public void imprimirTicketConSeleccion(Factura factura) {
        if (factura == null) return;

        String[] impresoras = obtenerImpresorasDisponibles();
        if (impresoras.length == 0) {
            mostrarAlerta(Alert.AlertType.ERROR, "Sin impresoras", "No se detectaron impresoras en el sistema.");
            return;
        }

        // 1. Buscar "POS-58" para establecerla por defecto
        String impDefecto = impresoras[0];
        for (String imp : impresoras) {
            if (imp.toLowerCase().contains("pos-58") || imp.toLowerCase().contains("pos58") || imp.toLowerCase().contains("58")) {
                impDefecto = imp;
                break;
            }
        }

        // 2. Diálogo de selección para el usuario
        ChoiceDialog<String> dialog = new ChoiceDialog<>(impDefecto, impresoras);
        dialog.setTitle("Imprimir Ticket");
        dialog.setHeaderText("Selecciona la impresora");
        dialog.setContentText("Impresora:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String seleccionada = result.get();
            try {
                // Llamada al método interno que realiza el trabajo físico
                imprimirTicket(factura, seleccionada);
                mostrarAlerta(Alert.AlertType.INFORMATION, "Éxito", "Ticket enviado correctamente a: " + seleccionada);
            } catch (Exception e) {
                mostrarAlerta(Alert.AlertType.ERROR, "Error de impresión", e.getMessage());
            }
        }
    }

    // Métodos auxiliares dentro de ImpresoraService para simplificar alertas JavaFX
    private void mostrarAlerta(Alert.AlertType tipo, String titulo, String mensaje) {
        Alert alert = new Alert(tipo);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }
}