package InterfacesUsuario;

import control.ControladorUsuarios;
import java.awt.Window;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;

/** Decide qué ventana mostrar según el estado del sistema y el rol de quien inicia sesión. */
public final class Navegador {
    private Navegador() {
    }

    /**
     * Pide confirmación cuando alguien cierra la ventana con la X. Si responde que sí, el programa termina;
     * si no, la ventana sigue abierta. (Cerrar sesión no pasa por aquí.)
     */
    public static void confirmarAlSalir(JFrame ventana) {
        ventana.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        ventana.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                Object[] opciones = {"Sí, salir", "No, volver"};
                int r = JOptionPane.showOptionDialog(ventana, "¿Seguro que deseas salir del sistema?", "Salir",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE, null, opciones, opciones[1]);
                if (r == 0) {
                    DB.ConexionOracle.cerrarTodo();
                    System.exit(0);
                }
            }
        });
    }

    /** El administrador principal ya lo crea el sistema al arrancar (InicializadorSistema), así que siempre se empieza en el login. */
    public static void iniciar() {
        new PantallaInicioSesion().setVisible(true);
    }

    public static void abrirSesion(Usuario usuario, Window origen) {
        if (usuario.isClavePendiente()) {
            DialogoCambioClave cambio = new DialogoCambioClave(origen, usuario);
            cambio.setVisible(true);
            if (!cambio.fueCambiada()) {
                return; // sin cambiar la contraseña temporal no se entra: se queda en el login
            }
            Object datos = new ControladorUsuarios().obtener(usuario.getIdUsuario()).getDatos();
            if (datos instanceof Usuario actualizado) {
                refrescar(usuario, actualizado);
            }
            usuario.setClavePendiente(false);
        }
        // La ventana de acceso sigue visible (con un aviso "Iniciando sesión...") hasta que la cuenta esté lista:
        // así la aplicación nunca parece cerrarse ni queda un hueco sin ventanas.
        PantallaCarga carga = new PantallaCarga(origen, "Iniciando sesión...");
        carga.mostrar();
        javax.swing.Timer espera = new javax.swing.Timer(60, e -> construirYMostrar(usuario, origen, carga));
        espera.setRepeats(false);
        espera.start();
    }

    private static void construirYMostrar(Usuario usuario, Window origen, PantallaCarga carga) {
        Rol rol = usuario.getRol();
        JFrame ventana;
        try {
            if (rol == Rol.PASAJERO) {
                ventana = new PantallaPortalPasajero(usuario);
            } else if (rol == Rol.CONDUCTOR) {
                ventana = new PantallaPortalConductor(usuario);
            } else {
                ventana = new PantallaPrincipal(usuario);
            }
        } catch (RuntimeException ex) {
            carga.cerrar();
            Tema.error(origen, "No se pudo abrir tu cuenta: " + ex.getMessage());
            return;
        }
        ventana.setIconImage(IconoApp.crear(64));
        ventana.setExtendedState(JFrame.MAXIMIZED_BOTH);
        ventana.setVisible(true);
        carga.cerrar();
        if (origen != null) {
            origen.dispose();
        }
        if (!usuario.tienePreguntaSeguridad()) {
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(ventana,
                    "Tu cuenta aún no tiene pregunta de seguridad.\nConfigúrala en Cuenta ▸ Mi perfil para poder recuperar tu contraseña si la olvidas.",
                    "Recomendación de seguridad", JOptionPane.INFORMATION_MESSAGE));
        }
    }

    /** Pide confirmación antes de cerrar la sesión (evita salir por un clic accidental). */
    public static void cerrarSesion(Window actual) {
        Object[] opciones = {"Sí, cerrar sesión", "Cancelar"};
        int respuesta = JOptionPane.showOptionDialog(actual, "¿Seguro que quieres cerrar tu sesión?\nLo que no hayas guardado se perderá.",
                "Cerrar sesión", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, opciones, opciones[1]);
        if (respuesta != 0) {
            return;
        }
        new PantallaInicioSesion().setVisible(true);
        if (actual != null) {
            actual.dispose();
        }
    }

    /** Copia en "destino" los datos actualizados de la cuenta para que la ventana abierta los refleje. */
    public static void refrescar(Usuario destino, Usuario fuente) {
        destino.setNombre(fuente.getNombre());
        destino.setCedula(fuente.getCedula());
        destino.setCargo(fuente.getCargo());
        destino.setEmail(fuente.getEmail());
        destino.setTelefono(fuente.getTelefono());
        destino.setContrasena(fuente.getContrasena());
        destino.setPreguntaSeguridad(fuente.getPreguntaSeguridad());
        destino.setRespuestaSeguridad(fuente.getRespuestaSeguridad());
        destino.setEstado(fuente.getEstado());
    }
}
