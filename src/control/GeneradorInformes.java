package control;

import fachadas.FachadaArchivos;
import java.io.File;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import objetosNegocio.Bus;
import objetosNegocio.Comprobante;
import objetosNegocio.Conductor;
import objetosNegocio.Empresa;
import objetosNegocio.Liquidacion;
import objetosNegocio.Movimiento;
import objetosNegocio.Novedad;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;
import objetosNegocio.Ruta;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.Fecha;
import objetosServicio.InformePdf;
import objetosServicio.Permisos;
import objetosServicio.Permisos.Modulo;
import persistencia.Buses;
import persistencia.Conductores;
import persistencia.Empresas;
import persistencia.Movimientos;
import persistencia.Novedades;
import persistencia.Pasajeros;
import persistencia.Reservas;
import persistencia.Rutas;
import persistencia.Viajes;

/**
 * Informe formal en PDF del sistema: de una sola sección (solo rutas, solo buses...) o de todo el sistema completo.
 * Cada persona solo puede incluir las secciones de los módulos que su cargo tiene permitido ver.
 */
public final class GeneradorInformes {

    public enum Seccion {
        RESUMEN("Resumen ejecutivo y estadísticas", null),
        EMPRESAS("Empresas", Modulo.EMPRESAS),
        USUARIOS("Usuarios del sistema", Modulo.USUARIOS),
        CONDUCTORES("Conductores", Modulo.CONDUCTORES),
        PASAJEROS("Pasajeros", Modulo.PASAJEROS),
        BUSES("Buses", Modulo.BUSES),
        RUTAS("Rutas", Modulo.RUTAS),
        VIAJES("Viajes", Modulo.VIAJES),
        RESERVAS("Reservas de pasajes", Modulo.RESERVAS),
        FACTURAS("Facturas y movimientos de dinero", Modulo.FACTURAS),
        RECARGAS("Recargas de billetera", Modulo.RECARGAS),
        PAGOS("Pagos a conductores", Modulo.PAGOS),
        NOVEDADES("Novedades, quejas y sugerencias", Modulo.NOVEDADES);

        private final String etiqueta;
        private final Modulo modulo;

        Seccion(String etiqueta, Modulo modulo) {
            this.etiqueta = etiqueta;
            this.modulo = modulo;
        }

        public String getEtiqueta() { return etiqueta; }
        public Modulo getModulo() { return modulo; }

        @Override
        public String toString() { return etiqueta; }
    }

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private GeneradorInformes() {
    }

    /** Secciones que esta persona puede incluir según su cargo. */
    public static List<Seccion> disponibles(Usuario actor) {
        List<Seccion> lista = new ArrayList<>();
        for (Seccion s : Seccion.values()) {
            if (s == Seccion.RESUMEN ? Permisos.puedeVer(actor, Modulo.REPORTES) : Permisos.puedeVer(actor, s.modulo)) {
                lista.add(s);
            }
        }
        return lista;
    }

    private static String fecha(Fecha f) {
        return f == null ? "" : String.format("%02d/%02d/%04d", f.getDia(), f.getMes(), f.getAnio());
    }

    private static String dinero(double v) {
        return String.format("$%.2f", v);
    }

    /** Genera el PDF con las secciones elegidas (las que no estén permitidas se ignoran). */
    public static void generar(Usuario actor, Set<Seccion> elegidas, File destino) throws IOException {
        Set<Seccion> permitidas = EnumSet.noneOf(Seccion.class);
        for (Seccion s : disponibles(actor)) {
            if (elegidas.contains(s)) {
                permitidas.add(s);
            }
        }
        if (permitidas.isEmpty()) {
            throw new IOException("No hay secciones permitidas para tu cargo");
        }
        boolean completo = permitidas.containsAll(disponibles(actor)) && permitidas.size() > 3;
        String titulo = completo ? "Informe general del sistema" : permitidas.size() == 1 ? "Informe de " + permitidas.iterator().next().getEtiqueta().toLowerCase()
                : "Informe del sistema";
        String sub = "Elaborado por " + actor.getNombre() + " · " + (actor.getCargo() == null || actor.getCargo().isEmpty() ? actor.getTipo() : actor.getCargo());
        InformePdf pdf = new InformePdf(titulo, sub, "RQ · Sistema de Transporte Interprovincial");
        try {
            Datos d = new Datos(actor);
            for (Seccion s : Seccion.values()) {
                if (permitidas.contains(s)) {
                    escribirSeccion(pdf, s, d, actor);
                }
            }
        } catch (excepciones.PersistenciaException e) {
            throw new IOException("No se pudieron leer los datos: " + e.getMessage(), e);
        }
        pdf.guardar(destino);
    }

