package control;

import fachadas.FachadaArchivos;
import excepciones.FachadaException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import objetosNegocio.Comprobante;
import objetosNegocio.Conductor;
import objetosNegocio.Liquidacion;
import objetosNegocio.Reserva;
import objetosNegocio.Rol;
import objetosNegocio.TarjetaGuardada;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.Permisos;
import persistencia.AlmacenTexto;
import persistencia.Conductores;

/**
 * Pago a los conductores. Cuando el conductor notifica que el viaje se realizó con éxito se calcula el 80 % de lo recaudado en
 * pasajes de ese viaje (dividido entre los conductores del viaje) y queda PENDIENTE; el administrador de finanzas lo paga a
 * la tarjeta de débito registrada del conductor y se emite la factura del pago. El 20 % restante es el margen de la plataforma.
 */
public class ControladorLiquidaciones {
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("liquidaciones.dat");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FachadaArchivos fachada = FachadaArchivos.getInstance();

    List<Liquidacion> todasInterno() throws IOException {
        List<Liquidacion> lista = new ArrayList<>();
        for (String[] f : ALMACEN.leer()) {
            lista.add(Liquidacion.deFila(f));
        }
        return lista;
    }

    /** Cuándo está previsto que termine el viaje (o cuándo sale si no tiene hora de llegada). */
    public static LocalDateTime salidaDe(Viaje v) {
        return ControladorCompras.salidaDe(v);
    }

    public synchronized ResultadoOperacion notificarViajeRealizado(Usuario conductor, String idViaje) {
        if (conductor == null || conductor.getRol() != Rol.CONDUCTOR || !conductor.estaActivo()) {
            return new ResultadoOperacion(false, "Solo un conductor activo puede notificar un viaje realizado");
        }
        try {
            Viaje viaje = fachada.obten(new Viaje(idViaje));
            if (viaje == null || !ControladorNovedades.esConductorDelViaje(viaje, conductor.getIdPerfil())) {
                return new ResultadoOperacion(false, "Ese viaje no está asignado a ti");
            }
            String estado = viaje.getEstado() == null ? "" : viaje.getEstado().trim();
            if (!estado.equalsIgnoreCase("Programado") && !estado.equalsIgnoreCase("En Curso")) {
                return new ResultadoOperacion(false, "Este viaje ya está en estado \"" + estado + "\": no se puede notificar de nuevo");
            }
            LocalDateTime salida = salidaDe(viaje);
            if (salida == null || LocalDateTime.now().isBefore(salida)) {
                return new ResultadoOperacion(false, "El viaje todavía no ha salido (sale " + (salida == null ? "?" : salida.format(FECHA))
                        + "). Notifícalo cuando se haya realizado.");
            }
            for (Liquidacion existente : todasInterno()) {
                if (existente.getIdViaje().equals(idViaje)) {
                    return new ResultadoOperacion(false, "Este viaje ya fue notificado");
                }
            }
            double recaudado = 0;
            int pasajeros = 0;
            for (Reserva r : fachada.consultaReservasPorViaje(idViaje)) {
                String e = r.getEstado() == null ? "" : r.getEstado();
                if (e.equalsIgnoreCase("Confirmada") || e.equalsIgnoreCase("Usada")) {
                    recaudado += r.getPrecioTotal();
                    pasajeros += r.getCantidadAsientos();
                }
            }
            if (recaudado <= 0) {
                viaje.setEstado("Completado");
                fachada.actualiza(viaje);
                return new ResultadoOperacion(true, "Viaje registrado como realizado. No tuvo pasajeros, así que no genera pago.");
            }
            List<String> conductores = new ArrayList<>();
            conductores.add(viaje.getConductor1().getIdConductor());
            if (viaje.getConductor2() != null && viaje.getConductor2().getIdConductor() != null && !viaje.getConductor2().getIdConductor().isEmpty()) {
                conductores.add(viaje.getConductor2().getIdConductor());
            }
            String ruta = new ControladorCompras().describirRuta(viaje);
            String empresa = viaje.getEmpresa() == null ? "" : (fachada.obten(viaje.getEmpresa()) == null ? viaje.getEmpresa().getIdEmpresa() : fachada.obten(viaje.getEmpresa()).getNombre());
            ControladorTarjetas tarjetas = new ControladorTarjetas();
            int siguiente = ALMACEN.leer().size();
            Liquidacion propia = null;
            for (String idC : conductores) {
                Conductor c = new Conductores("conductores.dat").obten(new Conductor(idC));
                Liquidacion l = new Liquidacion();
                l.setIdLiquidacion(String.format("LIQ%05d", ++siguiente));
                l.setIdViaje(idViaje);
                l.setIdConductor(idC);
                l.setNombreConductor(c == null ? idC : c.getNombre());
                l.setEmpresa(empresa);
                l.setRuta(ruta);
                l.setFechaViaje(salida.format(FECHA));
                l.setPasajeros(pasajeros);
                l.setRecaudado(recaudado);
                l.setPartes(conductores.size());
                l.setMonto(recaudado * Liquidacion.PORCENTAJE_CONDUCTOR / 100.0 / conductores.size());
                l.setNotificadoEn(LocalDateTime.now());
                TarjetaGuardada t = tarjetas.obtenerTarjetaCobro(idC);
                l.setTarjeta(t == null ? "SIN TARJETA REGISTRADA" : t.resumen());
                ALMACEN.agregar(l.aFila());
                if (idC.equals(conductor.getIdPerfil())) {
                    propia = l;
                }
            }
            viaje.setEstado("Finalizado");
            fachada.actualiza(viaje);
            return new ResultadoOperacion(true, String.format("Viaje notificado. Se generó tu pago de $%.2f (80 %% de lo recaudado); el administrador de finanzas lo pagará a tu tarjeta.",
                    propia == null ? 0 : propia.getMonto()), propia);
        } catch (FachadaException | IOException | excepciones.PersistenciaException e) {
            return new ResultadoOperacion(false, "No se pudo notificar el viaje: " + e.getMessage());
        }
    }

