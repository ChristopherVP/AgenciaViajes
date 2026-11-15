package control;

import fachadas.FachadaArchivos;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import objetosNegocio.Comprobante;
import objetosNegocio.Conductor;
import objetosNegocio.Liquidacion;
import objetosNegocio.Movimiento;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;
import objetosNegocio.Usuario;
import objetosServicio.Permisos;
import persistencia.AlmacenTexto;
import persistencia.Conductores;
import persistencia.Pasajeros;

/**
 * Libro de facturas del sistema. Se emite una factura por cada compra de pasaje, cada recarga acreditada y cada pago a un
 * conductor. La administración (según su cargo) ve todas; cada pasajero o conductor solo ve las suyas.
 * Las ventas de pasajes son el ingreso de la plataforma.
 */
public class ControladorFacturacion {
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("facturas.dat");

    /** Resumen de dinero para el panel de facturación y los informes. */
    public static class Resumen {
        public double ventasPasajes;
        public double recargasAcreditadas;
        public double pagadoConductores;
        public double porPagarConductores;
        /** Dinero devuelto a las billeteras por viajes cancelados (notas de crédito). */
        public double devoluciones;
        /** Pagos a conductores de viajes que ya se aceptaron pero todavía no terminan. */
        public double programadoConductores;
        public int facturas;

        /** Lo que se queda la plataforma de los viajes ya liquidados (el 20 %). */
        public double margenPlataforma;
    }

    // ------------------------------------------------------------------ emisión

    public Comprobante emitir(String tipo, String idTitular, String nombre, String cedula, String concepto, String detalle,
                              double total, String referencia, String emisor) throws IOException {
        return emitirConFecha(tipo, idTitular, nombre, cedula, concepto, detalle, total, referencia, emisor, LocalDateTime.now());
    }

    public synchronized Comprobante emitirConFecha(String tipo, String idTitular, String nombre, String cedula, String concepto, String detalle,
                                                   double total, String referencia, String emisor, LocalDateTime fecha) throws IOException {
        Comprobante c = new Comprobante();
        c.setNumero(String.format("001-001-%09d", ALMACEN.leer().size() + 1));
        c.setTipo(tipo);
        c.setIdTitular(idTitular);
        c.setNombreTitular(nombre);
        c.setCedulaTitular(cedula);
        c.setConcepto(concepto);
        c.setDetalle(detalle);
        c.setTotal(total);
        c.setFecha(fecha);
        c.setReferencia(referencia);
        c.setEmisor(emisor);
        ALMACEN.agregar(c.aFila());
        return c;
    }

    /** Factura de un pasaje comprado. Devuelve null si no se pudo escribir (la compra no se anula por eso). */
    public Comprobante emitirPasaje(Reserva reserva) {
        try {
            Pasajero p = reserva.getPasajero();
            String ruta = reserva.getViaje() == null ? "" : new ControladorCompras().describirRuta(reserva.getViaje());
            String empresa = reserva.getViaje() != null && reserva.getViaje().getEmpresa() != null ? nombreEmpresa(reserva) : "";
            String salida = "";
            if (reserva.getViaje() != null && ControladorCompras.salidaDe(reserva.getViaje()) != null) {
                salida = ControladorCompras.salidaDe(reserva.getViaje()).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
            }
            String detalle = "Reserva: " + reserva.getIdReserva() + "\nViaje: " + (reserva.getViaje() == null ? "" : reserva.getViaje().getIdViaje())
                    + "\nSalida: " + salida + "\nAsientos: " + String.join(", ", reserva.getNumerosAsientos())
                    + "\nCantidad: " + reserva.getCantidadAsientos() + " asiento(s)\nPagado con: billetera " + objetosServicio.Marca.NOMBRE
                    + "\nCompra definitiva: no admite devoluciones";
            return emitir(Comprobante.PASAJE, p.getIdPasajero(), p.getNombre(), p.getCedula(), "Pasaje " + ruta, detalle,
                    reserva.getPrecioTotal(), reserva.getIdReserva(), empresa);
        } catch (IOException | RuntimeException e) {
            System.err.println("No se pudo emitir la factura del pasaje: " + e.getMessage());
            return null;
        }
    }

