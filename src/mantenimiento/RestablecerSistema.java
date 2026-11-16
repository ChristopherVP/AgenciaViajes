package mantenimiento;

import DB.Bd;
import DB.ConexionOracle;
import DB.EsquemaOracle;
import control.ControladorUsuarios;
import control.InicializadorSistema;
import control.ResultadoOperacion;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import javax.swing.JOptionPane;

/**
 * HERRAMIENTA DE MANTENIMIENTO: deja el sistema como recién instalado.
 *
 * Ejecútala con clic derecho > Run File (NetBeans) sobre esta clase. NO forma parte del programa normal.
 *
 * Qué borra: TODO lo creado desde que se usa el sistema (usuarios, pasajeros, reservas, viajes, facturas, billeteras,
 * liquidaciones, notificaciones, novedades, tarjetas, fotos y documentos subidos, y cualquier empresa, bus, ruta o conductor
 * que hayas agregado tú).
 * Qué conserva / vuelve a crear: las tablas, el administrador principal y los datos iniciales (empresas, buses, rutas,
 * conductores y viajes de ejemplo), exactamente como en el primer arranque.
 *
 * ADMINISTRADOR POR DEFECTO PARA ENTRAR DESPUÉS DEL RESTABLECIMIENTO:
 *     Usuario:    admin
 *     Contraseña: CambiarAdmin1
 * (el sistema te obliga a cambiarla en el primer inicio de sesión)
 */
public final class RestablecerSistema {

    private RestablecerSistema() {
    }

    public static void main(String[] args) {
        String confirmacion = JOptionPane.showInputDialog(null,
                "Esto BORRARÁ todos los datos que has creado en Oracle y volverá al estado inicial.\n"
                        + "Se mantiene el administrador por defecto y los datos iniciales.\n\n"
                        + "Escribe BORRAR para continuar:", "Restablecer sistema", JOptionPane.WARNING_MESSAGE);
        if (confirmacion == null || !confirmacion.trim().equals("BORRAR")) {
            System.out.println("Cancelado: no se borró nada.");
            return;
        }
        try {
            EsquemaOracle.asegurar();
            Bd.transaccion(c -> {
                for (String tabla : EsquemaOracle.TABLAS_PARA_VACIAR) {
                    Bd.actualizar(c, "DELETE FROM " + tabla);
                }
                Bd.actualizar(c, "DELETE FROM CONFIGURACION");
                return null;
            });
            borrarCarpeta(Path.of("fotos"));
            borrarCarpeta(Path.of("documentos"));

            ResultadoOperacion admin = new ControladorUsuarios().asegurarAdministradorPrincipal();
            int viajes = InicializadorSistema.sembrarDatosIniciales();
            String mensaje = "Sistema restablecido.\n\nAdministrador: admin / CambiarAdmin1\nViajes iniciales creados: " + viajes
                    + (admin.isExito() ? "" : "\nAviso: " + admin.getMensaje());
            System.out.println(mensaje);
            JOptionPane.showMessageDialog(null, mensaje, "Restablecer sistema", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, "No se pudo restablecer: " + e.getMessage(), "Restablecer sistema", JOptionPane.ERROR_MESSAGE);
        } finally {
            ConexionOracle.cerrarTodo();
        }
    }

    private static void borrarCarpeta(Path carpeta) throws IOException {
        if (!Files.isDirectory(carpeta)) {
            return;
        }
        try (Stream<Path> recorrido = Files.walk(carpeta)) {
            for (Path p : (Iterable<Path>) recorrido.sorted(Comparator.reverseOrder())::iterator) {
                if (!p.equals(carpeta)) {
                    Files.deleteIfExists(p);
                }
            }
        }
    }
}
