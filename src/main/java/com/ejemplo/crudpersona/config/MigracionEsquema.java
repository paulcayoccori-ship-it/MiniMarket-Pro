package com.ejemplo.crudpersona.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Actualiza bases SQLite creadas con versiones anteriores del sistema.
 * <p>Hibernate ({@code ddl-auto=update}) agrega columnas nuevas, pero no modifica las
 * restricciones CHECK que ya existen. Las versiones anteriores exigian cantidades enteras
 * (minimo 1) y no conocian el movimiento "stock inicial", lo que impedia vender por kilo
 * (0.5 kg) o registrar productos con stock inicial ("CHECK constraint failed").</p>
 * <p>SQLite no permite cambiar un CHECK con ALTER TABLE, asi que la tabla se reconstruye
 * con el procedimiento recomendado por SQLite: se crea una copia sin esas restricciones,
 * se pasan los datos, se reemplaza la tabla y se recrean sus indices. No se pierde ningun dato.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@DependsOn("entityManagerFactory")
public class MigracionEsquema {

    /** Tablas con columnas de cantidad que ahora admiten decimales. */
    private static final List<String> TABLAS = List.of("venta_detalle", "compra_detalle", "movimiento_inventario");

    private final DataSource dataSource;

    @PostConstruct
    public void migrar() {
        try (Connection con = dataSource.getConnection()) {
            if (!esSqlite(con)) {
                return;
            }
            for (String tabla : TABLAS) {
                String sql = sqlTabla(con, tabla);
                if (sql == null) {
                    continue;
                }
                String nuevo = quitarChecksObsoletos(sql);
                if (!nuevo.equals(sql)) {
                    reconstruir(con, tabla, nuevo);
                    log.info("Esquema actualizado: restricciones antiguas eliminadas de la tabla {}", tabla);
                }
            }
        } catch (SQLException e) {
            log.error("No se pudo actualizar el esquema de la base de datos: {}", e.getMessage());
        }
    }

    /**
     * Quita los CHECK que limitan la cantidad a enteros >= 1 y la lista cerrada de tipos de
     * movimiento. Las demas restricciones se conservan.
     */
    static String quitarChecksObsoletos(String sql) {
        StringBuilder salida = new StringBuilder();
        String minusculas = sql.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < sql.length()) {
            int inicio = minusculas.indexOf("check", i);
            if (inicio < 0) {
                salida.append(sql, i, sql.length());
                break;
            }
            int abre = minusculas.indexOf('(', inicio);
            boolean esPalabra = (inicio == 0 || !Character.isLetterOrDigit(minusculas.charAt(inicio - 1)))
                    && abre > 0 && minusculas.substring(inicio + 5, abre).isBlank();
            if (!esPalabra) {
                salida.append(sql, i, inicio + 5);
                i = inicio + 5;
                continue;
            }
            int cierra = cierreDe(sql, abre);
            String condicion = minusculas.substring(abre, cierra + 1);
            boolean obsoleto = condicion.contains("cantidad") || condicion.contains("'anulacion_compra'");
            // Se quita tambien el espacio previo para dejar el SQL limpio.
            int corte = inicio;
            while (corte > i && Character.isWhitespace(sql.charAt(corte - 1))) {
                corte--;
            }
            if (obsoleto) {
                salida.append(sql, i, corte);
            } else {
                salida.append(sql, i, cierra + 1);
            }
            i = cierra + 1;
        }
        return salida.toString();
    }

    private static int cierreDe(String sql, int abre) {
        int nivel = 0;
        for (int j = abre; j < sql.length(); j++) {
            char c = sql.charAt(j);
            if (c == '(') {
                nivel++;
            } else if (c == ')' && --nivel == 0) {
                return j;
            }
        }
        return sql.length() - 1;
    }

    private static void reconstruir(Connection con, String tabla, String sqlNuevo) throws SQLException {
        List<String> indices = new ArrayList<>();
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type='index' AND tbl_name='"
                     + tabla + "' AND sql IS NOT NULL")) {
            while (rs.next()) {
                indices.add(rs.getString(1));
            }
        }
        String temporal = tabla + "_migracion";
        String crearTemporal = sqlNuevo.replaceFirst("(?i)create\\s+table\\s+(\"?)" + tabla + "\\1",
                "CREATE TABLE " + temporal);

        boolean autoCommit = con.getAutoCommit();
        boolean clavesForaneas;
        try (Statement st = con.createStatement(); ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            clavesForaneas = rs.next() && rs.getInt(1) == 1;
        }
        try (Statement st = con.createStatement()) {
            st.execute("PRAGMA foreign_keys=OFF");
            con.setAutoCommit(false);
            st.execute("DROP TABLE IF EXISTS " + temporal);
            st.execute(crearTemporal);
            st.execute("INSERT INTO " + temporal + " SELECT * FROM " + tabla);
            st.execute("DROP TABLE " + tabla);
            st.execute("ALTER TABLE " + temporal + " RENAME TO " + tabla);
            for (String indice : indices) {
                st.execute(indice);
            }
            con.commit();
        } catch (SQLException e) {
            con.rollback();
            throw e;
        } finally {
            con.setAutoCommit(autoCommit);
            if (clavesForaneas) {
                try (Statement st = con.createStatement()) {
                    st.execute("PRAGMA foreign_keys=ON");
                }
            }
        }
    }

    private static String sqlTabla(Connection con, String tabla) throws SQLException {
        try (Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT sql FROM sqlite_master WHERE type='table' AND name='" + tabla + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static boolean esSqlite(Connection con) throws SQLException {
        return con.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
    }
}