    // ------------------------------------------------------------------ datos

    private static final class Datos {
        final List<Empresa> empresas;
        final List<Bus> buses;
        final List<Ruta> rutas;
        final List<Viaje> viajes;
        final List<Reserva> reservas;
        final List<Conductor> conductores;
        final List<Pasajero> pasajeros;
        final List<Movimiento> movimientos;
        final List<Novedad> novedades;
        final List<Comprobante> facturas = new ArrayList<>();
        final List<Liquidacion> liquidaciones = new ArrayList<>();
        final Map<String, String> empresaNombre = new LinkedHashMap<>();
        final Map<String, Ruta> rutaPorId = new LinkedHashMap<>();
        final Map<String, Bus> busPorId = new LinkedHashMap<>();
        final Map<String, Conductor> conductorPorId = new LinkedHashMap<>();
        final Map<String, Pasajero> pasajeroPorId = new LinkedHashMap<>();

        Datos(Usuario actor) throws excepciones.PersistenciaException {
            empresas = new Empresas("empresas.dat").lista();
            buses = new Buses("buses.dat").lista();
            rutas = new Rutas("rutas.dat").lista();
            viajes = new Viajes("viajes.dat").lista();
            reservas = new Reservas("reservas.dat").lista();
            conductores = new Conductores("conductores.dat").lista();
            pasajeros = new Pasajeros("pasajeros.dat").lista();
            movimientos = new Movimientos("movimientos.dat").lista();
            novedades = new Novedades("novedades.dat").lista();
            for (Empresa e : empresas) empresaNombre.put(e.getIdEmpresa(), e.getNombre());
            for (Ruta r : rutas) rutaPorId.put(r.getIdRuta(), r);
            for (Bus b : buses) busPorId.put(b.getIdBus(), b);
            for (Conductor c : conductores) conductorPorId.put(c.getIdConductor(), c);
            for (Pasajero p : pasajeros) pasajeroPorId.put(p.getIdPasajero(), p);
            if (Permisos.puedeVer(actor, Modulo.FACTURAS)) {
                Object lista = new ControladorFacturacion().listarParaAdministrador(actor).getDatos();
                if (lista instanceof List<?>) {
                    for (Object o : (List<?>) lista) facturas.add((Comprobante) o);
                }
            }
            if (Permisos.puedeVer(actor, Modulo.PAGOS)) {
                Object lista = new ControladorLiquidaciones().listarParaAdministrador(actor).getDatos();
                if (lista instanceof List<?>) {
                    for (Object o : (List<?>) lista) liquidaciones.add((Liquidacion) o);
                }
            }
        }

        String empresa(Empresa e) {
            return e == null ? "" : empresaNombre.getOrDefault(e.getIdEmpresa(), e.getIdEmpresa());
        }

        String ruta(Ruta r) {
            if (r == null) return "";
            Ruta completa = rutaPorId.get(r.getIdRuta());
            return completa == null ? r.getIdRuta() : completa.getOrigen() + " > " + completa.getDestino();
        }

        String placa(Bus b) {
            if (b == null) return "";
            Bus completo = busPorId.get(b.getIdBus());
            return completo == null ? b.getIdBus() : completo.getPlaca();
        }

