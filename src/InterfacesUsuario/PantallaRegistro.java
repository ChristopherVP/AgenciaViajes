package InterfacesUsuario;

import control.ControladorUsuarios;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.File;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.filechooser.FileNameExtensionFilter;
import objetosNegocio.Empresa;
import objetosServicio.DocumentosConductor;
import objetosServicio.Fecha;
import persistencia.Empresas;

/**
 * Registro público. El pasajero completa sus datos; el conductor además elige la empresa en la que quiere trabajar,
 * declara su licencia y adjunta sus documentos para que un administrador los revise y apruebe su cuenta.
 * Cada campo se valida mientras se escribe (filtros de teclado) y al enviar (mensaje rojo bajo el campo).
 */
public class PantallaRegistro extends JDialog {
    private static final String SIN_PREGUNTA = "Selecciona una pregunta...";
    private static final String SIN_EMPRESA = "Selecciona una empresa...";
    private static final String SIN_TIPO = "Selecciona el tipo...";
    private static final String TIPO_D = "D - Profesional (buses)";
    private static final String TIPO_E = "E - Profesional (pesados)";

    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final JRadioButton rbPasajero = new JRadioButton("Soy pasajero", true);
    private final JRadioButton rbConductor = new JRadioButton("Soy conductor");
    private final JTextField txtLogin = Tema.campo(20);
    private final JTextField txtNombre = Tema.campo(20);
    private final JTextField txtCedula = Tema.campo(20);
    private final JLabel lblProvincia = Tema.etiqueta(" ");
    private final SelectorFecha selNacimiento = new SelectorFecha(LocalDate.now().getYear() - 18, LocalDate.now().getYear() - 100, false);
    private final JTextField txtDireccion = Tema.campo(20);
    private final JTextField txtTelefono = Tema.campo(20);
    private final JTextField txtEmail = Tema.campo(20);
    private final JPasswordField txtClave = Tema.clave(20);
    private final JPasswordField txtConfirmar = Tema.clave(20);
    private final JComboBox<String> cmbPregunta = new JComboBox<>();
    private final JTextField txtRespuesta = Tema.campo(20);
    // datos del conductor
    private final JComboBox<String> cmbEmpresa = new JComboBox<>();
    private final List<Empresa> empresas = cargarEmpresas();
    private final JComboBox<String> cmbTipoLicencia = new JComboBox<>(new String[]{SIN_TIPO, TIPO_D, TIPO_E});
    private final JTextField txtNumeroLicencia = Tema.campo(20);
    private final SelectorFecha selVenceLicencia = new SelectorFecha(LocalDate.now().getYear(), LocalDate.now().getYear() + 10, false);
    private final JComboBox<String> cmbBanco = new JComboBox<>();
    private final JTextField txtTarjeta = Tema.campo(20);
    private final SelectorFecha selVenceTarjeta = new SelectorFecha(LocalDate.now().getYear(), LocalDate.now().getYear() + 12, true);
    private final Map<DocumentosConductor.Tipo, File> documentos = new EnumMap<>(DocumentosConductor.Tipo.class);
    private final Map<DocumentosConductor.Tipo, JLabel> nombresDocumento = new EnumMap<>(DocumentosConductor.Tipo.class);
    private final Map<DocumentosConductor.Tipo, JLabel> erroresDocumento = new EnumMap<>(DocumentosConductor.Tipo.class);

    private final JLabel lblFortaleza = Tema.mensaje();
    private final JLabel lblMensaje = Tema.mensaje();
    private final JLabel lblAyuda = Tema.etiqueta(" ");
    private final ValidacionUI.Reglas reglas = new ValidacionUI.Reglas();
    private final JLabel[] filaDireccion = new JLabel[1];
    private final JLabel errDireccion = ValidacionUI.etiquetaError();
    private final List<java.awt.Component> componentesConductor = new java.util.ArrayList<>();
    private String loginCreado;

