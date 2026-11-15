package objetosNegocio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Pago a un conductor por un viaje: el 80 % de lo recaudado en pasajes de ese viaje (dividido entre los conductores del
 * viaje). Ciclo de vida:
 * <ul>
 *   <li>PROGRAMADA: el conductor aceptó el viaje. Aparece en la lista de pagos pero NO se puede pagar todavía.</li>
 *   <li>PENDIENTE: el viaje llegó a su destino (terminó la simulación). Ya se puede pagar y el monto es el definitivo.</li>
 *   <li>PAGADA: finanzas la pagó a la tarjeta de débito del conductor y se emitió la factura.</li>
 *   <li>CANCELADA: el viaje no se realizó (el administrador aceptó la cancelación). Sale de las listas.</li>
 * </ul>
 */
public class Liquidacion {
    public static final String PENDIENTE = "PENDIENTE";
    public static final String PAGADA = "PAGADA";
    public static final String PROGRAMADA = "PROGRAMADA";
    public static final String CANCELADA = "CANCELADA";
    public static final double PORCENTAJE_CONDUCTOR = 80.0;
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String idLiquidacion = "";
    private String idViaje = "";
    private String idConductor = "";
    private String nombreConductor = "";
    private String empresa = "";
    private String ruta = "";
    private String fechaViaje = "";
    private int pasajeros;
    private double recaudado;
    private int partes = 1;
    private double monto;
    private String estado = PENDIENTE;
    private LocalDateTime notificadoEn = LocalDateTime.now();
    private LocalDateTime pagadoEn;
    private String pagadoPor = "";
    private String tarjeta = "";
    private String numeroFactura = "";
    private String llegadaViaje = "";

    public String getIdLiquidacion() { return idLiquidacion; }
    public void setIdLiquidacion(String v) { this.idLiquidacion = v; }
    public String getIdViaje() { return idViaje; }
    public void setIdViaje(String v) { this.idViaje = v; }
    public String getIdConductor() { return idConductor; }
    public void setIdConductor(String v) { this.idConductor = v; }
    public String getNombreConductor() { return nombreConductor; }
    public void setNombreConductor(String v) { this.nombreConductor = v; }
    public String getEmpresa() { return empresa; }
    public void setEmpresa(String v) { this.empresa = v; }
    public String getRuta() { return ruta; }
    public void setRuta(String v) { this.ruta = v; }
    public String getFechaViaje() { return fechaViaje; }
    public void setFechaViaje(String v) { this.fechaViaje = v; }
    public int getPasajeros() { return pasajeros; }
    public void setPasajeros(int v) { this.pasajeros = v; }
    public double getRecaudado() { return recaudado; }
    public void setRecaudado(double v) { this.recaudado = Math.round(v * 100) / 100.0; }
    public int getPartes() { return partes; }
    public void setPartes(int v) { this.partes = Math.max(1, v); }
    public double getMonto() { return monto; }
    public void setMonto(double v) { this.monto = Math.round(v * 100) / 100.0; }
    public String getEstado() { return estado; }
    public void setEstado(String v) { this.estado = v; }
    public LocalDateTime getNotificadoEn() { return notificadoEn; }
    public void setNotificadoEn(LocalDateTime v) { this.notificadoEn = v; }
    public LocalDateTime getPagadoEn() { return pagadoEn; }
    public void setPagadoEn(LocalDateTime v) { this.pagadoEn = v; }
    public String getPagadoPor() { return pagadoPor; }
    public void setPagadoPor(String v) { this.pagadoPor = v; }
    public String getTarjeta() { return tarjeta; }
    public void setTarjeta(String v) { this.tarjeta = v; }
    public String getLlegadaViaje() { return llegadaViaje; }
    public void setLlegadaViaje(String v) { this.llegadaViaje = v == null ? "" : v; }
    public String getNumeroFactura() { return numeroFactura; }
    public void setNumeroFactura(String v) { this.numeroFactura = v; }

    public boolean esPagable() { return PENDIENTE.equals(estado); }
    public boolean estaVigente() { return !CANCELADA.equals(estado); }

    /** Monto que corresponde a la plataforma por esta parte del viaje (el 20 % restante). */
    public double getComisionPlataforma() {
        return Math.round((recaudado / partes - monto) * 100) / 100.0;
    }

    public String[] aFila() {
        return new String[]{idLiquidacion, idViaje, idConductor, nombreConductor, empresa, ruta, fechaViaje, String.valueOf(pasajeros),
                String.valueOf(recaudado), String.valueOf(partes), String.valueOf(monto), estado, notificadoEn.format(FORMATO),
                pagadoEn == null ? "" : pagadoEn.format(FORMATO), pagadoPor, tarjeta, numeroFactura, llegadaViaje};
    }

    public static Liquidacion deFila(String[] f) {
        Liquidacion l = new Liquidacion();
        l.idLiquidacion = f[0];
        l.idViaje = f[1];
        l.idConductor = f[2];
        l.nombreConductor = f[3];
        l.empresa = f[4];
        l.ruta = f[5];
        l.fechaViaje = f[6];
        l.pasajeros = Integer.parseInt(f[7]);
        l.recaudado = Double.parseDouble(f[8]);
        l.partes = Integer.parseInt(f[9]);
        l.monto = Double.parseDouble(f[10]);
        l.estado = f[11];
        l.notificadoEn = LocalDateTime.parse(f[12], FORMATO);
        l.pagadoEn = f[13].isEmpty() ? null : LocalDateTime.parse(f[13], FORMATO);
        l.pagadoPor = f[14];
        l.tarjeta = f[15];
        l.numeroFactura = f[16];
        l.llegadaViaje = f.length > 17 ? f[17] : "";
        return l;
    }
}
