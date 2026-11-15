package control;

import excepciones.FachadaException;
import fachadas.FachadaArchivos;
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
 * Pago a los conductores (80 % de lo recaudado en pasajes; el 20 % es el margen de la plataforma).
 * <ol>
 *   <li>El conductor ACEPTA el viaje: aparece en "Pagos a conductores" como PROGRAMADA (todavía no se puede pagar).</li>
 *   <li>El bus llega a su destino (termina la simulación en tiempo real): pasa a PENDIENTE con el monto definitivo.</li>
 *   <li>Finanzas la paga a la tarjeta de débito del conductor: PAGADA + factura.</li>
 *   <li>Si el administrador acepta que el conductor no haga el viaje: CANCELADA y sale de las listas.</li>
 * </ol>
 */
public class ControladorLiquidaciones {
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("liquidaciones.dat");
    public static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final FachadaArchivos fachada = FachadaArchivos.getInstance();

    List<Liquidacion> todasInterno() throws IOException {
        List<Liquidacion> lista = new ArrayList<>();
        for (String[] f : ALMACEN.leer()) {
            lista.add(Liquidacion.deFila(f));
        }
        return lista;
    }

    private static void guardar(List<Liquidacion> lista) throws IOException {
        List<String[]> filas = new ArrayList<>();
        for (Liquidacion l : lista) {
            filas.add(l.aFila());
        }
        ALMACEN.reescribir(filas);
    }

    /** Cuándo sale el viaje. */
    public static LocalDateTime salidaDe(Viaje v) {
        return ControladorCompras.salidaDe(v);
    }

    /** Pasajes que cuentan como ingreso: los pagados y no cancelados. Devuelve {recaudado, pasajeros}. */
    public double[] recaudoDe(String idViaje) throws FachadaException {
        double recaudado = 0;
        int pasajeros = 0;
        for (Reserva r : fachada.consultaReservasPorViaje(idViaje)) {
            String e = r.getEstado() == null ? "" : r.getEstado().toUpperCase();
            if (e.equals("CONFIRMADA") || e.equals("USADA") || e.equals("ABORDADO") || e.equals("COMPLETADA")) {
                recaudado += r.getPrecioTotal();
                pasajeros += r.getCantidadAsientos();
            }
        }
        return new double[]{Math.round(recaudado * 100) / 100.0, pasajeros};
    }

    private static List<String> conductoresDe(Viaje viaje) {
        List<String> ids = new ArrayList<>();
        if (viaje.getConductor1() != null && viaje.getConductor1().getIdConductor() != null && !viaje.getConductor1().getIdConductor().isEmpty()) {
            ids.add(viaje.getConductor1().getIdConductor());
        }
        if (viaje.getConductor2() != null && viaje.getConductor2().getIdConductor() != null && !viaje.getConductor2().getIdConductor().isEmpty()) {
            ids.add(viaje.getConductor2().getIdConductor());
        }
        return ids;
    }

    private Liquidacion nueva(Viaje viaje, String idConductor, int partes, int numero, String estado) throws FachadaException, excepciones.PersistenciaException {
        double[] rec = recaudoDe(viaje.getIdViaje());
        Conductor c = new Conductores("conductores.dat").obten(new Conductor(idConductor));
        objetosNegocio.Empresa emp = viaje.getEmpresa() == null ? null : fachada.obten(viaje.getEmpresa());
        LocalDateTime llegada = ControladorCompras.llegadaDe(viaje);
        Liquidacion l = new Liquidacion();
        l.setIdLiquidacion(String.format("LIQ%05d", numero));
        l.setIdViaje(viaje.getIdViaje());
        l.setIdConductor(idConductor);
        l.setNombreConductor(c == null ? idConductor : c.getNombre());
        l.setEmpresa(emp == null ? (viaje.getEmpresa() == null ? "" : viaje.getEmpresa().getIdEmpresa()) : emp.getNombre());
        l.setRuta(new ControladorCompras().describirRuta(viaje));
        LocalDateTime salida = salidaDe(viaje);
        l.setFechaViaje(salida == null ? "" : salida.format(FECHA));
        l.setLlegadaViaje(llegada == null ? "" : llegada.format(FECHA));
        l.setPasajeros((int) rec[1]);
        l.setRecaudado(rec[0]);
        l.setPartes(partes);
        l.setMonto(rec[0] * Liquidacion.PORCENTAJE_CONDUCTOR / 100.0 / partes);
        l.setEstado(estado);
        l.setNotificadoEn(LocalDateTime.now());
        TarjetaGuardada t = new ControladorTarjetas().obtenerTarjetaCobro(idConductor);
        l.setTarjeta(t == null ? "SIN TARJETA REGISTRADA" : t.resumen());
        return l;
    }