        String conductor(Conductor c) {
            if (c == null || c.getIdConductor() == null || c.getIdConductor().isEmpty()) return "";
            Conductor completo = conductorPorId.get(c.getIdConductor());
            return completo == null ? c.getIdConductor() : completo.getNombre();
        }

        String pasajero(Pasajero p) {
            if (p == null) return "";
            Pasajero completo = pasajeroPorId.get(p.getIdPasajero());
            return completo == null ? p.getIdPasajero() : completo.getNombre();
        }
    }

    private static Map<String, Integer> contar(List<String> valores) {
        Map<String, Integer> mapa = new LinkedHashMap<>();
        for (String v : valores) {
            String clave = v == null || v.isEmpty() ? "Sin dato" : v;
            mapa.merge(clave, 1, Integer::sum);
        }
        return mapa;
    }

    private static void graficoConteo(InformePdf pdf, String titulo, Map<String, Integer> conteo) {
        List<String> etiquetas = new ArrayList<>(conteo.keySet());
        List<Double> valores = new ArrayList<>();
        for (int v : conteo.values()) valores.add((double) v);
        pdf.barras(titulo, etiquetas, valores, "");
    }

    private static List<String> fila(String... celdas) {
        List<String> l = new ArrayList<>();
        Collections.addAll(l, celdas);
        return l;
    }

    // ------------------------------------------------------------------ secciones

