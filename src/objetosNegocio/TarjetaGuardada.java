package objetosNegocio;

/**
 * Tarjeta registrada de una persona. Por seguridad NUNCA se guarda el número completo ni el CVV: solo el banco, la marca,
 * el titular, el vencimiento y los últimos 4 dígitos. Para los conductores es la tarjeta de DÉBITO donde se les paga.
 */
public class TarjetaGuardada {
    public static final String COBRO = "COBRO";
    public static final String RECARGA = "RECARGA";

    private String idTitular = "";
    private String uso = COBRO;
    private String banco = "";
    private String marca = "";
    private String ultimos4 = "";
    private String titular = "";
    private String vence = "";

    public TarjetaGuardada() {
    }

    public TarjetaGuardada(String idTitular, String uso, String banco, String marca, String ultimos4, String titular, String vence) {
        this.idTitular = idTitular;
        this.uso = uso;
        this.banco = banco;
        this.marca = marca;
        this.ultimos4 = ultimos4;
        this.titular = titular;
        this.vence = vence;
    }

    public String getIdTitular() { return idTitular; }
    public String getUso() { return uso; }
    public String getBanco() { return banco; }
    public String getMarca() { return marca; }
    public String getUltimos4() { return ultimos4; }
    public String getTitular() { return titular; }
    public String getVence() { return vence; }

    /** Texto corto para mostrar: "Banco Pichincha · VISA ••••1234". */
    public String resumen() {
        return (banco.isEmpty() ? "" : banco + " · ") + marca + " ••••" + ultimos4;
    }

    public String[] aFila() {
        return new String[]{idTitular, uso, banco, marca, ultimos4, titular, vence};
    }

    public static TarjetaGuardada deFila(String[] f) {
        return new TarjetaGuardada(f[0], f[1], f[2], f[3], f[4], f[5], f[6]);
    }
}
