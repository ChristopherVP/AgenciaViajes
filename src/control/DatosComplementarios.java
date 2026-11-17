package control;

import excepciones.FachadaException;
import excepciones.PersistenciaException;
import fachadas.FachadaArchivos;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import objetosNegocio.Cargo;
import objetosNegocio.Conductor;
import objetosNegocio.DetalleFactura;
import objetosNegocio.Empresa;
import objetosNegocio.Factura;
import objetosNegocio.Novedad;
import objetosNegocio.Pago;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;
import objetosNegocio.Rol;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.ConfiguracionAdmin;
import objetosServicio.Fecha;
import objetosServicio.Marca;
import excepciones.Validador;
import persistencia.Conductores;
import persistencia.DetallesFactura;
import persistencia.Empresas;
import persistencia.Facturas;
import persistencia.Novedades;
import persistencia.Pagos;
import persistencia.Pasajeros;
import persistencia.Reservas;
import persistencia.Usuarios;

/**
 * Datos iniciales complementarios: cuentas de usuario (administradores, operadores, conductores y pasajeros),
 * reservas reales, facturas con sus detalles, pagos y novedades. Todo se guarda en Oracle.
 *
 * Reglas:
 *  - Se ejecuta una sola vez (la marca la pone {@link InicializadorSistema}).
 *  - Nunca borra ni pisa nada: cada paso solo agrega lo que falta.
 *  - Las contraseñas de las cuentas de ejemplo NO están en el código: se toman de la variable de entorno
 *    {@value #VARIABLE_CLAVE} (o la clave temporal del sistema) y cada cuenta debe cambiarla en su primer ingreso.
 */
public final class DatosComplementarios {
    public static final String VARIABLE_CLAVE = "RUTAVIVA_CLAVE_DEMO";
    private static final int VIAJES_CON_VENTAS = 9;
    private static final int RESERVAS_POR_VIAJE = 2;
    private static final String TARJETA_PRUEBA = "4111111111111111";

    public static class Resumen {
        public int usuarios;
        public int reservas;
        public int facturas;
        public int novedades;
        public final List<String> advertencias = new ArrayList<>();
    }

    @FunctionalInterface
    private interface Paso {
        void ejecutar() throws Exception;
    }

    private DatosComplementarios() {
    }

    public static Resumen sembrar() {
        Resumen r = new Resumen();
        ejecutar("Cuentas", r, () -> r.usuarios = sembrarCuentas());
        ejecutar("Reservas", r, () -> r.reservas = sembrarReservas());
        ejecutar("Facturas y pagos", r, () -> r.facturas = sincronizarFacturas());
        ejecutar("Novedades", r, () -> r.novedades = sembrarNovedades());
        return r;
    }

    private static void ejecutar(String nombre, Resumen resumen, Paso paso) {
        try {
            paso.ejecutar();
        } catch (Exception e) {
            resumen.advertencias.add(nombre + ": " + e.getMessage());
            System.err.println("Datos complementarios - " + nombre + ": " + e);
        }
    }

    // ------------------------------------------------------------------ cuentas

