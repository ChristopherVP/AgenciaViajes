package DB;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Conexión a Oracle con un grupo (pool) pequeño de conexiones reutilizables.
 *
 * Abrir una conexión a Oracle cuesta entre 100 y 800 ms; con el pool solo se paga una vez. Cada llamada a
 * {@link #abrir()} devuelve una conexión "prestada": al hacer close() (try-with-resources) NO se cierra de verdad,
 * vuelve al pool lista para el siguiente uso. Los datos de acceso salen de oracle.properties o de las variables de
 * entorno ORACLE_URL, ORACLE_USER y ORACLE_PASSWORD (nunca van escritos en el código).
 */
public final class ConexionOracle {
    private static final Path ARCHIVO_CONFIGURACION = Path.of("oracle.properties");
    private static final int MAXIMO_EN_POOL = 4;
    private static final long SEGUNDOS_SIN_VERIFICAR = 30;

    private static final BlockingQueue<Prestada> LIBRES = new LinkedBlockingQueue<>();
    private static Properties configuracion;

    private ConexionOracle() {
    }

    private record Prestada(Connection real, long ultimoUso) {
    }

    /** Devuelve una conexión lista para usar. Siempre se debe cerrar (try-with-resources) para devolverla al pool. */
    public static Connection abrir() throws SQLException {
        Prestada p = LIBRES.poll();
        while (p != null && !sigueViva(p)) {
            cerrarSilencioso(p.real());
            p = LIBRES.poll();
        }
        Connection real = p != null ? p.real() : nueva();
        return envolver(real);
    }

    /** Abre la primera conexión en segundo plano para que el arranque de la ventana no espere a Oracle. */
    public static void precalentar() {
        Thread hilo = new Thread(() -> {
            try (Connection c = abrir()) {
                c.isValid(1);
            } catch (SQLException | RuntimeException e) {
                // el error real se mostrará cuando el sistema intente usar la base de datos
            }
        }, "precalentar-oracle");
        hilo.setDaemon(true);
        hilo.start();
    }

    /** Cierra todas las conexiones del pool (la usan las herramientas de mantenimiento antes de terminar). */
    public static void cerrarTodo() {
        Prestada p;
        while ((p = LIBRES.poll()) != null) {
            cerrarSilencioso(p.real());
        }
    }

    private static boolean sigueViva(Prestada p) {
        try {
            if (p.real().isClosed()) {
                return false;
            }
            boolean reciente = System.currentTimeMillis() - p.ultimoUso() < SEGUNDOS_SIN_VERIFICAR * 1000;
            return reciente || p.real().isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    private static Connection nueva() throws SQLException {
        Properties c = configuracion();
        String url = leerValor("ORACLE_URL", "url", c);
        String usuario = leerValor("ORACLE_USER", "user", c);
        String clave = leerValor("ORACLE_PASSWORD", "password", c);
        Connection conexion = DriverManager.getConnection(url, usuario, clave);
        conexion.setAutoCommit(true);
        return conexion;
    }

    private static Connection envolver(Connection real) {
        boolean[] devuelta = {false};
        InvocationHandler manejador = (proxy, metodo, args) -> {
            String nombre = metodo.getName();
            if (nombre.equals("close")) {
                if (!devuelta[0]) {
                    devuelta[0] = true;
                    devolver(real);
                }
                return null;
            }
            if (nombre.equals("isClosed")) {
                return devuelta[0] || real.isClosed();
            }
            if (devuelta[0]) {
                throw new SQLException("La conexión ya fue devuelta al pool");
            }
            try {
                return metodo.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (Connection) Proxy.newProxyInstance(ConexionOracle.class.getClassLoader(), new Class<?>[]{Connection.class}, manejador);
    }

    private static void devolver(Connection real) {
        try {
            if (real.isClosed()) {
                return;
            }
            if (!real.getAutoCommit()) {
                real.rollback();
                real.setAutoCommit(true);
            }
            if (LIBRES.size() < MAXIMO_EN_POOL) {
                LIBRES.offer(new Prestada(real, System.currentTimeMillis()));
            } else {
                cerrarSilencioso(real);
            }
        } catch (SQLException e) {
            cerrarSilencioso(real);
        }
    }

    private static void cerrarSilencioso(Connection c) {
        try {
            c.close();
        } catch (SQLException e) {
            System.err.println("No se pudo cerrar una conexión: " + e.getMessage());
        }
    }

    private static synchronized Properties configuracion() {
        if (configuracion == null) {
            Properties propiedades = new Properties();
            if (Files.isRegularFile(ARCHIVO_CONFIGURACION)) {
                try (InputStream entrada = Files.newInputStream(ARCHIVO_CONFIGURACION)) {
                    propiedades.load(entrada);
                } catch (IOException e) {
                    throw new IllegalStateException("No se pudo leer " + ARCHIVO_CONFIGURACION + ": " + e.getMessage(), e);
                }
            }
            configuracion = propiedades;
        }
        return configuracion;
    }

    private static String leerValor(String variableEntorno, String clave, Properties configuracion) {
        String valor = System.getenv(variableEntorno);
        if (valor == null || valor.isBlank()) {
            valor = configuracion.getProperty(clave);
        }
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta '" + clave + "' en " + ARCHIVO_CONFIGURACION
                    + " (o la variable de entorno " + variableEntorno + ")");
        }
        return valor.trim();
    }
}
