package persistencia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Archivo de texto con un registro por línea y campos separados por tabulador. Lo usan los datos nuevos del sistema
 * (facturas, liquidaciones, tarjetas, asignaciones). Tiene extensión .dat como los demás archivos de datos, así que
 * borrar los .dat reinicia todo el sistema de forma consistente. Las escrituras son atómicas (archivo temporal + reemplazo).
 */
public final class AlmacenTexto {
    private static final Object CERROJO = new Object();
    private final Path ruta;

    public AlmacenTexto(String archivo) {
        this.ruta = Path.of(archivo);
    }

    public List<String[]> leer() throws IOException {
        synchronized (CERROJO) {
            List<String[]> filas = new ArrayList<>();
            if (!Files.exists(ruta)) {
                return filas;
            }
            for (String linea : Files.readAllLines(ruta, StandardCharsets.UTF_8)) {
                if (linea.isEmpty()) {
                    continue;
                }
                String[] partes = linea.split("\t", -1);
                for (int i = 0; i < partes.length; i++) {
                    partes[i] = desescapar(partes[i]);
                }
                filas.add(partes);
            }
            return filas;
        }
    }

    public void agregar(String... campos) throws IOException {
        synchronized (CERROJO) {
            List<String[]> filas = leer();
            filas.add(campos);
            reescribir(filas);
        }
    }

    public void reescribir(List<String[]> filas) throws IOException {
        synchronized (CERROJO) {
            StringBuilder sb = new StringBuilder();
            for (String[] fila : filas) {
                for (int i = 0; i < fila.length; i++) {
                    if (i > 0) {
                        sb.append('\t');
                    }
                    sb.append(escapar(fila[i]));
                }
                sb.append('\n');
            }
            Path temporal = Path.of(ruta.toString() + ".tmp");
            Files.writeString(temporal, sb.toString(), StandardCharsets.UTF_8);
            Files.move(temporal, ruta, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String escapar(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private static String desescapar(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                sb.append(n == 't' ? '\t' : n == 'n' ? '\n' : n == 'r' ? '\r' : n);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