    private static int sembrarCuentas() throws PersistenciaException {
        Usuarios dao = new Usuarios("usuarios.dat");
        List<Usuario> existentes = dao.lista();
        Set<String> logins = new HashSet<>();
        Set<String> cedulas = new HashSet<>();
        Set<String> perfiles = new HashSet<>();
        for (Usuario u : existentes) {
            logins.add(u.getLogin().toLowerCase());
            cedulas.add(u.getCedula());
            if (u.getIdPerfil() != null && !u.getIdPerfil().isEmpty()) {
                perfiles.add(u.getIdPerfil());
            }
        }
        int creados = 0;
        List<Empresa> empresas = new Empresas("empresas.dat").lista();
        for (int e = 0; e < empresas.size(); e++) {
            Empresa emp = empresas.get(e);
            String n = String.valueOf(e + 1);
            creados += agregar(dao, logins, cedulas, cuenta("gerente" + n, "Gerente " + emp.getNombre(),
                    DatosIniciales.cedulaConVerificador("18000000" + n), "gerente" + n + "@rutaviva.com", "099000000" + n,
                    Rol.ADMINISTRADOR, Usuario.ESTADO_ACTIVO, "", emp, Cargo.SUPERVISOR_OPERACIONES));
            creados += agregar(dao, logins, cedulas, cuenta("operador" + n, "Operador " + emp.getNombre(),
                    DatosIniciales.cedulaConVerificador("19000000" + n), "operador" + n + "@rutaviva.com", "098000000" + n,
                    Rol.OPERADOR, Usuario.ESTADO_ACTIVO, "", emp, Cargo.OPERADOR_EMPRESA));
        }
        for (Conductor c : new Conductores("conductores.dat").lista()) {
            if (perfiles.contains(c.getIdConductor()) || c.getCedula() == null) {
                continue;
            }
            String[] p = slug(c.getNombre()).split(" ");
            String base = p.length > 1 ? p[0].charAt(0) + p[1] : p[0];
            String estado = "ACTIVO".equalsIgnoreCase(c.getEstado()) ? Usuario.ESTADO_ACTIVO : Usuario.ESTADO_PENDIENTE;
            creados += agregar(dao, logins, cedulas, cuenta(loginLibre(base, logins), c.getNombre(), c.getCedula(), "",
                    c.getTelefono(), Rol.CONDUCTOR, estado, c.getIdConductor(), c.getEmpresa(), null));
        }
        for (Pasajero p : new Pasajeros("pasajeros.dat").lista()) {
            if (perfiles.contains(p.getIdPasajero()) || p.getCedula() == null || !esMayorDeEdad(p.getFechaNacimiento())) {
                continue;
            }
            String email = p.getEmail() == null ? "" : p.getEmail();
            String base = email.contains("@") ? email.substring(0, email.indexOf('@')) : slug(p.getNombre()).replace(" ", ".");
            creados += agregar(dao, logins, cedulas, cuenta(loginLibre(base, logins), p.getNombre(), p.getCedula(), email,
                    p.getTelefono(), Rol.PASAJERO, Usuario.ESTADO_ACTIVO, p.getIdPasajero(), null, null));
        }
        return creados;
    }

    private static int agregar(Usuarios dao, Set<String> logins, Set<String> cedulas, Usuario u) throws PersistenciaException {
        if (cedulas.contains(u.getCedula()) || logins.contains(u.getLogin().toLowerCase())) {
            return 0;
        }
        dao.agrega(u);
        logins.add(u.getLogin().toLowerCase());
        cedulas.add(u.getCedula());
        return 1;
    }

    private static Usuario cuenta(String login, String nombre, String cedula, String email, String telefono, Rol rol, String estado,
                                  String idPerfil, Empresa empresa, Cargo cargo) {
        Usuario u = new Usuario();
        u.setLogin(login);
        u.setNombre(nombre);
        u.setCedula(cedula);
        u.definirContrasena(claveTemporal());
        u.setEmail(email == null ? "" : email);
        u.setTelefono(telefono == null ? "" : telefono);
        u.setTipo(rol.getEtiqueta());
        u.setEstado(estado);
        u.setFechaRegistro(fecha(LocalDateTime.now()));
        u.setPreguntaSeguridad("");
        u.setRespuestaSeguridad("");
        u.setIdPerfil(idPerfil);
        u.setEmpresa(empresa != null && empresa.getIdEmpresa() != null && !empresa.getIdEmpresa().isEmpty() ? new Empresa(empresa.getIdEmpresa()) : null);
        u.setCargo(cargo == null ? "" : cargo.getEtiqueta());
        u.setClavePendiente(true);
        return u;
    }

    /** Clave inicial de las cuentas de ejemplo: variable de entorno si cumple la política; si no, la temporal del sistema. */
    private static String claveTemporal() {
        String valor = System.getenv(VARIABLE_CLAVE);
        return valor != null && Validador.errorContrasena(valor) == null ? valor : ConfiguracionAdmin.passwordTemporal();
    }

    private static String slug(String texto) {
        String s = Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return s.toLowerCase().replaceAll("[^a-z0-9. ]", "").trim();
    }

    private static String loginLibre(String base, Set<String> usados) {
        String limpio = slug(base).replace(" ", "");
        if (limpio.length() < 4) {
            limpio = limpio + "user";
        }
        limpio = limpio.length() > 17 ? limpio.substring(0, 17) : limpio;
        String candidato = limpio;
        for (int i = 2; usados.contains(candidato.toLowerCase()); i++) {
            candidato = limpio + i;
        }
        return candidato;
    }