    public ResultadoOperacion listarParaAdministrador(Usuario actor) {
        if (!Permisos.puedeVer(actor, Permisos.Modulo.PAGOS)) {
            return new ResultadoOperacion(false, "Tu cargo no tiene acceso a los pagos a conductores");
        }
        try {
            List<Liquidacion> lista = todasInterno();
            lista.sort(Comparator.comparing(Liquidacion::getIdLiquidacion).reversed());
            return new ResultadoOperacion(true, "OK", lista);
        } catch (IOException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    public ResultadoOperacion listarMias(Usuario conductor) {
        if (conductor == null || conductor.getRol() != Rol.CONDUCTOR) {
            return new ResultadoOperacion(false, "Acción no permitida");
        }
        try {
            List<Liquidacion> lista = todasInterno().stream().filter(l -> l.getIdConductor().equals(conductor.getIdPerfil()))
                    .sorted(Comparator.comparing(Liquidacion::getIdLiquidacion).reversed()).collect(Collectors.toList());
            return new ResultadoOperacion(true, "OK", lista);
        } catch (IOException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    public synchronized ResultadoOperacion pagar(Usuario actor, String idLiquidacion) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.PAGOS)) {
            return new ResultadoOperacion(false, "Solo el personal de finanzas puede pagar a los conductores");
        }
        try {
            List<Liquidacion> todas = todasInterno();
            Liquidacion l = todas.stream().filter(x -> x.getIdLiquidacion().equals(idLiquidacion)).findFirst().orElse(null);
            if (l == null) {
                return new ResultadoOperacion(false, "Pago no encontrado");
            }
            if (!Liquidacion.PENDIENTE.equals(l.getEstado())) {
                return new ResultadoOperacion(false, "Este pago ya fue realizado");
            }
            TarjetaGuardada t = new ControladorTarjetas().obtenerTarjetaCobro(l.getIdConductor());
            if (t == null) {
                return new ResultadoOperacion(false, l.getNombreConductor() + " no tiene una tarjeta de débito registrada: no se puede pagar");
            }
            l.setTarjeta(t.resumen());
            l.setEstado(Liquidacion.PAGADA);
            l.setPagadoEn(LocalDateTime.now());
            l.setPagadoPor(actor.getLogin());
            Comprobante factura = new ControladorFacturacion().emitirLiquidacion(l);
            l.setNumeroFactura(factura == null ? "" : factura.getNumero());
            List<String[]> filas = new ArrayList<>();
            boolean viajeCompleto = true;
            for (Liquidacion x : todas) {
                Liquidacion actual = x.getIdLiquidacion().equals(idLiquidacion) ? l : x;
                if (actual.getIdViaje().equals(l.getIdViaje()) && !Liquidacion.PAGADA.equals(actual.getEstado())) {
                    viajeCompleto = false;
                }
                filas.add(actual.aFila());
            }
            ALMACEN.reescribir(filas);
            if (viajeCompleto) {
                Viaje v = fachada.obten(new Viaje(l.getIdViaje()));
                if (v != null) {
                    v.setEstado("Completado");
                    fachada.actualiza(v);
                }
            }
            return new ResultadoOperacion(true, String.format("Pago de $%.2f realizado a %s (%s).", l.getMonto(), l.getNombreConductor(), l.getTarjeta()), l);
        } catch (FachadaException | IOException e) {
            return new ResultadoOperacion(false, "No se pudo registrar el pago: " + e.getMessage());
        }
    }
}
