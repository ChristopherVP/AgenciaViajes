package control;

import excepciones.PersistenciaException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import objetosNegocio.Conductor;
import objetosNegocio.Novedad;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.Fecha;
import persistencia.Conductores;
import persistencia.Novedades;
import persistencia.Usuarios;
import persistencia.Viajes;

/**
 * Canal de reportes del conductor hacia el administrador. Solo los conductores pueden reportar;
 * los pasajeros no tienen acceso a este flujo.
 */
public class ControladorNovedades {
    public static final int MAX_ABIERTAS = 5;
    /** Tipo (valor guardado) -> etiqueta visible. */
    public static final Map<String, String> TIPOS = new LinkedHashMap<>();
    static {
        TIPOS.put("NO_PUEDE_VIAJAR", "No puedo realizar el viaje");
        TIPOS.put("FALLA_MECANICA", "Falla mecánica del bus");
        TIPOS.put("INCIDENTE_RUTA", "Incidente en la ruta");
        TIPOS.put("QUEJA", "Queja o reclamo");
        TIPOS.put("OTRO", "Otro asunto");
        TIPOS.put("SUGERENCIA", "Sugerencia");
        TIPOS.put("PROBLEMA_PAGO", "Problema con un pago o recarga");
    }

    /** Tipos que puede elegir un pasajero al escribir al administrador. */
    public static final Map<String, String> TIPOS_PASAJERO = new LinkedHashMap<>();
    static {
        TIPOS_PASAJERO.put("QUEJA", "Queja o reclamo");
        TIPOS_PASAJERO.put("SUGERENCIA", "Sugerencia");
        TIPOS_PASAJERO.put("PROBLEMA_PAGO", "Problema con un pago o recarga");
        TIPOS_PASAJERO.put("OTRO", "Otro asunto");
    }

    private final Novedades novedades;
    private final Conductores conductores;
    private final Viajes viajes;
    private final Usuarios usuarios;

    public ControladorNovedades() {
        this(new Novedades("novedades.dat"), new Conductores("conductores.dat"), new Viajes("viajes.dat"), new Usuarios("usuarios.dat"));
    }

    public ControladorNovedades(Novedades novedades, Conductores conductores, Viajes viajes, Usuarios usuarios) {
        this.novedades = novedades;
        this.conductores = conductores;
        this.viajes = viajes;
        this.usuarios = usuarios;
    }

