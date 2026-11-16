package InterfacesUsuario;

import control.ControladorFacturacion;
import control.ResultadoOperacion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;
import objetosNegocio.Comprobante;
import objetosNegocio.Usuario;

/**
 * Facturas de todos los movimientos de dinero. La administración (según su cargo) ve todas, con el resumen de ingresos;
 * un pasajero o conductor solo ve las que él mismo generó. Son documentos de solo lectura: no se editan ni se eliminan.
 */
public class PanelFacturacion extends JPanel {
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ControladorFacturacion controlador = new ControladorFacturacion();
    private final Usuario usuario;
    private final boolean administracion;
    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final JComboBox<String> cmbTipo;
    /** Tipo de comprobante de cada opción del filtro ("" = todos), según quién mira la pantalla. */
    private final String[] tiposDelFiltro;
    private final JLabel lblEstado = Tema.mensaje();
    private final JPanel resumen = new JPanel(new GridLayout(2, 0, 10, 8));
    private List<Comprobante> actuales = new ArrayList<>();

    public PanelFacturacion(Usuario usuario, boolean administracion) {
        this.usuario = usuario;
        this.administracion = administracion;
        // El titular y la cédula solo tienen sentido para la administración: un pasajero o conductor siempre ve SUS facturas.
        String[] columnas = administracion
                ? new String[]{"N.º factura", "Fecha", "Tipo", "Titular", "Cédula", "Concepto", "Total", "Emitida por"}
                : new String[]{"N.º factura", "Fecha", "Tipo", "Concepto", "Total", "Emitida por"};
        modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        boolean esConductor = usuario != null && usuario.getRol() == objetosNegocio.Rol.CONDUCTOR;
        if (administracion) {
            tiposDelFiltro = new String[]{"", Comprobante.PASAJE, Comprobante.RECARGA, Comprobante.LIQUIDACION, Comprobante.REEMBOLSO};
            cmbTipo = new JComboBox<>(new String[]{"Todas las facturas", "Pasajes vendidos", "Recargas", "Pagos a conductores", "Reembolsos"});
        } else if (esConductor) {
            tiposDelFiltro = new String[]{""};
            cmbTipo = new JComboBox<>(new String[]{"Todas mis facturas"});
        } else {
            // el pasajero nunca paga a conductores: no existe ese tipo en su lista
            tiposDelFiltro = new String[]{"", Comprobante.PASAJE, Comprobante.RECARGA, Comprobante.REEMBOLSO};
            cmbTipo = new JComboBox<>(new String[]{"Todas mis facturas", "Pasajes comprados", "Recargas", "Reembolsos"});
        }
        setLayout(new BorderLayout(0, 8));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        Tema.tabla(tabla);
        tabla.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        tabla.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    verFactura();
                }
            }
        });
        JButton btnVer = Tema.boton("Ver factura", true);
        btnVer.addActionListener(e -> verFactura());
        JButton btnPdf = Tema.boton("Descargar PDF", false);
        btnPdf.addActionListener(e -> descargar());
        JButton btnActualizar = Tema.boton("Actualizar", false);
        btnActualizar.addActionListener(e -> cargar());
        cmbTipo.setFont(Tema.fuente(Font.PLAIN, 13f));
        cmbTipo.addActionListener(e -> cargar());
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        barra.setOpaque(false);
        barra.add(btnVer);
        barra.add(btnPdf);
        barra.add(btnActualizar);
        barra.add(Tema.etiqueta("Mostrar:"));
        barra.add(cmbTipo);
        JLabel nota = Tema.etiqueta(administracion ? "Solo lectura: las facturas no se editan ni se eliminan." : "Aquí están las facturas que generaste con tus compras y recargas.");
        nota.setForeground(Tema.TEXTO_SUAVE);
        barra.add(nota);

        resumen.setOpaque(false);
        JPanel arriba = new JPanel(new BorderLayout(0, 6));
        arriba.setOpaque(false);
        if (administracion) {
            arriba.add(resumen, BorderLayout.NORTH);
        }
        arriba.add(barra, BorderLayout.CENTER);
        add(arriba, BorderLayout.NORTH);
        add(new JScrollPane(tabla), BorderLayout.CENTER);
        add(lblEstado, BorderLayout.SOUTH);
        cargar();
    }

    public JTable getTabla() {
        return tabla;
    }

    @SuppressWarnings("unchecked")
    public void cargar() {
        modelo.setRowCount(0);
        ResultadoOperacion r = administracion ? controlador.listarParaAdministrador(usuario) : controlador.listarMias(usuario);
        if (!r.isExito()) {
            Tema.mostrarMensaje(lblEstado, r.getMensaje(), false);
            return;
        }
        String filtro = cmbTipo.getSelectedIndex() >= 0 && cmbTipo.getSelectedIndex() < tiposDelFiltro.length ? tiposDelFiltro[cmbTipo.getSelectedIndex()] : "";
        actuales = new ArrayList<>();
        double suma = 0;
        for (Comprobante c : (List<Comprobante>) r.getDatos()) {
            if (!filtro.isEmpty() && !c.getTipo().equals(filtro)) {
                continue;
            }
            actuales.add(c);
            suma += c.getTotal();
            if (administracion) {
                modelo.addRow(new Object[]{c.getNumero(), c.getFecha().format(FORMATO), c.getTipoEtiqueta(), c.getNombreTitular(), c.getCedulaTitular(),
                        c.getConcepto(), String.format("$%.2f", c.getTotal()), c.getEmisor()});
            } else {
                modelo.addRow(new Object[]{c.getNumero(), c.getFecha().format(FORMATO), c.getTipoEtiqueta(),
                        c.getConcepto(), String.format("$%.2f", c.getTotal()), c.getEmisor()});
            }
        }
        Tema.mostrarMensaje(lblEstado, actuales.size() + " factura(s) · total " + String.format("$%.2f", suma), true);
        if (administracion) {
            ControladorFacturacion.Resumen rs = controlador.resumen();
            resumen.removeAll();
            resumen.add(tarjeta("DINERO TOTAL DE LA EMPRESA (caja)", String.format("$%.2f", rs.cajaEmpresa)));
            resumen.add(tarjeta("Saldo en billeteras (de pasajeros)", String.format("$%.2f", rs.saldoBilleteras)));
            resumen.add(tarjeta("Dinero propio (sin deudas)", String.format("$%.2f", rs.dineroPropio)));
            resumen.add(tarjeta("Ingresos por venta de pasajes", String.format("$%.2f", rs.ventasPasajes)));
            resumen.add(tarjeta("Recargas acreditadas", String.format("$%.2f", rs.recargasAcreditadas)));
            resumen.add(tarjeta("Pagado a conductores (80 %)", String.format("$%.2f", rs.pagadoConductores)));
            resumen.add(tarjeta("Por pagar a conductores", String.format("$%.2f", rs.porPagarConductores)));
            resumen.add(tarjeta("Margen plataforma (20 %)", String.format("$%.2f", rs.margenPlataforma)));
            resumen.add(tarjeta("Devuelto a pasajeros", String.format("$%.2f", rs.devoluciones)));
            resumen.revalidate();
            resumen.repaint();
        }
    }

    private static JPanel tarjeta(String etiqueta, String valor) {
        JPanel p = new JPanel(new BorderLayout());
        p.setBackground(Color.WHITE);
        p.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        JLabel v = new JLabel(valor);
        v.setFont(Tema.fuente(Font.BOLD, 18f));
        v.setForeground(Color.BLACK);
        JLabel e = Tema.etiqueta(etiqueta);
        e.setForeground(Tema.TEXTO_SUAVE);
        p.add(v, BorderLayout.CENTER);
        p.add(e, BorderLayout.SOUTH);
        return p;
    }

    private Comprobante seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.info(this, "Selecciona una factura de la lista.");
            return null;
        }
        String numero = String.valueOf(modelo.getValueAt(fila, 0));
        return actuales.stream().filter(c -> c.getNumero().equals(numero)).findFirst().orElse(null);
    }

    private void descargar() {
        Comprobante c = seleccionada();
        if (c != null) {
            Descargas.guardarPdf(this, "Descargar factura", control.ComprobantesPdf.nombreSugerido("Factura", c.getNumero()),
                    destino -> new control.ComprobantesPdf().factura(destino, c));
        }
    }

    private void verFactura() {
        int fila = tabla.getSelectedRow();
        if (fila < 0) {
            Tema.info(this, "Selecciona una factura de la lista.");
            return;
        }
        String numero = String.valueOf(modelo.getValueAt(fila, 0));
        for (Comprobante c : actuales) {
            if (c.getNumero().equals(numero)) {
                new DialogoFactura(javax.swing.SwingUtilities.getWindowAncestor(this), c).setVisible(true);
                return;
            }
        }
    }
}
