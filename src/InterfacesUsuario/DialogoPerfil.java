package InterfacesUsuario;

import control.ControladorNovedades;
import control.ControladorUsuarios;
import control.ResultadoOperacion;
import excepciones.Validador;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Novedad;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;

/**
 * Mi perfil: un resumen de la cuenta y, a la izquierda, un botón para cada cosa que se puede hacer:
 * modificar datos, cambiar la contraseña, cambiar la pregunta de seguridad y (para pasajeros) escribirle al
 * administrador una queja, sugerencia o aviso de algo que pasó, y ver sus respuestas.
 */
public class DialogoPerfil extends JDialog {
    private final ControladorUsuarios controlador = new ControladorUsuarios();
    private final ControladorNovedades novedades = new ControladorNovedades();
    private final Usuario usuario;
    private final Runnable alActualizar;
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel contenido = new JPanel(tarjetas);
    private final JPanel resumen = new JPanel(new BorderLayout());
    private final List<JButton> menu = new ArrayList<>();

    // datos
    private final JTextField txtNombre = Tema.campo(22);
    private final JTextField txtEmail = Tema.campo(22);
    private final JTextField txtTelefono = Tema.campo(22);
    private final JLabel msjDatos = Tema.mensaje();
    private final ValidacionUI.Reglas reglasDatos = new ValidacionUI.Reglas();
    // clave
    private final JPasswordField txtActual = Tema.clave(22);
    private final JPasswordField txtNueva = Tema.clave(22);
    private final JPasswordField txtConfirmar = Tema.clave(22);
    private final JLabel fortaleza = Tema.mensaje();
    private final JLabel msjClave = Tema.mensaje();
    private final ValidacionUI.Reglas reglasClave = new ValidacionUI.Reglas();
    // seguridad
    private final JPasswordField txtClaveSeg = Tema.clave(22);
    private final JComboBox<String> cmbPregunta;
    private final JTextField txtRespuesta = Tema.campo(22);
    private final JLabel msjSeg = Tema.mensaje();
    private final ValidacionUI.Reglas reglasSeg = new ValidacionUI.Reglas();
    // novedades
    private final JComboBox<String> cmbTipo = new JComboBox<>();
    private final List<String> clavesTipo = new ArrayList<>();
    private final JTextField txtAsunto = Tema.campo(30);
    private final JTextArea txtMensaje = new JTextArea(6, 30);
    private final JLabel contadorAsunto = Tema.etiqueta("0/60");
    private final JLabel contadorMensaje = Tema.etiqueta("0/300");
    private final JLabel msjNovedad = Tema.mensaje();
    private final DefaultTableModel modeloNovedades = new DefaultTableModel(new String[]{"ID", "Fecha", "Tipo", "Asunto", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int r, int c) {
            return false;
        }
    };
    private final JTable tablaNovedades = new JTable(modeloNovedades);
    private final JTextArea detalleNovedad = new JTextArea();
    private List<Novedad> misNovedades = new ArrayList<>();

