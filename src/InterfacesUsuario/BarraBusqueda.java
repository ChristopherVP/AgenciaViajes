package InterfacesUsuario;

import java.awt.FlowLayout;
import java.awt.Font;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;

/**
 * Barra de búsqueda en vivo para cualquier tabla: un campo vacío para escribir y, al lado, el criterio
 * (todos los campos, ID, nombre, cédula... según las columnas de la tabla). Mientras se escribe, las filas
 * que no coinciden se descartan. Ignora mayúsculas y tildes.
 *
 * Importante: filtra reconstruyendo las filas del modelo (no usa RowSorter), así las pantallas que leen
 * "modelo.getValueAt(tabla.getSelectedRow(), 0)" siguen funcionando sin cambios.
 */
public class BarraBusqueda extends JPanel {
    private static final String TODOS = "Todos los campos";

    private final JTable tabla;
    private final DefaultTableModel modelo;
    private final JTextField txtBuscar = Tema.campo(26);
    private final JComboBox<String> cmbCriterio = new JComboBox<>();
    private final JLabel lblConteo = Tema.etiqueta(" ");
    private List<Vector<Object>> todas = new ArrayList<>();
    private boolean aplicando;
    private boolean pendiente;

    /** Devuelve null si la tabla no usa un DefaultTableModel (no se puede filtrar). */
    public static BarraBusqueda para(JTable tabla) {
        return tabla.getModel() instanceof DefaultTableModel ? new BarraBusqueda(tabla, (DefaultTableModel) tabla.getModel()) : null;
    }

    private BarraBusqueda(JTable tabla, DefaultTableModel modelo) {
        super(new FlowLayout(FlowLayout.LEFT, 10, 6));
        this.tabla = tabla;
        this.modelo = modelo;
        setOpaque(false);
        JLabel et = Tema.etiqueta("Buscar:");
        et.setFont(Tema.fuente(Font.BOLD, 14f));
        et.setForeground(Color_NEGRO);
        add(et);
        txtBuscar.setToolTipText("Escribe y la lista se filtra mientras tecleas");
        txtBuscar.putClientProperty("JTextField.placeholderText", "Escribe para buscar...");
        add(txtBuscar);
        JLabel por = Tema.etiqueta("por");
        por.setForeground(Color_NEGRO);
        add(por);
        cmbCriterio.setFont(Tema.fuente(Font.PLAIN, 13f));
        add(cmbCriterio);
        add(lblConteo);
        llenarCriterios();

        txtBuscar.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { aplicar(); }
            public void removeUpdate(DocumentEvent e) { aplicar(); }
            public void changedUpdate(DocumentEvent e) { aplicar(); }
        });
        cmbCriterio.addActionListener(e -> aplicar());
        // si la pantalla vuelve a cargar los datos (Actualizar, editar, eliminar...), se toma esa nueva lista completa
        modelo.addTableModelListener(new TableModelListener() {
            @Override
            public void tableChanged(TableModelEvent e) {
                if (aplicando || pendiente) {
                    return;
                }
                pendiente = true;
                SwingUtilities.invokeLater(() -> {
                    pendiente = false;
                    capturar();
                    aplicar();
                });
            }
        });
        capturar();
    }

    private static final java.awt.Color Color_NEGRO = java.awt.Color.BLACK;

    public JTextField getCampo() {
        return txtBuscar;
    }

    private void llenarCriterios() {
        cmbCriterio.addItem(TODOS);
        for (int i = 0; i < modelo.getColumnCount(); i++) {
            cmbCriterio.addItem(modelo.getColumnName(i));
        }
        // arranca buscando en todos los campos (así sirve escribir un ID, un nombre o una cédula); el selector permite afinar
        cmbCriterio.setSelectedIndex(0);
    }

    @SuppressWarnings("unchecked")
    private void capturar() {
        todas = new ArrayList<>();
        for (Object fila : modelo.getDataVector()) {
            todas.add(new Vector<>((Vector<Object>) fila));
        }
    }

    private void aplicar() {
        String q = normalizar(txtBuscar.getText());
        int col = cmbCriterio.getSelectedIndex() - 1; // -1 = todos los campos
        aplicando = true;
        try {
            modelo.setRowCount(0);
            int visibles = 0;
            for (Vector<Object> fila : todas) {
                if (q.isEmpty() || coincide(fila, col, q)) {
                    modelo.addRow(new Vector<>(fila));
                    visibles++;
                }
            }
            lblConteo.setText(q.isEmpty() ? " " : visibles + " de " + todas.size());
            lblConteo.setForeground(visibles == 0 ? Tema.ERROR : Tema.TEXTO_SUAVE);
        } finally {
            aplicando = false;
        }
        tabla.clearSelection();
    }

    private static boolean coincide(Vector<Object> fila, int col, String q) {
        if (col >= 0) {
            return col < fila.size() && normalizar(String.valueOf(fila.get(col))).contains(q);
        }
        for (Object celda : fila) {
            if (normalizar(String.valueOf(celda)).contains(q)) {
                return true;
            }
        }
        return false;
    }

    static String normalizar(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase().trim();
    }
}
