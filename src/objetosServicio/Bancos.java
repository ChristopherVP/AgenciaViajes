package objetosServicio;

import java.util.List;

/** Bancos y cooperativas más usados en Ecuador, para registrar la tarjeta de débito donde se paga a los conductores. */
public final class Bancos {
    public static final List<String> LISTA = List.of(
            "Banco Pichincha", "Banco del Pacífico", "Banco de Guayaquil", "Produbanco", "Banco Bolivariano",
            "Banco Internacional", "Banco del Austro", "Banco de Loja", "Banco Solidario", "Banco General Rumiñahui",
            "Banco de Machala", "Banco Amazonas", "BanEcuador", "Cooperativa JEP", "Cooperativa Jardín Azuay",
            "Cooperativa 29 de Octubre", "Otro banco o cooperativa");

    private Bancos() {
    }

    /** Marca de la tarjeta según su numeración. */
    public static String marca(String digitos) {
        if (digitos == null) return "TARJETA";
        if (digitos.startsWith("4")) return "VISA";
        if (digitos.matches("^(5[1-5]|2[2-7]).*")) return "MASTERCARD";
        if (digitos.startsWith("34") || digitos.startsWith("37")) return "AMEX";
        if (digitos.startsWith("36") || digitos.startsWith("30") || digitos.startsWith("38")) return "DINERS";
        return "TARJETA";
    }
}
