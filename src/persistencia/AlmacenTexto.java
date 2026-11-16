package persistencia;

import DB.Bd;
import DB.ConexionOracle;
import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * Nombre histórico: antes era un archivo de texto con un registro por línea. Ahora cada "archivo" es una tabla de Oracle
 * (asignaciones.dat -> ASIGNACIONES, notificaciones.dat -> NOTIFICACIONES, liquidaciones.dat -> LIQUIDACIONES,
 * tarjetas.dat -> TARJETAS, facturas.dat -> COMPROBANTES) con columnas tipadas. Mantiene la misma interfaz
 * (leer / agregar / reescribir) para que los controladores no cambien: cada fila sigue siendo un String[] en el orden de las columnas.
 */
public final class AlmacenTexto {
    private static final Object CERROJO = new Object();
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private enum Tipo { TEXTO, NUMERO, MOMENTO }

    private record Def(String tabla, String orden, String[] columnas, Tipo[] tipos) {
    }

    private static Def def(String tabla, String orden, Object... columnasYTipos) {
        int n = columnasYTipos.length / 2;
        String[] c = new String[n];
        Tipo[] t = new Tipo[n];
        for (int i = 0; i < n; i++) {
            c[i] = (String) columnasYTipos[2 * i];
            t[i] = (Tipo) columnasYTipos[2 * i + 1];
        }
        return new Def(tabla, orden, c, t);
    }

    private static final Tipo X = Tipo.TEXTO;
    private static final Tipo N = Tipo.NUMERO;
    private static final Tipo M = Tipo.MOMENTO;

    private static final Map<String, Def> TABLAS = Map.of(
            "asignaciones.dat", def("ASIGNACIONES", "ID_CONDUCTOR", "ID_CONDUCTOR", X, "ID_BUS", X, "ID_RUTA", X),
            "notificaciones.dat", def("NOTIFICACIONES", "ID_NOTIFICACION", "ID_NOTIFICACION", X, "DESTINO", X, "TITULO", X, "MENSAJE", X,
                    "FECHA", M, "LEIDO", N, "REFERENCIA", X),
            "liquidaciones.dat", def("LIQUIDACIONES", "ID_LIQUIDACION", "ID_LIQUIDACION", X, "ID_VIAJE", X, "ID_CONDUCTOR", X, "NOMBRE_CONDUCTOR", X,
                    "EMPRESA", X, "RUTA", X, "FECHA_VIAJE", X, "PASAJEROS", N, "RECAUDADO", N, "PARTES", N, "MONTO", N, "ESTADO", X,
                    "NOTIFICADO_EN", M, "PAGADO_EN", M, "PAGADO_POR", X, "TARJETA", X, "NUMERO_FACTURA", X, "LLEGADA_VIAJE", X),
            "tarjetas.dat", def("TARJETAS", "ID_TITULAR, USO", "ID_TITULAR", X, "USO", X, "BANCO", X, "MARCA", X, "ULTIMOS4", X, "TITULAR", X, "VENCE", X),
            "facturas.dat", def("COMPROBANTES", "NUMERO", "NUMERO", X, "TIPO", X, "ID_TITULAR", X, "NOMBRE_TITULAR", X, "CEDULA_TITULAR", X,
                    "CONCEPTO", X, "DETALLE", X, "TOTAL", N, "FECHA", M, "REFERENCIA", X, "EMISOR", X));

    private final Def def;

    public AlmacenTexto(String archivo) {
        this.def = TABLAS.get(archivo.toLowerCase(Locale.ROOT));
        if (def == null) {
            throw new IllegalArgumentException("No hay tabla para " + archivo);
        }
    }

    public List<String[]> leer() throws IOException {
        synchronized (CERROJO) {
            try (Connection c = ConexionOracle.abrir()) {
                return leer(c);
            } catch (SQLException e) {
                throw new IOException("No se pudo leer " + def.tabla() + ": " + e.getMessage(), e);
            }
        }
    }

    private List<String[]> leer(Connection c) throws SQLException {
        String sql = "SELECT " + String.join(", ", def.columnas()) + " FROM " + def.tabla() + " ORDER BY " + def.orden();
        return Bd.consultar(c, sql, rs -> {
            String[] fila = new String[def.columnas().length];
            for (int i = 0; i < fila.length; i++) {
                fila[i] = valor(rs, i);
            }
            return fila;
        });
    }

    private String valor(ResultSet rs, int i) throws SQLException {
        int pos = i + 1;
        switch (def.tipos()[i]) {
            case NUMERO: {
                BigDecimal n = rs.getBigDecimal(pos);
                return n == null ? "" : n.toPlainString();
            }
            case MOMENTO: {
                Timestamp t = rs.getTimestamp(pos);
                return t == null ? "" : t.toLocalDateTime().format(FORMATO);
            }
            default: {
                String s = rs.getString(pos);
                return s == null ? "" : s;
            }
        }
    }

    public void agregar(String... campos) throws IOException {
        synchronized (CERROJO) {
            try {
                Bd.actualizar(insertSql(), convertir(campos));
            } catch (excepciones.PersistenciaException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    /** Reemplaza todo el contenido de la tabla por las filas recibidas, en una sola transacción (o se guarda todo o nada). */
    public void reescribir(List<String[]> filas) throws IOException {
        synchronized (CERROJO) {
            try {
                Bd.transaccion(c -> {
                    Bd.actualizar(c, "DELETE FROM " + def.tabla());
                    List<Object[]> lote = new ArrayList<>();
                    for (String[] f : filas) {
                        lote.add(convertir(f));
                    }
                    Bd.insertarLote(c, insertSql(), lote);
                    return null;
                });
            } catch (excepciones.PersistenciaException e) {
                throw new IOException(e.getMessage(), e);
            }
        }
    }

    private String insertSql() {
        String marcas = String.join(",", java.util.Collections.nCopies(def.columnas().length, "?"));
        return "INSERT INTO " + def.tabla() + " (" + String.join(", ", def.columnas()) + ") VALUES (" + marcas + ")";
    }

    private Object[] convertir(String[] campos) {
        Object[] r = new Object[def.columnas().length];
        for (int i = 0; i < r.length; i++) {
            String s = i < campos.length ? campos[i] : "";
            if (s == null || s.isEmpty()) {
                r[i] = null;
                continue;
            }
            switch (def.tipos()[i]) {
                case NUMERO: r[i] = new BigDecimal(s); break;
                case MOMENTO: r[i] = Timestamp.valueOf(s); break;
                default: r[i] = s;
            }
        }
        return r;
    }
}
