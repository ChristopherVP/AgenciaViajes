package objetosServicio;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Constructor de informes formales en PDF sin librerías: portada, índice con páginas, secciones numeradas, párrafos,
 * indicadores, tablas que continúan en la página siguiente y gráficos de barras. Hoja A4 vertical, fuente Helvetica.
 */
public final class InformePdf {
    private static final float ANCHO = 595f;
    private static final float ALTO = 842f;
    private static final float MARGEN = 48f;
    private static final float AREA = ANCHO - 2 * MARGEN;
    private static final float TOPE = 70f;       // espacio superior reservado para el encabezado de página
    private static final float BASE = 58f;       // espacio inferior reservado para el pie de página

    private final String titulo;
    private final String subtitulo;
    private final String entidad;
    private final List<StringBuilder> paginas = new ArrayList<>();
    private final List<String> indiceTitulos = new ArrayList<>();
    private final List<Integer> indicePaginas = new ArrayList<>();
    private float y;
    private int seccion;
    private StringBuilder actual;

    public InformePdf(String titulo, String subtitulo, String entidad) {
        this.titulo = titulo;
        this.subtitulo = subtitulo;
        this.entidad = entidad;
        paginas.add(new StringBuilder()); // 0: portada
        paginas.add(new StringBuilder()); // 1: índice
        nuevaPagina();
    }

    private void nuevaPagina() {
        actual = new StringBuilder();
        paginas.add(actual);
        y = TOPE;
    }

    private void asegurar(float alto) {
        if (y + alto > ALTO - BASE) {
            nuevaPagina();
        }
    }

    // ------------------------------------------------------------------ elementos

    /** Nueva sección numerada; siempre empieza en una página nueva si queda poco espacio. */
    public InformePdf seccion(String nombre) {
        if (y > TOPE + 4 && y + 150 > ALTO - BASE) {
            nuevaPagina();
        } else if (y > TOPE + 4) {
            y += 14;
        }
        seccion++;
        String texto = seccion + ". " + nombre;
        indiceTitulos.add(texto);
        indicePaginas.add(paginas.size() - 1);
        asegurar(40);
        texto(actual, "F2", 15, MARGEN, ALTO - y - 14, texto, 0.04f, 0.18f, 0.29f);
        rect(actual, MARGEN, ALTO - y - 22, AREA, 1.2f, 0.96f, 0.65f, 0.14f);
        y += 32;
        return this;
    }

    public InformePdf subtitulo(String texto) {
        asegurar(24);
        texto(actual, "F2", 11, MARGEN, ALTO - y - 11, texto, 0.1f, 0.1f, 0.1f);
        y += 20;
        return this;
    }

    public InformePdf parrafo(String texto) {
        for (String linea : envolver(texto, AREA, 10)) {
            asegurar(14);
            texto(actual, "F1", 10, MARGEN, ALTO - y - 10, linea, 0.1f, 0.1f, 0.1f);
            y += 14;
        }
        y += 4;
        return this;
    }

    /** Fila de cuadros con una cifra grande y su descripción (hasta 4 por fila). */
    public InformePdf indicadores(String[] etiquetas, String[] valores) {
        int n = etiquetas.length;
        float gap = 10;
        float w = (AREA - gap * (n - 1)) / n;
        asegurar(58);
        for (int i = 0; i < n; i++) {
            float x = MARGEN + i * (w + gap);
            rect(actual, x, ALTO - y - 50, w, 50, 0.93f, 0.96f, 0.99f);
            rect(actual, x, ALTO - y - 50, 3, 50, 0.07f, 0.35f, 0.55f);
            texto(actual, "F2", 15, x + 10, ALTO - y - 24, ajustar(valores[i], w - 14, 15), 0.04f, 0.18f, 0.29f);
            texto(actual, "F1", 8, x + 10, ALTO - y - 40, ajustar(etiquetas[i], w - 14, 8), 0.3f, 0.35f, 0.4f);
        }
        y += 62;
        return this;
    }

