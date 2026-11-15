package control;

import excepciones.FachadaException;
import excepciones.PersistenciaException;
import fachadas.FachadaArchivos;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import objetosNegocio.Viaje;
import objetosServicio.Fecha;
import persistencia.Buses;
import persistencia.Conductores;
import persistencia.Empresas;
import persistencia.Movimientos;
import persistencia.Novedades;
import persistencia.Pasajeros;
import persistencia.Rutas;

/**
 * Datos de demostración: pasajeros (sin cuenta de acceso) con recargas, compras, facturas, novedades, reembolsos y pagos a
 * conductores programados, pendientes y pagados. Se cargan UNA sola vez (archivo marcador) y nunca borran nada existente.
 * Todo pertenece a las empresas existentes, así que los administradores por rol lo ven según su cargo.
 */
public final class DatosDemostracion {
    private static final File MARCA = new File("demo_datos_v1.flag");
    private static final DateTimeFormatter F = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final String[] NOMBRES = {"María Fernanda Cevallos", "Luis Alberto Andrade", "Karla Estefanía Mora", "Diego Armando Pazmiño",
            "Valeria Nicole Guerrero", "Carlos Eduardo Toapanta", "Andrea Paola Villacís", "Jorge Luis Naranjo", "Daniela Alejandra Pérez",
            "Mateo Sebastián Chicaiza", "Gabriela Monserrath Ortiz", "Kevin Rolando Quishpe", "Paola Alexandra Yánez", "Santiago Javier Lema",
            "Lucía Beatriz Almeida", "Byron Patricio Salazar", "Tatiana Elizabeth Proaño", "Esteban David Cárdenas", "Camila Johanna Reyes",
            "Fabián Ricardo Moreta", "Nataly Soledad Cruz", "Marco Vinicio Heredia", "Ana Belén Zurita", "Wilson Fernando Gualotuña"};

    private static final Random AZAR = new Random(2026);

    private DatosDemostracion() {
    }

    public static int sembrarSiHaceFalta() {
        if (MARCA.exists()) {
            return 0;
        }
        int[] creados = {0};
        List<String> problemas = new ArrayList<>();
        List<Pasajero> pasajeros = new ArrayList<>();
        List<Empresa> empresas = new ArrayList<>();
        List<Viaje> pasados = new ArrayList<>();
        paso("base", problemas, () -> {
            quitarSufijoAntiguo();
            registrarTarjetasDeCobro();
            pasajeros.addAll(crearPasajeros());
            empresas.addAll(new Empresas("empresas.dat").lista());
        });
        if (pasajeros.isEmpty() || empresas.isEmpty()) {
            return 0;
        }
        ControladorViajes viajes = new ControladorViajes();
        for (Empresa e : empresas) {
            paso("viajes pasados " + e.getIdEmpresa(), problemas, () -> pasados.addAll(crearViajesPasados(viajes, e)));
        }
        int[] turno = {0};
        for (Viaje v : pasados) {
            paso("ventas " + v.getIdViaje(), problemas, () -> {
                turno[0] = venderPasajes(v, pasajeros, turno[0], 4 + AZAR.nextInt(5));
                creados[0]++;
            });
        }
        paso("cierre de viajes", problemas, () -> new ControladorSimulacion().avanzar());
        paso("pagos", problemas, () -> pagarAlgunos(pasados));
        paso("cancelación de ejemplo", problemas, () -> cancelarUnoConReembolso(viajes, empresas.get(0), pasajeros));
        paso("viaje en curso", problemas, () -> creados[0] += preparaViajeEnCurso(viajes, empresas, pasajeros));
        paso("ventas futuras", problemas, () -> ventasFuturas(pasajeros));
        paso("novedades", problemas, () -> novedades(pasajeros));
        paso("recargas", problemas, () -> recargasExtra(pasajeros));
        paso("avance final", problemas, () -> new ControladorSimulacion().avanzar());
        // el marcador se escribe SIEMPRE: así un fallo parcial no duplica los datos en el siguiente arranque
        try {
            new ControladorNotificaciones().enviar(ControladorNotificaciones.ADMIN, "Datos de demostración cargados",
                    "Se cargaron pasajeros, reservas, facturas, novedades y pagos de ejemplo." + (problemas.isEmpty() ? "" : " Con " + problemas.size() + " aviso(s) en demo_log.txt."), "");
            if (!problemas.isEmpty()) {
                java.nio.file.Files.write(new File("demo_log.txt").toPath(), problemas, java.nio.charset.StandardCharsets.UTF_8);
            }
            if (!MARCA.createNewFile()) {
                System.err.println("No se pudo crear el marcador de datos de demostración");
            }
        } catch (IOException e) {
            System.err.println("No se pudo cerrar la carga de demostración: " + e.getMessage());
        }
        return creados[0];
    }

