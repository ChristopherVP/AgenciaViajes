package main;

import InterfacesUsuario.IconoApp;
import InterfacesUsuario.Navegador;
import InterfacesUsuario.SplashScreen;
import InterfacesUsuario.Tema;
import java.awt.Taskbar;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            error.printStackTrace();
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Ocurrió un error inesperado:\n" + error, objetosServicio.Marca.NOMBRE, JOptionPane.ERROR_MESSAGE));
        });
        SwingUtilities.invokeLater(() -> {
            Tema.aplicarAspecto();
            try {
                if (Taskbar.isTaskbarSupported() && Taskbar.getTaskbar().isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    Taskbar.getTaskbar().setIconImage(IconoApp.crear(128));
                }
            } catch (UnsupportedOperationException | SecurityException e) {
                // sin icono en la barra de tareas: no es crítico
            }
            new SplashScreen(Navegador::iniciar).iniciar();
            // Reloj de la simulación: salidas, llegadas y cancelaciones por falta de aceptación se aplican solos.
            new javax.swing.Timer(15000, e -> new control.ControladorSimulacion().avanzar()).start();
        });
    }
}
