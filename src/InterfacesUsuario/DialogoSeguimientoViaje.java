package InterfacesUsuario;

import control.ControladorCompras;
import control.ControladorSimulacion;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.Timer;
import objetosNegocio.Ruta;
import objetosNegocio.Viaje;

/** Seguimiento en tiempo real: el bus se mueve por el mapa desde la hora de salida hasta la hora de llegada. */
public class DialogoSeguimientoViaje extends JDialog {
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("dd/MM HH:mm");
    private final Viaje viaje;
    private final PanelMapaVivo mapa = new PanelMapaVivo();
    private final JProgressBar barra = new JProgressBar(0, 1000);
    private final JLabel lblFase = Tema.titulo(" ", 16f);
    private final JLabel lblDetalle = Tema.etiqueta(" ");
    private final Timer reloj;

    public DialogoSeguimientoViaje(Window padre, Viaje viaje) {
        super(padre, "Seguimiento en vivo · " + viaje.getIdViaje(), Dialog.ModalityType.MODELESS);
        this.viaje = viaje;
        setLayout(new BorderLayout(0, 8));
        Ruta ruta = new ControladorSimulacion().rutaDe(viaje);
        String origen = ruta == null ? "" : ruta.getOrigen();
        String destino = ruta == null ? "" : ruta.getDestino();
        add(Tema.encabezado(origen + " → " + destino, "Simulación en tiempo real"), BorderLayout.NORTH);
        mapa.mostrar(origen, destino);
        add(mapa, BorderLayout.CENTER);
        barra.setStringPainted(true);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setBorder(BorderFactory.createEmptyBorder(6, 16, 12, 16));
        sur.add(lblFase, BorderLayout.NORTH);
        sur.add(barra, BorderLayout.CENTER);
        JPanel pie = new JPanel(new BorderLayout());
        pie.add(lblDetalle, BorderLayout.CENTER);
        JButton cerrar = Tema.boton("Cerrar", false);
        cerrar.addActionListener(e -> dispose());
        pie.add(cerrar, BorderLayout.EAST);
        sur.add(pie, BorderLayout.SOUTH);
        add(sur, BorderLayout.SOUTH);
        setSize(new Dimension(760, 720));
        setLocationRelativeTo(padre);
        reloj = new Timer(1000, e -> refrescar());
        reloj.start();
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                reloj.stop();
            }
        });
        refrescar();
    }

    private void refrescar() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime salida = ControladorCompras.salidaDe(viaje);
        LocalDateTime llegada = ControladorCompras.llegadaDe(viaje);
        double p = ControladorSimulacion.progreso(viaje, ahora);
        mapa.setProgreso(p);
        barra.setValue((int) Math.round(p * 1000));
        barra.setString(Math.round(p * 100) + " %");
        lblFase.setText(ControladorSimulacion.faseDe(viaje, ahora));
        lblFase.setFont(Tema.fuente(Font.BOLD, 16f));
        if (salida == null || llegada == null) {
            lblDetalle.setText(" ");
            return;
        }
        String resto;
        if (ahora.isBefore(salida)) {
            resto = "Sale en " + texto(Duration.between(ahora, salida));
        } else if (ahora.isBefore(llegada)) {
            resto = "Llega en " + texto(Duration.between(ahora, llegada));
        } else {
            resto = "Viaje terminado";
        }
        lblDetalle.setText("Salida " + salida.format(HORA) + " · Llegada " + llegada.format(HORA) + " · " + resto);
    }

    private static String texto(Duration d) {
        long m = d.toMinutes();
        return m >= 60 ? (m / 60) + " h " + (m % 60) + " min" : Math.max(0, m) + " min " + (d.getSeconds() % 60) + " s";
    }
}
