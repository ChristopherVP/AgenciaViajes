package control;

import excepciones.FachadaException;
import fachadas.FachadaArchivos;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import objetosNegocio.Conductor;
import objetosNegocio.Ruta;
import objetosNegocio.Viaje;

/**
 * Los viajes ocurren en tiempo real. A la hora de salida el bus empieza a avanzar (su posición se calcula con la hora actual
 * entre la salida y la llegada) y al llegar el viaje se cierra solo: el pago del conductor se libera y la venta ya está cerrada
 * desde antes de la salida. {@link #avanzar()} aplica esos cambios de estado y se llama cada pocos segundos desde la aplicación.
 */
public class ControladorSimulacion {
    public static final String POR_ACEPTAR = "Por aceptar";
    public static final String PROGRAMADO = "Programado";
    public static final String EN_CURSO = "En Curso";
    public static final String FINALIZADO = "Finalizado";
    public static final String COMPLETADO = "Completado";
    public static final String CANCELADO = "Cancelado";
    /** Descanso mínimo del bus y del conductor entre el fin de un viaje y la salida del siguiente. */
    public static final int MINUTOS_DESCANSO = 30;

    private final FachadaArchivos fachada = FachadaArchivos.getInstance();

    /** Dónde está y hacia dónde puede ir un conductor; lo ve el administrador para organizar los viajes. */
    public static class Ubicacion {
        public String idConductor;
        public String nombre;
        public String placaBus = "";
        /** Qué está pasando ahora: En ruta, Disponible, Con viaje por aceptar, Sin bus ni ruta... */
        public String situacion = "";
        /** Texto para mostrar: "En ruta Quito → Riobamba (62 %)" o "En Riobamba (llegó 14/11 16:30)". */
        public String dondeEsta = "";
        /** Ciudad donde estará cuando termine todo lo que ya tiene asignado: desde ahí debe salir el siguiente viaje. */
        public String proximoOrigen = "";
        /** Último destino al que lo mandó la administración. */
        public String ultimoDestino = "";
        /** Su último viaje asignado: "Quito → Loja (llega 16/11/2026 09:00)". */
        public String ultimoViaje = "—";
        /** Hora más temprana a la que puede salir el siguiente viaje (después de terminar el actual y descansar). */
        public LocalDateTime disponibleDesde = LocalDateTime.now();
        public String idViajeActual = "";
        public String origenActual = "";
        public String destinoActual = "";
        public double progreso;
        public int porAceptar;
        public int programados;
        public boolean tieneAsignacion;
    }

    // ------------------------------------------------------------------ tiempo

    /** Avance del viaje entre 0 (no ha salido) y 1 (llegó), según la hora real. */
    public static double progreso(Viaje v, LocalDateTime ahora) {
        LocalDateTime salida = ControladorCompras.salidaDe(v);
        LocalDateTime llegada = ControladorCompras.llegadaDe(v);
        if (salida == null || llegada == null || !ahora.isAfter(salida)) {
            return 0;
        }
        if (!ahora.isBefore(llegada)) {
            return 1;
        }
        double total = Duration.between(salida, llegada).toSeconds();
        return total <= 0 ? 1 : Duration.between(salida, ahora).toSeconds() / total;
    }

    /** Fase real del viaje según la hora (no solo lo guardado): sirve para mostrarla al instante, sin esperar al siguiente ciclo. */
    public static String faseDe(Viaje v, LocalDateTime ahora) {
        String estado = v.getEstado() == null ? "" : v.getEstado().trim();
        if (estado.equalsIgnoreCase(POR_ACEPTAR) || estado.equalsIgnoreCase(CANCELADO) || estado.equalsIgnoreCase(FINALIZADO) || estado.equalsIgnoreCase(COMPLETADO)) {
            return estado;
        }
        LocalDateTime salida = ControladorCompras.salidaDe(v);
        LocalDateTime llegada = ControladorCompras.llegadaDe(v);
        if (salida != null && llegada != null) {
            if (!ahora.isBefore(llegada)) {
                return FINALIZADO;
            }
            if (!ahora.isBefore(salida)) {
                return EN_CURSO;
            }
        }
        return estado.isEmpty() ? PROGRAMADO : estado;
    }

