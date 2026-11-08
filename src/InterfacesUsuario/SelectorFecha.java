package InterfacesUsuario;

import java.awt.FlowLayout;
import java.awt.Font;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import objetosServicio.Fecha;

/**
 * Fecha elegida con listas desplegables (día, mes, año) para no tener que escribirla. Los días se ajustan
 * al mes y al año elegidos (febrero, años bisiestos, meses de 30 días). Con {@code soloMesAnio} no muestra el día.
 */
public class SelectorFecha extends JPanel {
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Septiembre", "Octubre", "Noviembre", "Diciembre"};

    private final JComboBox<String> cmbDia = new JComboBox<>();
    private final JComboBox<String> cmbMes = new JComboBox<>();
    private final JComboBox<String> cmbAnio = new JComboBox<>();
    private final boolean soloMesAnio;
    private boolean ajustando;

    /** @param anioDesde primer año de la lista; @param anioHasta último año; si desde &gt; hasta la lista va de mayor a menor. */
    public SelectorFecha(int anioDesde, int anioHasta, boolean soloMesAnio) {
        super(new FlowLayout(FlowLayout.LEFT, 8, 0));
        this.soloMesAnio = soloMesAnio;
        setOpaque(false);
        for (JComboBox<String> c : java.util.List.of(cmbDia, cmbMes, cmbAnio)) {
            c.setFont(Tema.fuente(Font.PLAIN, 13f));
        }
        cmbMes.addItem("Mes");
        for (String m : MESES) {
            cmbMes.addItem(m);
        }
        List<String> anios = new ArrayList<>();
        anios.add("Año");
        int paso = anioDesde <= anioHasta ? 1 : -1;
        for (int a = anioDesde; a != anioHasta + paso; a += paso) {
            anios.add(String.valueOf(a));
        }
        cmbAnio.setModel(new DefaultComboBoxModel<>(anios.toArray(new String[0])));
        llenarDias(31);
        if (!soloMesAnio) {
            add(cmbDia);
        }
        add(cmbMes);
        add(cmbAnio);
        cmbMes.addActionListener(e -> ajustarDias());
        cmbAnio.addActionListener(e -> ajustarDias());
    }

    private void llenarDias(int cantidad) {
        Object actual = cmbDia.getSelectedItem();
        ajustando = true;
        DefaultComboBoxModel<String> modelo = new DefaultComboBoxModel<>();
        modelo.addElement("Día");
        for (int d = 1; d <= cantidad; d++) {
            modelo.addElement(String.valueOf(d));
        }
        cmbDia.setModel(modelo);
        if (actual != null && modelo.getIndexOf(actual) >= 0) {
            cmbDia.setSelectedItem(actual);
        }
        ajustando = false;
    }

    private void ajustarDias() {
        if (ajustando || soloMesAnio) {
            return;
        }
        int mes = cmbMes.getSelectedIndex();
        int anio = anioElegido();
        int max = mes <= 0 ? 31 : YearMonth.of(anio > 0 ? anio : 2000, mes).lengthOfMonth();
        if (cmbDia.getItemCount() - 1 != max) {
            llenarDias(max);
        }
    }

    private int anioElegido() {
        String a = (String) cmbAnio.getSelectedItem();
        return a == null || !a.matches("\\d{4}") ? 0 : Integer.parseInt(a);
    }

    /** true si ya se eligió todo lo que corresponde (día, mes y año; o mes y año). */
    public boolean estaCompleta() {
        return cmbMes.getSelectedIndex() > 0 && anioElegido() > 0 && (soloMesAnio || cmbDia.getSelectedIndex() > 0);
    }

    /** Fecha elegida o null si falta algún dato. Con soloMesAnio devuelve el último día del mes. */
    public Fecha getFecha() {
        if (!estaCompleta()) {
            return null;
        }
        int mes = cmbMes.getSelectedIndex();
        int anio = anioElegido();
        int dia = soloMesAnio ? YearMonth.of(anio, mes).lengthOfMonth() : cmbDia.getSelectedIndex();
        return new Fecha(dia, mes, anio);
    }

    public LocalDate getLocalDate() {
        Fecha f = getFecha();
        return f == null ? null : LocalDate.of(f.getAnio(), f.getMes(), f.getDia());
    }

    public void setFecha(Fecha f) {
        if (f == null) {
            return;
        }
        cmbAnio.setSelectedItem(String.valueOf(f.getAnio()));
        cmbMes.setSelectedIndex(f.getMes());
        ajustarDias();
        if (!soloMesAnio) {
            cmbDia.setSelectedIndex(f.getDia());
        }
    }

    public void limpiar() {
        cmbDia.setSelectedIndex(0);
        cmbMes.setSelectedIndex(0);
        cmbAnio.setSelectedIndex(0);
    }

    public void alCambiar(Runnable accion) {
        java.awt.event.ActionListener l = e -> accion.run();
        cmbDia.addActionListener(l);
        cmbMes.addActionListener(l);
        cmbAnio.addActionListener(l);
    }

    @Override
    public void setEnabled(boolean habilitado) {
        super.setEnabled(habilitado);
        cmbDia.setEnabled(habilitado);
        cmbMes.setEnabled(habilitado);
        cmbAnio.setEnabled(habilitado);
    }

    /** Da el foco al primer desplegable que falta (para las validaciones de formulario). */
    @Override
    public boolean requestFocusInWindow() {
        if (!soloMesAnio && cmbDia.getSelectedIndex() <= 0) {
            return cmbDia.requestFocusInWindow();
        }
        return cmbMes.getSelectedIndex() <= 0 ? cmbMes.requestFocusInWindow() : cmbAnio.requestFocusInWindow();
    }

    /** Agrega un oyente de pérdida de foco a todos los desplegables (para validar al salir). */
    public void alPerderFoco(Runnable accion) {
        java.awt.event.FocusAdapter l = new java.awt.event.FocusAdapter() {
            @Override
            public void focusLost(java.awt.event.FocusEvent e) {
                accion.run();
            }
        };
        cmbDia.addFocusListener(l);
        cmbMes.addFocusListener(l);
        cmbAnio.addFocusListener(l);
    }
}