    private String nombreEmpresa(Reserva reserva) {
        try {
            objetosNegocio.Empresa e = FachadaArchivos.getInstance().obten(reserva.getViaje().getEmpresa());
            return e == null ? "" : e.getNombre();
        } catch (Exception ex) {
            return "";
        }
    }

    /** Nota de crédito: el dinero de un pasaje de un viaje cancelado vuelve a la billetera del pasajero. */
    public Comprobante emitirReembolso(Movimiento m, String motivo) {
        try {
            Pasajero p = new Pasajeros("pasajeros.dat").obten(new Pasajero(m.getIdPasajero()));
            String detalle = "Movimiento: " + m.getIdMovimiento() + "\nPasaje (reserva): " + m.getReferencia() + "\nMotivo: " + motivo
                    + "\nAcreditado a: billetera " + objetosServicio.Marca.NOMBRE;
            return emitir(Comprobante.REEMBOLSO, m.getIdPasajero(), p == null ? "" : p.getNombre(), p == null ? "" : p.getCedula(),
                    "Reembolso por viaje cancelado", detalle, m.getMonto(), m.getIdMovimiento(), objetosServicio.Marca.SISTEMA);
        } catch (IOException | RuntimeException | excepciones.PersistenciaException e) {
            System.err.println("No se pudo emitir la nota de crédito: " + e.getMessage());
            return null;
        }
    }

    public Comprobante emitirRecarga(Movimiento m) {
        try {
            Pasajero p = new Pasajeros("pasajeros.dat").obten(new Pasajero(m.getIdPasajero()));
            String detalle = "Movimiento: " + m.getIdMovimiento() + "\nMétodo: " + m.getMetodo() + "\nReferencia: " + m.getReferencia()
                    + "\nAcreditado a: billetera " + objetosServicio.Marca.NOMBRE;
            return emitir(Comprobante.RECARGA, m.getIdPasajero(), p == null ? "" : p.getNombre(), p == null ? "" : p.getCedula(),
                    "Recarga de billetera", detalle, m.getMonto(), m.getIdMovimiento(), objetosServicio.Marca.SISTEMA);
        } catch (IOException | RuntimeException | excepciones.PersistenciaException e) {
            System.err.println("No se pudo emitir la factura de la recarga: " + e.getMessage());
            return null;
        }
    }

    public Comprobante emitirLiquidacion(Liquidacion l) {
        return emitirLiquidacionConFecha(l, LocalDateTime.now());
    }

    public Comprobante emitirLiquidacionConFecha(Liquidacion l, LocalDateTime fecha) {
        try {
            Conductor c = new Conductores("conductores.dat").obten(new Conductor(l.getIdConductor()));
            String detalle = "Viaje " + l.getIdViaje() + " · " + l.getRuta() + "\nSalida: " + l.getFechaViaje()
                    + "\nPasajeros transportados: " + l.getPasajeros() + "\nRecaudado en pasajes: " + String.format("$%.2f", l.getRecaudado())
                    + "\nParticipación del conductor: " + (int) Liquidacion.PORCENTAJE_CONDUCTOR + " %" + (l.getPartes() > 1 ? " (dividida entre " + l.getPartes() + " conductores)" : "")
                    + "\nPagado a: " + l.getTarjeta();
            return emitirConFecha(Comprobante.LIQUIDACION, l.getIdConductor(), l.getNombreConductor(), c == null ? "" : c.getCedula(),
                    "Pago por el viaje " + l.getIdViaje(), detalle, l.getMonto(), l.getIdLiquidacion(), l.getEmpresa(), fecha);
        } catch (IOException | RuntimeException | excepciones.PersistenciaException e) {
            System.err.println("No se pudo emitir la factura del pago: " + e.getMessage());
            return null;
        }
    }

