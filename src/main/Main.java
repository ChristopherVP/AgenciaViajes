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
        DB.ConexionOracle.precalentar();
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
            java.util.concurrent.ScheduledExecutorService reloj = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "reloj-simulacion");
                t.setDaemon(true);
                return t;
            });
            reloj.scheduleWithFixedDelay(() -> {
                try {
                    new control.ControladorSimulacion().avanzar();
                } catch (RuntimeException ex) {
                    System.err.println("Reloj de simulación: " + ex.getMessage());
                }
            }, 20, 15, java.util.concurrent.TimeUnit.SECONDS);
        });
    }
}