    private static boolean esMayorDeEdad(Fecha f) {
        try {
            return f != null && LocalDate.of(f.getAnio(), f.getMes(), f.getDia()).plusYears(18).isBefore(LocalDate.now().plusDays(1));
        } catch (java.time.DateTimeException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ reservas (con el flujo real de compra)

    private static int sembrarReservas() throws PersistenciaException, FachadaException {
        if (!new Reservas("reservas.dat").lista().isEmpty()) {
            return 0;
        }
        List<Pasajero> pasajeros = new Pasajeros("pasajeros.dat").lista();
        List<Viaje> viajes = new ArrayList<>();
        for (Viaje v : FachadaArchivos.getInstance().consultaViajes()) {
            if ("Programado".equals(v.getEstado()) && ControladorCompras.esComprable(v) && ControladorCompras.salidaDe(v) != null) {
                viajes.add(v);
            }
        }
        viajes.sort(Comparator.comparing(ControladorCompras::salidaDe));
        if (pasajeros.isEmpty() || viajes.isEmpty()) {
            return 0;
        }
        int creadas = 0;
        int turno = 0;
        for (int i = 0; i < Math.min(VIAJES_CON_VENTAS, viajes.size()); i++) {
            Viaje v = viajes.get(i);
            int asiento = 1;
            for (int k = 0; k < RESERVAS_POR_VIAJE; k++) {
                Pasajero p = pasajeros.get(turno++ % pasajeros.size());
                List<Integer> asientos = new ArrayList<>();
                for (int a = 0; a <= k % 2; a++) {
                    asientos.add(asiento + a);
                }
                if (comprar(p, v, asientos)) {
                    asiento += asientos.size();
                    creadas++;
                }
            }
        }
        return creadas;
    }

    /** Compra con el flujo normal del sistema; si falta saldo, antes se acredita una recarga con tarjeta de prueba. */
    private static boolean comprar(Pasajero p, Viaje v, List<Integer> asientos) {
        ControladorCompras compras = new ControladorCompras();
        ControladorBilletera billetera = new ControladorBilletera();
        ResultadoOperacion cotizacion = compras.cotizar(p.getIdPasajero(), v.getIdViaje(), asientos.size());
        if (!cotizacion.isExito()) {
            return false;
        }
        double total = (Double) cotizacion.getDatos();
        ResultadoOperacion saldo = billetera.consultarSaldo(p.getIdPasajero());
        double disponible = saldo.isExito() ? (Double) saldo.getDatos() : 0;
        if (disponible + 1e-9 < total) {
            double falta = Math.ceil((total - disponible + 5) / 5) * 5;
            ResultadoOperacion recarga = billetera.solicitarRecargaTarjeta(p.getIdPasajero(), falta, p.getNombre(), TARJETA_PRUEBA, "12/30", "123");
            if (!recarga.isExito()) {
                return false;
            }
        }
        return compras.comprar(p.getIdPasajero(), v.getIdViaje(), asientos).isExito();
    }

    // ------------------------------------------------------------------ facturas, detalles y pagos

    /** Crea factura + detalle + pago para cada reserva CONFIRMADA que todavía no tenga factura. Devuelve cuántas creó. */
    public static int sincronizarFacturas() throws PersistenciaException {
        Set<String> conFactura = new HashSet<>();
        for (Factura f : new Facturas("facturas.dat").lista()) {
            if (f.getReserva() != null) {
                conFactura.add(f.getReserva().getIdReserva());
            }
        }
        int creadas = 0;
        for (Reserva r : new Reservas("reservas.dat").lista()) {
            if ("CONFIRMADA".equalsIgnoreCase(r.getEstado()) && !conFactura.contains(r.getIdReserva())) {
                if (registrarFacturaYPago(r)) {
                    creadas++;
                }
            }
        }
        return creadas;
    }

    /** Guarda en Oracle la factura de una reserva, su línea de detalle y el pago. La usa también la compra de pasajes. */
    public static boolean registrarFacturaYPago(Reserva r) throws PersistenciaException {
        if (r == null || r.getPasajero() == null || r.getEmpresa() == null || r.getViaje() == null || r.getPrecioTotal() <= 0) {
            return false;
        }
        Pasajero p = new Pasajeros("pasajeros.dat").obten(new Pasajero(r.getPasajero().getIdPasajero()));
        Empresa e = new Empresas("empresas.dat").obten(new Empresa(r.getEmpresa().getIdEmpresa()));
        if (p == null || e == null) {
            return false;
        }
        Fecha emision = r.getFechaReserva() != null && r.getFechaReserva().getAnio() > 0 ? r.getFechaReserva() : fecha(LocalDateTime.now());
        Factura f = new Factura("", p, new Reserva(r.getIdReserva()), r.getPrecioTotal(), emision, "Pagada", new Empresa(e.getIdEmpresa()));
        f.setNombreEmpresaSnapshot(e.getNombre());
        f.setRucEmpresaSnapshot(e.getRuc());
        f.setDireccionEmpresaSnapshot(e.getDireccion());
        f.setNombreVendedor(Marca.NOMBRE);
        f.setDatosPasajeroSnapshot(p.getNombre() + " · CI " + p.getCedula());
        new Facturas("facturas.dat").agrega(f);

        int cantidad = Math.max(1, r.getCantidadAsientos());
        String ruta = describirRuta(r.getViaje().getIdViaje());
        new DetallesFactura("detalles_factura.dat").agrega(new DetalleFactura("", f.getIdFactura(),
                "Pasaje " + ruta + " (viaje " + r.getViaje().getIdViaje() + ")", cantidad, r.getPrecioTotal() / cantidad));

        Usuario cuenta = null;
        for (Usuario u : new Usuarios("usuarios.dat").listaPorTipo(Rol.PASAJERO.getEtiqueta())) {
            if (p.getIdPasajero().equals(u.getIdPerfil())) {
                cuenta = u;
                break;
            }
        }
        String metodo = r.getMetodoPago() == null || r.getMetodoPago().isEmpty() ? "BILLETERA" : r.getMetodoPago();
        new Pagos("pagos.dat").agrega(new Pago("", cuenta, f, r.getPrecioTotal(), emision, metodo));
        return true;
    }

    private static String describirRuta(String idViaje) {
        try {
            Viaje v = FachadaArchivos.getInstance().obten(new Viaje(idViaje));
            return v == null ? "" : new ControladorCompras().describirRuta(v);
        } catch (FachadaException e) {
            return "";
        }
    }

    // ------------------------------------------------------------------ novedades

    private static int sembrarNovedades() throws PersistenciaException {
        Novedades dao = new Novedades("novedades.dat");
        if (!dao.lista().isEmpty()) {
            return 0;
        }
        List<Pasajero> pasajeros = new Pasajeros("pasajeros.dat").lista();
        List<Conductor> conductores = new Conductores("conductores.dat").lista();
        if (pasajeros.isEmpty() || conductores.isEmpty()) {
            return 0;
        }
        String[][] datos = {
                {"P", "QUEJA", "Retraso en la salida del bus", "El bus salió 40 minutos tarde y no avisaron a los pasajeros por ningún medio.", "RESUELTA", "Gracias por avisar, ya hablamos con el conductor y reforzamos los avisos."},
                {"P", "PROBLEMA_PAGO", "Recarga no se reflejó", "Recargué por transferencia en efectivo y todavía no aparece el saldo en mi billetera.", "EN_REVISION", ""},
                {"P", "SUGERENCIA", "Más horarios en la mañana", "Sería muy útil tener más salidas entre las 5:00 y las 7:00 hacia la sierra centro.", "ABIERTA", ""},
                {"C", "FALLA_MECANICA", "Ruido en los frenos del bus", "Escuché un ruido fuerte al frenar en el último tramo. Pido revisión antes del próximo viaje.", "RESUELTA", "Se envió el bus a mantenimiento, ya está revisado."},
                {"C", "INCIDENTE_RUTA", "Derrumbe en la vía", "Hubo un derrumbe menor y se perdieron 30 minutos esperando el paso de vehículos.", "ABIERTA", ""}};
        int c = 0;
        for (String[] d : datos) {
            LocalDateTime cuando = LocalDateTime.now().minusDays(1 + c).minusHours(c * 2L);
            Novedad n = new Novedad();
            n.setIdConductor("P".equals(d[0]) ? pasajeros.get(c % pasajeros.size()).getIdPasajero() : conductores.get(c % conductores.size()).getIdConductor());
            n.setIdViaje("");
            n.setTipo(d[1]);
            n.setAsunto(d[2]);
            n.setMensaje(d[3]);
            n.setFecha(fecha(cuando));
            n.setHora(cuando.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
            n.setEstado(d[4]);
            n.setRespuesta(d[5]);
            n.setIdRevisor(d[5].isEmpty() ? "" : "ADMIN");
            dao.agrega(n);
            c++;
        }
        return c;
    }

    private static Fecha fecha(LocalDateTime t) {
        return new Fecha(t.getDayOfMonth(), t.getMonthValue(), t.getYear());
    }
}