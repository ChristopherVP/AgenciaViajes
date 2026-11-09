package InterfacesUsuario;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import objetosNegocio.Ruta;

/**
 * Mapa del viaje que el pasajero va a comprar. Aclara que el viaje es directo (el bus no hace paradas): las ciudades del mapa
 * son solo por donde pasa, para que el pasajero conozca el trayecto.
 */
public class DialogoMapaRuta extends JDialog {
    public DialogoMapaRuta(Window padre, Ruta ruta, String detalleViaje) {
        super(padre, "Ruta del viaje", Dialog.ModalityType.APPLICATION_MODAL);
        setIconImage(IconoRQ.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado(ruta.getOrigen() + "  →  " + ruta.getDestino(), "Viaje directo · el bus no hace paradas"), BorderLayout.NORTH);
        PanelMapaRuta mapa = new PanelMapaRuta();
        List<String> pasaPor = mapa.mostrar(ruta.getOrigen(), ruta.getDestino());

        JPanel info = new JPanel(new BorderLayout(0, 6));
        info.setBackground(Tema.FONDO);
        info.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        info.setPreferredSize(new Dimension(300, 0));
        JLabel titulo = Tema.titulo("Tu trayecto", 16f);
        String texto = "<b>Origen:</b> " + ruta.getOrigen() + "<br><b>Destino:</b> " + ruta.getDestino()
                + "<br><b>Distancia:</b> " + String.format("%.0f km", ruta.getDistancia())
                + "<br><b>Duración estimada:</b> " + String.format("%.1f horas", ruta.getTiempoEstimado())
                + (detalleViaje == null || detalleViaje.isEmpty() ? "" : "<br>" + detalleViaje)
                + "<br><br><b>El bus pasará por:</b><br>"
                + (pasaPor.isEmpty() ? "No pasa por otras ciudades importantes." : String.join(", ", pasaPor) + ".")
                + "<br><br><i>Es información para que conozcas el camino: el viaje es directo y el bus <b>no se detiene</b> en estas ciudades.</i>";
        javax.swing.JEditorPane detalle = new javax.swing.JEditorPane("text/html",
                "<html><body style='font-family:" + Tema.fuente(Font.PLAIN, 13f).getFamily() + ";font-size:12px'>" + texto + "</body></html>");
        detalle.setEditable(false);
        detalle.setOpaque(false);
        detalle.setBorder(null);
        info.add(titulo, BorderLayout.NORTH);
        info.add(new javax.swing.JScrollPane(detalle, javax.swing.JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, javax.swing.JScrollPane.HORIZONTAL_SCROLLBAR_NEVER) {{
            setBorder(null);
            setOpaque(false);
            getViewport().setOpaque(false);
        }}, BorderLayout.CENTER);
        JButton cerrar = Tema.boton("Cerrar", true);
        cerrar.addActionListener(e -> dispose());
        info.add(cerrar, BorderLayout.SOUTH);

        add(mapa, BorderLayout.CENTER);
        add(info, BorderLayout.EAST);
        setPreferredSize(new Dimension(900, 700));
        pack();
        setLocationRelativeTo(padre);
    }
}
