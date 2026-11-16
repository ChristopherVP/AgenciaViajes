package InterfacesUsuario;

import control.InformeViajesPeriodo;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.ButtonGroup;
import javax.swing.filechooser.FileNameExtensionFilter;
import objetosNegocio.Usuario;
import objetosServicio.PeriodoInforme;

/** Informe de viajes por quincena, mes o año (PDF o Excel). Solo pide lo necesario: quincena, mes y año, o solo el año. */
public class DialogoInformeViajes extends JDialog {
    private final Usuario usuario;
    private final JComboBox<PeriodoInforme> cmbPeriodo = new JComboBox<>(PeriodoInforme.values());
    private final JComboBox<String> cmbQuincena = new JComboBox<>(new String[]{"Primera quincena (1 al 15)", "Segunda quincena (16 al fin de mes)"});
    private final JComboBox<String> cmbMes = new JComboBox<>();
    private final JComboBox<Integer> cmbAnio = new JComboBox<>();
    private final JLabel lblQuincena = Tema.etiqueta("Quincena:");
    private final JLabel lblMes = Tema.etiqueta("Mes:");
    private final JLabel lblResumen = Tema.etiqueta(" ");
    private final JRadioButton rbPdf = new JRadioButton("PDF (.pdf)", true);
    private final JRadioButton rbExcel = new JRadioButton("Excel (.xlsx)");

    public DialogoInformeViajes(Window padre, Usuario usuario) {
        super(padre, "Informe de viajes por periodo", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Informe de viajes", "Elige el periodo que quieres revisar"), BorderLayout.NORTH);

        for (int m = 1; m <= 12; m++) {
            String n = java.time.Month.of(m).getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es"));
            cmbMes.addItem(n.substring(0, 1).toUpperCase() + n.substring(1));
        }
        int anio = LocalDate.now().getYear();
        for (int a = anio; a >= anio - 10; a--) {
            cmbAnio.addItem(a);
        }
        cmbMes.setSelectedIndex(LocalDate.now().getMonthValue() - 1);
        cmbQuincena.setSelectedIndex(LocalDate.now().getDayOfMonth() <= 15 ? 0 : 1);
        cmbPeriodo.setSelectedItem(PeriodoInforme.MENSUAL);

        JPanel f = Tema.panelFormulario();
        Tema.fila(f, 0, "Periodo", cmbPeriodo);
        Tema.fila(f, 1, "Año", cmbAnio);
        JLabel[] fila2 = {Tema.fila(f, 2, "Mes", cmbMes)};
        Tema.fila(f, 3, "Quincena", cmbQuincena);
        ButtonGroup grupo = new ButtonGroup();
        grupo.add(rbPdf);
        grupo.add(rbExcel);
        JPanel formatos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        formatos.setOpaque(false);
        formatos.add(rbPdf);
        formatos.add(rbExcel);
        Tema.fila(f, 4, "Formato", formatos);

        JButton generar = Tema.boton("Generar y guardar", true);
        generar.addActionListener(e -> generar());
        JButton cerrar = Tema.boton("Cerrar", false);
        cerrar.addActionListener(e -> dispose());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 6));
        botones.setOpaque(false);
        botones.add(cerrar);
        botones.add(generar);
        JPanel centro = new JPanel(new BorderLayout(0, 8));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(javax.swing.BorderFactory.createEmptyBorder(12, 24, 12, 24));
        centro.add(f, BorderLayout.NORTH);
        centro.add(lblResumen, BorderLayout.CENTER);
        centro.add(botones, BorderLayout.SOUTH);
        add(centro, BorderLayout.CENTER);

        cmbPeriodo.addActionListener(e -> ajustar());
        cmbAnio.addActionListener(e -> ajustar());
        cmbMes.addActionListener(e -> ajustar());
        cmbQuincena.addActionListener(e -> ajustar());
        ajustar();
        pack();
        setLocationRelativeTo(padre);
    }

    private LocalDate referencia() {
        PeriodoInforme p = (PeriodoInforme) cmbPeriodo.getSelectedItem();
        int anio = (Integer) cmbAnio.getSelectedItem();
        int mes = p == PeriodoInforme.ANUAL ? 1 : cmbMes.getSelectedIndex() + 1;
        int dia = p == PeriodoInforme.QUINCENAL && cmbQuincena.getSelectedIndex() == 1 ? 16 : 1;
        return LocalDate.of(anio, mes, dia);
    }

    private void ajustar() {
        PeriodoInforme p = (PeriodoInforme) cmbPeriodo.getSelectedItem();
        cmbMes.setEnabled(p != PeriodoInforme.ANUAL);
        cmbQuincena.setEnabled(p == PeriodoInforme.QUINCENAL);
        lblResumen.setText("Periodo a informar: " + p.rango(referencia()).etiqueta());
    }

    private void generar() {
        PeriodoInforme p = (PeriodoInforme) cmbPeriodo.getSelectedItem();
        LocalDate ref = referencia();
        if (p.rango(ref).desde().isAfter(LocalDate.now())) {
            Tema.error(this, "No se pueden generar informes de un periodo que aún no comienza.");
            return;
        }
        boolean excel = rbExcel.isSelected();
        String ext = excel ? "xlsx" : "pdf";
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar informe");
        selector.setFileFilter(new FileNameExtensionFilter(excel ? "Libro de Excel (*.xlsx)" : "Documento PDF (*.pdf)", ext));
        selector.setSelectedFile(new File("Informe_viajes_" + p.name().toLowerCase() + "_" + ref + "." + ext));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith("." + ext)) {
            destino = new File(destino.getParentFile(), destino.getName() + "." + ext);
        }
        try {
            InformeViajesPeriodo.Informe inf = InformeViajesPeriodo.construir(usuario, p, ref);
            if (excel) {
                ExportadorTabla.escribirXlsx(destino, inf.titulo + " · " + inf.periodo, inf.cabecera, InformeViajesPeriodo.filasExcel(inf));
            } else {
                InformeViajesPeriodo.escribirPdf(inf, usuario, destino);
            }
        } catch (IOException e) {
            Tema.error(this, "No se pudo generar el informe: " + e.getMessage());
            return;
        }
        Object[] opciones = {"Abrir informe", "Cerrar"};
        if (JOptionPane.showOptionDialog(this, "Informe guardado en:\n" + destino.getAbsolutePath(), "Informe generado", JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE, null, opciones, opciones[0]) == 0) {
            try {
                Desktop.getDesktop().open(destino);
            } catch (Exception e) {
                Tema.info(this, "Ábrelo desde la carpeta indicada.");
            }
        }
    }
}
