package control;

import excepciones.FachadaException;
import fachadas.FachadaArchivos;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import objetosNegocio.Comprobante;
import objetosNegocio.Conductor;
import objetosNegocio.Liquidacion;
import objetosNegocio.Movimiento;
import objetosNegocio.Pasajero;
import objetosNegocio.Reserva;
import objetosNegocio.Ruta;
import objetosNegocio.Viaje;
import objetosServicio.InformePdf;
import objetosServicio.InformePdf.Documento;
import objetosServicio.Marca;

/** Genera el PDF detallado de UNA operación: una factura, un pasaje, un movimiento de la billetera o un pago a conductor. */
public class ComprobantesPdf {
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final FachadaArchivos fachada = FachadaArchivos.getInstance();

    private static String dinero(double v) {
        return String.format(java.util.Locale.US, "$%.2f", v);
    }

    public static String nombreSugerido(String prefijo, String id) {
        return prefijo + "_" + id.replaceAll("[^A-Za-z0-9-]", "") + ".pdf";
    }

    private static String tipoLegible(String tipo) {
        return switch (tipo) {
            case Comprobante.PASAJE -> "Factura de pasaje";
            case Comprobante.RECARGA -> "Factura de recarga de billetera";
            case Comprobante.LIQUIDACION -> "Factura de pago a conductor";
            case Comprobante.REEMBOLSO -> "Nota de crédito (reembolso)";
            default -> tipo;
        };
    }

    // ------------------------------------------------------------------ factura

    public void factura(File destino, Comprobante c) throws IOException {
        Documento d = new Documento(c.getTipo().equals(Comprobante.REEMBOLSO) ? "Nota de crédito" : "Factura", c.getNumero(), c.getFecha().format(FECHA))
                .estado("EMITIDA")
                .bloque("Cliente", "Nombre", c.getNombreTitular(), "Cédula", c.getCedulaTitular(), "Código", c.getIdTitular())
                .bloque("Emisor", "Emitida por", c.getEmisor(), "Tipo", tipoLegible(c.getTipo()), "Referencia", c.getReferencia())
                .bloque("Concepto", "Descripción", c.getConcepto())
                .bloque("Detalle de la operación", detalleComoPares(c.getDetalle()))
                .bloque("Valores", "Subtotal (sin impuestos)", dinero(c.getTotal()), "IVA", "$0.00 (servicio de transporte terrestre de pasajeros)", "Valor total", dinero(c.getTotal()))
                .total("Total", dinero(c.getTotal()));
        if (c.getTipo().equals(Comprobante.PASAJE)) {
            d.pie("Documento electrónico generado por el sistema " + Marca.NOMBRE + ". Los pasajes comprados no admiten cancelación ni devolución.");
        }
        InformePdf.guardarDocumento(destino, d);
    }

    /** "Clave valor" por línea ("Reserva RES002") -> pares para la tabla; las líneas sin valor se muestran como nota. */
    private static String[] detalleComoPares(String detalle) {
        java.util.List<String> pares = new java.util.ArrayList<>();
        for (String linea : (detalle == null ? "" : detalle).split("\n")) {
            String l = linea.trim();
            if (l.isEmpty()) {
                continue;
            }
            int dp = l.indexOf(": ");
            if (dp > 0 && dp <= 34) {
                pares.add(l.substring(0, dp));
                pares.add(l.substring(dp + 2));
                continue;
            }
            int sp = l.indexOf(' ');
            boolean clave = sp > 0 && sp <= 14 && Character.isUpperCase(l.charAt(0)) && !l.contains(" · ");
            if (clave) {
                pares.add(l.substring(0, sp));
                pares.add(l.substring(sp + 1));
            } else {
                pares.add("Nota");
                pares.add(l);
            }
        }
        return pares.toArray(new String[0]);
    }

    // ------------------------------------------------------------------ pasaje

