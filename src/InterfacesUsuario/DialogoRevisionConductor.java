package InterfacesUsuario;

import control.ControladorUsuarios;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Window;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import java.util.Properties;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import objetosNegocio.Empresa;
import objetosNegocio.Usuario;
import objetosServicio.DocumentosConductor;
import persistencia.Empresas;

/**
 * Revisión de la solicitud de un conductor: el administrador lee sus datos y abre cada documento adjunto
 * (cédula, licencia, antecedentes, certificado médico). Solo puede aprobar si confirma las verificaciones y
 * los documentos obligatorios están completos; si algo no está en regla, rechaza la solicitud.
 */
public class DialogoRevisionConductor extends JDialog {
    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final Usuario actor;
    private final Usuario conductor;
    private final Runnable alTerminar;
    private final JCheckBox chkCedula = new JCheckBox("La copia de la cédula es legible y coincide con los datos del registro");
    private final JCheckBox chkLicencia = new JCheckBox("La licencia profesional es válida, del tipo requerido y está vigente");
    private final JCheckBox chkAntecedentes = new JCheckBox("Los antecedentes penales no tienen impedimentos para conducir");
    private final JButton btnAprobar = Tema.boton("Aprobar conductor", true);

    public DialogoRevisionConductor(Window padre, Usuario actor, Usuario conductor, Runnable alTerminar) {
        super(padre, "Revisar solicitud de conductor", Dialog.ModalityType.APPLICATION_MODAL);
        this.actor = actor;
        this.conductor = conductor;
        this.alTerminar = alTerminar;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Solicitud de conductor", conductor.getNombre() + " · cédula " + conductor.getCedula()), BorderLayout.NORTH);

        Properties datos = DocumentosConductor.leerDatos(conductor.getCedula());
        String empresa = conductor.tieneEmpresa() ? conductor.getEmpresa().getIdEmpresa() : "(sin empresa)";
        try {
            if (conductor.tieneEmpresa()) {
                Empresa e = new Empresas("empresas.dat").obten(new Empresa(conductor.getEmpresa().getIdEmpresa()));
                if (e != null) {
                    empresa = e.getNombre();
                }
            }
        } catch (Exception ignorado) {
            // se muestra el id de la empresa
        }

        JPanel info = Tema.panelFormulario();
        int f = 0;
        f = dato(info, f, "Empresa solicitada", empresa);
        f = dato(info, f, "Usuario", conductor.getLogin());
        f = dato(info, f, "Fecha de nacimiento", datos.getProperty("nacimiento", "—"));
        f = dato(info, f, "Teléfono", conductor.getTelefono());
        f = dato(info, f, "Correo", conductor.getEmail());
        f = dato(info, f, "Licencia", "Tipo " + datos.getProperty("licenciaTipo", "?") + " · N.º " + datos.getProperty("licenciaNumero", "—"));
        dato(info, f, "Licencia vence", textoVencimiento(datos.getProperty("licenciaVence")));

        JPanel docs = Tema.panelFormulario();
        Map<DocumentosConductor.Tipo, Path> archivos = DocumentosConductor.listar(conductor.getCedula());
        int d = 0;
        for (DocumentosConductor.Tipo tipo : DocumentosConductor.Tipo.values()) {
            Path ruta = archivos.get(tipo);
            JPanel fila = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            fila.setOpaque(false);
            JButton ver = Tema.boton("Abrir", false);
            ver.setEnabled(ruta != null);
            if (ruta != null) {
                ver.addActionListener(e -> abrir(ruta));
            }
            JLabel estado = Tema.etiqueta(ruta != null ? ruta.getFileName().toString() : (tipo.esObligatorio() ? "FALTA (obligatorio)" : "No adjuntó"));
            estado.setForeground(ruta != null ? Tema.EXITO : (tipo.esObligatorio() ? Tema.ERROR : Tema.TEXTO_SUAVE));
            fila.add(ver);
            fila.add(estado);
            Tema.fila(docs, d++, tipo.getEtiqueta(), fila);
        }

        JPanel verificaciones = new JPanel(new java.awt.GridLayout(0, 1, 0, 4));
        verificaciones.setOpaque(false);
        for (JCheckBox c : new JCheckBox[]{chkCedula, chkLicencia, chkAntecedentes}) {
            c.setOpaque(false);
            c.setFont(Tema.fuente(Font.PLAIN, 13f));
            c.addActionListener(e -> actualizarBoton());
            verificaciones.add(c);
        }

        JPanel centro = new ValidacionUI.PanelDesplazable(new BorderLayout(0, 10));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 28, 14, 28));
        JPanel bloques = new JPanel();
        bloques.setLayout(new javax.swing.BoxLayout(bloques, javax.swing.BoxLayout.Y_AXIS));
        bloques.setOpaque(false);
        for (JPanel bloque : new JPanel[]{titulado("Datos del solicitante", info), titulado("Documentos adjuntos", docs),
                titulado("Verificaciones del administrador", verificaciones)}) {
            bloque.setAlignmentX(0f);
            bloques.add(bloque);
            bloques.add(javax.swing.Box.createVerticalStrut(10));
        }
        centro.add(bloques, BorderLayout.CENTER);

        JButton rechazar = Tema.boton("Rechazar solicitud", false);
        rechazar.addActionListener(e -> rechazar());
        JButton cancelar = Tema.boton("Cerrar", false);
        cancelar.addActionListener(e -> dispose());
        btnAprobar.addActionListener(e -> aprobar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(cancelar);
        botones.add(rechazar);
        botones.add(btnAprobar);
        centro.add(botones, BorderLayout.SOUTH);
        JScrollPane scroll = new JScrollPane(centro);
        scroll.setBorder(null);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(scroll, BorderLayout.CENTER);
        actualizarBoton();
        setPreferredSize(new Dimension(760, 770));
        pack();
        setLocationRelativeTo(padre);
    }

    private static JPanel titulado(String titulo, JPanel contenido) {
        JPanel p = new JPanel(new BorderLayout());
        p.setOpaque(false);
        p.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Tema.BORDE), titulo,
                javax.swing.border.TitledBorder.LEFT, javax.swing.border.TitledBorder.TOP, Tema.fuente(Font.BOLD, 13f), java.awt.Color.BLACK));
        p.add(contenido, BorderLayout.NORTH);
        return p;
    }

    private static int dato(JPanel panel, int fila, String etiqueta, String valor) {
        JLabel v = Tema.etiqueta(valor == null || valor.isEmpty() ? "—" : valor);
        v.setFont(Tema.fuente(Font.BOLD, 13f));
        Tema.fila(panel, fila, etiqueta, v);
        return fila + 1;
    }

    private static String textoVencimiento(String texto) {
        if (texto == null) {
            return "—";
        }
        try {
            String[] p = texto.split("/");
            LocalDate vence = LocalDate.of(Integer.parseInt(p[2]), Integer.parseInt(p[1]), Integer.parseInt(p[0]));
            long dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), vence);
            return texto + (dias < 0 ? "  (VENCIDA)" : dias < 90 ? "  (vence en " + dias + " días)" : "");
        } catch (RuntimeException e) {
            return texto;
        }
    }

    private void actualizarBoton() {
        boolean completos = DocumentosConductor.completos(conductor.getCedula());
        btnAprobar.setEnabled(completos && chkCedula.isSelected() && chkLicencia.isSelected() && chkAntecedentes.isSelected()
                && Usuario.ESTADO_PENDIENTE.equalsIgnoreCase(conductor.getEstado()));
    }

    private void abrir(Path ruta) {
        try {
            Desktop.getDesktop().open(ruta.toFile());
        } catch (Exception e) {
            Tema.error(this, "No se pudo abrir el documento. Búscalo en: " + ruta.toAbsolutePath());
        }
    }

    private void aprobar() {
        ResultadoOperacion r = controlador.aprobarUsuario(actor, conductor.getIdUsuario());
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            return;
        }
        Tema.info(this, r.getMensaje());
        alTerminar.run();
        dispose();
    }

    private void rechazar() {
        if (JOptionPane.showConfirmDialog(this, "¿Rechazar la solicitud de " + conductor.getNombre() + "?\nSu cuenta quedará inactiva.",
                "Rechazar solicitud", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) != JOptionPane.YES_OPTION) {
            return;
        }
        ResultadoOperacion r = controlador.rechazarUsuario(actor, conductor.getIdUsuario());
        if (!r.isExito()) {
            Tema.error(this, r.getMensaje());
            return;
        }
        alTerminar.run();
        dispose();
    }
}