    // ------------------------------------------------------------------ consultas

    private List<Comprobante> todas() throws IOException {
        List<Comprobante> lista = new ArrayList<>();
        for (String[] f : ALMACEN.leer()) {
            lista.add(Comprobante.deFila(f));
        }
        lista.sort(Comparator.comparing(Comprobante::getNumero).reversed());
        return lista;
    }

    /** Todas las facturas, para los informes internos (sin filtro de permisos: quien llama ya validó el acceso a Reportes). */
    public List<Comprobante> todasParaInformes() {
        try {
            return todas();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    /** Todas las facturas de todos los movimientos (solo cargos con acceso a Facturas). */
    public ResultadoOperacion listarParaAdministrador(Usuario actor) {
        if (!Permisos.puedeVer(actor, Permisos.Modulo.FACTURAS)) {
            return new ResultadoOperacion(false, "Tu cargo no tiene acceso a las facturas");
        }
        try {
            return new ResultadoOperacion(true, "OK", todas());
        } catch (IOException e) {
            return new ResultadoOperacion(false, "No se pudieron leer las facturas: " + e.getMessage());
        }
    }

    /** Solo las facturas que generó esa persona (pasajero o conductor). */
    public ResultadoOperacion listarMias(Usuario usuario) {
        if (usuario == null || usuario.getIdPerfil() == null || usuario.getIdPerfil().isEmpty()) {
            return new ResultadoOperacion(false, "Acción no permitida");
        }
        try {
            List<Comprobante> mias = todas().stream().filter(c -> usuario.getIdPerfil().equals(c.getIdTitular())).collect(Collectors.toList());
            return new ResultadoOperacion(true, "OK", mias);
        } catch (IOException e) {
            return new ResultadoOperacion(false, "No se pudieron leer las facturas: " + e.getMessage());
        }
    }

    public Comprobante buscarPorReferencia(String tipo, String referencia) {
        try {
            for (Comprobante c : todas()) {
                if (c.getTipo().equals(tipo) && c.getReferencia().equals(referencia)) {
                    return c;
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer el libro de facturas: " + e.getMessage());
        }
        return null;
    }

    public Resumen resumen() {
        Resumen r = new Resumen();
        try {
            for (Comprobante c : todas()) {
                r.facturas++;
                switch (c.getTipo()) {
                    case Comprobante.PASAJE: r.ventasPasajes += c.getTotal(); break;
                    case Comprobante.RECARGA: r.recargasAcreditadas += c.getTotal(); break;
                    case Comprobante.LIQUIDACION: r.pagadoConductores += c.getTotal(); break;
                    case Comprobante.REEMBOLSO: r.devoluciones += c.getTotal(); break;
                    default: break;
                }
            }
            for (Liquidacion l : new ControladorLiquidaciones().todasInterno()) {
                if (Liquidacion.PENDIENTE.equals(l.getEstado())) {
                    r.porPagarConductores += l.getMonto();
                }
                if (Liquidacion.PROGRAMADA.equals(l.getEstado())) {
                    r.programadoConductores += l.getMonto();
                }
                if (Liquidacion.PENDIENTE.equals(l.getEstado()) || Liquidacion.PAGADA.equals(l.getEstado())) {
                    r.margenPlataforma += l.getComisionPlataforma();
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo calcular el resumen: " + e.getMessage());
        }
        r.ventasPasajes = Math.round(r.ventasPasajes * 100) / 100.0;
        r.recargasAcreditadas = Math.round(r.recargasAcreditadas * 100) / 100.0;
        r.pagadoConductores = Math.round(r.pagadoConductores * 100) / 100.0;
        r.porPagarConductores = Math.round(r.porPagarConductores * 100) / 100.0;
        r.margenPlataforma = Math.round(r.margenPlataforma * 100) / 100.0;
        r.devoluciones = Math.round(r.devoluciones * 100) / 100.0;
        r.programadoConductores = Math.round(r.programadoConductores * 100) / 100.0;
        return r;
    }
}
