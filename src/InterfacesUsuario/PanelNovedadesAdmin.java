package InterfacesUsuario;

import control.ControladorNovedades;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Novedad;
import objetosNegocio.Usuario;

/** Bandeja de reportes que los conductores envían al administrador. */
public class PanelNovedadesAdmin extends JPanel {
    private final ControladorNovedades controlador = new ControladorNovedades();
    private final Usuario actor;
    private final JComboBox<String> cmbFiltro = new JComboBox<>(new String[]{"Sin resolver", "Todos", "Resueltos"});
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"ID", "Fecha", "Remitente", "Viaje", "Tipo", "Asunto", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private final JTextArea detalle = new JTextArea();
    private List<Novedad> filas = new ArrayList<>();

    public PanelNovedadesAdmin(Usuario actor) {
        this.actor = actor;
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel arriba = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        arriba.setOpaque(false);
        arriba.add(Tema.etiqueta("Mostrar"));
        arriba.add(cmbFiltro);
        javax.swing.JButton responder = Tema.boton("Atender reporte", true);
        javax.swing.JButton pdf = Tema.boton("Descargar PDF", false);
        javax.swing.JButton actualizar = Tema.boton("Actualizar", false);
        responder.addActionListener(e -> responder());
        pdf.addActionListener(e -> descargarPdf());
        actualizar.addActionListener(e -> recargar());
        cmbFiltro.addActionListener(e -> recargar());
        arriba.add(responder);
        arriba.add(pdf);
        arriba.add(actualizar);
        add(arriba, BorderLayout.NORTH);

        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.getSelectionModel().addListSelectionListener(e -> mostrarDetalle());
        detalle.setEditable(false);
        detalle.setLineWrap(true);
        detalle.setWrapStyleWord(true);
        detalle.setFont(Tema.fuente(Font.PLAIN, 14f));
        detalle.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JScrollPane sp = new JScrollPane(tabla);
        sp.getViewport().setBackground(Color.WHITE);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, sp, new JScrollPane(detalle));
        split.setResizeWeight(0.6);
        split.setBorder(null);
        add(split, BorderLayout.CENTER);
        recargar();
    }

    @SuppressWarnings("unchecked")
    public final void recargar() {
        modelo.setRowCount(0);
        detalle.setText("");
        ResultadoOperacion r = controlador.listarParaAdministrador(actor);
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            return;
        }
        Map<String, String> nombres = controlador.nombresDeConductores();
        String filtro = (String) cmbFiltro.getSelectedItem();
        filas = new ArrayList<>();
        for (Novedad n : (List<Novedad>) r.getDatos()) {
            boolean resuelta = Novedad.RESUELTA.equals(n.getEstado());
            if ("Sin resolver".equals(filtro) && resuelta || "Resueltos".equals(filtro) && !resuelta) {
                continue;
            }
            filas.add(n);
            String fecha = n.getFecha() == null ? "" : String.format("%02d/%02d/%04d %s", n.getFecha().getDia(), n.getFecha().getMes(), n.getFecha().getAnio(), n.getHora());
            modelo.addRow(new Object[]{n.getIdNovedad(), fecha, nombres.getOrDefault(n.getIdConductor(), n.getIdConductor()), n.getIdViaje(),
                    ControladorNovedades.TIPOS.getOrDefault(n.getTipo(), n.getTipo()), n.getAsunto(), n.getEstado()});
        }
    }

    /** La tabla puede estar filtrada por la barra de búsqueda, así que la novedad se busca por su ID y no por la posición. */
    private Novedad novedadDeFila(int fila) {
        if (fila < 0 || fila >= modelo.getRowCount()) {
            return null;
        }
        String id = String.valueOf(modelo.getValueAt(fila, 0));
        for (Novedad n : filas) {
            if (n.getIdNovedad().equals(id)) {
                return n;
            }
        }
        return null;
    }

    private void mostrarDetalle() {
        Novedad n = novedadDeFila(tabla.getSelectedRow());
        if (n == null) {
            return;
        }
        detalle.setText(n.getAsunto() + "\n\n" + n.getMensaje() + "\n\n"
                + (n.getRespuesta() == null || n.getRespuesta().isEmpty() ? "(Sin respuesta todavía)" : "Respuesta enviada:\n" + n.getRespuesta()));
        detalle.setCaretPosition(0);
    }

    private void responder() {
        Novedad n = novedadDeFila(tabla.getSelectedRow());
        if (n == null) {
            Tema.error(this, "Selecciona un reporte de la lista.");
            return;
        }
        String remitente = controlador.nombresDeConductores().getOrDefault(n.getIdConductor(), n.getIdConductor());
        new DialogoResponderNovedad(javax.swing.SwingUtilities.getWindowAncestor(this), actor, n, remitente, this::recargar).setVisible(true);
    }

    private void descargarPdf() {
        Novedad n = novedadDeFila(tabla.getSelectedRow());
        if (n == null) {
            Tema.error(this, "Selecciona un reporte de la lista.");
            return;
        }
        Descargas.guardarPdf(this, "Descargar reporte", control.ComprobantesPdf.nombreSugerido("Reporte", n.getIdNovedad()),
                destino -> new control.ComprobantesPdf().novedad(destino, n));
    }
}
