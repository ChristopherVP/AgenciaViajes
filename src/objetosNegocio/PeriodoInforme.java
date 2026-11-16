package objetosServicio;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** Periodo de un informe: quincenal (1-15 y 16-fin de mes), mensual o anual. Mismo criterio que los reportes de EcuMail. */
public enum PeriodoInforme {
    QUINCENAL("Quincenal (1-15 y 16-fin de mes)"),
    MENSUAL("Mensual"),
    ANUAL("Anual");

    /** Rango [desde, hasta) con una etiqueta lista para mostrar. */
    public record Rango(LocalDate desde, LocalDate hasta, String etiqueta) {
        public boolean contiene(LocalDate dia) {
            return dia != null && !dia.isBefore(desde) && dia.isBefore(hasta);
        }
    }

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Locale ES = Locale.forLanguageTag("es");

    private final String descripcion;

    PeriodoInforme(String descripcion) {
        this.descripcion = descripcion;
    }

    public Rango rango(LocalDate referencia) {
        switch (this) {
            case QUINCENAL: {
                LocalDate primero = referencia.withDayOfMonth(1);
                LocalDate inicio = referencia.getDayOfMonth() <= 15 ? primero : primero.plusDays(15);
                LocalDate fin = referencia.getDayOfMonth() <= 15 ? primero.plusDays(15) : primero.plusMonths(1);
                return new Rango(inicio, fin, inicio.format(FORMATO) + " al " + fin.minusDays(1).format(FORMATO));
            }
            case MENSUAL: {
                LocalDate inicio = referencia.withDayOfMonth(1);
                String mes = referencia.getMonth().getDisplayName(TextStyle.FULL, ES);
                return new Rango(inicio, inicio.plusMonths(1), mes.substring(0, 1).toUpperCase() + mes.substring(1) + " " + referencia.getYear());
            }
            default: {
                LocalDate inicio = referencia.withDayOfYear(1);
                return new Rango(inicio, inicio.plusYears(1), "Año " + referencia.getYear());
            }
        }
    }

    @Override
    public String toString() {
        return descripcion;
    }
}
