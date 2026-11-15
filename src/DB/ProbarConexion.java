package DB;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class ProbarConexion {
    private ProbarConexion() {
    }

    public static void main(String[] args) {
        try (Connection conexion = ConexionOracle.abrir();
             Statement consulta = conexion.createStatement();
             ResultSet resultado = consulta.executeQuery("SELECT USER FROM DUAL")) {
            System.out.println("Oracle: " + conexion.getMetaData().getDatabaseProductVersion());
            if (resultado.next()) {
                System.out.println("Usuario conectado: " + resultado.getString(1));
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
            System.exit(1);
        } catch (SQLException e) {
            System.err.println("Error de conexión (código " + e.getErrorCode() + "): " + e.getMessage());
            System.exit(1);
        }
    }
}