    /** Resuelve origen/destino cuando el viaje solo trae el id de su ruta. */
    public Ruta rutaDe(Viaje v) {
        try {
            if (v.getRuta() == null) {
                return null;
            }
            if (v.getRuta().getOrigen() != null && v.getRuta().getDestino() != null) {
                return v.getRuta();
            }
            return fachada.obten(v.getRuta());
        } catch (FachadaException e) {
            return v.getRuta();
        }
    }

    // ------------------------------------------------------------------ cambios de estado

    /** Aplica los cambios que ya tocan por la hora. Devuelve cuántos viajes cambiaron. */
    public synchronized int avanzar() {
        int cambios = 0;
        LocalDateTime ahora = LocalDateTime.now();
        ControladorLiquidaciones liquidaciones = new ControladorLiquidaciones();
        try {
            for (Viaje v : fachada.consultaViajes()) {
                String estado = v.getEstado() == null ? "" : v.getEstado().trim();
                LocalDateTime salida = ControladorCompras.salidaDe(v);
                LocalDateTime llegada = ControladorCompras.llegadaDe(v);
                if (salida == null || llegada == null) {
                    continue;
                }
                if (estado.equalsIgnoreCase(POR_ACEPTAR)) {
                    if (!ahora.isBefore(salida)) {
                        v.setEstado(CANCELADO);
                        fachada.actualiza(v);
                        String ruta = new ControladorCompras().describirRuta(v);
                        ControladorNotificaciones avisos = new ControladorNotificaciones();
                        avisos.enviar(ControladorNotificaciones.ADMIN, "Viaje vencido sin aceptar",
                                "El viaje " + v.getIdViaje() + " (" + ruta + ") se canceló porque el conductor no lo aceptó antes de la salida.", v.getIdViaje());
                        if (v.getConductor1() != null) {
                            avisos.enviar(v.getConductor1().getIdConductor(), "Viaje vencido",
                                    "El viaje " + v.getIdViaje() + " (" + ruta + ") se canceló porque no lo aceptaste antes de la salida.", v.getIdViaje());
                        }
                        cambios++;
                    }
                } else if (estado.equalsIgnoreCase(PROGRAMADO)) {
                    liquidaciones.programar(v);
                    if (!ahora.isBefore(llegada)) {
                        if (liquidaciones.finalizarViaje(v)) {
                            cambios++;
                        }
                    } else if (!ahora.isBefore(salida)) {
                        v.setEstado(EN_CURSO);
                        fachada.actualiza(v);
                        cambios++;
                    }
                } else if (estado.equalsIgnoreCase(EN_CURSO) && !ahora.isBefore(llegada)) {
                    if (liquidaciones.finalizarViaje(v)) {
                        cambios++;
                    }
                }
            }
        } catch (FachadaException e) {
            System.err.println("No se pudo avanzar el estado de los viajes: " + e.getMessage());
        }
        return cambios;
    }

    // ------------------------------------------------------------------ ubicación del conductor