    public InformePdf tabla(String nombre, List<String> cabecera, List<List<String>> filas) {
        if (nombre != null && !nombre.isEmpty()) {
            subtitulo(nombre);
        }
        if (filas.isEmpty()) {
            parrafo("No hay registros para mostrar.");
            return this;
        }
        int columnas = cabecera.size();
        double[] peso = new double[columnas];
        double total = 0;
        for (int c = 0; c < columnas; c++) {
            int max = cabecera.get(c).length();
            for (List<String> f : filas) {
                max = Math.max(max, f.get(c).length());
            }
            peso[c] = Math.min(max, 28) + 3;
            total += peso[c];
        }
        float[] ancho = new float[columnas];
        for (int c = 0; c < columnas; c++) {
            ancho[c] = (float) (AREA * peso[c] / total);
        }
        final float alto = 16;
        boolean cabeceraPendiente = true;
        for (int i = 0; i < filas.size(); i++) {
            asegurar(cabeceraPendiente ? alto * 2 : alto);
            if (cabeceraPendiente || y == TOPE) {
                dibujarCabecera(cabecera, ancho, alto);
                cabeceraPendiente = false;
            }
            if (i % 2 == 1) {
                rect(actual, MARGEN, ALTO - y - alto + 3, AREA, alto, 0.96f, 0.97f, 0.99f);
            }
            float x = MARGEN;
            for (int c = 0; c < columnas; c++) {
                texto(actual, "F1", 7.5f, x + 3, ALTO - y - 8, ajustar(filas.get(i).get(c), ancho[c] - 6, 7.5f), 0.1f, 0.1f, 0.1f);
                x += ancho[c];
            }
            linea(actual, MARGEN, ALTO - y - alto + 3, MARGEN + AREA);
            y += alto;
        }
        y += 8;
        return this;
    }

    private void dibujarCabecera(List<String> cabecera, float[] ancho, float alto) {
        rect(actual, MARGEN, ALTO - y - alto + 3, AREA, alto, 0.07f, 0.35f, 0.55f);
        float x = MARGEN;
        for (int c = 0; c < cabecera.size(); c++) {
            texto(actual, "F2", 8, x + 3, ALTO - y - 8, ajustar(cabecera.get(c), ancho[c] - 6, 8), 1f, 1f, 1f);
            x += ancho[c];
        }
        y += alto;
    }

    /** Gráfico de barras horizontales. */
    public InformePdf barras(String nombre, List<String> etiquetas, List<Double> valores, String prefijoValor) {
        subtitulo(nombre);
        if (etiquetas.isEmpty()) {
            parrafo("No hay datos para graficar.");
            return this;
        }
        double max = 0;
        for (double v : valores) {
            max = Math.max(max, v);
        }
        if (max <= 0) {
            max = 1;
        }
        float etiqueta = 150;
        float zonaBarra = AREA - etiqueta - 70;
        for (int i = 0; i < etiquetas.size(); i++) {
            asegurar(20);
            float w = (float) (zonaBarra * valores.get(i) / max);
            texto(actual, "F1", 8.5f, MARGEN, ALTO - y - 11, ajustar(etiquetas.get(i), etiqueta - 8, 8.5f), 0.1f, 0.1f, 0.1f);
            rect(actual, MARGEN + etiqueta, ALTO - y - 14, Math.max(w, 1.5f), 12, 0.07f, 0.35f, 0.55f);
            String v = prefijoValor + (valores.get(i) == Math.floor(valores.get(i)) && prefijoValor.isEmpty() ? String.format(Locale.US, "%.0f", valores.get(i))
                    : String.format(Locale.US, "%.2f", valores.get(i)));
            texto(actual, "F2", 8.5f, MARGEN + etiqueta + Math.max(w, 1.5f) + 5, ALTO - y - 11, v, 0.04f, 0.18f, 0.29f);
            y += 20;
        }
        y += 8;
        return this;
    }

    // ------------------------------------------------------------------ salida

