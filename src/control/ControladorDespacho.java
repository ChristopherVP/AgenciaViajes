package control;

import excepciones.FachadaException;
import excepciones.PersistenciaException;
import fachadas.FachadaArchivos;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import objetosNegocio.Asiento;
import objetosNegocio.Bus;
import objetosNegocio.Conductor;
import objetosServicio.Fecha;
import objetosNegocio.Novedad;
import objetosNegocio.Rol;
import objetosNegocio.Ruta;
import objetosNegocio.TarjetaGuardada;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.Permisos;
import persistencia.Conductores;
import persistencia.Novedades;
import persistencia.Rutas;

/**
 * Organización de los viajes: la administración ve dónde está cada conductor y le asigna el siguiente viaje DESDE donde
 * terminará el anterior. El viaje nace "Por aceptar" (nadie lo puede comprar todavía); el conductor lo acepta, con tarjeta
 * de cobro registrada y vigente, y recién entonces sale a la venta y aparece en los pagos pendientes de finalizar.
 */
public class ControladorDespacho {
    /** Mínimo de anticipación con la que se puede asignar un viaje, para que el conductor alcance a aceptarlo. */
    public static final int MINUTOS_MINIMOS_ANTES = 15;

    private final FachadaArchivos fachada = FachadaArchivos.getInstance();
    private final ControladorSimulacion simulacion = new ControladorSimulacion();
    private final ControladorNotificaciones avisos = new ControladorNotificaciones();

    private static String norm(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").trim().toUpperCase();
    }

    // ------------------------------------------------------------------ vista de la flota

