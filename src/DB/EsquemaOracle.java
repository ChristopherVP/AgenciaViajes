package DB;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Crea y actualiza las tablas del sistema en Oracle.
 *
 * Cómo funciona:
 *  - La tabla ESQUEMA_VERSION guarda qué migraciones ya se aplicaron.
 *  - Al iniciar, {@link #asegurar()} hace UNA consulta (SELECT MAX(VERSION)); si ya está en la última versión no hace nada más.
 *  - La primera vez (base vacía) aplica la migración 1, que crea todas las tablas. Si una tabla ya existía, la salta.
 *  - Si algún día cambias el modelo (columna nueva, tabla nueva), NO modifiques la migración 1: agrega una migración
 *    nueva al final de {@link #MIGRACIONES} (por ejemplo "ALTER TABLE VIAJES ADD OBSERVACION VARCHAR2(200)").
 *    Se ejecutará una sola vez en el próximo arranque y quedará registrada.
 */
public final class EsquemaOracle {

    private record Paso(String tabla, String sql) {
        static Paso tabla(String nombre, String sql) {
            return new Paso(nombre, sql);
        }

        static Paso otro(String sql) {
            return new Paso(null, sql);
        }
    }

    private record Migracion(int version, String descripcion, List<Paso> pasos) {
    }

    private static final List<Migracion> MIGRACIONES = List.of(
            new Migracion(1, "Tablas iniciales del sistema", List.of(
                    Paso.tabla("EMPRESAS", "CREATE TABLE EMPRESAS ("
                            + "ID_EMPRESA VARCHAR2(10) PRIMARY KEY, NOMBRE VARCHAR2(120) NOT NULL, RUC VARCHAR2(20), "
                            + "DIRECCION VARCHAR2(200), TELEFONO VARCHAR2(20), EMAIL VARCHAR2(100))"),
                    Paso.tabla("BUSES", "CREATE TABLE BUSES ("
                            + "ID_BUS VARCHAR2(10) PRIMARY KEY, PLACA VARCHAR2(15) NOT NULL, NUMERO_CHASIS VARCHAR2(40), "
                            + "NUMERO_MOTOR VARCHAR2(40), MARCA VARCHAR2(40), ANIO_FABRICACION NUMBER(4), CAPACIDAD NUMBER(4) NOT NULL, "
                            + "ESTADO VARCHAR2(20), ID_EMPRESA VARCHAR2(10), "
                            + "CONSTRAINT FK_BUSES_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("RUTAS", "CREATE TABLE RUTAS ("
                            + "ID_RUTA VARCHAR2(10) PRIMARY KEY, ORIGEN VARCHAR2(100) NOT NULL, DESTINO VARCHAR2(100) NOT NULL, "
                            + "DISTANCIA NUMBER(8,2), PRECIO NUMBER(10,2), ID_EMPRESA VARCHAR2(10), TIEMPO_ESTIMADO NUMBER(6,2), "
                            + "PARADAS VARCHAR2(300), TIEMPO_DETENCION NUMBER(4), ADVERTENCIAS VARCHAR2(300), ESTADO VARCHAR2(20), "
                            + "CONSTRAINT FK_RUTAS_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("CONDUCTORES", "CREATE TABLE CONDUCTORES ("
                            + "ID_CONDUCTOR VARCHAR2(10) PRIMARY KEY, NOMBRE VARCHAR2(120) NOT NULL, CEDULA VARCHAR2(15), "
                            + "TELEFONO VARCHAR2(20), LICENCIA VARCHAR2(20), ESTADO VARCHAR2(20), ID_EMPRESA VARCHAR2(10), "
                            + "CONSTRAINT FK_CONDUCTORES_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("PASAJEROS", "CREATE TABLE PASAJEROS ("
                            + "ID_PASAJERO VARCHAR2(10) PRIMARY KEY, NOMBRE VARCHAR2(120) NOT NULL, CEDULA VARCHAR2(15), "
                            + "TELEFONO VARCHAR2(20), EMAIL VARCHAR2(100), DIRECCION VARCHAR2(200), ID_EMPRESA VARCHAR2(10), "
                            + "FECHA_NACIMIENTO DATE, DISCAPACIDAD NUMBER(1) DEFAULT 0, "
                            + "CONSTRAINT FK_PASAJEROS_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("USUARIOS", "CREATE TABLE USUARIOS ("
                            + "ID_USUARIO VARCHAR2(10) PRIMARY KEY, LOGIN VARCHAR2(40) NOT NULL, NOMBRE VARCHAR2(120) NOT NULL, "
                            + "CEDULA VARCHAR2(15), CONTRASENA VARCHAR2(200), EMAIL VARCHAR2(100), TELEFONO VARCHAR2(20), "
                            + "TIPO VARCHAR2(20), FECHA_REGISTRO DATE, ID_EMPRESA VARCHAR2(10), ESTADO VARCHAR2(20), "
                            + "PREGUNTA_SEGURIDAD VARCHAR2(200), RESPUESTA_SEGURIDAD VARCHAR2(200), ID_PERFIL VARCHAR2(10), "
                            + "CARGO VARCHAR2(100), INTENTOS_RECUPERACION NUMBER(4) DEFAULT 0, BLOQUEO_HASTA NUMBER(19) DEFAULT 0, "
                            + "CLAVE_PENDIENTE NUMBER(1) DEFAULT 0, CONSTRAINT UQ_USUARIOS_LOGIN UNIQUE (LOGIN), "
                            + "CONSTRAINT FK_USUARIOS_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("VIAJES", "CREATE TABLE VIAJES ("
                            + "ID_VIAJE VARCHAR2(10) PRIMARY KEY, ID_BUS VARCHAR2(10) NOT NULL, ID_RUTA VARCHAR2(10) NOT NULL, "
                            + "FECHA_SALIDA DATE, FECHA_LLEGADA DATE, ASIENTOS_DISPONIBLES NUMBER(4), ESTADO VARCHAR2(20), "
                            + "ID_EMPRESA VARCHAR2(10) NOT NULL, HORA_SALIDA VARCHAR2(5), HORA_LLEGADA VARCHAR2(5), PRECIO NUMBER(10,2), "
                            + "ID_CONDUCTOR1 VARCHAR2(10), ID_CONDUCTOR2 VARCHAR2(10), "
                            + "CONSTRAINT FK_VIAJES_BUS FOREIGN KEY (ID_BUS) REFERENCES BUSES (ID_BUS), "
                            + "CONSTRAINT FK_VIAJES_RUTA FOREIGN KEY (ID_RUTA) REFERENCES RUTAS (ID_RUTA), "
                            + "CONSTRAINT FK_VIAJES_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA), "
                            + "CONSTRAINT FK_VIAJES_COND1 FOREIGN KEY (ID_CONDUCTOR1) REFERENCES CONDUCTORES (ID_CONDUCTOR), "
                            + "CONSTRAINT FK_VIAJES_COND2 FOREIGN KEY (ID_CONDUCTOR2) REFERENCES CONDUCTORES (ID_CONDUCTOR))"),
                    Paso.tabla("RESERVAS", "CREATE TABLE RESERVAS ("
                            + "ID_RESERVA VARCHAR2(10) PRIMARY KEY, ID_PASAJERO VARCHAR2(10) NOT NULL, ID_VIAJE VARCHAR2(10) NOT NULL, "
                            + "CANTIDAD_ASIENTOS NUMBER(3), FECHA_RESERVA DATE, ESTADO VARCHAR2(20), ID_EMPRESA VARCHAR2(10) NOT NULL, "
                            + "PRECIO_TOTAL NUMBER(10,2), METODO_PAGO VARCHAR2(20), NINOS_BRAZOS NUMBER(3) DEFAULT 0, "
                            + "CONSTRAINT FK_RESERVAS_PASAJERO FOREIGN KEY (ID_PASAJERO) REFERENCES PASAJEROS (ID_PASAJERO), "
                            + "CONSTRAINT FK_RESERVAS_VIAJE FOREIGN KEY (ID_VIAJE) REFERENCES VIAJES (ID_VIAJE), "
                            + "CONSTRAINT FK_RESERVAS_EMPRESA FOREIGN KEY (ID_EMPRESA) REFERENCES EMPRESAS (ID_EMPRESA))"),
                    Paso.tabla("ASIENTOS", "CREATE TABLE ASIENTOS ("
                            + "ID_ASIENTO VARCHAR2(30) PRIMARY KEY, NUMERO_ASIENTO NUMBER(4), ESTADO VARCHAR2(20), "
                            + "ID_VIAJE VARCHAR2(10) NOT NULL, ID_RESERVA VARCHAR2(10), UBICACION VARCHAR2(30), LIBERADO_EN VARCHAR2(60), "
                            + "CONSTRAINT FK_ASIENTOS_VIAJE FOREIGN KEY (ID_VIAJE) REFERENCES VIAJES (ID_VIAJE) ON DELETE CASCADE)"),
                    Paso.tabla("FACTURAS", "CREATE TABLE FACTURAS ("
                            + "ID_FACTURA VARCHAR2(10) PRIMARY KEY, ID_PASAJERO VARCHAR2(10), ID_RESERVA VARCHAR2(10), TOTAL NUMBER(12,2), "
                            + "FECHA_EMISION DATE, ESTADO VARCHAR2(20), ID_EMPRESA VARCHAR2(10), NOMBRE_EMPRESA_SNAPSHOT VARCHAR2(120), "
                            + "RUC_EMPRESA_SNAPSHOT VARCHAR2(20), DIRECCION_EMPRESA_SNAPSHOT VARCHAR2(200), NOMBRE_VENDEDOR VARCHAR2(120), "
                            + "DATOS_PASAJERO_SNAPSHOT VARCHAR2(200))"),
                    Paso.tabla("DETALLES_FACTURA", "CREATE TABLE DETALLES_FACTURA ("
                            + "ID_DETALLE VARCHAR2(10) PRIMARY KEY, ID_FACTURA VARCHAR2(10) NOT NULL, DESCRIPCION VARCHAR2(200), "
                            + "CANTIDAD NUMBER(6), PRECIO_UNITARIO NUMBER(12,2), SUBTOTAL NUMBER(12,2), "
                            + "CONSTRAINT FK_DETALLES_FACTURA FOREIGN KEY (ID_FACTURA) REFERENCES FACTURAS (ID_FACTURA) ON DELETE CASCADE)"),
                    Paso.tabla("PAGOS", "CREATE TABLE PAGOS ("
                            + "ID_PAGO VARCHAR2(10) PRIMARY KEY, ID_USUARIO VARCHAR2(10), ID_FACTURA VARCHAR2(10), MONTO NUMBER(12,2), "
                            + "FECHA_PAGO DATE, METODO VARCHAR2(20))"),
                    Paso.tabla("MOVIMIENTOS", "CREATE TABLE MOVIMIENTOS ("
                            + "ID_MOVIMIENTO VARCHAR2(10) PRIMARY KEY, ID_PASAJERO VARCHAR2(10) NOT NULL, TIPO VARCHAR2(12), "
                            + "MONTO NUMBER(12,2), METODO VARCHAR2(12), ESTADO VARCHAR2(12), REFERENCIA VARCHAR2(100), FECHA DATE, "
                            + "HORA VARCHAR2(8), ID_REVISOR VARCHAR2(10), MOTIVO VARCHAR2(400), "
                            + "CONSTRAINT FK_MOVIMIENTOS_PASAJERO FOREIGN KEY (ID_PASAJERO) REFERENCES PASAJEROS (ID_PASAJERO))"),
                    Paso.tabla("NOVEDADES", "CREATE TABLE NOVEDADES ("
                            + "ID_NOVEDAD VARCHAR2(10) PRIMARY KEY, ID_CONDUCTOR VARCHAR2(10), ID_VIAJE VARCHAR2(10), TIPO VARCHAR2(20), "
                            + "ASUNTO VARCHAR2(200), MENSAJE VARCHAR2(1500), FECHA DATE, HORA VARCHAR2(8), ESTADO VARCHAR2(12), "
                            + "RESPUESTA VARCHAR2(1000), ID_REVISOR VARCHAR2(10))"),
                    Paso.tabla("ASIGNACIONES", "CREATE TABLE ASIGNACIONES ("
                            + "ID_CONDUCTOR VARCHAR2(10) PRIMARY KEY, ID_BUS VARCHAR2(10) NOT NULL, ID_RUTA VARCHAR2(10) NOT NULL, "
                            + "CONSTRAINT UQ_ASIGNACIONES_BUS UNIQUE (ID_BUS), "
                            + "CONSTRAINT FK_ASIG_CONDUCTOR FOREIGN KEY (ID_CONDUCTOR) REFERENCES CONDUCTORES (ID_CONDUCTOR) ON DELETE CASCADE, "
                            + "CONSTRAINT FK_ASIG_BUS FOREIGN KEY (ID_BUS) REFERENCES BUSES (ID_BUS), "
                            + "CONSTRAINT FK_ASIG_RUTA FOREIGN KEY (ID_RUTA) REFERENCES RUTAS (ID_RUTA))"),
                    Paso.tabla("NOTIFICACIONES", "CREATE TABLE NOTIFICACIONES ("
                            + "ID_NOTIFICACION VARCHAR2(12) PRIMARY KEY, DESTINO VARCHAR2(20) NOT NULL, TITULO VARCHAR2(200), "
                            + "MENSAJE VARCHAR2(1500), FECHA TIMESTAMP, LEIDO NUMBER(1) DEFAULT 0, REFERENCIA VARCHAR2(20))"),
                    Paso.tabla("LIQUIDACIONES", "CREATE TABLE LIQUIDACIONES ("
                            + "ID_LIQUIDACION VARCHAR2(12) PRIMARY KEY, ID_VIAJE VARCHAR2(10), ID_CONDUCTOR VARCHAR2(10), "
                            + "NOMBRE_CONDUCTOR VARCHAR2(120), EMPRESA VARCHAR2(120), RUTA VARCHAR2(200), FECHA_VIAJE VARCHAR2(20), "
                            + "PASAJEROS NUMBER(6), RECAUDADO NUMBER(12,2), PARTES NUMBER(2), MONTO NUMBER(12,2), ESTADO VARCHAR2(12), "
                            + "NOTIFICADO_EN TIMESTAMP, PAGADO_EN TIMESTAMP, PAGADO_POR VARCHAR2(40), TARJETA VARCHAR2(120), "
                            + "NUMERO_FACTURA VARCHAR2(20), LLEGADA_VIAJE VARCHAR2(20))"),
                    Paso.tabla("TARJETAS", "CREATE TABLE TARJETAS ("
                            + "ID_TITULAR VARCHAR2(10) NOT NULL, USO VARCHAR2(10) NOT NULL, BANCO VARCHAR2(80), MARCA VARCHAR2(20), "
                            + "ULTIMOS4 VARCHAR2(4), TITULAR VARCHAR2(120), VENCE VARCHAR2(5), PRIMARY KEY (ID_TITULAR, USO))"),
                    Paso.tabla("COMPROBANTES", "CREATE TABLE COMPROBANTES ("
                            + "NUMERO VARCHAR2(20) PRIMARY KEY, TIPO VARCHAR2(12) NOT NULL, ID_TITULAR VARCHAR2(10), NOMBRE_TITULAR VARCHAR2(120), "
                            + "CEDULA_TITULAR VARCHAR2(15), CONCEPTO VARCHAR2(300), DETALLE VARCHAR2(1500), TOTAL NUMBER(12,2), "
                            + "FECHA TIMESTAMP, REFERENCIA VARCHAR2(40), EMISOR VARCHAR2(120))"),
                    Paso.tabla("CONFIGURACION", "CREATE TABLE CONFIGURACION ("
                            + "CLAVE VARCHAR2(60) PRIMARY KEY, VALOR VARCHAR2(200))"))),
            new Migracion(2, "Índices para consultas por conductor, estado y fecha (informes por periodo)", List.of(
                    Paso.otro("CREATE INDEX IDX_VIAJES_CONDUCTOR1 ON VIAJES (ID_CONDUCTOR1)"),
                    Paso.otro("CREATE INDEX IDX_VIAJES_ESTADO ON VIAJES (ESTADO)"),
                    Paso.otro("CREATE INDEX IDX_VIAJES_SALIDA ON VIAJES (FECHA_SALIDA)"),
                    Paso.otro("CREATE INDEX IDX_RESERVAS_VIAJE ON RESERVAS (ID_VIAJE)"),
                    Paso.otro("CREATE INDEX IDX_RESERVAS_PASAJERO ON RESERVAS (ID_PASAJERO)"),
                    Paso.otro("CREATE INDEX IDX_ASIENTOS_VIAJE ON ASIENTOS (ID_VIAJE)"),
                    Paso.otro("CREATE INDEX IDX_MOVIMIENTOS_PASAJERO ON MOVIMIENTOS (ID_PASAJERO)"),
                    Paso.otro("CREATE INDEX IDX_NOTIF_DESTINO ON NOTIFICACIONES (DESTINO)"),
                    Paso.otro("CREATE INDEX IDX_COMPROBANTES_FECHA ON COMPROBANTES (FECHA)"))));

    private static final int ULTIMA_VERSION = MIGRACIONES.get(MIGRACIONES.size() - 1).version();

    /** Tablas en el orden en que se pueden vaciar sin romper claves foráneas (hijas primero). */
    public static final List<String> TABLAS_PARA_VACIAR = List.of("ASIENTOS", "DETALLES_FACTURA", "PAGOS", "FACTURAS", "MOVIMIENTOS",
            "RESERVAS", "NOVEDADES", "LIQUIDACIONES", "COMPROBANTES", "NOTIFICACIONES", "TARJETAS", "ASIGNACIONES", "VIAJES",
            "USUARIOS", "PASAJEROS", "CONDUCTORES", "RUTAS", "BUSES", "EMPRESAS");

    private static volatile boolean verificado;

    private EsquemaOracle() {
    }

    /** Camino normal: una sola consulta. Solo trabaja de verdad la primera vez o cuando hay una migración nueva. */
    public static synchronized void asegurar() throws SQLException {
        if (verificado) {
            return;
        }
        try (Connection c = ConexionOracle.abrir()) {
            int actual = versionActual(c);
            if (actual < ULTIMA_VERSION) {
                for (Migracion m : MIGRACIONES) {
                    if (m.version() > actual) {
                        aplicar(c, m);
                    }
                }
            }
        }
        verificado = true;
    }

    private static int versionActual(Connection c) throws SQLException {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SELECT MAX(VERSION) FROM ESQUEMA_VERSION")) {
            return rs.next() ? rs.getInt(1) : 0;
        } catch (SQLException e) {
            if (tablaExiste(c, "ESQUEMA_VERSION")) {
                throw e;
            }
            try (Statement st = c.createStatement()) {
                st.execute("CREATE TABLE ESQUEMA_VERSION (VERSION NUMBER(5) PRIMARY KEY, DESCRIPCION VARCHAR2(200), "
                        + "APLICADA TIMESTAMP DEFAULT CURRENT_TIMESTAMP)");
            }
            return 0;
        }
    }

    private static void aplicar(Connection c, Migracion m) throws SQLException {
        for (Paso p : m.pasos()) {
            if (p.tabla() != null && tablaExiste(c, p.tabla())) {
                continue;
            }
            try (Statement st = c.createStatement()) {
                st.execute(p.sql());
            } catch (SQLException e) {
                if (p.tabla() != null || !yaExiste(e)) {
                    throw new SQLException("Falló la migración " + m.version() + " (" + m.descripcion() + "): " + e.getMessage(), e);
                }
            }
        }
        Bd.actualizar(c, "INSERT INTO ESQUEMA_VERSION (VERSION, DESCRIPCION) VALUES (?, ?)", m.version(), m.descripcion());
    }

    private static boolean yaExiste(SQLException e) {
        return e.getErrorCode() == 955 || e.getErrorCode() == 1408 || e.getErrorCode() == 42111
                || "42S11".equals(e.getSQLState());
    }

    private static boolean tablaExiste(Connection c, String tabla) throws SQLException {
        DatabaseMetaData meta = c.getMetaData();
        try (ResultSet rs = meta.getTables(null, c.getSchema(), tabla, new String[]{"TABLE"})) {
            return rs.next();
        }
    }
}