    public Ubicacion ubicacionDe(Conductor conductor) {
        Ubicacion u = new Ubicacion();
        u.idConductor = conductor.getIdConductor();
        u.nombre = conductor.getNombre();
        LocalDateTime ahora = LocalDateTime.now();
        ControladorAsignaciones.Asignacion asignacion = new ControladorAsignaciones().obtener(conductor.getIdConductor());
        Ruta rutaBase = null;
        try {
            if (asignacion != null) {
                u.tieneAsignacion = true;
                objetosNegocio.Bus bus = fachada.obten(new objetosNegocio.Bus(asignacion.getIdBus()));
                u.placaBus = bus == null ? asignacion.getIdBus() : bus.getPlaca();
                rutaBase = fachada.obten(new Ruta(asignacion.getIdRuta()));
            }
            List<Viaje> suyos = new ArrayList<>();
            for (Viaje v : fachada.consultaViajes()) {
                String estado = v.getEstado() == null ? "" : v.getEstado().trim();
                if (ControladorNovedades.esConductorDelViaje(v, conductor.getIdConductor()) && !estado.equalsIgnoreCase(CANCELADO)
                        && ControladorCompras.salidaDe(v) != null) {
                    suyos.add(v);
                }
            }
            suyos.sort(Comparator.comparing(ControladorCompras::llegadaDe));
            Viaje enRuta = null;
            Viaje ultimoTerminado = null;
            Viaje ultimoAsignado = suyos.isEmpty() ? null : suyos.get(suyos.size() - 1);
            for (Viaje v : suyos) {
                String fase = faseDe(v, ahora);
                if (fase.equalsIgnoreCase(POR_ACEPTAR)) {
                    u.porAceptar++;
                } else if (fase.equalsIgnoreCase(PROGRAMADO)) {
                    u.programados++;
                } else if (fase.equalsIgnoreCase(EN_CURSO)) {
                    enRuta = v;
                } else if (fase.equalsIgnoreCase(FINALIZADO) || fase.equalsIgnoreCase(COMPLETADO)) {
                    ultimoTerminado = v;
                }
            }
            if (!u.tieneAsignacion) {
                u.situacion = "Sin bus ni ruta asignados";
                u.dondeEsta = "—";
                u.proximoOrigen = "";
                return u;
            }
            if (enRuta != null) {
                Ruta r = rutaDe(enRuta);
                u.idViajeActual = enRuta.getIdViaje();
                u.origenActual = r == null ? "?" : r.getOrigen();
                u.destinoActual = r == null ? "?" : r.getDestino();
                u.progreso = progreso(enRuta, ahora);
                u.situacion = "En ruta";
                u.dondeEsta = String.format("En ruta %s → %s (%d %%)", u.origenActual, u.destinoActual, Math.round(u.progreso * 100));
            } else if (ultimoTerminado != null) {
                Ruta r = rutaDe(ultimoTerminado);
                u.situacion = u.porAceptar > 0 ? "Con viaje por aceptar" : (u.programados > 0 ? "Con viaje programado" : "Disponible");
                u.dondeEsta = "En " + (r == null ? "?" : r.getDestino()) + " (llegó " + ControladorCompras.llegadaDe(ultimoTerminado).format(ControladorLiquidaciones.FECHA) + ")";
            } else {
                u.situacion = u.porAceptar > 0 ? "Con viaje por aceptar" : (u.programados > 0 ? "Con viaje programado" : "Disponible");
                u.dondeEsta = rutaBase == null ? "—" : "En " + rutaBase.getOrigen() + " (base, aún no sale)";
            }
            if (ultimoAsignado != null) {
                Ruta r = rutaDe(ultimoAsignado);
                u.proximoOrigen = r == null ? "" : r.getDestino();
                u.ultimoDestino = u.proximoOrigen;
                u.ultimoViaje = (r == null ? "?" : r.getOrigen() + " → " + r.getDestino()) + " (llega " + ControladorCompras.llegadaDe(ultimoAsignado).format(ControladorLiquidaciones.FECHA) + ")";
                LocalDateTime libre = ControladorCompras.llegadaDe(ultimoAsignado).plusMinutes(MINUTOS_DESCANSO);
                u.disponibleDesde = libre.isAfter(ahora) ? libre : ahora;
            } else {
                u.proximoOrigen = rutaBase == null ? "" : rutaBase.getOrigen();
                u.ultimoDestino = "—";
                u.disponibleDesde = ahora;
            }
        } catch (FachadaException e) {
            u.situacion = "Error";
            u.dondeEsta = e.getMessage();
        }
        return u;
    }
}