    @FunctionalInterface
    private interface Paso {
        void ejecutar() throws Exception;
    }

    /** Ejecuta un paso; si falla lo anota y sigue con el resto (una carga a medias es peor que una carga con un hueco). */
    private static void paso(String nombre, List<String> problemas, Paso paso) {
        try {
            paso.ejecutar();
        } catch (Exception e) {
            String msg = nombre + ": " + e.getClass().getSimpleName() + " " + e.getMessage();
            problemas.add(msg);
            System.err.println("Demo: " + msg);
        }
    }

    // ------------------------------------------------------------------ base

    private static void quitarSufijoAntiguo() throws PersistenciaException {
        Empresas dao = new Empresas("empresas.dat");
        for (Empresa e : dao.lista()) {
            if (e.getNombre() != null && e.getNombre().endsWith(" RQ")) {
                e.setNombre(e.getNombre().substring(0, e.getNombre().length() - 3));
                dao.actualiza(e);
            }
        }
    }

    /** Cada conductor activo sin tarjeta recibe una de ejemplo (así puede aceptar viajes y cobrar). */
    private static void registrarTarjetasDeCobro() throws PersistenciaException {
        ControladorTarjetas tarjetas = new ControladorTarjetas();
        String[] numeros = {"4111111111111111", "5555555555554444", "4012888888881881"};
        String[] bancos = {"Banco Pichincha", "Banco del Pacífico", "Produbanco"};
        int i = 0;
        for (Conductor c : new Conductores("conductores.dat").lista()) {
            if ("ACTIVO".equalsIgnoreCase(c.getEstado()) && tarjetas.obtenerTarjetaCobro(c.getIdConductor()) == null) {
                tarjetas.guardarTarjetaCobro(c.getIdConductor(), bancos[i % 3], numeros[i % 3], c.getNombre().toUpperCase(), "12/30");
                i++;
            }
        }
    }

    private static List<Pasajero> crearPasajeros() throws PersistenciaException {
        Pasajeros dao = new Pasajeros("pasajeros.dat");
        List<Pasajero> existentes = dao.lista();
        List<Pasajero> nuevos = new ArrayList<>();
        for (int i = 0; i < NOMBRES.length; i++) {
            String cedula = DatosIniciales.cedulaConVerificador(String.format("17%07d", 3100000 + i * 7919 % 9000000));
            Pasajero ya = existentes.stream().filter(p -> cedula.equals(p.getCedula())).findFirst().orElse(null);
            if (ya != null) {
                nuevos.add(ya); // carga anterior interrumpida: se reutilizan los pasajeros de ejemplo que ya existen
                continue;
            }
            String[] partes = NOMBRES[i].toLowerCase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").split(" ");
            String email = partes[0] + "." + partes[partes.length - 1] + (i + 1) + "@correo.com";
            // edades variadas para que apliquen las tarifas preferenciales (niño, adolescente, tercera edad)
            int anio = i % 8 == 3 ? 2012 : i % 8 == 5 ? 1952 + i % 5 : 1970 + (i * 3) % 35;
            Pasajero p = new Pasajero("", NOMBRES[i], cedula, "09" + String.format("%08d", 81234500 + i * 37), email,
                    "Quito, sector " + (i + 1), null, new Fecha(1 + i % 27, 1 + i % 12, anio), i % 11 == 7);
            dao.agrega(p);
            nuevos.add(p);
        }
        return nuevos;
    }

