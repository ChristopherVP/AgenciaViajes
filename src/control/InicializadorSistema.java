package control;

import DB.Bd;
import DB.EsquemaOracle;
import excepciones.PersistenciaException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Prepara el sistema al arrancar, lo más rápido posible:
 * 1) comprueba las tablas de Oracle (una sola consulta; solo las crea la primera vez o si hay una migración nueva),
 * 2) asegura la cuenta del administrador principal,
 * 3) la PRIMERA vez carga los datos iniciales (empresas, buses, rutas, conductores y viajes). Queda marcado en la tabla
 *    CONFIGURACION y no vuelve a ejecutarse, así nunca se pisan los datos que crees.
 * NUNCA borra información existente.
 */
public final class InicializadorSistema {
    private static final String CLAVE_DATOS_INICIALES = "DATOS_INICIALES";

    public interface Progreso {
        void avance(int porcentaje, String mensaje);
    }

    public static class Resumen {
        public boolean administradorCreado;
        public int viajesCreados;
        public final List<String> advertencias = new ArrayList<>();
    }

    private InicializadorSistema() {
    }

    public static Resumen preparar(Progreso progreso) {
        Resumen resumen = new Resumen();
        notificar(progreso, 15, "Conectando con la base de datos...");
        try {
            EsquemaOracle.asegurar();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo preparar la base de datos Oracle: " + e.getMessage(), e);
        }

        notificar(progreso, 55, "Verificando el administrador principal...");
        ResultadoOperacion admin = new ControladorUsuarios().asegurarAdministradorPrincipal();
        if (!admin.isExito()) {
            resumen.advertencias.add("Administrador principal: " + admin.getMensaje());
        } else {
            resumen.administradorCreado = admin.getMensaje().startsWith("Administrador principal creado");
        }

        try {
            if (!datosInicialesYaCargados()) {
                notificar(progreso, 70, "Cargando datos iniciales (solo la primera vez)...");
                resumen.viajesCreados = DatosIniciales.sembrarSiHaceFalta();
                Bd.actualizar("INSERT INTO CONFIGURACION (CLAVE, VALOR) VALUES (?, ?)", CLAVE_DATOS_INICIALES, "1");
            }
        } catch (PersistenciaException e) {
            resumen.advertencias.add("Datos iniciales: " + e.getMessage());
        }
        notificar(progreso, 100, "Todo listo");
        return resumen;
    }

    private static boolean datosInicialesYaCargados() throws PersistenciaException {
        return Bd.uno("SELECT VALOR FROM CONFIGURACION WHERE CLAVE = ?", rs -> rs.getString(1), CLAVE_DATOS_INICIALES) != null;
    }

    /** Quita la marca de "datos iniciales cargados" (la usa la herramienta de restablecimiento antes de volver a sembrar). */
    public static void olvidarDatosIniciales() throws PersistenciaException {
        Bd.actualizar("DELETE FROM CONFIGURACION WHERE CLAVE = ?", CLAVE_DATOS_INICIALES);
    }

    /** Siembra los datos iniciales y deja la marca (la usa la herramienta de restablecimiento). */
    public static int sembrarDatosIniciales() throws PersistenciaException {
        int viajes = DatosIniciales.sembrarSiHaceFalta();
        if (!datosInicialesYaCargados()) {
            Bd.actualizar("INSERT INTO CONFIGURACION (CLAVE, VALOR) VALUES (?, ?)", CLAVE_DATOS_INICIALES, "1");
        }
        return viajes;
    }

    private static void notificar(Progreso progreso, int porcentaje, String mensaje) {
        if (progreso != null) {
            progreso.avance(Math.min(100, porcentaje), mensaje);
        }
    }
}
