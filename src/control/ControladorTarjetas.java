package control;

import excepciones.Validador;
import java.io.IOException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import objetosNegocio.TarjetaGuardada;
import objetosServicio.Bancos;
import persistencia.AlmacenTexto;

/**
 * Tarjeta de débito donde se le paga al conductor. Se valida como una tarjeta real (banco de la lista, número con Luhn,
 * marca de débito conocida, vencimiento vigente y titular igual al nombre del conductor) y solo se guarda lo mínimo:
 * banco, marca, titular, vencimiento y los últimos 4 dígitos.
 */
public class ControladorTarjetas {
    private static final AlmacenTexto ALMACEN = new AlmacenTexto("tarjetas.dat");

    /** null si los datos sirven; si no, el mensaje del problema. */
    public static String validarTarjetaCobro(String banco, String numero, String titular, String vence, String nombreConductor) {
        if (banco == null || !Bancos.LISTA.contains(banco)) {
            return "Elige el banco o cooperativa de tu tarjeta de débito";
        }
        String d = numero == null ? "" : numero.replaceAll("[\\s-]", "");
        if (!d.matches("\\d{13,19}")) {
            return "El número de tarjeta debe tener entre 13 y 19 dígitos";
        }
        if (!Validador.esTarjetaValida(d)) {
            return "El número de tarjeta no es válido, revísalo";
        }
        String marca = Bancos.marca(d);
        if (!marca.equals("VISA") && !marca.equals("MASTERCARD")) {
            return "Solo se aceptan tarjetas de débito Visa o Mastercard (también Maestro y Visa Electron)";
        }
        if (vence == null || !vence.matches("(0[1-9]|1[0-2])/\\d{2}")) {
            return "Elige el mes y el año de vencimiento de la tarjeta";
        }
        String[] p = vence.split("/");
        YearMonth ym = YearMonth.of(2000 + Integer.parseInt(p[1]), Integer.parseInt(p[0]));
        if (ym.isBefore(YearMonth.from(LocalDate.now()))) {
            return "La tarjeta está vencida";
        }
        if (ym.isAfter(YearMonth.from(LocalDate.now()).plusYears(12))) {
            return "La fecha de vencimiento de la tarjeta no es válida";
        }
        if (titular == null || normalizar(titular).isEmpty() || !normalizar(titular).equals(normalizar(nombreConductor))) {
            return "La tarjeta debe estar a tu nombre: el titular tiene que ser igual a tu nombre completo";
        }
        return null;
    }

    /** Solo el número: longitud, algoritmo de Luhn y que sea Visa o Mastercard. null si está bien. */
    public static String errorNumero(String numero) {
        String d = numero == null ? "" : numero.replaceAll("[\\s-]", "");
        if (d.length() < 13) {
            return "El número de tarjeta debe tener entre 13 y 19 dígitos";
        }
        if (!Validador.esTarjetaValida(d)) {
            return "El número de tarjeta no es válido, revísalo";
        }
        String marca = Bancos.marca(d);
        return marca.equals("VISA") || marca.equals("MASTERCARD") ? null : "Solo se aceptan tarjetas de débito Visa o Mastercard";
    }

    private static String normalizar(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("\\s+", " ").trim().toUpperCase();
    }

    /** Guarda (reemplaza) la tarjeta de cobro del conductor. Los datos ya deben haberse validado. */
    public synchronized ResultadoOperacion guardarTarjetaCobro(String idConductor, String banco, String numero, String titular, String vence) {
        try {
            String d = numero.replaceAll("[\\s-]", "");
            TarjetaGuardada t = new TarjetaGuardada(idConductor, TarjetaGuardada.COBRO, banco, Bancos.marca(d), d.substring(d.length() - 4), normalizar(titular), vence);
            List<String[]> filas = new ArrayList<>();
            for (String[] f : ALMACEN.leer()) {
                if (!(f[0].equals(idConductor) && f[1].equals(TarjetaGuardada.COBRO))) {
                    filas.add(f);
                }
            }
            filas.add(t.aFila());
            ALMACEN.reescribir(filas);
            return new ResultadoOperacion(true, "Tarjeta de cobro guardada: " + t.resumen(), t);
        } catch (IOException e) {
            return new ResultadoOperacion(false, "No se pudo guardar la tarjeta: " + e.getMessage());
        }
    }

    /** Valida y guarda la tarjeta de un conductor ya registrado (desde su perfil). */
    public ResultadoOperacion cambiarTarjetaCobro(objetosNegocio.Usuario conductor, String banco, String numero, String vence) {
        if (conductor == null || conductor.getRol() != objetosNegocio.Rol.CONDUCTOR) {
            return new ResultadoOperacion(false, "Solo los conductores registran una tarjeta de cobro");
        }
        String problema = validarTarjetaCobro(banco, numero, conductor.getNombre(), vence, conductor.getNombre());
        if (problema != null) {
            return new ResultadoOperacion(false, problema);
        }
        return guardarTarjetaCobro(conductor.getIdPerfil(), banco, numero, conductor.getNombre(), vence);
    }

    public TarjetaGuardada obtenerTarjetaCobro(String idConductor) {
        try {
            for (String[] f : ALMACEN.leer()) {
                if (f[0].equals(idConductor) && f[1].equals(TarjetaGuardada.COBRO)) {
                    return TarjetaGuardada.deFila(f);
                }
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer las tarjetas: " + e.getMessage());
        }
        return null;
    }
}
