package control;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import persistencia.AlmacenTexto;

/**
 * Avisos internos del sistema. Cada aviso va dirigido a una persona (id del conductor, por ejemplo CND007) o a toda la
 * administración ({@link #ADMIN}). Se usan para avisarle al conductor que le asignaron un viaje y al administrador que el
 * conductor aceptó, rechazó o pidió cancelar. Se guardan en notificaciones.dat.
 */
public class ControladorNotificaciones {
    public static final String ADMIN = "ADMIN";
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("notificaciones.dat");
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static class Aviso {
        private final String id;
        private final String destino;
        private final String titulo;
        private final String mensaje;
        private final LocalDateTime fecha;
        private final boolean leido;
        private final String referencia;

        Aviso(String[] f) {
            this.id = f[0];
            this.destino = f[1];
            this.titulo = f[2];
            this.mensaje = f[3];
            this.fecha = LocalDateTime.parse(f[4], FORMATO);
            this.leido = "1".equals(f[5]);
            this.referencia = f.length > 6 ? f[6] : "";
        }

        public String getId() { return id; }
        public String getDestino() { return destino; }
        public String getTitulo() { return titulo; }
        public String getMensaje() { return mensaje; }
        public LocalDateTime getFecha() { return fecha; }
        public boolean isLeido() { return leido; }
        public String getReferencia() { return referencia; }
    }

    public synchronized void enviar(String destino, String titulo, String mensaje, String referencia) {
        try {
            int siguiente = ALMACEN.leer().size() + 1;
            ALMACEN.agregar(String.format("NOT%05d", siguiente), destino, titulo, mensaje, LocalDateTime.now().format(FORMATO), "0",
                    referencia == null ? "" : referencia);
        } catch (IOException e) {
            System.err.println("No se pudo guardar el aviso: " + e.getMessage());
        }
    }

    /** Aviso con una fecha concreta (lo usan los datos de demostración). */
    public synchronized void enviarConFecha(String destino, String titulo, String mensaje, String referencia, LocalDateTime fecha, boolean leido) {
        try {
            int siguiente = ALMACEN.leer().size() + 1;
            ALMACEN.agregar(String.format("NOT%05d", siguiente), destino, titulo, mensaje, fecha.format(FORMATO), leido ? "1" : "0",
                    referencia == null ? "" : referencia);
        } catch (IOException e) {
            System.err.println("No se pudo guardar el aviso: " + e.getMessage());
        }
    }

    /** Todos los avisos de ese destinatario, del más reciente al más antiguo. */
    public synchronized List<Aviso> deDestino(String destino) {
        try {
            return ALMACEN.leer().stream().map(Aviso::new).filter(a -> a.getDestino().equals(destino))
                    .sorted(Comparator.comparing(Aviso::getFecha).reversed().thenComparing(Aviso::getId, Comparator.reverseOrder()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("No se pudieron leer los avisos: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<Aviso> sinLeer(String destino) {
        return deDestino(destino).stream().filter(a -> !a.isLeido()).collect(Collectors.toList());
    }

    public synchronized void marcarLeidos(String destino) {
        try {
            List<String[]> filas = ALMACEN.leer();
            boolean cambio = false;
            for (String[] f : filas) {
                if (f[1].equals(destino) && !"1".equals(f[5])) {
                    f[5] = "1";
                    cambio = true;
                }
            }
            if (cambio) {
                ALMACEN.reescribir(filas);
            }
        } catch (IOException e) {
            System.err.println("No se pudieron marcar los avisos: " + e.getMessage());
        }
    }
}