    // ------------------------------------------------------------------ ciclo de vida

    /** El conductor aceptó el viaje: queda en la lista de pagos como PROGRAMADA. No hace nada si ya existe. */
    public synchronized void programar(Viaje viaje) {
        try {
            List<Liquidacion> todas = todasInterno();
            List<String> conductores = conductoresDe(viaje);
            int numero = todas.size();
            boolean cambio = false;
            for (String idC : conductores) {
                boolean existe = todas.stream().anyMatch(l -> l.getIdViaje().equals(viaje.getIdViaje()) && l.getIdConductor().equals(idC) && l.estaVigente());
                if (!existe) {
                    todas.add(nueva(viaje, idC, conductores.size(), ++numero, Liquidacion.PROGRAMADA));
                    cambio = true;
                }
            }
            if (cambio) {
                guardar(todas);
            }
        } catch (FachadaException | IOException | excepciones.PersistenciaException e) {
            System.err.println("No se pudo programar el pago del viaje " + viaje.getIdViaje() + ": " + e.getMessage());
        }
    }

    /**
     * El bus llegó a su destino: el pago pasa a PENDIENTE con el monto definitivo y el viaje a Finalizado. Si no tuvo pasajeros
     * no hay nada que pagar y el viaje queda Completado. Devuelve true si cambió algo.
     */
    public synchronized boolean finalizarViaje(Viaje viaje) {
        try {
            double[] rec = recaudoDe(viaje.getIdViaje());
            List<Liquidacion> todas = todasInterno();
            List<String> conductores = conductoresDe(viaje);
            int numero = todas.size();
            for (String idC : conductores) {
                boolean existe = todas.stream().anyMatch(l -> l.getIdViaje().equals(viaje.getIdViaje()) && l.getIdConductor().equals(idC) && l.estaVigente());
                if (!existe) {
                    todas.add(nueva(viaje, idC, conductores.size(), ++numero, Liquidacion.PROGRAMADA));
                }
            }
            for (Reserva r : fachada.consultaReservasPorViaje(viaje.getIdViaje())) {
                String e = r.getEstado() == null ? "" : r.getEstado().toUpperCase();
                if (e.equals("CONFIRMADA") || e.equals("ABORDADO")) {
                    r.setEstado("COMPLETADA");
                    fachada.actualiza(r);
                }
            }
            if (rec[0] <= 0) {
                todas.removeIf(l -> l.getIdViaje().equals(viaje.getIdViaje()) && Liquidacion.PROGRAMADA.equals(l.getEstado()));
                guardar(todas);
                viaje.setEstado("Completado");
                fachada.actualiza(viaje);
                return true;
            }
            ControladorNotificaciones avisos = new ControladorNotificaciones();
            for (Liquidacion l : todas) {
                if (l.getIdViaje().equals(viaje.getIdViaje()) && Liquidacion.PROGRAMADA.equals(l.getEstado())) {
                    l.setPasajeros((int) rec[1]);
                    l.setRecaudado(rec[0]);
                    l.setPartes(conductores.size());
                    l.setMonto(rec[0] * Liquidacion.PORCENTAJE_CONDUCTOR / 100.0 / conductores.size());
                    l.setEstado(Liquidacion.PENDIENTE);
                    l.setNotificadoEn(LocalDateTime.now());
                    TarjetaGuardada t = new ControladorTarjetas().obtenerTarjetaCobro(l.getIdConductor());
                    l.setTarjeta(t == null ? "SIN TARJETA REGISTRADA" : t.resumen());
                    avisos.enviar(l.getIdConductor(), "Viaje finalizado",
                            String.format("Llegaste a destino en el viaje %s (%s). Tu pago de $%.2f quedó pendiente: finanzas lo depositará en tu tarjeta.",
                                    viaje.getIdViaje(), l.getRuta(), l.getMonto()), viaje.getIdViaje());
                }
            }
            guardar(todas);
            viaje.setEstado("Finalizado");
            fachada.actualiza(viaje);
            return true;
        } catch (FachadaException | IOException | excepciones.PersistenciaException e) {
            System.err.println("No se pudo finalizar el viaje " + viaje.getIdViaje() + ": " + e.getMessage());
            return false;
        }
    }