    public PantallaRegistro(Window padre) {
        super(padre, "Crear cuenta", Dialog.ModalityType.APPLICATION_MODAL);
        setIconImage(IconoRQ.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Crear cuenta", "Elige tu rol y completa tus datos"), BorderLayout.NORTH);

        ButtonGroup grupo = new ButtonGroup();
        for (JRadioButton rb : new JRadioButton[]{rbPasajero, rbConductor}) {
            rb.setOpaque(false);
            rb.setFont(Tema.fuente(Font.BOLD, 14f));
            grupo.add(rb);
            rb.addActionListener(e -> actualizarRol());
        }
        cmbPregunta.addItem(SIN_PREGUNTA);
        for (String p : Tema.PREGUNTAS) {
            cmbPregunta.addItem(p);
        }
        cmbEmpresa.addItem(SIN_EMPRESA);
        for (Empresa e : empresas) {
            cmbEmpresa.addItem(e.getNombre());
        }
        cmbBanco.addItem("Selecciona tu banco...");
        for (String b : objetosServicio.Bancos.LISTA) {
            cmbBanco.addItem(b);
        }
        for (JComboBox<String> c : java.util.List.of(cmbPregunta, cmbEmpresa, cmbTipoLicencia, cmbBanco)) {
            c.setFont(Tema.fuente(Font.PLAIN, 13f));
        }

        // filtros de escritura
        ValidacionUI.usuario(txtLogin, 20);
        ValidacionUI.soloLetras(txtNombre, 50);
        ValidacionUI.soloDigitos(txtCedula, 10);
        ValidacionUI.mostrarProvincia(txtCedula, lblProvincia);
        ValidacionUI.limite(txtDireccion, 80);
        ValidacionUI.celular(txtTelefono);
        ValidacionUI.correo(txtEmail, 50);
        ValidacionUI.limite(txtRespuesta, 40);
        ValidacionUI.mayusculasAlfanumerico(txtNumeroLicencia, 12);
        ValidacionUI.tarjeta(txtTarjeta);
        lblProvincia.setForeground(Tema.TEXTO_SUAVE);

        JLabel errLogin = ValidacionUI.etiquetaError();
        JLabel errNombre = ValidacionUI.etiquetaError();
        JLabel errCedula = ValidacionUI.etiquetaError();
        JLabel errNacimiento = ValidacionUI.etiquetaError();
        JLabel errTelefono = ValidacionUI.etiquetaError();
        JLabel errEmail = ValidacionUI.etiquetaError();
        JLabel errClave = ValidacionUI.etiquetaError();
        JLabel errConfirmar = ValidacionUI.etiquetaError();
        JLabel errPregunta = ValidacionUI.etiquetaError();
        JLabel errRespuesta = ValidacionUI.etiquetaError();

        JPanel formulario = Tema.panelFormulario();
        int f = 0;
        f = ValidacionUI.fila(formulario, f, "Usuario *", txtLogin, errLogin);
        f = ValidacionUI.fila(formulario, f, "Nombre completo *", txtNombre, errNombre);
        f = ValidacionUI.fila(formulario, f, "Cédula *", txtCedula, errCedula);
        Tema.fila(formulario, f++, "", lblProvincia);
        f = ValidacionUI.fila(formulario, f, "Fecha de nacimiento *", selNacimiento, errNacimiento);
        f = ValidacionUI.fila(formulario, f, "Teléfono celular *", txtTelefono, errTelefono);
        f = ValidacionUI.fila(formulario, f, "Correo *", txtEmail, errEmail);
        f = ValidacionUI.fila(formulario, f, "Dirección (opcional)", txtDireccion, errDireccion, filaDireccion);
        f = agregarBloqueConductor(formulario, f);
        f = ValidacionUI.fila(formulario, f, "Contraseña *", txtClave, errClave);
        Tema.fila(formulario, f++, "", lblFortaleza);
        f = ValidacionUI.fila(formulario, f, "Confirmar contraseña *", txtConfirmar, errConfirmar);
        JCheckBox chkMostrar = new JCheckBox("Mostrar contraseñas");
        chkMostrar.setOpaque(false);
        chkMostrar.setFont(Tema.fuente(Font.PLAIN, 12f));
        char eco = txtClave.getEchoChar();
        chkMostrar.addActionListener(e -> {
            char c = chkMostrar.isSelected() ? (char) 0 : eco;
            txtClave.setEchoChar(c);
            txtConfirmar.setEchoChar(c);
        });
        Tema.fila(formulario, f++, "", chkMostrar);
        f = ValidacionUI.fila(formulario, f, "Pregunta de seguridad *", cmbPregunta, errPregunta);
        ValidacionUI.fila(formulario, f, "Respuesta *", txtRespuesta, errRespuesta);
        Tema.vincularFortaleza(txtClave, lblFortaleza);

        // reglas en el orden del formulario
        reglas.agregar(txtLogin, errLogin, () -> Validador.errorLogin(txtLogin.getText()));
        reglas.agregar(txtNombre, errNombre, () -> Validador.errorNombre(txtNombre.getText()));
        reglas.agregar(txtCedula, errCedula, () -> Validador.errorCedula(txtCedula.getText()));
        reglas.agregar(selNacimiento, errNacimiento, this::errorNacimiento);
        reglas.agregar(txtTelefono, errTelefono, () -> Validador.errorTelefonoCelular(txtTelefono.getText()));
        reglas.agregar(txtEmail, errEmail, () -> Validador.errorEmailCuenta(txtEmail.getText()));
        reglas.agregar(txtClave, errClave, () -> Validador.errorContrasena(new String(txtClave.getPassword())));
        reglas.agregar(txtConfirmar, errConfirmar, () -> new String(txtClave.getPassword()).equals(new String(txtConfirmar.getPassword()))
                ? null : "Las contraseñas no coinciden");
        reglas.agregar(cmbPregunta, errPregunta, () -> cmbPregunta.getSelectedIndex() <= 0 ? "Elige una pregunta de seguridad" : null);
        reglas.agregar(txtRespuesta, errRespuesta, () -> {
            String problema = Validador.errorRespuestaSeguridad(txtRespuesta.getText());
            return problema == null && txtRespuesta.getText().trim().equalsIgnoreCase(new String(txtClave.getPassword()))
                    ? "La respuesta no puede ser igual a tu contraseña" : problema;
        });

        JPanel roles = new JPanel(new FlowLayout(FlowLayout.LEFT, 18, 4));
        roles.setOpaque(false);
        roles.add(rbPasajero);
        roles.add(rbConductor);
        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(roles, BorderLayout.NORTH);
        lblAyuda.setForeground(Tema.TEXTO_SUAVE);
        arriba.add(lblAyuda, BorderLayout.SOUTH);

        JButton btnCrear = Tema.boton("Crear cuenta", true);
        btnCrear.addActionListener(e -> crear());
        JButton btnCancelar = Tema.boton("Cancelar", false);
        btnCancelar.addActionListener(e -> dispose());
        getRootPane().setDefaultButton(btnCrear);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(btnCancelar);
        botones.add(btnCrear);

        JPanel centro = new ValidacionUI.PanelDesplazable(new BorderLayout(0, 8));
        centro.setBackground(Tema.FONDO);
        centro.setBorder(BorderFactory.createEmptyBorder(14, 40, 14, 40));
        centro.add(arriba, BorderLayout.NORTH);
        centro.add(formulario, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(lblMensaje, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        centro.add(sur, BorderLayout.SOUTH);

        JScrollPane scroll = new JScrollPane(centro);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        add(scroll, BorderLayout.CENTER);
        actualizarRol();
        Dimension pantalla = Toolkit.getDefaultToolkit().getScreenSize();
        setPreferredSize(new Dimension(Math.min(980, pantalla.width - 80), pantalla.height - 90));
        pack();
        setLocationRelativeTo(padre);
    }

    private static List<Empresa> cargarEmpresas() {
        try {
            return new Empresas("empresas.dat").lista();
        } catch (Exception e) {
            return List.of();
        }
    }

    /** Fila con campos propios del conductor (empresa, licencia y documentos); solo se ve cuando se elige "Soy conductor". */
    private int agregarBloqueConductor(JPanel formulario, int fila) {
        JLabel errEmpresa = ValidacionUI.etiquetaError();
        JLabel errTipo = ValidacionUI.etiquetaError();
        JLabel errNumero = ValidacionUI.etiquetaError();
        JLabel errVence = ValidacionUI.etiquetaError();
        int b = fila;
        int inicio = formulario.getComponentCount();
        JLabel titulo = Tema.titulo("Datos para trabajar como conductor", 15f);
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = b++;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(10, 0, 4, 0);
        formulario.add(titulo, g);
        b = ValidacionUI.fila(formulario, b, "Empresa *", cmbEmpresa, errEmpresa);
        b = ValidacionUI.fila(formulario, b, "Tipo de licencia *", cmbTipoLicencia, errTipo);
        b = ValidacionUI.fila(formulario, b, "N.º de licencia *", txtNumeroLicencia, errNumero);
        b = ValidacionUI.fila(formulario, b, "Vence el *", selVenceLicencia, errVence);

        JLabel docs = Tema.nota(560, "<html><b>Documentos (PDF, JPG o PNG, máximo 5 MB cada uno).</b> El administrador los revisará "
                + "para confirmar que tienes todo al día antes de aprobar tu cuenta.</html>");
        g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = b++;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(8, 0, 4, 0);
        formulario.add(docs, g);
        for (DocumentosConductor.Tipo tipo : DocumentosConductor.Tipo.values()) {
            JLabel nombre = Tema.etiqueta("Ningún archivo");
            nombre.setForeground(Tema.TEXTO_SUAVE);
            JLabel error = ValidacionUI.etiquetaError();
            nombresDocumento.put(tipo, nombre);
            erroresDocumento.put(tipo, error);
            JButton adjuntar = Tema.boton("Adjuntar", false);
            adjuntar.addActionListener(e -> elegirDocumento(tipo));
            JPanel filaDoc = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            filaDoc.setOpaque(false);
            filaDoc.add(adjuntar);
            filaDoc.add(nombre);
            b = ValidacionUI.fila(formulario, b, tipo.getEtiqueta() + (tipo.esObligatorio() ? " *" : ""), filaDoc, error);
        }

        JLabel errBanco = ValidacionUI.etiquetaError();
        JLabel errTarjeta = ValidacionUI.etiquetaError();
        JLabel errVenceTarjeta = ValidacionUI.etiquetaError();
        JLabel tituloTarjeta = Tema.titulo("Tarjeta de débito para recibir tus pagos", 15f);
        g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = b++;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(14, 0, 2, 0);
        formulario.add(tituloTarjeta, g);
        JLabel notaTarjeta = Tema.nota(560, "<html>Recibirás el <b>80 %</b> de lo que paguen los pasajeros en cada viaje que realices. Solo se acepta una tarjeta "
                + "de <b>débito</b> Visa o Mastercard <b>a tu nombre</b>. Guardamos únicamente el banco, la marca y los últimos 4 dígitos.</html>");
        notaTarjeta.setForeground(Tema.TEXTO_SUAVE);
        g = new GridBagConstraints();
        g.gridx = 0;
        g.gridy = b++;
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, 0, 4, 0);
        formulario.add(notaTarjeta, g);
        b = ValidacionUI.fila(formulario, b, "Banco *", cmbBanco, errBanco);
        b = ValidacionUI.fila(formulario, b, "N.º de tarjeta *", txtTarjeta, errTarjeta);
        b = ValidacionUI.fila(formulario, b, "Vence *", selVenceTarjeta, errVenceTarjeta);
        reglas.agregar(cmbBanco, errBanco, () -> cmbBanco.getSelectedIndex() <= 0 ? "Elige tu banco o cooperativa" : null);
        reglas.agregar(txtTarjeta, errTarjeta, () -> {
            String d = txtTarjeta.getText().replace(" ", "");
            if (d.length() < 13) {
                return "El número de tarjeta debe tener entre 13 y 19 dígitos";
            }
            String marca = objetosServicio.Bancos.marca(d);
            if (!Validador.esTarjetaValida(d)) {
                return "El número de tarjeta no es válido, revísalo";
            }
            return marca.equals("VISA") || marca.equals("MASTERCARD") ? null : "Solo se aceptan tarjetas de débito Visa o Mastercard";
        });
        reglas.agregar(selVenceTarjeta, errVenceTarjeta, () -> {
            LocalDate v = selVenceTarjeta.getLocalDate();
            if (v == null) {
                return "Elige el mes y el año de vencimiento";
            }
            return java.time.YearMonth.from(v).isBefore(java.time.YearMonth.now()) ? "La tarjeta está vencida" : null;
        });

        reglas.agregar(cmbEmpresa, errEmpresa, () -> cmbEmpresa.getSelectedIndex() <= 0 ? "Elige la empresa en la que vas a trabajar" : null);
        reglas.agregar(cmbTipoLicencia, errTipo, () -> cmbTipoLicencia.getSelectedIndex() <= 0 ? "Elige el tipo de licencia" : null);
        reglas.agregar(txtNumeroLicencia, errNumero, () -> txtNumeroLicencia.getText().trim().matches("[A-Z0-9]{6,12}") ? null
                : "El número de licencia debe tener entre 6 y 12 letras o números");
        reglas.agregar(selVenceLicencia, errVence, this::errorVencimiento);
        for (DocumentosConductor.Tipo tipo : DocumentosConductor.Tipo.values()) {
            JLabel error = erroresDocumento.get(tipo);
            reglas.agregar(nombresDocumento.get(tipo), error, () -> {
                File archivo = documentos.get(tipo);
                if (archivo == null) {
                    return tipo.esObligatorio() ? "Adjunta este documento" : null;
                }
                return DocumentosConductor.errorArchivo(archivo);
            });
        }

        for (int k = inicio; k < formulario.getComponentCount(); k++) {
            componentesConductor.add(formulario.getComponent(k));
        }
        return b;
    }

    private void elegirDocumento(DocumentosConductor.Tipo tipo) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle(tipo.getEtiqueta());
        selector.setFileFilter(new FileNameExtensionFilter("PDF, JPG o PNG", "pdf", "jpg", "jpeg", "png"));
        if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        String problema = DocumentosConductor.errorArchivo(archivo);
        JLabel nombre = nombresDocumento.get(tipo);
        JLabel error = erroresDocumento.get(tipo);
        if (problema != null) {
            documentos.remove(tipo);
            nombre.setText("Ningún archivo");
            error.setText(problema);
            error.setVisible(true);
            return;
        }
        documentos.put(tipo, archivo);
        nombre.setText(archivo.getName() + " (" + Math.max(1, archivo.length() / 1024) + " KB)");
        nombre.setForeground(Tema.EXITO);
        error.setVisible(false);
    }

    private String errorNacimiento() {
        Fecha f = selNacimiento.getFecha();
        if (f == null) {
            return "Selecciona día, mes y año de nacimiento";
        }
        return Validador.errorMayorDeEdad(String.format("%02d/%02d/%04d", f.getDia(), f.getMes(), f.getAnio()));
    }

    private String errorVencimiento() {
        LocalDate fecha = selVenceLicencia.getLocalDate();
        if (fecha == null) {
            return "Selecciona la fecha de vencimiento de tu licencia";
        }
        return fecha.isBefore(LocalDate.now().plusDays(30)) ? "Tu licencia está vencida o vence en menos de 30 días: renuévala antes de registrarte" : null;
    }

    private void actualizarRol() {
        boolean pasajero = rbPasajero.isSelected();
        filaDireccion[0].setVisible(pasajero);
        txtDireccion.setVisible(pasajero);
        for (java.awt.Component c : componentesConductor) {
            boolean esError = c instanceof JLabel l && Boolean.TRUE.equals(l.getClientProperty("rq.error"));
            c.setVisible(!pasajero && (!esError || !((JLabel) c).getText().isBlank()));
        }
        reglas.limpiar();
        Tema.mostrarMensaje(lblMensaje, " ", true);
        lblAyuda.setText(pasajero
                ? Tema.html(640, "Podrás comprar pasajes con tu billetera. Debes ser mayor de 18 años.")
                : Tema.html(640, "Elige la empresa en la que quieres trabajar y adjunta tus documentos. Tu cuenta quedará <b>pendiente de aprobación</b> "
                + "hasta que un administrador verifique que todo está en regla."));
        revalidate();
        repaint();
    }

    private void crear() {
        if (!reglas.validar()) {
            Tema.mostrarMensaje(lblMensaje, "Revisa los campos marcados en rojo.", false);
            return;
        }
        String clave = new String(txtClave.getPassword());
        String confirmar = new String(txtConfirmar.getPassword());
        String pregunta = String.valueOf(cmbPregunta.getSelectedItem());
        Fecha nacimiento = selNacimiento.getFecha();
        ResultadoOperacion r;
        if (rbPasajero.isSelected()) {
            r = controlador.registrarPasajero(txtLogin.getText(), txtNombre.getText(), txtCedula.getText(), clave, confirmar, txtEmail.getText(),
                    txtTelefono.getText(), txtDireccion.getText(), nacimiento, false, pregunta, txtRespuesta.getText());
        } else {
            String tipo = String.valueOf(cmbTipoLicencia.getSelectedItem()).substring(0, 1);
            String idEmpresa = empresas.get(cmbEmpresa.getSelectedIndex() - 1).getIdEmpresa();
            r = controlador.registrarConductor(txtLogin.getText(), txtNombre.getText(), txtCedula.getText(), nacimiento, clave, confirmar, txtEmail.getText(),
                    txtTelefono.getText(), idEmpresa, tipo, txtNumeroLicencia.getText(), selVenceLicencia.getFecha(), documentos,
                    String.valueOf(cmbBanco.getSelectedItem()), txtTarjeta.getText(),
                    String.format("%02d/%02d", selVenceTarjeta.getFecha().getMes(), selVenceTarjeta.getFecha().getAnio() % 100), pregunta, txtRespuesta.getText());
        }
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblMensaje, r.getMensaje(), false);
            return;
        }
        loginCreado = txtLogin.getText().trim();
        Tema.info(this, r.getMensaje());
        dispose();
    }

    public String getLoginCreado() {
        return loginCreado;
    }
}
