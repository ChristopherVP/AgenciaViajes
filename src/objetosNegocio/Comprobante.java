package objetosNegocio;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Factura o comprobante emitido por el sistema: por la compra de un pasaje, por una recarga acreditada a la
 * billetera o por la liquidación (pago) a un conductor. Cada persona solo ve las suyas; la administración ve todas.
 */
public class Comprobante {
    public static final String PASAJE = "PASAJE";
    public static final String RECARGA = "RECARGA";
    public static final String LIQUIDACION = "LIQUIDACION";
    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private String numero = "";
    private String tipo = PASAJE;
    private String idTitular = "";
    private String nombreTitular = "";
    private String cedulaTitular = "";
    private String concepto = "";
    private String detalle = "";
    private double total;
    private LocalDateTime fecha = LocalDateTime.now();
    private String referencia = "";
    private String emisor = "";

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getIdTitular() { return idTitular; }
    public void setIdTitular(String idTitular) { this.idTitular = idTitular; }
    public String getNombreTitular() { return nombreTitular; }
    public void setNombreTitular(String nombreTitular) { this.nombreTitular = nombreTitular; }
    public String getCedulaTitular() { return cedulaTitular; }
    public void setCedulaTitular(String cedulaTitular) { this.cedulaTitular = cedulaTitular; }
    public String getConcepto() { return concepto; }
    public void setConcepto(String concepto) { this.concepto = concepto; }
    public String getDetalle() { return detalle; }
    public void setDetalle(String detalle) { this.detalle = detalle; }
    public double getTotal() { return total; }
    public void setTotal(double total) { this.total = Math.round(total * 100) / 100.0; }
    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }
    public String getReferencia() { return referencia; }
    public void setReferencia(String referencia) { this.referencia = referencia; }
    public String getEmisor() { return emisor; }
    public void setEmisor(String emisor) { this.emisor = emisor; }

    public String getTipoEtiqueta() {
        switch (tipo) {
            case RECARGA: return "Recarga de billetera";
            case LIQUIDACION: return "Pago a conductor";
            default: return "Pasaje";
        }
    }

    public String[] aFila() {
        return new String[]{numero, tipo, idTitular, nombreTitular, cedulaTitular, concepto, detalle, String.valueOf(total),
                fecha.format(FORMATO), referencia, emisor};
    }

    public static Comprobante deFila(String[] f) {
        Comprobante c = new Comprobante();
        c.numero = f[0];
        c.tipo = f[1];
        c.idTitular = f[2];
        c.nombreTitular = f[3];
        c.cedulaTitular = f[4];
        c.concepto = f[5];
        c.detalle = f[6];
        c.total = Double.parseDouble(f[7]);
        c.fecha = LocalDateTime.parse(f[8], FORMATO);
        c.referencia = f[9];
        c.emisor = f[10];
        return c;
    }
}
