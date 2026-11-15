package InterfacesUsuario;

import control.GeneradorInformes;
import control.GeneradorInformes.Seccion;
import java.awt.BorderLayout;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.filechooser.FileNameExtensionFilter;
import objetosNegocio.Usuario;

/**
 * Informes formales en PDF del sistema: un botón por sección (solo rutas, solo buses...), una selección de varias secciones
 * o el informe completo de todo el sistema. Solo aparecen las secciones que el cargo de la persona puede ver.
 */
public class DialogoInformes extends JDialog {
    private final Usuario usuario;
    private final Map<Seccion, JCheckBox> marcas = new LinkedHashMap<>();

    public DialogoInformes(Window padre, Usuario usuario) {
        super(padre, "Informes en PDF", Dialog.ModalityType.APPLICATION_MODAL);
        this.usuario = usuario;
        setIconImage(IconoApp.crear(64));
        setLayout(new BorderLayout());
        add(Tema.encabezado("Informes en PDF", "Un informe formal para analizar los datos del sistema"), BorderLayout.NORTH);

        List<Seccion> disponibles = GeneradorInformes.disponibles(usuario);
        JPanel lista = new ValidacionUI.PanelDesplazable(new GridBagLayout());
        lista.setBackground(Tema.FONDO);
        lista.setBorder(BorderFactory.createEmptyBorder(8, 18, 8, 18));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(4, 4, 4, 4);
        g.anchor = GridBagConstraints.WEST;
        int fila = 0;
        for (Seccion s : disponibles) {
            JCheckBox chk = new JCheckBox(s.getEtiqueta());
            chk.setOpaque(false);
            chk.setFont(Tema.fuente(Font.PLAIN, 14f));
            marcas.put(s, chk);
            JButton solo = Tema.boton("Solo este informe", false);
            solo.addActionListener(e -> generar(EnumSet.of(s)));
            g.gridx = 0;
            g.gridy = fila;
            g.weightx = 1;
            g.fill = GridBagConstraints.HORIZONTAL;
            lista.add(chk, g);
            g.gridx = 1;
            g.weightx = 0;
            g.fill = GridBagConstraints.NONE;
            lista.add(solo, g);
            fila++;
        }
        JScrollPane scroll = new JScrollPane(lista);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);

        JButton todo = Tema.boton("Informe completo del sistema", true);
        todo.addActionListener(e -> generar(EnumSet.copyOf(disponibles)));
        JButton marcadas = Tema.boton("Generar con las marcadas", false);
        marcadas.addActionListener(e -> {
            Set<Seccion> elegidas = EnumSet.noneOf(Seccion.class);
            marcas.forEach((s, c) -> {
                if (c.isSelected()) elegidas.add(s);
            });
            if (elegidas.isEmpty()) {
                Tema.info(this, "Marca al menos una sección.");
                return;
            }
            generar(elegidas);
        });
        JButton marcarTodo = Tema.boton("Marcar todo", false);
        marcarTodo.addActionListener(e -> marcas.values().forEach(c -> c.setSelected(true)));
        JButton limpiar = Tema.boton("Quitar marcas", false);
        limpiar.addActionListener(e -> marcas.values().forEach(c -> c.setSelected(false)));
        JButton cerrar = Tema.boton("Cerrar", false);
        cerrar.addActionListener(e -> dispose());

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 6));
        botones.setBackground(Tema.FONDO);
        botones.add(marcarTodo);
        botones.add(limpiar);
        botones.add(marcadas);
        botones.add(todo);
        botones.add(cerrar);
        JLabel nota = Tema.nota(620, "<html>Cada informe incluye portada, índice, resumen con gráficos y las tablas con los datos. "
                + "El <b>informe completo</b> reúne todas las secciones que tu cargo puede ver.</html>");
        nota.setForeground(Tema.TEXTO_SUAVE);
        nota.setBorder(BorderFactory.createEmptyBorder(8, 22, 0, 22));
        JPanel centro = new JPanel(new BorderLayout());
        centro.setBackground(Tema.FONDO);
        centro.add(nota, BorderLayout.NORTH);
        centro.add(scroll, BorderLayout.CENTER);
        add(centro, BorderLayout.CENTER);
        add(botones, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(760, 640));
        pack();
        setLocationRelativeTo(padre);
    }

    private void generar(Set<Seccion> secciones) {
        JFileChooser selector = new JFileChooser();
        selector.setDialogTitle("Guardar informe");
        selector.setFileFilter(new FileNameExtensionFilter("PDF (*.pdf)", "pdf"));
        String nombre = secciones.size() == 1 ? secciones.iterator().next().getEtiqueta() : "sistema";
        nombre = Normalizer.normalize(nombre, Normalizer.Form.NFD).replaceAll("[^A-Za-z0-9]+", "_").replaceAll("^_+|_+$", "");
        selector.setSelectedFile(new File("Informe_" + nombre + "_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm")) + ".pdf"));
        if (selector.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File destino = selector.getSelectedFile();
        if (!destino.getName().toLowerCase().endsWith(".pdf")) {
            destino = new File(destino.getParentFile(), destino.getName() + ".pdf");
        }
        try {
            GeneradorInformes.generar(usuario, secciones, destino);
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