    public void guardar(File destino) throws IOException {
        LocalDateTime ahora = LocalDateTime.now();
        String fecha = ahora.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        // portada
        StringBuilder p = paginas.get(0);
        rect(p, 0, ALTO - 330, ANCHO, 330, 0.04f, 0.18f, 0.29f);
        rect(p, MARGEN, ALTO - 332, 90, 5, 0.96f, 0.65f, 0.14f);
        texto(p, "F2", 12, MARGEN, ALTO - 90, entidad.toUpperCase(), 0.75f, 0.85f, 0.95f);
        int linea = 0;
        for (String l : envolver(titulo, AREA, 30)) {
            texto(p, "F2", 30, MARGEN, ALTO - 170 - linea * 38, l, 1f, 1f, 1f);
            linea++;
        }
        texto(p, "F1", 14, MARGEN, ALTO - 190 - (linea - 1) * 38 - 30, subtitulo, 0.85f, 0.92f, 0.98f);
        texto(p, "F1", 11, MARGEN, ALTO - 400, "Fecha de emisión: " + fecha, 0.2f, 0.2f, 0.2f);
        texto(p, "F1", 11, MARGEN, ALTO - 420, "Documento generado automáticamente por el sistema " + Marca.NOMBRE + ".", 0.2f, 0.2f, 0.2f);
        texto(p, "F1", 9, MARGEN, 70, "Información confidencial de uso interno. Los valores corresponden a los datos registrados hasta la fecha de emisión.", 0.4f, 0.4f, 0.4f);
        // índice
        p = paginas.get(1);
        texto(p, "F2", 18, MARGEN, ALTO - 100, "Contenido", 0.04f, 0.18f, 0.29f);
        rect(p, MARGEN, ALTO - 108, AREA, 1.2f, 0.96f, 0.65f, 0.14f);
        float yi = 140;
        for (int i = 0; i < indiceTitulos.size(); i++) {
            texto(p, "F1", 11, MARGEN, ALTO - yi, indiceTitulos.get(i), 0.1f, 0.1f, 0.1f);
            String pag = String.valueOf(indicePaginas.get(i) + 1);
            texto(p, "F1", 11, MARGEN + AREA - anchoMedido(pag, 11), ALTO - yi, pag, 0.1f, 0.1f, 0.1f);
            yi += 22;
        }
        // encabezado y pie
        for (int i = 1; i < paginas.size(); i++) {
            StringBuilder s = paginas.get(i);
            texto(s, "F1", 8, MARGEN, ALTO - 36, entidad + " · " + titulo, 0.4f, 0.4f, 0.4f);
            linea(s, MARGEN, ALTO - 42, MARGEN + AREA);
            texto(s, "F1", 8, MARGEN, 30, "Emitido el " + fecha, 0.4f, 0.4f, 0.4f);
            String num = "Página " + (i + 1) + " de " + paginas.size();
            texto(s, "F1", 8, MARGEN + AREA - anchoMedido(num, 8), 30, num, 0.4f, 0.4f, 0.4f);
        }

        ensamblar(paginas, destino);
    }

