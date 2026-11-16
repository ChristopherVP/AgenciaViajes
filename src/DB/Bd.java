package DB;

import excepciones.PersistenciaException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import objetosServicio.Fecha;

/**
 * Utilidades JDBC comunes a todos los DAO: consultas con parámetros (siempre PreparedStatement, nunca texto
 * concatenado), transacciones, conversión de {@link Fecha} a DATE y generación de códigos correlativos (EMP001, VIA002...).
 */
public final class Bd {

    private Bd() {
    }

    @FunctionalInterface
    public interface Mapeador<T> {
        T mapear(ResultSet rs) throws SQLException;
    }

    @FunctionalInterface
    public interface Operacion<T> {
        T ejecutar(Connection conexion) throws SQLException, PersistenciaException;
    }

    // ------------------------------------------------------------------ consultas

    public static <T> List<T> consultar(String sql, Mapeador<T> mapeador, Object... parametros) throws PersistenciaException {
        try (Connection c = ConexionOracle.abrir()) {
            return consultar(c, sql, mapeador, parametros);
        } catch (SQLException e) {
            throw traducir("No se pudo leer de la base de datos", e);
        }
    }

    public static <T> List<T> consultar(Connection c, String sql, Mapeador<T> mapeador, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignar(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> filas = new ArrayList<>();
                while (rs.next()) {
                    filas.add(mapeador.mapear(rs));
                }
                return filas;
            }
        }
    }

    /** Primer resultado o null si no hay filas. */
    public static <T> T uno(String sql, Mapeador<T> mapeador, Object... parametros) throws PersistenciaException {
        List<T> filas = consultar(sql, mapeador, parametros);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** INSERT / UPDATE / DELETE. Devuelve las filas afectadas. */
    public static int actualizar(String sql, Object... parametros) throws PersistenciaException {
        try (Connection c = ConexionOracle.abrir(); PreparedStatement ps = c.prepareStatement(sql)) {
            asignar(ps, parametros);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw traducir("No se pudo guardar en la base de datos", e);
        }
    }

    public static int actualizar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignar(ps, parametros);
            return ps.executeUpdate();
        }
    }

    /** Ejecuta varias operaciones como una sola transacción: o se guarda todo o no se guarda nada. */
    public static <T> T transaccion(Operacion<T> operacion) throws PersistenciaException {
        try (Connection c = ConexionOracle.abrir()) {
            c.setAutoCommit(false);
            try {
                T resultado = operacion.ejecutar(c);
                c.commit();
                return resultado;
            } catch (SQLException | PersistenciaException | RuntimeException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw traducir("No se pudo guardar en la base de datos", e);
        }
    }

    public static void insertarLote(Connection c, String sql, List<Object[]> filas) throws SQLException {
        if (filas.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Object[] fila : filas) {
                asignar(ps, fila);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    public static int contar(String tabla) throws PersistenciaException {
        Integer n = uno("SELECT COUNT(*) FROM " + tabla, rs -> rs.getInt(1));
        return n == null ? 0 : n;
    }

    private static void asignar(PreparedStatement ps, Object[] parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object v = parametros[i];
            int pos = i + 1;
            if (v == null) {
                ps.setNull(pos, Types.NULL);
            } else if (v instanceof String s) {
                if (s.isEmpty()) {
                    ps.setNull(pos, Types.VARCHAR);
                } else {
                    ps.setString(pos, s);
                }
            } else if (v instanceof Fecha f) {
                Date d = aDate(f);
                if (d == null) {
                    ps.setNull(pos, Types.DATE);
                } else {
                    ps.setDate(pos, d);
                }
            } else if (v instanceof Boolean b) {
                ps.setInt(pos, b ? 1 : 0);
            } else {
                ps.setObject(pos, v);
            }
        }
    }

    // ------------------------------------------------------------------ lectura de columnas

    /** Texto no nulo: Oracle guarda el texto vacío como NULL, el sistema siempre trabaja con "". */
    public static String texto(ResultSet rs, String columna) throws SQLException {
        String s = rs.getString(columna);
        return s == null ? "" : s;
    }

    public static boolean logico(ResultSet rs, String columna) throws SQLException {
        return rs.getInt(columna) == 1;
    }

    public static Fecha fecha(ResultSet rs, String columna) throws SQLException {
        Date d = rs.getDate(columna);
        if (d == null) {
            return new Fecha(0, 0, 0);
        }
        LocalDate l = d.toLocalDate();
        return new Fecha(l.getDayOfMonth(), l.getMonthValue(), l.getYear());
    }

    /** null si la fecha no existe o no es válida (0/0/0 era la "fecha vacía" de los archivos). */
    public static Date aDate(Fecha f) {
        if (f == null || f.getAnio() <= 0 || f.getMes() <= 0 || f.getDia() <= 0) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.of(f.getAnio(), f.getMes(), f.getDia()));
        } catch (java.time.DateTimeException e) {
            return null;
        }
    }

    // ------------------------------------------------------------------ códigos correlativos

    /**
     * Siguiente código con prefijo y ceros a la izquierda: ("VIA", 3) devuelve VIA001, VIA002... Toma el mayor número
     * existente (no cuenta filas), así un código borrado nunca se vuelve a usar mientras exista uno mayor.
     */
    public static String siguienteCodigo(Connection c, String tabla, String columna, String prefijo, int digitos) throws SQLException {
        String sql = "SELECT " + columna + " FROM " + tabla + " WHERE " + columna + " LIKE ?";
        int maximo = 0;
        for (String codigo : consultar(c, sql, rs -> rs.getString(1), prefijo + "%")) {
            String resto = codigo.substring(prefijo.length());
            if (!resto.isEmpty() && resto.chars().allMatch(Character::isDigit) && resto.length() <= 9) {
                maximo = Math.max(maximo, Integer.parseInt(resto));
            }
        }
        return String.format("%s%0" + digitos + "d", prefijo, maximo + 1);
    }

    /**
     * Inserta una fila con código nuevo. Si dos operaciones piden el mismo código a la vez, la restricción PRIMARY KEY
     * rechaza la segunda y aquí se reintenta con el siguiente.
     */
    public static String insertarConCodigo(String tabla, String columna, String prefijo, int digitos, CodigoAsignado asignador,
                                           String sqlInsert, ParametrosConCodigo parametros) throws PersistenciaException {
        for (int intento = 0; intento < 5; intento++) {
            try (Connection c = ConexionOracle.abrir()) {
                String codigo = siguienteCodigo(c, tabla, columna, prefijo, digitos);
                try (PreparedStatement ps = c.prepareStatement(sqlInsert)) {
                    asignar(ps, parametros.con(codigo));
                    ps.executeUpdate();
                }
                asignador.asignar(codigo);
                return codigo;
            } catch (SQLException e) {
                if (!esClaveDuplicada(e) || intento == 4) {
                    throw traducir("No se pudo guardar en la base de datos", e);
                }
            }
        }
        throw new PersistenciaException("No se pudo generar un código nuevo");
    }

    @FunctionalInterface
    public interface CodigoAsignado {
        void asignar(String codigo);
    }

    @FunctionalInterface
    public interface ParametrosConCodigo {
        Object[] con(String codigo);
    }

    // ------------------------------------------------------------------ errores

    public static boolean esClaveDuplicada(SQLException e) {
        return e.getErrorCode() == 1 || "23505".equals(e.getSQLState()) || e.getErrorCode() == 23505;
    }

    public static PersistenciaException traducir(String contexto, SQLException e) {
        int codigo = e.getErrorCode();
        String estado = e.getSQLState() == null ? "" : e.getSQLState();
        if (codigo == 2292 || codigo == 23503 || (estado.equals("23503") && e.getMessage() != null && e.getMessage().contains("Referential"))) {
            return new PersistenciaException("No se puede eliminar: otros registros dependen de este (viajes, reservas, buses, etc.)", e);
        }
        if (codigo == 2291) {
            return new PersistenciaException("Hay un dato que no existe (empresa, bus, ruta, conductor o pasajero)", e);
        }
        if (esClaveDuplicada(e)) {
            return new PersistenciaException("Ya existe un registro con ese código o ese dato único", e);
        }
        return new PersistenciaException(contexto + ": " + e.getMessage(), e);
    }
}