    public synchronized ResultadoOperacion reportar(Usuario actor, String idViaje, String tipo, String asunto, String mensaje) {
        boolean esConductor = actor != null && actor.getRol() == Rol.CONDUCTOR;
        boolean esPasajero = actor != null && actor.getRol() == Rol.PASAJERO;
        if (actor == null || !(esConductor || esPasajero) || !actor.estaActivo() || actor.getIdPerfil() == null || actor.getIdPerfil().isEmpty()) {
            return new ResultadoOperacion(false, "Solo los conductores y los pasajeros pueden enviar reportes al administrador");
        }
        if (tipo == null || !(esPasajero ? TIPOS_PASAJERO : TIPOS).containsKey(tipo)) {
            return new ResultadoOperacion(false, "Selecciona el tipo de reporte");
        }
        String a = asunto == null ? "" : asunto.trim();
        String m = mensaje == null ? "" : mensaje.trim();
        if (a.length() < 5 || a.length() > 60) {
            return new ResultadoOperacion(false, "El asunto debe tener entre 5 y 60 caracteres");
        }
        if (m.length() < 10 || m.length() > 300) {
            return new ResultadoOperacion(false, "El mensaje debe tener entre 10 y 300 caracteres");
        }
        try {
            String viajeFinal = "";
            if (esConductor && idViaje != null && !idViaje.trim().isEmpty()) {
                Viaje v = viajes.obten(new Viaje(idViaje.trim()));
                if (v == null || !esConductorDelViaje(v, actor.getIdPerfil())) {
                    return new ResultadoOperacion(false, "Ese viaje no está asignado a tu cuenta");
                }
                viajeFinal = v.getIdViaje();
            }
            boolean sobreViaje = "NO_PUEDE_VIAJAR".equals(tipo) || "FALLA_MECANICA".equals(tipo);
            if (esConductor && "NO_PUEDE_VIAJAR".equals(tipo) && viajeFinal.isEmpty()) {
                return new ResultadoOperacion(false, "Elige el viaje que no podrás realizar");
            }
            if (esConductor && sobreViaje && !viajeFinal.isEmpty()) {
                Viaje v = viajes.obten(new Viaje(viajeFinal));
                java.time.LocalDateTime salida = v == null ? null : ControladorCompras.salidaDe(v);
                if (salida == null || !salida.isAfter(LocalDateTime.now())) {
                    return new ResultadoOperacion(false, "El bus de ese viaje ya salió: ya no se puede reportar un problema para cancelarlo o cambiar el bus");
                }
                final String idV = viajeFinal;
                boolean yaAvisado = novedades.lista().stream().anyMatch(n -> actor.getIdPerfil().equals(n.getIdConductor()) && idV.equals(n.getIdViaje())
                        && ("NO_PUEDE_VIAJAR".equals(n.getTipo()) || "FALLA_MECANICA".equals(n.getTipo())));
                if (yaAvisado) {
                    return new ResultadoOperacion(false, "Ya avisaste un problema sobre el viaje " + viajeFinal + ". Solo se puede avisar una vez: espera la respuesta del administrador.");
                }
            }
            long abiertas = novedades.lista().stream()
                    .filter(n -> actor.getIdPerfil().equals(n.getIdConductor()) && !Novedad.RESUELTA.equals(n.getEstado())).count();
            if (abiertas >= MAX_ABIERTAS) {
                return new ResultadoOperacion(false, "Ya tienes " + MAX_ABIERTAS + " reportes sin resolver. Espera la respuesta del administrador.");
            }
            LocalDateTime ahora = LocalDateTime.now();
            Novedad n = new Novedad();
            n.setIdConductor(actor.getIdPerfil());
            n.setIdViaje(viajeFinal);
            n.setTipo(tipo);
            n.setAsunto(a);
            n.setMensaje(m);
            n.setFecha(new Fecha(ahora.getDayOfMonth(), ahora.getMonthValue(), ahora.getYear()));
            n.setHora(ahora.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            n.setEstado(Novedad.ABIERTA);
            n.setRespuesta("");
            n.setIdRevisor("");
            novedades.agrega(n);
            new ControladorNotificaciones().enviar(ControladorNotificaciones.ADMIN, "Nuevo reporte: " + TIPOS.get(tipo),
                    (esConductor ? "Conductor " : "Pasajero ") + actor.getNombre() + ": " + a + (viajeFinal.isEmpty() ? "" : " (viaje " + viajeFinal + ")")
                            + ". Revisa la pestaña Novedades.", n.getIdNovedad());
            return new ResultadoOperacion(true, "Reporte enviado al administrador", n);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Reportes enviados por la persona (conductor o pasajero), del más reciente al más antiguo. */
    public synchronized ResultadoOperacion listarMios(Usuario actor) {
        if (actor == null || (actor.getRol() != Rol.CONDUCTOR && actor.getRol() != Rol.PASAJERO)) {
            return new ResultadoOperacion(false, "Acción no permitida");
        }
        try {
            List<Novedad> lista = novedades.lista().stream().filter(n -> actor.getIdPerfil().equals(n.getIdConductor()))
                    .sorted(ordenReciente()).collect(Collectors.toList());
            return new ResultadoOperacion(true, "OK", lista);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    public synchronized ResultadoOperacion listarDeConductor(Usuario actor) {
        if (actor == null || actor.getRol() != Rol.CONDUCTOR) {
            return new ResultadoOperacion(false, "Acción no permitida");
        }
        try {
            List<Novedad> lista = novedades.lista().stream().filter(n -> actor.getIdPerfil().equals(n.getIdConductor()))
                    .sorted(ordenReciente()).collect(Collectors.toList());
            return new ResultadoOperacion(true, "OK", lista);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Super administrador: todas. Administrador de empresa: solo las de conductores de su empresa. */
    public synchronized ResultadoOperacion listarParaAdministrador(Usuario actor) {
        if (actor == null || actor.getRol() != Rol.ADMINISTRADOR) {
            return new ResultadoOperacion(false, "Solo un administrador puede ver los reportes");
        }
        try {
            Map<String, Conductor> porId = new LinkedHashMap<>();
            for (Conductor c : conductores.lista()) {
                porId.put(c.getIdConductor(), c);
            }
            List<Novedad> lista = new ArrayList<>();
            for (Novedad n : novedades.lista()) {
                // los reportes de pasajeros no pertenecen a una empresa: los ve el administrador general
                if (actor.esSuperAdministrador() || perteneceAEmpresa(porId.get(n.getIdConductor()), actor)) {
                    lista.add(n);
                }
            }
            lista.sort(ordenReciente());
            return new ResultadoOperacion(true, "OK", lista);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    public synchronized ResultadoOperacion responder(Usuario actor, String idNovedad, String respuesta, String nuevoEstado) {
        if (!objetosServicio.Permisos.puedeEditar(actor, objetosServicio.Permisos.Modulo.NOVEDADES)) {
            return new ResultadoOperacion(false, "Tu cargo no puede responder reportes");
        }
        if (!Novedad.EN_REVISION.equals(nuevoEstado) && !Novedad.RESUELTA.equals(nuevoEstado)) {
            return new ResultadoOperacion(false, "Estado inválido");
        }
        String r = respuesta == null ? "" : respuesta.trim();
        if (r.length() < 5 || r.length() > 200) {
            return new ResultadoOperacion(false, "La respuesta debe tener entre 5 y 200 caracteres");
        }
        try {
            Novedad n = novedades.obten(idNovedad);
            if (n == null) {
                return new ResultadoOperacion(false, "Reporte no encontrado");
            }
            if (!actor.esSuperAdministrador() && !perteneceAEmpresa(conductores.obten(new Conductor(n.getIdConductor())), actor)) {
                return new ResultadoOperacion(false, "Este reporte no corresponde a tu empresa");
            }
            n.setRespuesta(r);
            n.setEstado(nuevoEstado);
            n.setIdRevisor(actor.getIdUsuario());
            novedades.actualiza(n);
            return new ResultadoOperacion(true, "Respuesta guardada", n);
        } catch (PersistenciaException e) {
            return new ResultadoOperacion(false, e.getMessage());
        }
    }

    /** Correos de los administradores que deben recibir el reporte de este conductor (para abrir el cliente de correo). */
    public List<String> correosDeDestino(Usuario conductor) {
        Set<String> correos = new LinkedHashSet<>();
        try {
            Conductor ficha = conductor == null ? null : conductores.obten(new Conductor(conductor.getIdPerfil()));
            for (Usuario u : usuarios.lista()) {
                boolean administrador = u.getRol() == Rol.ADMINISTRADOR && u.estaActivo() && u.getEmail() != null && !u.getEmail().isEmpty();
                if (!administrador) {
                    continue;
                }
                boolean aplica = u.esSuperAdministrador() || (ficha != null && ficha.getEmpresa() != null && u.tieneEmpresa()
                        && u.getEmpresa().getIdEmpresa().equals(ficha.getEmpresa().getIdEmpresa()));
                if (aplica) {
                    correos.add(u.getEmail());
                }
            }
        } catch (PersistenciaException ignorado) {
            // sin correos: el reporte interno igual queda registrado
        }
        return new ArrayList<>(correos);
    }

    /** Id de conductor o de pasajero -> nombre, para mostrar quién envió cada reporte. */
    public Map<String, String> nombresDeConductores() {
        Map<String, String> mapa = new LinkedHashMap<>();
        try {
            for (Conductor c : conductores.lista()) {
                mapa.put(c.getIdConductor(), c.getNombre());
            }
            for (objetosNegocio.Pasajero p : new persistencia.Pasajeros("pasajeros.dat").lista()) {
                mapa.put(p.getIdPasajero(), p.getNombre());
            }
        } catch (PersistenciaException ignorado) {
            // se mostrará el id
        }
        return mapa;
    }

    public static boolean esConductorDelViaje(Viaje v, String idConductor) {
        return (v.getConductor1() != null && idConductor.equals(v.getConductor1().getIdConductor()))
                || (v.getConductor2() != null && idConductor.equals(v.getConductor2().getIdConductor()));
    }

    private static boolean perteneceAEmpresa(Conductor c, Usuario admin) {
        return c != null && c.getEmpresa() != null && admin.tieneEmpresa()
                && admin.getEmpresa().getIdEmpresa().equals(c.getEmpresa().getIdEmpresa());
    }

    private static Comparator<Novedad> ordenReciente() {
        return Comparator.comparing((Novedad n) -> {
            Fecha f = n.getFecha();
            String fecha = f == null ? "00000000" : String.format("%04d%02d%02d", f.getAnio(), f.getMes(), f.getDia());
            return fecha + n.getHora() + n.getIdNovedad();
        }).reversed();
    }
}