    /** Escribe el archivo PDF a partir del contenido de cada página. */
    private static void ensamblar(List<StringBuilder> paginas, File destino) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> pos = new ArrayList<>();
        escribir(out, "%PDF-1.4\n");
        objeto(out, pos, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < paginas.size(); i++) {
            kids.append(5 + 2 * i).append(" 0 R ");
        }
        objeto(out, pos, "<< /Type /Pages /Kids [" + kids + "] /Count " + paginas.size() + " >>");
        objeto(out, pos, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objeto(out, pos, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        for (int i = 0; i < paginas.size(); i++) {
            objeto(out, pos, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Resources << /Font << /F1 3 0 R /F2 4 0 R >> >> /Contents " + (6 + 2 * i) + " 0 R >>");
            byte[] datos = paginas.get(i).toString().getBytes(StandardCharsets.ISO_8859_1);
            pos.add(out.size());
            escribir(out, pos.size() + " 0 obj\n<< /Length " + datos.length + " >>\nstream\n");
            out.write(datos);
            escribir(out, "\nendstream\nendobj\n");
        }
        int xref = out.size();
        escribir(out, "xref\n0 " + (pos.size() + 1) + "\n0000000000 65535 f \n");
        for (int o : pos) {
            escribir(out, String.format("%010d 00000 n \n", o));
        }
        escribir(out, "trailer\n<< /Size " + (pos.size() + 1) + " /Root 1 0 R >>\nstartxref\n" + xref + "\n%%EOF\n");
        try (OutputStream os = new FileOutputStream(destino)) {
            out.writeTo(os);
        }
    }

    /** Factura de una sola hoja lista para imprimir o enviar. */
    public static void guardarFactura(File destino, String emisor, String numero, String tipo, String fecha, String titular, String cedula,
                                      String concepto, String detalle, String total) throws IOException {
        StringBuilder s = new StringBuilder();
        rect(s, 0, ALTO - 120, ANCHO, 120, 0.04f, 0.18f, 0.29f);
        rect(s, MARGEN, ALTO - 124, 80, 4, 0.96f, 0.65f, 0.14f);
        texto(s, "F2", 22, MARGEN, ALTO - 60, "FACTURA", 1f, 1f, 1f);
        texto(s, "F1", 11, MARGEN, ALTO - 82, Marca.SISTEMA, 0.85f, 0.92f, 0.98f);
        texto(s, "F2", 12, ANCHO - MARGEN - anchoMedido("N.º " + numero, 12), ALTO - 60, "N.º " + numero, 1f, 1f, 1f);
        texto(s, "F1", 10, ANCHO - MARGEN - anchoMedido(fecha, 10), ALTO - 80, fecha, 0.85f, 0.92f, 0.98f);
        float yy = ALTO - 160;
        texto(s, "F2", 10, MARGEN, yy, "CLIENTE", 0.3f, 0.35f, 0.4f);
        texto(s, "F2", 13, MARGEN, yy - 18, titular, 0.1f, 0.1f, 0.1f);
        texto(s, "F1", 10, MARGEN, yy - 34, "Cédula: " + cedula, 0.2f, 0.2f, 0.2f);
        texto(s, "F2", 10, ANCHO / 2 + 20, yy, "EMITIDA POR", 0.3f, 0.35f, 0.4f);
        texto(s, "F1", 11, ANCHO / 2 + 20, yy - 18, ajustar(emisor, AREA / 2 - 20, 11), 0.1f, 0.1f, 0.1f);
        texto(s, "F1", 10, ANCHO / 2 + 20, yy - 34, tipo, 0.2f, 0.2f, 0.2f);
        yy -= 70;
        rect(s, MARGEN, yy - 4, AREA, 20, 0.07f, 0.35f, 0.55f);
        texto(s, "F2", 9, MARGEN + 6, yy + 3, "CONCEPTO", 1f, 1f, 1f);
        texto(s, "F2", 9, MARGEN + AREA - 6 - anchoMedido("VALOR", 9), yy + 3, "VALOR", 1f, 1f, 1f);
        yy -= 26;
        texto(s, "F2", 11, MARGEN + 6, yy, ajustar(concepto, AREA - 110, 11), 0.1f, 0.1f, 0.1f);
        texto(s, "F2", 11, MARGEN + AREA - 6 - anchoMedido(total, 11), yy, total, 0.1f, 0.1f, 0.1f);
        yy -= 20;
        for (String linea : detalle.split("\n")) {
            for (String l : envolver(linea, AREA - 30, 9.5f)) {
                texto(s, "F1", 9.5f, MARGEN + 14, yy, l, 0.25f, 0.25f, 0.25f);
                yy -= 14;
            }
        }
        yy -= 8;
        linea(s, MARGEN, yy, MARGEN + AREA);
        rect(s, MARGEN + AREA - 190, yy - 52, 190, 40, 0.93f, 0.96f, 0.99f);
        texto(s, "F2", 10, MARGEN + AREA - 180, yy - 36, "TOTAL", 0.04f, 0.18f, 0.29f);
        texto(s, "F2", 16, MARGEN + AREA - 10 - anchoMedido(total, 16), yy - 38, total, 0.04f, 0.18f, 0.29f);
        texto(s, "F1", 8.5f, MARGEN, 60, "Documento electrónico generado por el sistema " + Marca.NOMBRE + ". Los pasajes comprados no admiten cancelación ni devolución.", 0.4f, 0.4f, 0.4f);
        List<StringBuilder> paginas = new ArrayList<>();
        paginas.add(s);
        ensamblar(paginas, destino);
    }

    /** Documento de una sola operación (factura, pasaje, movimiento de billetera, pago a conductor...) en bloques de datos. */
    public static final class Documento {
        private final String titulo;
        private final String numero;
        private final String fecha;
        private String estado = "";
        private String totalEtiqueta = "";
        private String total = "";
        private String pie = "";
        private final List<String> titulos = new ArrayList<>();
        private final List<List<String[]>> bloques = new ArrayList<>();

        public Documento(String titulo, String numero, String fecha) {
            this.titulo = titulo;
            this.numero = numero;
            this.fecha = fecha;
        }

        public Documento estado(String e) {
            this.estado = e == null ? "" : e;
            return this;
        }

        public Documento total(String etiqueta, String valor) {
            this.totalEtiqueta = etiqueta;
            this.total = valor;
            return this;
        }

        public Documento pie(String p) {
            this.pie = p == null ? "" : p;
            return this;
        }

        /** Bloque con título y pares etiqueta/valor: {@code bloque("Viaje", "Ruta", "Quito → Riobamba", "Salida", "...")}. */
        public Documento bloque(String tituloBloque, String... pares) {
            List<String[]> filas = new ArrayList<>();
            for (int i = 0; i + 1 < pares.length; i += 2) {
                if (pares[i + 1] != null && !pares[i + 1].isBlank()) {
                    filas.add(new String[]{pares[i], pares[i + 1]});
                }
            }
            titulos.add(tituloBloque);
            bloques.add(filas);
            return this;
        }
    }

    public static void guardarDocumento(File destino, Documento d) throws IOException {
        List<StringBuilder> paginas = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        paginas.add(s);
        rect(s, 0, ALTO - 120, ANCHO, 120, 0.04f, 0.18f, 0.29f);
        rect(s, MARGEN, ALTO - 124, 80, 4, 0.96f, 0.65f, 0.14f);
        texto(s, "F2", 22, MARGEN, ALTO - 58, d.titulo.toUpperCase(Locale.ROOT), 1f, 1f, 1f);
        texto(s, "F1", 11, MARGEN, ALTO - 82, Marca.SISTEMA, 0.85f, 0.92f, 0.98f);
        String num = d.numero.isEmpty() ? "" : "N.º " + d.numero;
        texto(s, "F2", 12, ANCHO - MARGEN - anchoMedido(num, 12), ALTO - 58, num, 1f, 1f, 1f);
        texto(s, "F1", 10, ANCHO - MARGEN - anchoMedido(d.fecha, 10), ALTO - 78, d.fecha, 0.85f, 0.92f, 0.98f);
        if (!d.estado.isEmpty()) {
            float w = anchoMedido(d.estado, 9) + 18;
            rect(s, ANCHO - MARGEN - w, ALTO - 106, w, 16, 0.96f, 0.65f, 0.14f);
            texto(s, "F2", 9, ANCHO - MARGEN - w + 9, ALTO - 101, d.estado, 0.04f, 0.18f, 0.29f);
        }
        float yy = ALTO - 156;
        for (int i = 0; i < d.bloques.size(); i++) {
            List<String[]> filas = d.bloques.get(i);
            if (filas.isEmpty()) {
                continue;
            }
            if (yy < 150) {
                s = new StringBuilder();
                paginas.add(s);
                yy = ALTO - 70;
            }
            rect(s, MARGEN, yy - 4, AREA, 18, 0.07f, 0.35f, 0.55f);
            texto(s, "F2", 9.5f, MARGEN + 8, yy + 1, d.titulos.get(i).toUpperCase(Locale.ROOT), 1f, 1f, 1f);
            yy -= 22;
            boolean par = false;
            for (String[] fila : filas) {
                List<String> lineas = new ArrayList<>();
                for (String parte : fila[1].split("\n")) {
                    lineas.addAll(envolver(parte, AREA - 190, 10));
                }
                float alto = Math.max(1, lineas.size()) * 13 + 5;
                if (yy - alto < 90) {
                    s = new StringBuilder();
                    paginas.add(s);
                    yy = ALTO - 70;
                }
                if (par) {
                    rect(s, MARGEN, yy - alto + 11, AREA, alto, 0.95f, 0.97f, 0.99f);
                }
                texto(s, "F2", 10, MARGEN + 8, yy, ajustar(fila[0], 150, 10), 0.3f, 0.35f, 0.4f);
                float ly = yy;
                for (String l : lineas) {
                    texto(s, "F1", 10, MARGEN + 170, ly, l, 0.1f, 0.1f, 0.1f);
                    ly -= 13;
                }
                yy -= alto;
                par = !par;
            }
            yy -= 14;
        }
        if (!d.total.isEmpty()) {
            if (yy < 120) {
                s = new StringBuilder();
                paginas.add(s);
                yy = ALTO - 70;
            }
            linea(s, MARGEN, yy + 6, MARGEN + AREA);
            rect(s, MARGEN + AREA - 230, yy - 44, 230, 44, 0.93f, 0.96f, 0.99f);
            texto(s, "F2", 10, MARGEN + AREA - 220, yy - 20, d.totalEtiqueta.toUpperCase(Locale.ROOT), 0.04f, 0.18f, 0.29f);
            texto(s, "F2", 18, MARGEN + AREA - 10 - anchoMedido(d.total, 18), yy - 30, d.total, 0.04f, 0.18f, 0.29f);
        }
        String pie = d.pie.isEmpty() ? "Documento electrónico generado por el sistema " + Marca.NOMBRE + "." : d.pie;
        float py = 66;
        for (String l : envolver(pie, AREA, 8.5f)) {
            texto(s, "F1", 8.5f, MARGEN, py, l, 0.4f, 0.4f, 0.4f);
            py -= 11;
        }
        ensamblar(paginas, destino);
    }

    // ------------------------------------------------------------------ utilidades de dibujo

    private static void objeto(ByteArrayOutputStream out, List<Integer> pos, String cuerpo) throws IOException {
        pos.add(out.size());
        escribir(out, pos.size() + " 0 obj\n" + cuerpo + "\nendobj\n");
    }

    private static void escribir(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static String f(float v) {
        return String.format(Locale.US, "%.2f", v);
    }

    private static void texto(StringBuilder s, String fuente, float tam, float x, float yy, String t, float r, float g, float b) {
        s.append("BT /").append(fuente).append(' ').append(f(tam)).append(" Tf ").append(f(r)).append(' ').append(f(g)).append(' ').append(f(b))
                .append(" rg ").append(f(x)).append(' ').append(f(yy)).append(" Td (").append(escapar(t)).append(") Tj ET\n");
    }

    private static void rect(StringBuilder s, float x, float yy, float w, float h, float r, float g, float b) {
        s.append(f(r)).append(' ').append(f(g)).append(' ').append(f(b)).append(" rg ").append(f(x)).append(' ').append(f(yy)).append(' ')
                .append(f(w)).append(' ').append(f(h)).append(" re f\n");
    }

    private static void linea(StringBuilder s, float x1, float yy, float x2) {
        s.append("0.85 0.88 0.9 RG 0.4 w ").append(f(x1)).append(' ').append(f(yy)).append(" m ").append(f(x2)).append(' ').append(f(yy)).append(" l S\n");
    }

    private static String escapar(String t) {
        StringBuilder sb = new StringBuilder();
        for (char ch : (t == null ? "" : t).toCharArray()) {
            if (ch == '(' || ch == ')' || ch == '\\') {
                sb.append('\\').append(ch);
            } else if (ch >= 32 && ch <= 255) {
                sb.append(ch);
            } else if (ch == '–' || ch == '—' || ch == '→') {
                sb.append(ch == '→' ? "->" : "-");
            } else if (ch == '·' || ch == '•') {
                sb.append('·');
            } else if (ch == '\n' || ch == '\r' || ch == '\t') {
                sb.append(' ');
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static List<String> envolver(String texto, float ancho, float tam) {
        List<String> lineas = new ArrayList<>();
        for (String parrafo : (texto == null ? "" : texto).split("\n")) {
            StringBuilder actualLinea = new StringBuilder();
            for (String palabra : parrafo.split(" ")) {
                String prueba = actualLinea.length() == 0 ? palabra : actualLinea + " " + palabra;
                if (anchoMedido(prueba, tam) > ancho && actualLinea.length() > 0) {
                    lineas.add(actualLinea.toString());
                    actualLinea = new StringBuilder(palabra);
                } else {
                    actualLinea = new StringBuilder(prueba);
                }
            }
            lineas.add(actualLinea.toString());
        }
        return lineas;
    }

    private static String ajustar(String t, float max, float tam) {
        if (t == null) {
            return "";
        }
        if (anchoMedido(t, tam) <= max) {
            return t;
        }
        String r = t;
        while (!r.isEmpty() && anchoMedido(r + "..", tam) > max) {
            r = r.substring(0, r.length() - 1);
        }
        return r + "..";
    }

    private static float anchoMedido(String t, float tam) {
        float total = 0;
        for (char ch : t.toCharArray()) {
            float w;
            if (ch == ' ') w = 0.278f;
            else if (Character.isDigit(ch)) w = 0.556f;
            else if ("ijl.,:;'|!".indexOf(ch) >= 0) w = 0.25f;
            else if ("ft()[]-/".indexOf(ch) >= 0) w = 0.33f;
            else if ("mwMW@".indexOf(ch) >= 0) w = 0.85f;
            else if (Character.isUpperCase(ch)) w = 0.68f;
            else w = 0.54f;
            total += w;
        }
        return total * tam * 1.04f;
    }
}
