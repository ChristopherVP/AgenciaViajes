package control;

import excepciones.FachadaException;
import fachadas.FachadaArchivos;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import objetosNegocio.Conductor;
import objetosNegocio.Liquidacion;
import objetosNegocio.Usuario;
import objetosNegocio.Viaje;
import objetosServicio.InformePdf;
import objetosServicio.Permisos;
import objetosServicio.PeriodoInforme;
import persistencia.Conductores;

/**
 * Informe de los viajes de un periodo (quincena, mes o año): cuántos viajes hubo por estado, pasajeros transportados, dinero
 * recaudado, pagos a conductores, margen de la plataforma y el detalle viaje por viaje. Un operador solo ve su empresa.
 */
public final class InformeViajesPeriodo {

    /** Contenido ya calculado: sirve igual para PDF que para Excel. */
    public static final class Informe {
        public String titulo;
        public String periodo;
        public String[] etiquetas;
        public String[] valores;
        public Map<String, Integer> viajesPorEstado = new LinkedHashMap<>();
        public List<String> cabecera = new ArrayList<>();
        public List<List<String>> filas = new ArrayList<>();
        public int viajes;
        public double recaudado;
        public double pagoConductores;
        public double margen;
    }

    private InformeViajesPeriodo() {
    }

    private static String dinero(double v) {
        return String.format("$%.2f", v);
    }

    public static Informe construir(Usuario actor, PeriodoInforme periodo, LocalDate referencia) throws IOException {
        if (!Permisos.puedeVer(actor, Permisos.Modulo.VIAJES)) {
            throw new IOException("Tu cargo no tiene acceso a los viajes");
        }
        PeriodoInforme.Rango rango = periodo.rango(referencia);
        Informe inf = new Informe();
        inf.titulo = "Informe de viajes · " + periodo;
        inf.periodo = rango.etiqueta();
        inf.cabecera = List.of("Viaje", "Empresa", "Ruta", "Salida", "Bus", "Conductor", "Estado", "Pasajeros", "Recaudado", "Pago conductor (80 %)", "Margen (20 %)");
        try {
            FachadaArchivos fachada = FachadaArchivos.getInstance();
            ControladorLiquidaciones liquidaciones = new ControladorLiquidaciones();
            Map<String, String> nombresConductor = new LinkedHashMap<>();
            for (Conductor c : new Conductores("conductores.dat").lista()) {
                nombresConductor.put(c.getIdConductor(), c.getNombre());
            }
            int pasajeros = 0;
            List<Viaje> ordenados = new ArrayList<>(fachada.consultaViajes());
            ordenados.sort((a, b) -> {
                java.time.LocalDateTime sa = ControladorCompras.salidaDe(a);
                java.time.LocalDateTime sb = ControladorCompras.salidaDe(b);
                return sa == null || sb == null ? 0 : sa.compareTo(sb);
            });
            for (Viaje v : ordenados) {
                java.time.LocalDateTime salida = ControladorCompras.salidaDe(v);
                if (salida == null || !rango.contiene(salida.toLocalDate())) {
                    continue;
                }
                if (actor.tieneEmpresa() && (v.getEmpresa() == null || !actor.getEmpresa().getIdEmpresa().equals(v.getEmpresa().getIdEmpresa()))) {
                    continue;
                }
                String estado = v.getEstado() == null ? "" : v.getEstado();
                boolean cancelado = ControladorSimulacion.CANCELADO.equalsIgnoreCase(estado);
                double[] rec = cancelado ? new double[]{0, 0} : liquidaciones.recaudoDe(v.getIdViaje());
                double pago = Math.round(rec[0] * Liquidacion.PORCENTAJE_CONDUCTOR) / 100.0;
                double margen = Math.round((rec[0] - pago) * 100) / 100.0;
                inf.viajes++;
                inf.viajesPorEstado.merge(estado.isEmpty() ? "Sin estado" : estado, 1, Integer::sum);
                inf.recaudado += rec[0];
                inf.pagoConductores += pago;
                inf.margen += margen;
                pasajeros += (int) rec[1];
                String idC = v.getConductor1() == null ? "" : v.getConductor1().getIdConductor();
                String empresa = v.getEmpresa() == null ? "" : v.getEmpresa().getIdEmpresa();
                inf.filas.add(List.of(v.getIdViaje(), empresa, new ControladorCompras().describirRuta(v), salida.format(ControladorLiquidaciones.FECHA),
                        v.getBus() == null ? "" : v.getBus().getIdBus(), nombresConductor.getOrDefault(idC, idC), estado, String.valueOf((int) rec[1]),
                        dinero(rec[0]), dinero(pago), dinero(margen)));
            }
            ControladorFacturacion.Resumen caja = new ControladorFacturacion().resumen();
            inf.etiquetas = new String[]{"Viajes del periodo", "Pasajeros", "Recaudado", "Pago a conductores", "Margen plataforma", "Dinero total en caja (hoy)"};
            inf.valores = new String[]{String.valueOf(inf.viajes), String.valueOf(pasajeros), dinero(inf.recaudado), dinero(inf.pagoConductores),
                    dinero(inf.margen), dinero(caja.cajaEmpresa)};
        } catch (FachadaException | excepciones.PersistenciaException e) {
            throw new IOException("No se pudieron leer los viajes: " + e.getMessage(), e);
        }
        return inf;
    }

    public static void escribirPdf(Informe inf, Usuario actor, File destino) throws IOException {
        String sub = "Periodo: " + inf.periodo + " · Elaborado por " + actor.getNombre();
        InformePdf pdf = new InformePdf(inf.titulo, sub, objetosServicio.Marca.SISTEMA);
        pdf.seccion("Resumen del periodo");
        pdf.indicadores(new String[]{inf.etiquetas[0], inf.etiquetas[1], inf.etiquetas[2]}, new String[]{inf.valores[0], inf.valores[1], inf.valores[2]});
        pdf.indicadores(new String[]{inf.etiquetas[3], inf.etiquetas[4], inf.etiquetas[5]}, new String[]{inf.valores[3], inf.valores[4], inf.valores[5]});
        if (!inf.viajesPorEstado.isEmpty()) {
            List<Double> valores = new ArrayList<>();
            inf.viajesPorEstado.values().forEach(n -> valores.add((double) n));
            pdf.barras("Viajes por estado", new ArrayList<>(inf.viajesPorEstado.keySet()), valores, "");
        }
        pdf.seccion("Detalle de viajes");
        pdf.tabla(null, inf.cabecera, inf.filas);
        pdf.guardar(destino);
    }

    /** Filas para Excel: el detalle más una fila final con los totales. */
    public static List<List<String>> filasExcel(Informe inf) {
        List<List<String>> filas = new ArrayList<>(inf.filas);
        filas.add(List.of("TOTAL", "", inf.viajes + " viaje(s)", "", "", "", "", "", dinero(inf.recaudado), dinero(inf.pagoConductores), dinero(inf.margen)));
        return filas;
    }
}