    public void pasaje(File destino, Reserva r) throws IOException {
        try {
            Viaje v = r.getViaje() == null ? null : fachada.obten(r.getViaje());
            Ruta ruta = v == null ? null : fachada.obten(v.getRuta());
            objetosNegocio.Bus bus = v == null || v.getBus() == null ? null : fachada.obten(v.getBus());
            Pasajero p = r.getPasajero() == null ? null : fachada.obten(r.getPasajero());
            String empresa = r.getEmpresa() == null ? "" : fachada.obten(r.getEmpresa()) == null ? "" : fachada.obten(r.getEmpresa()).getNombre();
            LocalDateTime salida = v == null ? null : ControladorCompras.salidaDe(v);
            LocalDateTime llegada = v == null ? null : ControladorCompras.llegadaDe(v);
            StringBuilder conductores = new StringBuilder();
            if (v != null) {
                for (Conductor c : new Conductor[]{v.getConductor1(), v.getConductor2()}) {
                    if (c != null && c.getIdConductor() != null && !c.getIdConductor().isEmpty()) {
                        Conductor real = new persistencia.Conductores("conductores.dat").obten(new Conductor(c.getIdConductor()));
                        conductores.append(conductores.length() > 0 ? ", " : "").append(real == null ? c.getIdConductor() : real.getNombre());
                    }
                }
            }
            Comprobante f = new ControladorFacturacion().buscarPorReferencia(Comprobante.PASAJE, r.getIdReserva());
            List<String> asientos = r.getNumerosAsientos();
            if (asientos == null || asientos.isEmpty()) {
                asientos = new java.util.ArrayList<>();
                for (objetosNegocio.Asiento a : fachadas.FachadaArchivos.getInstance().consultaAsientosPorViaje(r.getViaje().getIdViaje())) {
                    if (r.getIdReserva().equals(a.getIdReserva())) {
                        asientos.add(String.valueOf(a.getNumeroAsiento()));
                    }
                }
                asientos.sort(java.util.Comparator.comparingInt(Integer::parseInt));
            }
            Documento d = new Documento("Pasaje de bus", r.getIdReserva(), f == null ? (llegada == null ? "" : LocalDateTime.now().format(FECHA)) : f.getFecha().format(FECHA))
                    .estado(r.getEstado() == null ? "" : r.getEstado().toUpperCase())
                    .bloque("Pasajero", "Nombre", p == null ? "" : p.getNombre(), "Cédula", p == null ? "" : p.getCedula(),
                            "Tipo de tarifa", p == null ? "" : p.getTipoPasajero())
                    .bloque("Viaje", "Ruta", ruta == null ? "" : ruta.getOrigen() + " → " + ruta.getDestino(),
                            "Paradas", ruta == null ? "" : ruta.getParadas(),
                            "Salida", salida == null ? "" : salida.format(FECHA), "Llegada estimada", llegada == null ? "" : llegada.format(FECHA),
                            "Distancia", ruta == null ? "" : String.format(java.util.Locale.US, "%.0f km", ruta.getDistancia()),
                            "Código del viaje", v == null ? "" : v.getIdViaje(), "Empresa", empresa)
                    .bloque("Bus y conductor", "Bus (placa)", bus == null ? "" : bus.getPlaca(), "Conductor", conductores.toString())
                    .bloque("Asientos", "Cantidad", String.valueOf(r.getCantidadAsientos()),
                            "Números de asiento", asientos == null || asientos.isEmpty() ? "" : String.join(", ", asientos))
                    .bloque("Pago", "Método", "Billetera " + Marca.NOMBRE, "Factura", f == null ? "" : f.getNumero(),
                            "Total pagado", dinero(r.getPrecioTotal()))
                    .total("Total pagado", dinero(r.getPrecioTotal()))
                    .pie("Presenta este pasaje con tu cédula al abordar. Compra definitiva: no admite cancelación ni devolución. Documento generado por " + Marca.NOMBRE + ".");
            InformePdf.guardarDocumento(destino, d);
        } catch (FachadaException | excepciones.PersistenciaException e) {
            throw new IOException("No se pudo leer el viaje del pasaje: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ billetera

    public void movimiento(File destino, Movimiento m) throws IOException {
        try {
            Pasajero p = new persistencia.Pasajeros("pasajeros.dat").obten(new Pasajero(m.getIdPasajero()));
            // saldo de la billetera justo después de este movimiento
            double saldo = 0;
            List<Movimiento> todos = new persistencia.Movimientos("movimientos.dat").listaPorPasajero(m.getIdPasajero());
            todos.sort(Comparator.comparing(Movimiento::getIdMovimiento));
            for (Movimiento x : todos) {
                if (Movimiento.APROBADA.equals(x.getEstado())) {
                    saldo += Movimiento.COMPRA.equals(x.getTipo()) ? -x.getMonto() : x.getMonto();
                }
                if (x.getIdMovimiento().equals(m.getIdMovimiento())) {
                    break;
                }
            }
            boolean resta = Movimiento.COMPRA.equals(m.getTipo());
            String fecha = m.getFecha() == null ? "" : String.format("%02d/%02d/%04d %s", m.getFecha().getDia(), m.getFecha().getMes(), m.getFecha().getAnio(), m.getHora() == null ? "" : m.getHora());
            String tipo = switch (m.getTipo()) {
                case Movimiento.RECARGA -> "Recarga de saldo";
                case Movimiento.COMPRA -> "Compra de pasaje";
                case Movimiento.REEMBOLSO -> "Reembolso por viaje cancelado";
                default -> m.getTipo();
            };
            Comprobante f = Movimiento.RECARGA.equals(m.getTipo()) ? new ControladorFacturacion().buscarPorReferencia(Comprobante.RECARGA, m.getIdMovimiento())
                    : Movimiento.REEMBOLSO.equals(m.getTipo()) ? new ControladorFacturacion().buscarPorReferencia(Comprobante.REEMBOLSO, m.getIdMovimiento()) : null;
            Documento d = new Documento("Movimiento de billetera", m.getIdMovimiento(), fecha)
                    .estado(m.getEstado())
                    .bloque("Titular de la billetera", "Nombre", p == null ? "" : p.getNombre(), "Cédula", p == null ? "" : p.getCedula(), "Código", m.getIdPasajero())
                    .bloque("Movimiento", "Tipo", tipo, "Método", m.getMetodo(),
                            Movimiento.COMPRA.equals(m.getTipo()) ? "Pasaje (reserva)" : "Referencia", m.getReferencia(),
                            "Estado", m.getEstado(), "Motivo / observación", m.getMotivo(), "Revisado por", m.getIdRevisor())
                    .bloque("Efecto en el saldo", "Monto", (resta ? "-" : "+") + dinero(m.getMonto()),
                            "Saldo después del movimiento", Movimiento.APROBADA.equals(m.getEstado()) ? dinero(saldo) : "No aplica (movimiento no aprobado)",
                            "Factura / nota de crédito", f == null ? "" : f.getNumero())
                    .total(resta ? "Valor debitado" : "Valor acreditado", (resta ? "-" : "+") + dinero(m.getMonto()));
            InformePdf.guardarDocumento(destino, d);
        } catch (excepciones.PersistenciaException e) {
            throw new IOException("No se pudo leer la billetera: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ pago a conductor

    public void pagoConductor(File destino, Liquidacion l) throws IOException {
        String fecha = (l.getPagadoEn() != null ? l.getPagadoEn() : l.getNotificadoEn() == null ? LocalDateTime.now() : l.getNotificadoEn()).format(FECHA);
        Comprobante f = new ControladorFacturacion().buscarPorReferencia(Comprobante.LIQUIDACION, l.getIdLiquidacion());
        String estado = switch (l.getEstado()) {
            case Liquidacion.PROGRAMADA -> "PROGRAMADO";
            case Liquidacion.PENDIENTE -> "PENDIENTE DE PAGO";
            default -> l.getEstado();
        };
        Documento d = new Documento("Pago a conductor", l.getIdLiquidacion(), fecha).estado(estado)
                .bloque("Conductor", "Nombre", l.getNombreConductor(), "Código", l.getIdConductor(), "Empresa", l.getEmpresa())
                .bloque("Viaje", "Código", l.getIdViaje(), "Ruta", l.getRuta(), "Salida", l.getFechaViaje(), "Llegada", l.getLlegadaViaje())
                .bloque("Cálculo del pago", "Pasajeros transportados", String.valueOf(l.getPasajeros()), "Recaudado en pasajes", dinero(l.getRecaudado()),
                        "Participación del conductor", (int) Liquidacion.PORCENTAJE_CONDUCTOR + " %" + (l.getPartes() > 1 ? " (dividida entre " + l.getPartes() + " conductores)" : ""),
                        "Margen de la plataforma (20 %)", dinero(l.getComisionPlataforma()))
                .bloque("Depósito", "Tarjeta de débito", l.getTarjeta(), "Pagado por", l.getPagadoPor(),
                        "Fecha de pago", l.getPagadoEn() == null ? "Aún no pagado" : l.getPagadoEn().format(FECHA), "Factura", f == null ? "" : f.getNumero())
                .total(Liquidacion.PAGADA.equals(l.getEstado()) ? "Valor pagado" : "Valor a pagar", dinero(l.getMonto()));
        InformePdf.guardarDocumento(destino, d);
    }
}