    public DialogoPerfil(Window padre, Usuario usuario, Runnable alActualizar) {
        super(padre, "Mi perfil", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        this.alActualizar = alActualizar;
        cmbPregunta = new JComboBox<>(usuario.getRol() == Rol.ADMINISTRADOR ? Tema.PREGUNTAS_ADMIN : Tema.PREGUNTAS);
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado(usuario.getNombre(), usuario.getTipo() + (usuario.getCargo().isEmpty() ? "" : " · " + usuario.getCargo()) + "  ·  usuario: " + usuario.getLogin()), BorderLayout.NORTH);

        contenido.setBackground(Tema.FONDO);
        contenido.add(resumen, "resumen");
        contenido.add(pestanaDatos(), "datos");
        contenido.add(pestanaClave(), "clave");
        contenido.add(pestanaSeguridad(), "seguridad");
        boolean pasajero = usuario.getRol() == Rol.PASAJERO;
        boolean conFoto = pasajero || usuario.getRol() == Rol.CONDUCTOR;
        if (conFoto) {
            contenido.add(pestanaFoto(), "foto");
        }
        if (pasajero) {
            contenido.add(pestanaNovedad(), "novedad");
            contenido.add(pestanaMisNovedades(), "mis");
        }

        JPanel izquierda = new JPanel(new GridLayout(0, 1, 0, 8));
        izquierda.setBackground(Color.WHITE);
        izquierda.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, Tema.BORDE),
                BorderFactory.createEmptyBorder(16, 14, 16, 14)));
        agregarBoton(izquierda, "Mi resumen", "resumen");
        agregarBoton(izquierda, "Modificar mis datos", "datos");
        agregarBoton(izquierda, "Cambiar contraseña", "clave");
        agregarBoton(izquierda, "Pregunta de seguridad", "seguridad");
        if (conFoto) {
            agregarBoton(izquierda, "Foto de perfil", "foto");
        }
        if (pasajero) {
            agregarBoton(izquierda, "Enviar novedad o queja", "novedad");
            agregarBoton(izquierda, "Mis novedades", "mis");
        }
        JPanel contenedorIzq = new JPanel(new BorderLayout());
        contenedorIzq.setBackground(Color.WHITE);
        contenedorIzq.add(izquierda, BorderLayout.NORTH);
        contenedorIzq.setPreferredSize(new Dimension(250, 0));
        add(contenedorIzq, BorderLayout.WEST);
        add(contenido, BorderLayout.CENTER);

        actualizarResumen();
        mostrar("resumen");
        setPreferredSize(new Dimension(940, 660));
        pack();
        setLocationRelativeTo(padre);
    }

    private final JLabel lblFoto = new JLabel("", javax.swing.SwingConstants.CENTER);
    private final JLabel msjFoto = Tema.mensaje();

    private JPanel pestanaFoto() {
        JPanel p = new JPanel(new BorderLayout(0, 12));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(24, 40, 24, 40));
        lblFoto.setPreferredSize(new Dimension(256, 256));
        lblFoto.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        JButton subir = Tema.boton("Subir foto (PNG o JPG)", true);
        subir.addActionListener(e -> subirFoto());
        JButton quitar = Tema.boton("Quitar foto", false);
        quitar.addActionListener(e -> {
            ResultadoOperacion r = new control.ControladorFotos().quitar(usuario.getIdPerfil());
            Tema.mostrarMensaje(msjFoto, r.getMensaje(), r.isExito());
            mostrarFoto();
            alActualizar.run();
            BarraSuperior.refrescarAvatar(getOwner());
            actualizarResumen();
        });
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 0));
        botones.setOpaque(false);
        botones.add(subir);
        botones.add(quitar);
        JPanel centro = new JPanel(new FlowLayout(FlowLayout.CENTER));
        centro.setOpaque(false);
        centro.add(lblFoto);
        JLabel nota = Tema.nota(560, "<html>Solo se aceptan imágenes <b>PNG o JPG</b> (máx. 8 MB). Se recorta en cuadrado automáticamente.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout(0, 8));
        sur.setOpaque(false);
        sur.add(msjFoto, BorderLayout.NORTH);
        sur.add(botones, BorderLayout.CENTER);
        p.add(nota, BorderLayout.NORTH);
        p.add(centro, BorderLayout.CENTER);
        p.add(sur, BorderLayout.SOUTH);
        mostrarFoto();
        return p;
    }

    private void mostrarFoto() {
        java.awt.image.BufferedImage img = new control.ControladorFotos().cargar(usuario.getIdPerfil());
        lblFoto.setIcon(img == null ? null : new javax.swing.ImageIcon(img));
        lblFoto.setText(img == null ? "Sin foto" : "");
    }

    private void subirFoto() {
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setAcceptAllFileFilterUsed(false);
        fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Imágenes PNG o JPG", "png", "jpg", "jpeg"));
        if (fc.showOpenDialog(this) != javax.swing.JFileChooser.APPROVE_OPTION) {
            return;
        }
        ResultadoOperacion r = new control.ControladorFotos().guardar(usuario.getIdPerfil(), fc.getSelectedFile());
        Tema.mostrarMensaje(msjFoto, r.getMensaje(), r.isExito());
        mostrarFoto();
        alActualizar.run();
        BarraSuperior.refrescarAvatar(getOwner());
        actualizarResumen();
    }

    private void agregarBoton(JPanel panel, String texto, String tarjeta) {
        JButton b = Tema.boton(texto, false);
        b.addActionListener(e -> mostrar(tarjeta));
        menu.add(b);
        panel.add(b);
    }

    private void mostrar(String tarjeta) {
        tarjetas.show(contenido, tarjeta);
        if ("mis".equals(tarjeta)) {
            cargarMisNovedades();
        }
        if ("resumen".equals(tarjeta)) {
            actualizarResumen();
        }
    }

    private JPanel contenedor(JPanel formulario, JLabel mensaje, JButton boton, String titulo) {
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(18, 30, 18, 30));
        p.add(Tema.titulo(titulo, 18f), BorderLayout.NORTH);
        JPanel medio = new JPanel(new BorderLayout());
        medio.setOpaque(false);
        medio.add(formulario, BorderLayout.NORTH);
        p.add(medio, BorderLayout.CENTER);
        JPanel sur = new JPanel(new BorderLayout(0, 6));
        sur.setOpaque(false);
        sur.add(mensaje, BorderLayout.NORTH);
        JPanel b = new JPanel(new FlowLayout(FlowLayout.CENTER));
        b.setOpaque(false);
        b.add(boton);
        sur.add(b, BorderLayout.CENTER);
        p.add(sur, BorderLayout.SOUTH);
        return p;
    }

    // ------------------------------------------------------------------ resumen

    private void actualizarResumen() {
        resumen.removeAll();
        resumen.setBackground(Tema.FONDO);
        resumen.setBorder(BorderFactory.createEmptyBorder(18, 30, 18, 30));
        JPanel f = Tema.panelFormulario();
        int i = 0;
        i = linea(f, i, "Nombre", usuario.getNombre());
        i = linea(f, i, "Usuario", usuario.getLogin());
        i = linea(f, i, "Rol", usuario.getTipo());
        if (!usuario.getCargo().isEmpty()) {
            i = linea(f, i, "Cargo", usuario.getCargo());
        }
        String provincia = Validador.provinciaPorCedula(usuario.getCedula());
        i = linea(f, i, "Cédula", usuario.getCedula() + (provincia.isEmpty() || provincia.startsWith("Código") ? "" : "  (" + provincia + ")"));
        i = linea(f, i, "Correo", usuario.getEmail());
        i = linea(f, i, "Teléfono", usuario.getTelefono());
        i = linea(f, i, "Estado de la cuenta", usuario.getEstado());
        i = linea(f, i, "Pregunta de seguridad", usuario.tienePreguntaSeguridad() ? "Configurada" : "NO configurada (no podrás recuperar tu contraseña)");
        JLabel ayuda = Tema.nota(560, "<html>Usa los botones de la izquierda para modificar tus datos, cambiar tu contraseña"
                + (usuario.getRol() == Rol.PASAJERO ? " o escribirle una novedad o queja al administrador." : ".") + "</html>");
        ayuda.setForeground(Tema.TEXTO_SUAVE);
        JPanel cabeza = new JPanel(new BorderLayout(14, 0));
        cabeza.setOpaque(false);
        cabeza.add(new JLabel(BarraSuperior.avatar(usuario, 84)), BorderLayout.WEST);
        cabeza.add(Tema.titulo("Mi cuenta", 18f), BorderLayout.CENTER);
        resumen.add(cabeza, BorderLayout.NORTH);
        JPanel medio = new JPanel(new BorderLayout(0, 14));
        medio.setOpaque(false);
        medio.add(f, BorderLayout.NORTH);
        medio.add(ayuda, BorderLayout.CENTER);
        resumen.add(medio, BorderLayout.CENTER);
        resumen.revalidate();
        resumen.repaint();
    }

    private static int linea(JPanel f, int fila, String etiqueta, String valor) {
        JLabel v = Tema.etiqueta(valor == null || valor.isEmpty() ? "—" : valor);
        v.setFont(Tema.fuente(Font.BOLD, 14f));
        Tema.fila(f, fila, etiqueta, v);
        return fila + 1;
    }

    // ------------------------------------------------------------------ datos

    private JPanel pestanaDatos() {
        txtNombre.setText(usuario.getNombre());
        txtNombre.setEnabled(usuario.getRol() != Rol.CONDUCTOR);
        txtEmail.setText(usuario.getEmail());
        txtTelefono.setText(usuario.getTelefono());
        ValidacionUI.soloLetras(txtNombre, 50);
        ValidacionUI.correo(txtEmail, 50);
        ValidacionUI.celular(txtTelefono);
        JTextField cedula = Tema.campo(22);
        cedula.setText(usuario.getCedula());
        cedula.setEnabled(false);
        JLabel errNombre = ValidacionUI.etiquetaError();
        JLabel errEmail = ValidacionUI.etiquetaError();
        JLabel errTelefono = ValidacionUI.etiquetaError();
        JPanel f = Tema.panelFormulario();
        int i = 0;
        i = ValidacionUI.fila(f, i, "Nombre", txtNombre, errNombre);
        Tema.fila(f, i++, "Cédula", cedula);
        i = ValidacionUI.fila(f, i, "Correo (opcional)", txtEmail, errEmail);
        ValidacionUI.fila(f, i, "Celular (opcional)", txtTelefono, errTelefono);
        reglasDatos.agregar(txtNombre, errNombre, () -> usuario.getRol() == Rol.CONDUCTOR ? null : Validador.errorNombre(txtNombre.getText()));
        reglasDatos.agregar(txtEmail, errEmail, () -> Validador.errorEmailOpcional(txtEmail.getText()));
        reglasDatos.agregar(txtTelefono, errTelefono, () -> Validador.errorTelefonoOpcional(txtTelefono.getText()));
        JButton guardar = Tema.boton("Guardar cambios", true);
        guardar.addActionListener(e -> {
            if (!reglasDatos.validar()) {
                Tema.mostrarMensaje(msjDatos, "Revisa los campos marcados en rojo.", false);
                return;
            }
            ResultadoOperacion r = controlador.actualizarPerfil(usuario.getIdUsuario(), txtNombre.getText(), txtEmail.getText(), txtTelefono.getText());
            Tema.mostrarMensaje(msjDatos, r.getMensaje(), r.isExito());
            if (r.isExito()) {
                Navegador.refrescar(usuario, (Usuario) r.getDatos());
                alActualizar.run();
            }
        });
        return contenedor(f, msjDatos, guardar, "Modificar mis datos");
    }

    // ------------------------------------------------------------------ contraseña

    private JPanel pestanaClave() {
        JLabel errActual = ValidacionUI.etiquetaError();
        JLabel errNueva = ValidacionUI.etiquetaError();
        JLabel errConfirmar = ValidacionUI.etiquetaError();
        JPanel f = Tema.panelFormulario();
        int i = 0;
        i = ValidacionUI.fila(f, i, "Contraseña actual", txtActual, errActual);
        i = ValidacionUI.fila(f, i, "Contraseña nueva", txtNueva, errNueva);
        Tema.fila(f, i++, "", fortaleza);
        ValidacionUI.fila(f, i, "Confirmar nueva", txtConfirmar, errConfirmar);
        Tema.vincularFortaleza(txtNueva, fortaleza);
        reglasClave.agregar(txtActual, errActual, () -> new String(txtActual.getPassword()).isEmpty() ? "Escribe tu contraseña actual" : null);
        reglasClave.agregar(txtNueva, errNueva, () -> {
            String nueva = new String(txtNueva.getPassword());
            String problema = Validador.errorContrasena(nueva);
            return problema == null && nueva.equals(new String(txtActual.getPassword())) ? "La nueva contraseña debe ser distinta de la actual" : problema;
        });
        reglasClave.agregar(txtConfirmar, errConfirmar, () -> new String(txtNueva.getPassword()).equals(new String(txtConfirmar.getPassword()))
                ? null : "Las contraseñas no coinciden");
        JButton cambiar = Tema.boton("Cambiar contraseña", true);
        cambiar.addActionListener(e -> {
            if (!reglasClave.validar()) {
                Tema.mostrarMensaje(msjClave, "Revisa los campos marcados en rojo.", false);
                return;
            }
            ResultadoOperacion r = controlador.cambiarContrasena(usuario.getIdUsuario(), new String(txtActual.getPassword()),
                    new String(txtNueva.getPassword()), new String(txtConfirmar.getPassword()));
            Tema.mostrarMensaje(msjClave, r.getMensaje(), r.isExito());
            if (r.isExito()) {
                Navegador.refrescar(usuario, (Usuario) r.getDatos());
                txtActual.setText("");
                txtNueva.setText("");
                txtConfirmar.setText("");
                reglasClave.limpiar();
            }
        });
        return contenedor(f, msjClave, cambiar, "Cambiar contraseña");
    }

    // ------------------------------------------------------------------ pregunta de seguridad

    private JPanel pestanaSeguridad() {
        cmbPregunta.setEditable(true);
        cmbPregunta.setFont(Tema.fuente(Font.PLAIN, 13f));
        if (usuario.tienePreguntaSeguridad()) {
            cmbPregunta.setSelectedItem(usuario.getPreguntaSeguridad());
        } else {
            cmbPregunta.setSelectedItem(null);
        }
        ValidacionUI.limite(txtRespuesta, 40);
        JLabel errPregunta = ValidacionUI.etiquetaError();
        JLabel errRespuesta = ValidacionUI.etiquetaError();
        JLabel errClave = ValidacionUI.etiquetaError();
        JLabel nota = Tema.nota(560, "<html>La pregunta de seguridad te permite recuperar tu contraseña usando solo tu cédula. Elige una de la lista o escribe la tuya. "
                + (usuario.tienePreguntaSeguridad() ? "Ya tienes una configurada; puedes cambiarla." : "<b>Aún no tienes una configurada.</b>") + "</html>");
        JPanel f = Tema.panelFormulario();
        int i = 0;
        i = ValidacionUI.fila(f, i, "Pregunta", cmbPregunta, errPregunta);
        i = ValidacionUI.fila(f, i, "Respuesta", txtRespuesta, errRespuesta);
        ValidacionUI.fila(f, i, "Tu contraseña actual", txtClaveSeg, errClave);
        reglasSeg.agregar(cmbPregunta, errPregunta, () -> {
            String p = textoPregunta();
            String problema = Validador.errorPreguntaSeguridad(p);
            return problema == null && !p.trim().endsWith("?") ? "Termina la pregunta con el signo ?" : problema;
        });
        reglasSeg.agregar(txtRespuesta, errRespuesta, () -> Validador.errorRespuestaSeguridad(txtRespuesta.getText()));
        reglasSeg.agregar(txtClaveSeg, errClave, () -> new String(txtClaveSeg.getPassword()).isEmpty() ? "Escribe tu contraseña actual para confirmar" : null);
        JPanel centro = new JPanel(new BorderLayout(0, 10));
        centro.setOpaque(false);
        centro.add(nota, BorderLayout.NORTH);
        centro.add(f, BorderLayout.CENTER);
        JButton guardar = Tema.boton("Guardar pregunta", true);
        guardar.addActionListener(e -> {
            if (!reglasSeg.validar()) {
                Tema.mostrarMensaje(msjSeg, "Revisa los campos marcados en rojo.", false);
                return;
            }
            ResultadoOperacion r = controlador.cambiarPreguntaSeguridad(usuario.getIdUsuario(), new String(txtClaveSeg.getPassword()), textoPregunta(), txtRespuesta.getText());
            Tema.mostrarMensaje(msjSeg, r.getMensaje(), r.isExito());
            if (r.isExito()) {
                Navegador.refrescar(usuario, (Usuario) r.getDatos());
                txtRespuesta.setText("");
                txtClaveSeg.setText("");
                reglasSeg.limpiar();
            }
        });
        return contenedor(centro, msjSeg, guardar, "Pregunta de seguridad");
    }

    private String textoPregunta() {
        Object o = cmbPregunta.getEditor().getItem();
        return o == null ? "" : o.toString();
    }

    // ------------------------------------------------------------------ novedades

    private JPanel pestanaNovedad() {
        for (Map.Entry<String, String> e : ControladorNovedades.TIPOS_PASAJERO.entrySet()) {
            clavesTipo.add(e.getKey());
            cmbTipo.addItem(e.getValue());
        }
        cmbTipo.setFont(Tema.fuente(Font.PLAIN, 13f));
        ValidacionUI.limite(txtAsunto, 60);
        txtMensaje.setLineWrap(true);
        txtMensaje.setWrapStyleWord(true);
        txtMensaje.setFont(Tema.fuente(Font.PLAIN, 14f));
        txtMensaje.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        txtAsunto.getDocument().addDocumentListener(contador(contadorAsunto, txtAsunto::getText, 60));
        txtMensaje.getDocument().addDocumentListener(contador(contadorMensaje, txtMensaje::getText, 300));
        JScrollPane areaMensaje = new JScrollPane(txtMensaje);
        areaMensaje.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        JPanel f = Tema.panelFormulario();
        Tema.fila(f, 0, "Tipo", cmbTipo);
        Tema.fila(f, 1, "Asunto", txtAsunto);
        Tema.fila(f, 2, "", contadorAsunto);
        Tema.fila(f, 3, "Cuéntanos qué pasó", areaMensaje);
        Tema.fila(f, 4, "", contadorMensaje);
        JLabel nota = Tema.nota(560, "<html>Tu mensaje le llega al administrador, que podrá responderte. Lo verás en <b>Mis novedades</b>. "
                + "Puedes tener hasta " + ControladorNovedades.MAX_ABIERTAS + " novedades sin resolver a la vez.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel medio = new JPanel(new BorderLayout(0, 10));
        medio.setOpaque(false);
        medio.add(nota, BorderLayout.NORTH);
        medio.add(f, BorderLayout.CENTER);
        JButton enviar = Tema.boton("Enviar al administrador", true);
        enviar.addActionListener(e -> enviarNovedad());
        return contenedor(medio, msjNovedad, enviar, "Enviar novedad o queja");
    }

    private static DocumentListener contador(JLabel etiqueta, java.util.function.Supplier<String> texto, int max) {
        return new DocumentListener() {
            private void actualizar() {
                int n = texto.get().length();
                etiqueta.setText(n + "/" + max);
                etiqueta.setForeground(n > max ? Tema.ERROR : Tema.TEXTO_SUAVE);
            }

            public void insertUpdate(DocumentEvent e) { actualizar(); }
            public void removeUpdate(DocumentEvent e) { actualizar(); }
            public void changedUpdate(DocumentEvent e) { actualizar(); }
        };
    }

    private void enviarNovedad() {
        String asunto = txtAsunto.getText().trim();
        String mensaje = txtMensaje.getText().trim();
        String problema = null;
        if (asunto.length() < 5) {
            problema = "El asunto debe tener al menos 5 caracteres";
            txtAsunto.requestFocusInWindow();
        } else if (mensaje.length() < 10) {
            problema = "Cuéntanos un poco más: el mensaje debe tener al menos 10 caracteres";
            txtMensaje.requestFocusInWindow();
        } else if (mensaje.length() > 300) {
            problema = "El mensaje no puede superar los 300 caracteres";
            txtMensaje.requestFocusInWindow();
        }
        if (problema != null) {
            Tema.mostrarMensaje(msjNovedad, problema, false);
            return;
        }
        ResultadoOperacion r = novedades.reportar(usuario, "", clavesTipo.get(cmbTipo.getSelectedIndex()), asunto, mensaje);
        Tema.mostrarMensaje(msjNovedad, r.getMensaje(), r.isExito());
        if (r.isExito()) {
            txtAsunto.setText("");
            txtMensaje.setText("");
            Tema.info(this, "Tu novedad fue enviada. Revisa la respuesta en \"Mis novedades\".");
            mostrar("mis");
        }
    }

    private JPanel pestanaMisNovedades() {
        Tema.tabla(tablaNovedades);
        tablaNovedades.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tablaNovedades.getSelectionModel().addListSelectionListener(e -> mostrarDetalleNovedad());
        detalleNovedad.setEditable(false);
        detalleNovedad.setLineWrap(true);
        detalleNovedad.setWrapStyleWord(true);
        detalleNovedad.setFont(Tema.fuente(Font.PLAIN, 14f));
        detalleNovedad.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, new JScrollPane(tablaNovedades), new JScrollPane(detalleNovedad));
        split.setResizeWeight(0.55);
        split.setBorder(null);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(18, 30, 18, 30));
        p.add(Tema.titulo("Mis novedades", 18f), BorderLayout.NORTH);
        p.add(split, BorderLayout.CENTER);
        return p;
    }

    @SuppressWarnings("unchecked")
    private void cargarMisNovedades() {
        modeloNovedades.setRowCount(0);
        detalleNovedad.setText("");
        ResultadoOperacion r = novedades.listarMios(usuario);
        if (!r.isExito()) {
            return;
        }
        misNovedades = (List<Novedad>) r.getDatos();
        for (Novedad n : misNovedades) {
            String fecha = n.getFecha() == null ? "" : String.format("%02d/%02d/%04d", n.getFecha().getDia(), n.getFecha().getMes(), n.getFecha().getAnio());
            modeloNovedades.addRow(new Object[]{n.getIdNovedad(), fecha, ControladorNovedades.TIPOS.getOrDefault(n.getTipo(), n.getTipo()),
                    n.getAsunto(), Novedad.RESUELTA.equals(n.getEstado()) ? "RESUELTA" : (Novedad.EN_REVISION.equals(n.getEstado()) ? "EN REVISIÓN" : "ENVIADA")});
        }
    }

    private void mostrarDetalleNovedad() {
        int fila = tablaNovedades.getSelectedRow();
        if (fila < 0 || fila >= modeloNovedades.getRowCount()) {
            return;
        }
        String id = String.valueOf(modeloNovedades.getValueAt(fila, 0));
        for (Novedad n : misNovedades) {
            if (n.getIdNovedad().equals(id)) {
                detalleNovedad.setText(n.getAsunto() + "\n\n" + n.getMensaje() + "\n\n"
                        + (n.getRespuesta() == null || n.getRespuesta().isEmpty() ? "(El administrador aún no responde)" : "Respuesta del administrador:\n" + n.getRespuesta()));
                detalleNovedad.setCaretPosition(0);
                return;
            }
        }
    }
}