    private static List<Viaje> crearViajesPasados(ControladorViajes viajes, Empresa empresa) throws PersistenciaException {
        List<Bus> buses = new ArrayList<>();
        for (Bus b : new Buses("buses.dat").lista()) {
            if (b.getEmpresa() != null && b.getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa())) buses.add(b);
        }
        List<Ruta> rutas = new ArrayList<>();
        for (Ruta r : new Rutas("rutas.dat").lista()) {
            if (r.getEmpresa() != null && r.getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa()) && "ACTIVA".equalsIgnoreCase(r.getEstado())) rutas.add(r);
        }
        List<Conductor> conductores = new ArrayList<>();
        for (Conductor c : new Conductores("conductores.dat").lista()) {
            if (c.getEmpresa() != null && c.getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa()) && "ACTIVO".equalsIgnoreCase(c.getEstado())) conductores.add(c);
        }
        List<Viaje> creados = new ArrayList<>();
        if (buses.isEmpty() || rutas.isEmpty() || conductores.isEmpty()) {
            return creados;
        }
        String[] horas = {"06:00", "08:30", "13:00", "16:30"};
        for (int k = 0; k < 4; k++) {
            Ruta ruta = rutas.get(k % rutas.size());
            Conductor cond = conductores.get(k % conductores.size());
            ControladorAsignaciones.Asignacion asig = new ControladorAsignaciones().obtener(cond.getIdConductor());
            if (asig == null) {
                continue;
            }
            Bus bus = buses.stream().filter(b -> b.getIdBus().equals(asig.getIdBus())).findFirst().orElse(null);
            if (bus == null) {
                continue;
            }
            LocalDateTime salida = LocalDate.now().minusDays(2 + 3L * k).atTime(Integer.parseInt(horas[k].substring(0, 2)), Integer.parseInt(horas[k].substring(3)));
            LocalDateTime llegada = salida.plusMinutes(Math.round((ruta.getTiempoEstimado() > 0 ? ruta.getTiempoEstimado() : 3) * 60));
            ResultadoOperacion r = viajes.programarViaje("", bus.getIdBus(), ruta.getIdRuta(), fecha(salida), fecha(llegada), empresa.getIdEmpresa(), "Programado",
                    true, salida.format(DateTimeFormatter.ofPattern("HH:mm")), llegada.format(DateTimeFormatter.ofPattern("HH:mm")), ruta.getPrecio(), cond.getIdConductor(), "");
            if (r.isExito() && r.getDatos() instanceof Viaje v) {
                creados.add(v);
            } else {
                System.err.println("Demo: viaje pasado omitido (" + r.getMensaje() + ")");
            }
        }
        return creados;
    }

    private static Fecha fecha(LocalDateTime t) {
        return new Fecha(t.getDayOfMonth(), t.getMonthValue(), t.getYear());
    }

    // ------------------------------------------------------------------ dinero

    private static Movimiento movimiento(String idPasajero, String tipo, double monto, String metodo, String estado, String ref, LocalDateTime cuando) throws PersistenciaException {
        Movimiento m = new Movimiento();
        m.setIdPasajero(idPasajero);
        m.setTipo(tipo);
        m.setMonto(Math.round(monto * 100) / 100.0);
        m.setMetodo(metodo);
        m.setEstado(estado);
        m.setReferencia(ref);
        m.setFecha(fecha(cuando));
        m.setHora(cuando.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
        m.setIdRevisor(Movimiento.APROBADA.equals(estado) && Movimiento.METODO_EFECTIVO.equals(metodo) ? "ADMIN" : "");
        m.setMotivo(Movimiento.RECHAZADA.equals(estado) ? "Comprobante ilegible" : "");
        new Movimientos("movimientos.dat").agrega(m);
        return m;
    }

    /** Recarga aprobada con su factura. */
    private static void recargar(Pasajero p, double monto, LocalDateTime cuando) throws PersistenciaException, IOException {
        boolean tarjeta = AZAR.nextBoolean();
        String ref = tarjeta ? "VISA •••• " + (1000 + AZAR.nextInt(9000)) : "COMP-" + (10000 + AZAR.nextInt(90000));
        Movimiento m = movimiento(p.getIdPasajero(), Movimiento.RECARGA, monto, tarjeta ? Movimiento.METODO_TARJETA : Movimiento.METODO_EFECTIVO,
                Movimiento.APROBADA, ref, cuando);
        String detalle = "Movimiento: " + m.getIdMovimiento() + "\nMétodo: " + m.getMetodo() + "\nReferencia: " + m.getReferencia()
                + "\nAcreditado a: billetera " + objetosServicio.Marca.NOMBRE;
        new ControladorFacturacion().emitirConFecha(Comprobante.RECARGA, p.getIdPasajero(), p.getNombre(), p.getCedula(), "Recarga de billetera", detalle,
                m.getMonto(), m.getIdMovimiento(), objetosServicio.Marca.SISTEMA, cuando);
    }

    private static double saldo(String idPasajero) {
        ResultadoOperacion r = new ControladorBilletera().consultarSaldo(idPasajero);
        return r.isExito() ? (Double) r.getDatos() : 0;
    }

    /** Compra directa en el pasado: reserva, cobro, confirmación y factura con la fecha de la compra. */
    private static int venderPasajes(Viaje v, List<Pasajero> pasajeros, int turno, int compradores) throws PersistenciaException, IOException, FachadaException {
        ControladorReservas reservas = new ControladorReservas();
        LocalDateTime salida = ControladorCompras.salidaDe(v);
        int siguienteAsiento = 1;
        for (int i = 0; i < compradores; i++) {
            Pasajero p = pasajeros.get((turno + i) % pasajeros.size());
            int cantidad = 1 + AZAR.nextInt(2);
            List<Integer> asientos = new ArrayList<>();
            for (int a = 0; a < cantidad; a++) asientos.add(siguienteAsiento++);
            LocalDateTime compra = salida.minusDays(1 + AZAR.nextInt(4)).minusHours(1 + AZAR.nextInt(6));
            ResultadoOperacion creada = reservas.crearReserva("TEMP", p.getIdPasajero(), v.getIdViaje(), cantidad, asientos, v.getEmpresa().getIdEmpresa(),
                    new Fecha(compra.getDayOfMonth(), compra.getMonthValue(), compra.getYear()), "BILLETERA", 0);
            if (!creada.isExito()) {
                continue;
            }
            Reserva reserva = creada.getDatosComoTipo(Reserva.class);
            try {
                double total = reserva.getPrecioTotal();
                double falta = total - saldo(p.getIdPasajero());
                if (falta > 0) {
                    recargar(p, Math.ceil((falta + 5) / 5) * 5, compra.minusHours(2));
                }
                movimiento(p.getIdPasajero(), Movimiento.COMPRA, total, Movimiento.METODO_BILLETERA, Movimiento.APROBADA, reserva.getIdReserva(), compra);
                Reserva final_ = FachadaArchivos.getInstance().obten(new Reserva(reserva.getIdReserva()));
                List<String> nums = new ArrayList<>();
                for (Integer n : asientos) nums.add(String.valueOf(n));
                Reserva paraFactura = final_ != null ? final_ : reserva;
                paraFactura.setNumerosAsientos(nums);
                facturaPasaje(paraFactura, p, v, compra);
            } catch (PersistenciaException | IOException | FachadaException | RuntimeException ex) {
                System.err.println("Demo: compra descartada (" + ex.getMessage() + ")");
                reservas.eliminarReserva(reserva.getIdReserva());
            }
        }
        return turno + compradores;
    }

    private static void facturaPasaje(Reserva reserva, Pasajero p, Viaje v, LocalDateTime cuando) throws IOException {
        String ruta = new ControladorCompras().describirRuta(v);
        LocalDateTime salida = ControladorCompras.salidaDe(v);
        String detalle = "Reserva: " + reserva.getIdReserva() + "\nViaje: " + v.getIdViaje() + "\nSalida: " + (salida == null ? "" : salida.format(F))
                + "\nAsientos: " + String.join(", ", reserva.getNumerosAsientos()) + "\nCantidad: " + reserva.getCantidadAsientos()
                + " asiento(s)\nPagado con: billetera " + objetosServicio.Marca.NOMBRE + "\nCompra definitiva: no admite devoluciones";
        new ControladorFacturacion().emitirConFecha(Comprobante.PASAJE, p.getIdPasajero(), p.getNombre(), p.getCedula(), "Pasaje " + ruta, detalle,
                reserva.getPrecioTotal(), reserva.getIdReserva(), "", cuando);
    }

    // ------------------------------------------------------------------ pagos

    /** Los dos viajes más antiguos de cada empresa ya se pagaron; el resto queda PENDIENTE de pago. */
    private static void pagarAlgunos(List<Viaje> pasados) throws IOException {
        ControladorLiquidaciones liq = new ControladorLiquidaciones();
        for (Viaje v : pasados) {
            LocalDateTime salida = ControladorCompras.salidaDe(v);
            if (salida == null || salida.isAfter(LocalDateTime.now().minusDays(6))) {
                continue;
            }
            for (Liquidacion l : liq.todasInterno()) {
                if (l.getIdViaje().equals(v.getIdViaje()) && Liquidacion.PENDIENTE.equals(l.getEstado())) {
                    LocalDateTime llegada = ControladorCompras.llegadaDe(v);
                    liq.pagarDirecto(l.getIdLiquidacion(), (llegada == null ? salida : llegada).plusHours(5), "Tarjeta de débito registrada", "Finanzas");
                    new ControladorNotificaciones().enviarConFecha(l.getIdConductor(), "Pago recibido",
                            String.format("Te pagamos $%.2f por el viaje %s.", l.getMonto(), v.getIdViaje()), l.getIdLiquidacion(), (llegada == null ? salida : llegada).plusHours(5), true);
                }
            }
            try {
                Viaje actual = FachadaArchivos.getInstance().obten(new Viaje(v.getIdViaje()));
                if (actual != null && liq.todasInterno().stream().filter(x -> x.getIdViaje().equals(v.getIdViaje()) && x.estaVigente())
                        .allMatch(x -> Liquidacion.PAGADA.equals(x.getEstado()))) {
                    actual.setEstado("Completado");
                    FachadaArchivos.getInstance().actualiza(actual);
                }
            } catch (FachadaException e) {
                System.err.println("Demo: no se pudo completar el viaje " + v.getIdViaje());
            }
        }
    }

    private static void cancelarUnoConReembolso(ControladorViajes viajes, Empresa empresa, List<Pasajero> pasajeros) throws PersistenciaException, IOException, FachadaException {
        List<Viaje> candidatos = new ArrayList<>();
        for (Viaje v : FachadaArchivos.getInstance().consultaViajes()) {
            LocalDateTime s = ControladorCompras.salidaDe(v);
            if ("Programado".equals(v.getEstado()) && s != null && s.isAfter(LocalDateTime.now().plusDays(1)) && v.getEmpresa() != null
                    && v.getEmpresa().getIdEmpresa().equals(empresa.getIdEmpresa())) {
                candidatos.add(v);
            }
        }
        if (candidatos.isEmpty()) {
            return;
        }
        Viaje v = candidatos.get(candidatos.size() - 1);
        ControladorCompras compras = new ControladorCompras();
        for (int i = 0; i < 3; i++) {
            Pasajero p = pasajeros.get(i);
            recargar(p, 30, LocalDateTime.now().minusHours(3 + i));
            compras.comprar(p.getIdPasajero(), v.getIdViaje(), List.of(1 + i));
        }
        ResultadoOperacion r = viajes.cancelarViaje(v.getIdViaje(), "Falla mecánica del bus (demostración)");
        if (!r.isExito()) {
            System.err.println("Demo: no se pudo cancelar el viaje de ejemplo: " + r.getMensaje());
        }
    }

    // ------------------------------------------------------------------ viaje en curso y futuros

    /** Un viaje que salió hace unos minutos: así se ve el bus moviéndose en el mapa desde el primer arranque. */
    private static int preparaViajeEnCurso(ControladorViajes viajes, List<Empresa> empresas, List<Pasajero> pasajeros) throws PersistenciaException, IOException, FachadaException {
        Empresa e = empresas.get(empresas.size() - 1);
        List<Bus> buses = new ArrayList<>();
        for (Bus b : new Buses("buses.dat").lista()) if (b.getEmpresa() != null && b.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa())) buses.add(b);
        List<Ruta> rutas = new ArrayList<>();
        for (Ruta r : new Rutas("rutas.dat").lista()) if (r.getEmpresa() != null && r.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa()) && r.getTiempoEstimado() >= 2) rutas.add(r);
        List<Conductor> conductores = new ArrayList<>();
        for (Conductor c : new Conductores("conductores.dat").lista()) if (c.getEmpresa() != null && c.getEmpresa().getIdEmpresa().equals(e.getIdEmpresa()) && "ACTIVO".equalsIgnoreCase(c.getEstado())) conductores.add(c);
        if (buses.isEmpty() || rutas.isEmpty() || conductores.isEmpty()) {
            return 0;
        }
        for (int intento = 0; intento < buses.size(); intento++) {
            Conductor cond = conductores.get(conductores.size() - 1 - intento % conductores.size());
            ControladorAsignaciones.Asignacion asig = new ControladorAsignaciones().obtener(cond.getIdConductor());
            Bus bus = asig == null ? null : buses.stream().filter(b -> b.getIdBus().equals(asig.getIdBus())).findFirst().orElse(null);
            if (bus == null) {
                continue;
            }
            Ruta ruta = rutas.get(0);
            LocalDateTime salida = LocalDateTime.now().minusMinutes(25).withSecond(0).withNano(0);
            LocalDateTime llegada = salida.plusMinutes(Math.round(ruta.getTiempoEstimado() * 60));
            ResultadoOperacion r = viajes.programarViaje("", bus.getIdBus(), ruta.getIdRuta(), fecha(salida), fecha(llegada), e.getIdEmpresa(), "Programado", true,
                    salida.format(DateTimeFormatter.ofPattern("HH:mm")), llegada.format(DateTimeFormatter.ofPattern("HH:mm")), ruta.getPrecio(), cond.getIdConductor(), "");
            if (r.isExito() && r.getDatos() instanceof Viaje v) {
                venderPasajes(v, pasajeros, 10, 5);
                new ControladorLiquidaciones().programar(v);
                return 1;
            }
        }
        return 0;
    }

    /** Compras reales (con el flujo normal del sistema) en los viajes futuros ya programados, y su pago PROGRAMADO al conductor. */
    private static void ventasFuturas(List<Pasajero> pasajeros) throws PersistenciaException, IOException, FachadaException {
        ControladorCompras compras = new ControladorCompras();
        ControladorLiquidaciones liq = new ControladorLiquidaciones();
        java.util.Map<String, Integer> porEmpresa = new java.util.HashMap<>();
        int turno = 3;
        for (Viaje v : FachadaArchivos.getInstance().consultaViajes()) {
            LocalDateTime s = ControladorCompras.salidaDe(v);
            if (!"Programado".equals(v.getEstado()) || s == null || !s.isAfter(LocalDateTime.now().plusHours(1)) || v.getEmpresa() == null) {
                continue;
            }
            int hechos = porEmpresa.merge(v.getEmpresa().getIdEmpresa(), 1, Integer::sum);
            if (hechos > 3) {
                continue;
            }
            int asiento = 1;
            for (int i = 0; i < 4; i++) {
                Pasajero p = pasajeros.get((turno++) % pasajeros.size());
                recargar(p, 25 + 5 * AZAR.nextInt(5), LocalDateTime.now().minusHours(2 + i));
                ResultadoOperacion r = compras.comprar(p.getIdPasajero(), v.getIdViaje(), List.of(asiento++));
                if (!r.isExito()) {
                    System.err.println("Demo: compra omitida (" + r.getMensaje() + ")");
                }
            }
            Viaje actual = FachadaArchivos.getInstance().obten(new Viaje(v.getIdViaje()));
            if (actual != null) {
                liq.programar(actual);
            }
        }
    }

    // ------------------------------------------------------------------ novedades y extras

    private static void novedades(List<Pasajero> pasajeros) throws PersistenciaException {
        Novedades dao = new Novedades("novedades.dat");
        List<Conductor> conductores = new Conductores("conductores.dat").lista();
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
            n.setIdConductor("P".equals(d[0]) ? pasajeros.get(c % pasajeros.size()).getIdPasajero() : conductores.get(c % Math.max(1, conductores.size())).getIdConductor());
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
    }

    /** Recargas pendientes y rechazadas (el efectivo lo aprueba finanzas) para que el panel de recargas tenga trabajo. */
    private static void recargasExtra(List<Pasajero> pasajeros) throws PersistenciaException, IOException {
        for (int i = 0; i < 3; i++) {
            movimiento(pasajeros.get(14 + i).getIdPasajero(), Movimiento.RECARGA, 20 + 10 * i, Movimiento.METODO_EFECTIVO, Movimiento.PENDIENTE,
                    "COMP-" + (70000 + i), LocalDateTime.now().minusHours(1 + i));
        }
        movimiento(pasajeros.get(20).getIdPasajero(), Movimiento.RECARGA, 15, Movimiento.METODO_EFECTIVO, Movimiento.RECHAZADA, "COMP-65432", LocalDateTime.now().minusDays(2));
        for (int i = 0; i < pasajeros.size(); i += 3) {
            recargar(pasajeros.get(i), 10 + 5 * (i % 4), LocalDateTime.now().minusDays(1).minusHours(i));
        }
    }
}
