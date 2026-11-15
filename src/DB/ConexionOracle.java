package DB;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public final class ConexionOracle {
    private static final Path ARCHIVO_CONFIGURACION = Path.of("oracle.properties");

    private ConexionOracle() {
    }

    public static Connection abrir() throws SQLException {
        Properties configuracion = cargarConfiguracion();
        String url = leerValor("ORACLE_URL", "url", configuracion);
        String usuario = leerValor("ORACLE_USER", "user", configuracion);
        String clave = leerValor("ORACLE_PASSWORD", "password", configuracion);
        return DriverManager.getConnection(url, usuario, clave);
    }

    private static Properties cargarConfiguracion() {
        Properties propiedades = new Properties();
        if (Files.isRegularFile(ARCHIVO_CONFIGURACION)) {
            try (InputStream entrada = Files.newInputStream(ARCHIVO_CONFIGURACION)) {
                propiedades.load(entrada);
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo leer " + ARCHIVO_CONFIGURACION + ": " + e.getMessage(), e);
            }
        }
        return propiedades;
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