    /** Todos los conductores activos con su ubicación actual y el punto desde el que debe salir su próximo viaje. */
    public ResultadoOperacion flota(Usuario actor) {
        if (!Permisos.puedeVer(actor, Permisos.Modulo.CONDUCTORES) && !Permisos.puedeVer(actor, Permisos.Modulo.VIAJES)) {
            return new ResultadoOperacion(false, "Tu cargo no tiene acceso a la flota");
        }
        try {
            List<ControladorSimulacion.Ubicacion> lista = new ArrayList<>();
            for (Conductor c : new Conductores("conductores.dat").lista()) {
                if (!"ACTIVO".equalsIgnoreCase(c.getEstado())) {
                    continue;
                }
                if (actor.tieneEmpresa() && (c.getEmpresa() == null || !c.getEmpresa().getIdEmpresa().equals(actor.getEmpresa().getIdEmpresa()))) {
                    continue;
                }
                lista.add(simulacion.ubicacionDe(c));
            }
            lista.sort(Comparator.comparing((ControladorSimulacion.Ubicacion u) -> u.nombre));
            return new ResultadoOperacion(true, "OK", lista);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    // ------------------------------------------------------------------ rutas de salida

    /**
     * Rutas de la empresa que salen de {@code origen}. Si hay rutas que LLEGAN a ese punto y todavía no existe la de regreso,
     * se crea (Quito→Riobamba genera Riobamba→Quito) para que el conductor pueda volver o seguir viaje.
     */
    public List<Ruta> rutasDesde(String origen, String idEmpresa) {
        List<Ruta> salidas = new ArrayList<>();
        try {
            List<Ruta> todas = fachada.consultaRutas();
            Rutas dao = new Rutas("rutas.dat");
            for (Ruta r : new ArrayList<>(todas)) {
                if (r.getEmpresa() == null || !r.getEmpresa().getIdEmpresa().equals(idEmpresa) || !"ACTIVA".equalsIgnoreCase(r.getEstado())
                        || !norm(r.getDestino()).equals(norm(origen))) {
                    continue;
                }
                boolean existeInversa = todas.stream().anyMatch(x -> x.getEmpresa() != null && x.getEmpresa().getIdEmpresa().equals(idEmpresa)
                        && norm(x.getOrigen()).equals(norm(r.getDestino())) && norm(x.getDestino()).equals(norm(r.getOrigen())));
                if (!existeInversa) {
                    Ruta inversa = new Ruta("", r.getDestino(), r.getOrigen(), r.getDistancia(), r.getPrecio(), r.getEmpresa(), r.getTiempoEstimado(),
                            invertir(r.getParadas()), r.getTiempoDetencion(), r.getAdvertencias(), "ACTIVA");
                    dao.agrega(inversa);
                    todas.add(inversa);
                }
            }
            for (Ruta r : todas) {
                if (r.getEmpresa() != null && r.getEmpresa().getIdEmpresa().equals(idEmpresa) && "ACTIVA".equalsIgnoreCase(r.getEstado()) && norm(r.getOrigen()).equals(norm(origen))) {
                    salidas.add(r);
                }
            }
            salidas.sort(Comparator.comparing(Ruta::getDestino));
        } catch (FachadaException | PersistenciaException e) {
            System.err.println("No se pudieron consultar las rutas: " + e.getMessage());
        }
        return salidas;
    }

    private static String invertir(String paradas) {
        if (paradas == null || paradas.isBlank()) {
            return "";
        }
        List<String> p = new ArrayList<>(List.of(paradas.split("\\s*,\\s*")));
        java.util.Collections.reverse(p);
        return String.join(", ", p);
    }

    // ------------------------------------------------------------------ asignación

    public ResultadoOperacion asignarViaje(Usuario actor, String idConductor, String idRuta, LocalDateTime salida) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.VIAJES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede asignar viajes");
        }
        if (salida == null || idRuta == null || idRuta.isBlank()) {
            return new ResultadoOperacion(false, "Elige el destino y la fecha y hora de salida");
        }
        try {
            Conductor c = new Conductores("conductores.dat").obten(new Conductor(idConductor));
            if (c == null || !"ACTIVO".equalsIgnoreCase(c.getEstado())) {
                return new ResultadoOperacion(false, "El conductor no existe o no está activo");
            }
            ControladorAsignaciones.Asignacion asig = new ControladorAsignaciones().obtener(idConductor);
            if (asig == null) {
                return new ResultadoOperacion(false, c.getNombre() + " no tiene bus asignado. Asígnalo primero en la pestaña Conductores.");
            }
            ControladorSimulacion.Ubicacion u = simulacion.ubicacionDe(c);
            Ruta ruta = fachada.obten(new Ruta(idRuta));
            Bus bus = fachada.obten(new Bus(asig.getIdBus()));
            if (ruta == null || bus == null) {
                return new ResultadoOperacion(false, "La ruta o el bus ya no existen");
            }
            if (!norm(ruta.getOrigen()).equals(norm(u.proximoOrigen))) {
                return new ResultadoOperacion(false, c.getNombre() + " estará en " + u.proximoOrigen + ": el viaje debe salir de ahí, no de " + ruta.getOrigen());
            }
            if (bus.getEmpresa() != null && ruta.getEmpresa() != null && !bus.getEmpresa().getIdEmpresa().equals(ruta.getEmpresa().getIdEmpresa())) {
                return new ResultadoOperacion(false, "El bus y la ruta deben ser de la misma empresa");
            }
            if (salida.isBefore(LocalDateTime.now().plusMinutes(MINUTOS_MINIMOS_ANTES))) {
                return new ResultadoOperacion(false, "La salida debe ser al menos " + MINUTOS_MINIMOS_ANTES + " minutos desde ahora para que el conductor alcance a aceptarla");
            }
            if (salida.isBefore(u.disponibleDesde)) {
                return new ResultadoOperacion(false, c.getNombre() + " recién queda libre el " + u.disponibleDesde.format(ControladorLiquidaciones.FECHA)
                        + " (termina su viaje actual y descansa " + ControladorSimulacion.MINUTOS_DESCANSO + " min)");
            }
            LocalDateTime llegada = salida.plusMinutes(Math.round((ruta.getTiempoEstimado() > 0 ? ruta.getTiempoEstimado() : 3) * 60));
            ResultadoOperacion r = new ControladorViajes().programarViaje("", bus.getIdBus(), ruta.getIdRuta(),
                    new Fecha(salida.getDayOfMonth(), salida.getMonthValue(), salida.getYear()),
                    new Fecha(llegada.getDayOfMonth(), llegada.getMonthValue(), llegada.getYear()),
                    ruta.getEmpresa().getIdEmpresa(), ControladorSimulacion.POR_ACEPTAR, true,
                    String.format("%02d:%02d", salida.getHour(), salida.getMinute()), String.format("%02d:%02d", llegada.getHour(), llegada.getMinute()),
                    ruta.getPrecio(), idConductor, "");
            if (!r.isExito()) {
                return r;
            }
            Viaje v = (Viaje) r.getDatos();
            avisos.enviar(idConductor, "Nuevo viaje asignado",
                    String.format("Te asignaron el viaje %s: %s → %s, sale el %s y llega el %s. Entra a Mis viajes y acéptalo (necesitas tu tarjeta de cobro registrada).",
                            v.getIdViaje(), ruta.getOrigen(), ruta.getDestino(), salida.format(ControladorLiquidaciones.FECHA), llegada.format(ControladorLiquidaciones.FECHA)), v.getIdViaje());
            return new ResultadoOperacion(true, "Viaje " + v.getIdViaje() + " asignado a " + c.getNombre() + ". Le llegó un aviso para que lo acepte.", v);
        } catch (FachadaException | PersistenciaException e) {
            return new ResultadoOperacion(false, "No se pudo asignar el viaje: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ decisiones del conductor

    private Viaje viajeDelConductor(Usuario conductor, String idViaje) throws FachadaException {
        Viaje v = fachada.obten(new Viaje(idViaje));
        return v != null && ControladorNovedades.esConductorDelViaje(v, conductor.getIdPerfil()) ? v : null;
    }

    private static boolean esConductor(Usuario u) {
        return u != null && u.getRol() == Rol.CONDUCTOR && u.estaActivo() && u.getIdPerfil() != null;
    }

    /** null si la tarjeta de cobro está registrada (ya pasó la validación al guardarla) y vigente; si no, el motivo. */
    public static String errorTarjeta(String idConductor) {
        TarjetaGuardada t = new ControladorTarjetas().obtenerTarjetaCobro(idConductor);
        if (t == null) {
            return "Para aceptar viajes primero registra tu tarjeta de débito en Mi perfil: ahí te depositamos tus pagos.";
        }
        try {
            String[] p = t.getVence().split("/");
            if (YearMonth.of(2000 + Integer.parseInt(p[1]), Integer.parseInt(p[0])).isBefore(YearMonth.from(LocalDate.now()))) {
                return "Tu tarjeta registrada está vencida. Actualízala en Mi perfil para poder aceptar viajes.";
            }
        } catch (RuntimeException e) {
            return "Tu tarjeta registrada no es válida. Vuelve a registrarla en Mi perfil.";
        }
        return null;
    }

    public synchronized ResultadoOperacion aceptar(Usuario conductor, String idViaje) {
        if (!esConductor(conductor)) {
            return new ResultadoOperacion(false, "Solo un conductor activo puede aceptar viajes");
        }
        try {
            Viaje v = viajeDelConductor(conductor, idViaje);
            if (v == null) {
                return new ResultadoOperacion(false, "Ese viaje no está asignado a ti");
            }
            if (!ControladorSimulacion.POR_ACEPTAR.equalsIgnoreCase(v.getEstado())) {
                return new ResultadoOperacion(false, "Este viaje ya no está esperando tu respuesta (estado: " + v.getEstado() + ")");
            }
            String tarjeta = errorTarjeta(conductor.getIdPerfil());
            if (tarjeta != null) {
                return new ResultadoOperacion(false, tarjeta);
            }
            LocalDateTime salida = ControladorCompras.salidaDe(v);
            if (salida == null || !salida.isAfter(LocalDateTime.now())) {
                return new ResultadoOperacion(false, "La hora de salida de este viaje ya pasó");
            }
            v.setEstado(ControladorSimulacion.PROGRAMADO);
            fachada.actualiza(v);
            new ControladorLiquidaciones().programar(v);
            avisos.enviar(ControladorNotificaciones.ADMIN, "Viaje aceptado",
                    conductor.getNombre() + " aceptó el viaje " + v.getIdViaje() + " (" + new ControladorCompras().describirRuta(v) + "). Ya está a la venta.", v.getIdViaje());
            return new ResultadoOperacion(true, "Viaje aceptado. Ya está a la venta y quedó en tus pagos programados; se te paga al llegar a destino.", v);
        } catch (FachadaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Rechazar un viaje que todavía no estaba aceptado: se cancela y la administración debe asignar otro. */
    public synchronized ResultadoOperacion rechazar(Usuario conductor, String idViaje) {
        if (!esConductor(conductor)) {
            return new ResultadoOperacion(false, "Solo un conductor activo puede rechazar viajes");
        }
        try {
            Viaje v = viajeDelConductor(conductor, idViaje);
            if (v == null) {
                return new ResultadoOperacion(false, "Ese viaje no está asignado a ti");
            }
            if (!ControladorSimulacion.POR_ACEPTAR.equalsIgnoreCase(v.getEstado())) {
                return new ResultadoOperacion(false, "Solo puedes rechazar viajes que aún no aceptaste. Si ya lo aceptaste, pide la cancelación al administrador.");
            }
            v.setEstado(ControladorSimulacion.CANCELADO);
            fachada.actualiza(v);
            avisos.enviar(ControladorNotificaciones.ADMIN, "Viaje rechazado",
                    conductor.getNombre() + " rechazó el viaje " + v.getIdViaje() + " (" + new ControladorCompras().describirRuta(v) + "). Asigna otro.", v.getIdViaje());
            return new ResultadoOperacion(true, "Viaje rechazado. Se avisó a la administración.", v);
        } catch (FachadaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Un viaje ya aceptado solo se cancela si el administrador lo acepta: aquí se envía la solicitud (llega a Novedades). */
    public synchronized ResultadoOperacion solicitarCancelacion(Usuario conductor, String idViaje, String motivo) {
        if (!esConductor(conductor)) {
            return new ResultadoOperacion(false, "Solo un conductor activo puede pedir la cancelación");
        }
        try {
            Viaje v = viajeDelConductor(conductor, idViaje);
            if (v == null) {
                return new ResultadoOperacion(false, "Ese viaje no está asignado a ti");
            }
            if (!ControladorSimulacion.PROGRAMADO.equalsIgnoreCase(v.getEstado())) {
                return new ResultadoOperacion(false, "Solo puedes pedir cancelar un viaje aceptado que todavía no sale");
            }
            LocalDateTime salida = ControladorCompras.salidaDe(v);
            if (salida == null || !salida.isAfter(LocalDateTime.now())) {
                return new ResultadoOperacion(false, "El bus ya salió: no se puede cancelar");
            }
            if (pendienteDeCancelacion(idViaje) != null) {
                return new ResultadoOperacion(false, "Ya enviaste una solicitud para este viaje. Espera la respuesta del administrador.");
            }
            String m = motivo == null ? "" : motivo.trim();
            if (m.length() < 10 || m.length() > 280) {
                return new ResultadoOperacion(false, "Cuenta el problema en 10 a 280 caracteres");
            }
            ResultadoOperacion r = new ControladorNovedades().reportar(conductor, idViaje, "NO_PUEDE_VIAJAR", "No puedo realizar el viaje " + idViaje, m);
            if (r.isExito()) {
                avisos.enviar(ControladorNotificaciones.ADMIN, "Solicitud de cancelación",
                        conductor.getNombre() + " pide no realizar el viaje " + idViaje + ". Revisa la pestaña Novedades.", idViaje);
                return new ResultadoOperacion(true, "Solicitud enviada. Seguirás con el viaje hasta que el administrador la acepte.", r.getDatos());
            }
            return r;
        } catch (FachadaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** La solicitud de cancelación sin resolver de ese viaje, o null. */
    public Novedad pendienteDeCancelacion(String idViaje) {
        try {
            for (Novedad n : new Novedades("novedades.dat").lista()) {
                if ("NO_PUEDE_VIAJAR".equals(n.getTipo()) && idViaje.equals(n.getIdViaje()) && !Novedad.RESUELTA.equals(n.getEstado())) {
                    return n;
                }
            }
        } catch (PersistenciaException e) {
            System.err.println("No se pudieron leer las novedades: " + e.getMessage());
        }
        return null;
    }

    // ------------------------------------------------------------------ decisiones del administrador

    public synchronized ResultadoOperacion aprobarCancelacion(Usuario actor, String idNovedad) {
        return resolverCancelacion(actor, idNovedad, true, "");
    }

    public synchronized ResultadoOperacion denegarCancelacion(Usuario actor, String idNovedad, String motivo) {
        return resolverCancelacion(actor, idNovedad, false, motivo);
    }

    private ResultadoOperacion resolverCancelacion(Usuario actor, String idNovedad, boolean aprobar, String motivo) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.VIAJES) && !Permisos.puedeEditar(actor, Permisos.Modulo.NOVEDADES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede resolver cancelaciones de viajes");
        }
        try {
            Novedades dao = new Novedades("novedades.dat");
            Novedad n = dao.obten(idNovedad);
            if (!esReporteDeViaje(n)) {
                return new ResultadoOperacion(false, "Este reporte no es una solicitud sobre un viaje");
            }
            if (Novedad.RESUELTA.equals(n.getEstado())) {
                return new ResultadoOperacion(false, "Esta solicitud ya fue resuelta");
            }
            String texto;
            if (aprobar) {
                Viaje v = fachada.obten(new Viaje(n.getIdViaje()));
                if (v != null && ControladorSimulacion.EN_CURSO.equals(ControladorSimulacion.faseDe(v, LocalDateTime.now()))) {
                    return new ResultadoOperacion(false, "El bus ya salió: el viaje no se puede cancelar");
                }
                ResultadoOperacion r = new ControladorViajes().cancelarViaje(n.getIdViaje(), "El conductor no puede realizar el viaje");
                if (!r.isExito() && v != null && !ControladorSimulacion.CANCELADO.equalsIgnoreCase(v.getEstado())) {
                    return r;
                }
                texto = "Cancelación aceptada: el viaje salió de tus pagos. " + (r.isExito() ? r.getMensaje() : "");
            } else {
                String m = motivo == null ? "" : motivo.trim();
                texto = "Cancelación denegada" + (m.isEmpty() ? "" : ": " + m) + ". Debes realizar el viaje.";
            }
            if (texto.length() > 200) {
                texto = texto.substring(0, 197) + "...";
            }
            n.setRespuesta(texto);
            n.setEstado(Novedad.RESUELTA);
            n.setIdRevisor(actor.getIdUsuario());
            dao.actualiza(n);
            avisos.enviar(n.getIdConductor(), aprobar ? "Cancelación aceptada" : "Cancelación denegada", texto, n.getIdViaje());
            return new ResultadoOperacion(true, aprobar ? "Viaje cancelado. " + texto : "Solicitud denegada.", n);
        } catch (PersistenciaException | FachadaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Reportes del conductor que afectan a un viaje concreto: "no puedo viajar" o "falla mecánica" con el viaje elegido. */
    public static boolean esReporteDeViaje(Novedad n) {
        return n != null && ("NO_PUEDE_VIAJAR".equals(n.getTipo()) || "FALLA_MECANICA".equals(n.getTipo()))
                && n.getIdViaje() != null && !n.getIdViaje().isEmpty();
    }

    /**
     * El administrador acepta el reporte y reemplaza el bus del viaje por otro de la misma empresa que esté activo, libre a esa hora,
     * sin otro conductor y con la misma capacidad (los asientos ya vendidos se conservan). Si el reporte es una falla mecánica,
     * el bus averiado queda INACTIVO. Si no hay reemplazo se devuelve error y el admin puede cancelar el viaje (con reembolso).
     */
    public synchronized ResultadoOperacion reasignarBus(Usuario actor, String idNovedad) {
        if (!Permisos.puedeEditar(actor, Permisos.Modulo.VIAJES) && !Permisos.puedeEditar(actor, Permisos.Modulo.NOVEDADES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede reasignar buses");
        }
        try {
            Novedades dao = new Novedades("novedades.dat");
            Novedad n = dao.obten(idNovedad);
            if (!esReporteDeViaje(n)) {
                return new ResultadoOperacion(false, "Este reporte no es sobre un viaje");
            }
            if (Novedad.RESUELTA.equals(n.getEstado())) {
                return new ResultadoOperacion(false, "Este reporte ya fue resuelto");
            }
            Viaje v = fachada.obten(new Viaje(n.getIdViaje()));
            LocalDateTime salida = v == null ? null : ControladorCompras.salidaDe(v);
            if (v == null || salida == null || !salida.isAfter(LocalDateTime.now())
                    || !(ControladorSimulacion.PROGRAMADO.equalsIgnoreCase(v.getEstado()) || ControladorSimulacion.POR_ACEPTAR.equalsIgnoreCase(v.getEstado()))) {
                return new ResultadoOperacion(false, "El viaje ya salió, terminó o fue cancelado: no se puede cambiar su bus");
            }
            Bus viejo = fachada.obten(new Bus(v.getBus().getIdBus()));
            String idEmpresa = v.getEmpresa() == null ? "" : v.getEmpresa().getIdEmpresa();
            ControladorAsignaciones asignaciones = new ControladorAsignaciones();
            java.util.Set<String> ocupados = new java.util.HashSet<>();
            for (ControladorAsignaciones.Asignacion a : asignaciones.listar()) {
                ocupados.add(a.getIdBus());
            }
            ControladorViajes viajes = new ControladorViajes();
            Bus nuevo = null;
            for (Bus b : fachada.consultaBuses()) {
                boolean sirve = b.getEmpresa() != null && b.getEmpresa().getIdEmpresa().equals(idEmpresa) && "ACTIVO".equalsIgnoreCase(b.getEstado())
                        && !b.getIdBus().equals(viejo.getIdBus()) && b.getCapacidad() == viejo.getCapacidad() && !ocupados.contains(b.getIdBus())
                        && viajes.verificarDisponibilidadBus(b.getIdBus(), v.getFechaSalida(), v.getFechaLlegada(), v.getHoraSalida(), v.getHoraLlegada(), v.getIdViaje()).isExito();
                if (sirve) {
                    nuevo = b;
                    break;
                }
            }
            if (nuevo == null) {
                return new ResultadoOperacion(false, "No hay otro bus activo, libre a esa hora y con " + viejo.getCapacidad()
                        + " asientos. Si no se puede reemplazar, cancela el viaje (se reembolsa a los pasajeros).");
            }
            v.setBus(nuevo);
            fachada.actualiza(v);
            String idConductor = n.getIdConductor();
            asignaciones.cambiarBus(idConductor, nuevo.getIdBus());
            int otros = 0;
            for (Viaje o : fachada.consultaViajes()) {
                LocalDateTime s = ControladorCompras.salidaDe(o);
                boolean futuro = s != null && s.isAfter(LocalDateTime.now()) && !o.getIdViaje().equals(v.getIdViaje())
                        && (ControladorSimulacion.PROGRAMADO.equalsIgnoreCase(o.getEstado()) || ControladorSimulacion.POR_ACEPTAR.equalsIgnoreCase(o.getEstado()));
                if (futuro && ControladorNovedades.esConductorDelViaje(o, idConductor) && o.getBus() != null && o.getBus().getIdBus().equals(viejo.getIdBus())
                        && viajes.verificarDisponibilidadBus(nuevo.getIdBus(), o.getFechaSalida(), o.getFechaLlegada(), o.getHoraSalida(), o.getHoraLlegada(), o.getIdViaje()).isExito()) {
                    o.setBus(nuevo);
                    fachada.actualiza(o);
                    otros++;
                }
            }
            if ("FALLA_MECANICA".equals(n.getTipo())) {
                viejo.setEstado("INACTIVO");
                fachada.actualiza(viejo);
            }
            String texto = "Reporte aceptado. Tu viaje " + v.getIdViaje() + " sale ahora con el bus " + nuevo.getPlaca()
                    + (otros > 0 ? " (también en tus " + otros + " viaje(s) siguientes)" : "") + ".";
            n.setRespuesta(texto.length() > 200 ? texto.substring(0, 197) + "..." : texto);
            n.setEstado(Novedad.RESUELTA);
            n.setIdRevisor(actor.getIdUsuario());
            dao.actualiza(n);
            avisos.enviar(idConductor, "Bus reasignado", texto, v.getIdViaje());
            return new ResultadoOperacion(true, texto, n);
        } catch (PersistenciaException | FachadaException | java.io.IOException e) {
            return new ResultadoOperacion(false, "No se pudo reasignar el bus: " + e.getMessage());
        }
    }

    /** Cantidad de asientos vendidos (para mostrar en las listas del conductor). */
    public int asientosVendidos(Viaje v) {
        try {
            int n = 0;
            for (Asiento a : fachada.consultaAsientosPorViaje(v.getIdViaje())) {
                if (!Asiento.DISPONIBLE.equals(a.getEstado())) {
                    n++;
                }
            }
            return n;
        } catch (FachadaException e) {
            return 0;
        }
    }
}