    /** El viaje no se hará: sus pagos salen de la lista (CANCELADA). */
    public synchronized void cancelarDeViaje(String idViaje) {
        try {
            List<Liquidacion> todas = todasInterno();
            boolean cambio = false;
            for (Liquidacion l : todas) {
                if (l.getIdViaje().equals(idViaje) && !Liquidacion.PAGADA.equals(l.getEstado()) && !Liquidacion.CANCELADA.equals(l.getEstado())) {
                    l.setEstado(Liquidacion.CANCELADA);
                    cambio = true;
                }
            }
            if (cambio) {
                guardar(todas);
            }
        } catch (IOException e) {
            System.err.println("No se pudo cancelar el pago del viaje " + idViaje + ": " + e.getMessage());
        }
    }

    /**
     * Ya no hay botón para notificar: el viaje termina solo cuando llega la hora de llegada. Se conserva por compatibilidad:
     * si el bus ya llegó, finaliza el viaje; si no, explica cuándo llegará.
     */
    public synchronized ResultadoOperacion notificarViajeRealizado(Usuario conductor, String idViaje) {
        if (conductor == null || conductor.getRol() != Rol.CONDUCTOR || !conductor.estaActivo()) {
            return new ResultadoOperacion(false, "Solo un conductor activo puede consultar el cierre de un viaje");
        }
        try {
            Viaje viaje = fachada.obten(new Viaje(idViaje));
            if (viaje == null || !ControladorNovedades.esConductorDelViaje(viaje, conductor.getIdPerfil())) {
                return new ResultadoOperacion(false, "Ese viaje no está asignado a ti");
            }
            LocalDateTime llegada = ControladorCompras.llegadaDe(viaje);
            if (llegada != null && LocalDateTime.now().isBefore(llegada)) {
                return new ResultadoOperacion(false, "El viaje termina solo al llegar a destino (" + llegada.format(FECHA) + "). Tu pago se libera en ese momento.");
            }
            new ControladorSimulacion().avanzar();
            return new ResultadoOperacion(true, "El viaje ya fue cerrado automáticamente. Revisa la pestaña Mis pagos.");
        } catch (FachadaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ consultas

    /** Los pagos aún programados muestran el monto con lo vendido hasta ahora. */
    private List<Liquidacion> vigentes() throws IOException {
        List<Liquidacion> lista = todasInterno().stream().filter(Liquidacion::estaVigente).collect(Collectors.toList());
        for (Liquidacion l : lista) {
            if (Liquidacion.PROGRAMADA.equals(l.getEstado())) {
                try {
                    double[] rec = recaudoDe(l.getIdViaje());
                    l.setPasajeros((int) rec[1]);
                    l.setRecaudado(rec[0]);
                    l.setMonto(rec[0] * Liquidacion.PORCENTAJE_CONDUCTOR / 100.0 / l.getPartes());
                } catch (FachadaException ignorado) {
                    // se muestran los últimos valores guardados
                }
            }
        }
        lista.sort(Comparator.comparing(ControladorLiquidaciones::clave).reversed());
        return lista;
    }

    private static String clave(Liquidacion l) {
        try {
            return LocalDateTime.parse(l.getFechaViaje(), FECHA).format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")) + l.getIdLiquidacion();
        } catch (Exception e) {
            return "0" + l.getIdLiquidacion();
        }
    }

    public ResultadoOperacion listarParaAdministrador(Usuario actor) {
        if (!Permisos.puedeVer(actor, Permisos.Modulo.PAGOS)) {
            return new ResultadoOperacion(false, "Tu cargo no tiene acceso a los pagos a conductores");
        }
        try {
            return new ResultadoOperacion(true, "OK", vigentes());
        } catch (IOException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    public ResultadoOperacion listarMias(Usuario conductor) {
        if (conductor == null || conductor.getRol() != Rol.CONDUCTOR) {
            return new ResultadoOperacion(false, "Acción no permitida");
        }
        try {
            List<Liquidacion> lista = vigentes().stream().filter(l -> l.getIdConductor().equals(conductor.getIdPerfil())).collect(Collectors.toList());
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
            if (Liquidacion.PAGADA.equals(l.getEstado())) {
                return new ResultadoOperacion(false, "Este pago ya fue realizado");
            }
            if (Liquidacion.PROGRAMADA.equals(l.getEstado())) {
                return new ResultadoOperacion(false, "El viaje todavía no termina (llega " + l.getLlegadaViaje() + "). El pago se libera cuando el bus llegue a su destino.");
            }
            if (!Liquidacion.PENDIENTE.equals(l.getEstado())) {
                return new ResultadoOperacion(false, "Este pago fue cancelado");
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
            boolean viajeCompleto = true;
            for (Liquidacion x : todas) {
                if (x.getIdViaje().equals(l.getIdViaje()) && x.estaVigente() && !Liquidacion.PAGADA.equals(x.getEstado())) {
                    viajeCompleto = false;
                }
            }
            guardar(todas);
            if (viajeCompleto) {
                Viaje v = fachada.obten(new Viaje(l.getIdViaje()));
                if (v != null) {
                    v.setEstado("Completado");
                    fachada.actualiza(v);
                }
            }
            new ControladorNotificaciones().enviar(l.getIdConductor(), "Pago recibido",
                    String.format("Finanzas te pagó $%.2f por el viaje %s (%s).", l.getMonto(), l.getIdViaje(), l.getTarjeta()), l.getIdViaje());
            return new ResultadoOperacion(true, String.format("Pago de $%.2f realizado a %s (%s).", l.getMonto(), l.getNombreConductor(), l.getTarjeta()), l);
        } catch (FachadaException | IOException e) {
            return new ResultadoOperacion(false, "No se pudo registrar el pago: " + e.getMessage());
        }
    }

    /** Añade pagos ya hechos (datos de demostración) sin pasar por las validaciones de tiempo. */
    synchronized void agregarDirecto(Liquidacion l) throws IOException {
        List<Liquidacion> todas = todasInterno();
        todas.add(l);
        guardar(todas);
    }

    /** Marca como pagada una liquidación PENDIENTE con la fecha dada y emite su factura (datos de demostración). */
    synchronized boolean pagarDirecto(String idLiquidacion, LocalDateTime cuando, String tarjeta, String pagadoPor) throws IOException {
        List<Liquidacion> todas = todasInterno();
        for (Liquidacion l : todas) {
            if (l.getIdLiquidacion().equals(idLiquidacion) && Liquidacion.PENDIENTE.equals(l.getEstado())) {
                l.setEstado(Liquidacion.PAGADA);
                l.setPagadoEn(cuando);
                l.setPagadoPor(pagadoPor);
                l.setTarjeta(tarjeta);
                Comprobante c = new ControladorFacturacion().emitirLiquidacionConFecha(l, cuando);
                l.setNumeroFactura(c == null ? "" : c.getNumero());
                guardar(todas);
                return true;
            }
        }
        return false;
    }

    int siguienteNumero() throws IOException {
        return ALMACEN.leer().size() + 1;
    }
}