    private static void escribirSeccion(InformePdf pdf, Seccion s, Datos d, Usuario actor) throws excepciones.PersistenciaException {
        pdf.seccion(s.getEtiqueta());
        switch (s) {
            case RESUMEN: resumen(pdf, d, actor); break;
            case EMPRESAS: {
                List<List<String>> filas = new ArrayList<>();
                for (Empresa e : d.empresas) {
                    long b = d.buses.stream().filter(x -> x.getEmpresa() != null && x.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa())).count();
                    long r = d.rutas.stream().filter(x -> x.getEmpresa() != null && x.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa())).count();
                    long c = d.conductores.stream().filter(x -> x.getEmpresa() != null && x.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa())).count();
                    filas.add(fila(e.getIdEmpresa(), e.getNombre(), e.getRuc(), e.getTelefono(), e.getEmail(), String.valueOf(b), String.valueOf(r), String.valueOf(c)));
                }
                pdf.parrafo("Empresas de transporte registradas: " + d.empresas.size() + ".");
                pdf.tabla(null, fila("ID", "Nombre", "RUC", "Teléfono", "Email", "Buses", "Rutas", "Conduct."), filas);
                break;
            }
            case USUARIOS: {
                List<List<String>> filas = new ArrayList<>();
                List<String> roles = new ArrayList<>();
                Object lista = new ControladorUsuarios().listarVisibles(actor);
                for (Object o : (List<?>) lista) {
                    Usuario u = (Usuario) o;
                    roles.add(u.getTipo());
                    filas.add(fila(u.getIdUsuario(), u.getLogin(), u.getNombre(), u.getCedula(), u.getTipo(), u.getCargo(), u.getEstado()));
                }
                graficoConteo(pdf, "Usuarios por rol", contar(roles));
                pdf.tabla("Detalle de usuarios", fila("ID", "Usuario", "Nombre", "Cédula", "Rol", "Cargo", "Estado"), filas);
                break;
            }
            case CONDUCTORES: {
                ControladorAsignaciones asig = new ControladorAsignaciones();
                List<List<String>> filas = new ArrayList<>();
                List<String> estados = new ArrayList<>();
                for (Conductor c : d.conductores) {
                    estados.add(c.getEstado());
                    filas.add(fila(c.getIdConductor(), c.getNombre(), c.getCedula(), d.empresa(c.getEmpresa()), c.getLicencia(), c.getEstado(), asig.describir(c.getIdConductor())));
                }
                graficoConteo(pdf, "Conductores por estado", contar(estados));
                pdf.tabla("Detalle de conductores", fila("ID", "Nombre", "Cédula", "Empresa", "Licencia", "Estado", "Bus y ruta asignados"), filas);
                break;
            }
            case PASAJEROS: {
                List<List<String>> filas = new ArrayList<>();
                List<String> tipos = new ArrayList<>();
                for (Pasajero p : d.pasajeros) {
                    tipos.add(p.getTipoPasajero());
                    filas.add(fila(p.getIdPasajero(), p.getNombre(), p.getCedula(), p.getTipoPasajero(), p.getTelefono(), p.getEmail()));
                }
                graficoConteo(pdf, "Pasajeros por tipo", contar(tipos));
                pdf.tabla("Detalle de pasajeros", fila("ID", "Nombre", "Cédula", "Tipo", "Teléfono", "Email"), filas);
                break;
            }
            case BUSES: {
                List<List<String>> filas = new ArrayList<>();
                int capacidad = 0;
                for (Bus b : d.buses) {
                    capacidad += b.getCapacidad();
                    filas.add(fila(b.getIdBus(), b.getPlaca(), b.getMarca(), String.valueOf(b.getAnioFabricacion()), String.valueOf(b.getCapacidad()), d.empresa(b.getEmpresa()), b.getEstado()));
                }
                pdf.parrafo("Flota registrada: " + d.buses.size() + " buses con una capacidad total de " + capacidad + " asientos.");
                graficoConteo(pdf, "Buses por empresa", contar(d.buses.stream().map(b -> d.empresa(b.getEmpresa())).collect(java.util.stream.Collectors.toList())));
                pdf.tabla("Detalle de buses", fila("ID", "Placa", "Marca", "Año", "Capac.", "Empresa", "Estado"), filas);
                break;
            }
            case RUTAS: {
                List<List<String>> filas = new ArrayList<>();
                for (Ruta r : d.rutas) {
                    filas.add(fila(r.getIdRuta(), r.getOrigen(), r.getDestino(), String.format("%.0f", r.getDistancia()), dinero(r.getPrecio()),
                            String.format("%.1f h", r.getTiempoEstimado()), d.empresa(r.getEmpresa()), r.getEstado()));
                }
                pdf.parrafo("Todas las rutas son viajes directos: el bus no hace paradas, solo pasa por las ciudades del trayecto.");
                List<String> et = new ArrayList<>();
                List<Double> val = new ArrayList<>();
                for (Ruta r : d.rutas) {
                    et.add(r.getOrigen() + " > " + r.getDestino());
                    val.add(r.getPrecio());
                }
                pdf.barras("Precio del pasaje por ruta", et, val, "$");
                pdf.tabla("Detalle de rutas", fila("ID", "Origen", "Destino", "Km", "Precio", "Duración", "Empresa", "Estado"), filas);
                break;
            }
            case VIAJES: {
                List<List<String>> filas = new ArrayList<>();
                List<String> estados = new ArrayList<>();
                for (Viaje v : d.viajes) {
                    estados.add(v.getEstado());
                    filas.add(fila(v.getIdViaje(), d.empresa(v.getEmpresa()), d.ruta(v.getRuta()), fecha(v.getFechaSalida()) + " " + v.getHoraSalida(),
                            d.placa(v.getBus()), d.conductor(v.getConductor1()), String.valueOf(v.getAsientosDisponibles()), v.getEstado()));
                }
                graficoConteo(pdf, "Viajes por estado", contar(estados));
                pdf.tabla("Detalle de viajes", fila("ID", "Empresa", "Ruta", "Salida", "Bus", "Conductor", "Libres", "Estado"), filas);
                break;
            }
            case RESERVAS: {
                List<List<String>> filas = new ArrayList<>();
                List<String> estados = new ArrayList<>();
                double total = 0;
                for (Reserva r : d.reservas) {
                    estados.add(r.getEstado());
                    if ("Confirmada".equalsIgnoreCase(r.getEstado()) || "Usada".equalsIgnoreCase(r.getEstado())) total += r.getPrecioTotal();
                    filas.add(fila(r.getIdReserva(), d.pasajero(r.getPasajero()), r.getViaje() == null ? "" : r.getViaje().getIdViaje(),
                            String.join(", ", r.getNumerosAsientos()), dinero(r.getPrecioTotal()), r.getEstado(), fecha(r.getFechaReserva())));
                }
                pdf.parrafo("Pasajes vendidos: " + d.reservas.size() + " reservas por un total de " + dinero(total) + ". Las compras son definitivas: no se cancelan ni se reembolsan.");
                graficoConteo(pdf, "Reservas por estado", contar(estados));
                pdf.tabla("Detalle de reservas", fila("ID", "Pasajero", "Viaje", "Asientos", "Total", "Estado", "Fecha"), filas);
                break;
            }
            case FACTURAS: {
                List<List<String>> filas = new ArrayList<>();
                double[] totales = new double[3];
                for (Comprobante c : d.facturas) {
                    int i = Comprobante.PASAJE.equals(c.getTipo()) ? 0 : Comprobante.RECARGA.equals(c.getTipo()) ? 1 : 2;
                    totales[i] += c.getTotal();
                    filas.add(fila(c.getNumero(), c.getTipoEtiqueta(), c.getNombreTitular(), c.getConcepto(), dinero(c.getTotal()), c.getFecha().format(FECHA_HORA)));
                }
                pdf.parrafo("Se emitieron " + d.facturas.size() + " facturas. Las ventas de pasajes son el ingreso de la plataforma; las recargas son saldo de los pasajeros; "
                        + "los pagos a conductores son el 80 % de lo recaudado en cada viaje realizado.");
                pdf.barras("Dinero por tipo de factura", List.of("Ventas de pasajes", "Recargas acreditadas", "Pagos a conductores"), List.of(totales[0], totales[1], totales[2]), "$");
                pdf.tabla("Registro de facturas", fila("N.º factura", "Tipo", "Titular", "Concepto", "Total", "Fecha"), filas);
                break;
            }
            case RECARGAS: {
                List<List<String>> filas = new ArrayList<>();
                List<String> estados = new ArrayList<>();
                for (Movimiento m : d.movimientos) {
                    if (!"RECARGA".equalsIgnoreCase(m.getTipo())) continue;
                    estados.add(m.getEstado());
                    Pasajero p = d.pasajeroPorId.get(m.getIdPasajero());
                    filas.add(fila(m.getIdMovimiento(), p == null ? m.getIdPasajero() : p.getNombre(), m.getMetodo(), dinero(m.getMonto()), m.getEstado(), fecha(m.getFecha()) + " " + m.getHora()));
                }
                graficoConteo(pdf, "Recargas por estado", contar(estados));
                pdf.tabla("Detalle de recargas", fila("ID", "Pasajero", "Método", "Monto", "Estado", "Fecha"), filas);
                break;
            }
            case PAGOS: {
                List<List<String>> filas = new ArrayList<>();
                double pagado = 0, pendiente = 0;
                for (Liquidacion l : d.liquidaciones) {
                    if (Liquidacion.PAGADA.equals(l.getEstado())) pagado += l.getMonto(); else pendiente += l.getMonto();
                    filas.add(fila(l.getIdLiquidacion(), l.getNombreConductor(), l.getIdViaje(), String.valueOf(l.getPasajeros()), dinero(l.getRecaudado()),
                            dinero(l.getMonto()), l.getEstado(), l.getTarjeta()));
                }
                pdf.indicadores(new String[]{"Pagado a conductores", "Pendiente de pago"}, new String[]{dinero(pagado), dinero(pendiente)});
                pdf.tabla("Detalle de pagos (80 % del recaudado por viaje)", fila("ID", "Conductor", "Viaje", "Pasaj.", "Recaudado", "Pago 80%", "Estado", "Tarjeta"), filas);
                break;
            }
            case NOVEDADES: {
                Map<String, String> nombres = new ControladorNovedades().nombresDeConductores();
                List<List<String>> filas = new ArrayList<>();
                List<String> estados = new ArrayList<>();
                for (Novedad n : d.novedades) {
                    estados.add(n.getEstado());
                    filas.add(fila(n.getIdNovedad(), nombres.getOrDefault(n.getIdConductor(), n.getIdConductor()), ControladorNovedades.TIPOS.getOrDefault(n.getTipo(), n.getTipo()),
                            n.getAsunto(), n.getEstado(), fecha(n.getFecha())));
                }
                graficoConteo(pdf, "Novedades por estado", contar(estados));
                pdf.tabla("Detalle de novedades", fila("ID", "Remitente", "Tipo", "Asunto", "Estado", "Fecha"), filas);
                break;
            }
            default: break;
        }
    }

    private static void resumen(InformePdf pdf, Datos d, Usuario actor) {
        pdf.parrafo("Este informe resume el estado del sistema de transporte interprovincial RQ: la oferta (empresas, flota, rutas y viajes), la demanda "
                + "(pasajeros y reservas) y, según tu cargo, el movimiento de dinero.");
        pdf.indicadores(new String[]{"Empresas", "Buses", "Rutas", "Viajes"},
                new String[]{String.valueOf(d.empresas.size()), String.valueOf(d.buses.size()), String.valueOf(d.rutas.size()), String.valueOf(d.viajes.size())});
        pdf.indicadores(new String[]{"Conductores", "Pasajeros", "Reservas", "Novedades"},
                new String[]{String.valueOf(d.conductores.size()), String.valueOf(d.pasajeros.size()), String.valueOf(d.reservas.size()), String.valueOf(d.novedades.size())});
        if (Permisos.puedeVer(actor, Modulo.FACTURAS) || Permisos.puedeVer(actor, Modulo.PAGOS)) {
            ControladorFacturacion.Resumen r = new ControladorFacturacion().resumen();
            pdf.indicadores(new String[]{"Ingresos por ventas", "Recargas acreditadas", "Pagado a conductores", "Margen plataforma 20 %"},
                    new String[]{dinero(r.ventasPasajes), dinero(r.recargasAcreditadas), dinero(r.pagadoConductores), dinero(r.margenPlataforma)});
            pdf.indicadores(new String[]{"Por pagar a conductores", "Facturas emitidas"}, new String[]{dinero(r.porPagarConductores), String.valueOf(r.facturas)});
            Map<String, Double> porEmpresa = new LinkedHashMap<>();
            for (Comprobante c : d.facturas) {
                if (Comprobante.PASAJE.equals(c.getTipo())) {
                    porEmpresa.merge(c.getEmisor().isEmpty() ? "Sin empresa" : c.getEmisor(), c.getTotal(), Double::sum);
                }
            }
            pdf.barras("Ventas de pasajes por empresa", new ArrayList<>(porEmpresa.keySet()), new ArrayList<>(porEmpresa.values()), "$");
        }
        graficoConteo(pdf, "Viajes por estado", contar(d.viajes.stream().map(Viaje::getEstado).collect(java.util.stream.Collectors.toList())));
        graficoConteo(pdf, "Reservas por estado", contar(d.reservas.stream().map(Reserva::getEstado).collect(java.util.stream.Collectors.toList())));
        long confirmadas = d.reservas.stream().filter(r -> "Confirmada".equalsIgnoreCase(r.getEstado()) || "Usada".equalsIgnoreCase(r.getEstado())).count();
        long programados = d.viajes.stream().filter(v -> "Programado".equalsIgnoreCase(v.getEstado())).count();
        pdf.subtitulo("Conclusiones");
        pdf.parrafo("• Hay " + programados + " viaje(s) programado(s) de " + d.viajes.size() + " en total y " + confirmadas + " reserva(s) vigente(s) o utilizada(s).");
        pdf.parrafo("• Todos los viajes son directos y cada conductor trabaja con el bus y la ruta que se le asignó.");
        pdf.parrafo("• Los pasajes no admiten devoluciones. El conductor recibe el 80 % de lo recaudado por cada viaje realizado; el 20 % restante es el margen de la plataforma.");
    }
}
