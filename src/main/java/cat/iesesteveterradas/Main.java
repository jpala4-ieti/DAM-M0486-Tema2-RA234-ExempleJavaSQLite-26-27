package cat.iesesteveterradas;

import cat.iesesteveterradas.utils.UtilsSQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;

/**
 * Exemple d'accés a una base de dades SQLite amb JDBC:
 * <ul>
 *   <li>Creació i inicialització de la base de dades (DDL + INSERT).</li>
 *   <li>Metadades: llista de taules (DatabaseMetaData) i columnes (ResultSetMetaData).</li>
 *   <li>Consultes (SELECT) i modificacions (UPDATE, DELETE) amb PreparedStatement.</li>
 * </ul>
 *
 * Criteri de sortida: System.out per a la informació adreçada a l'usuari
 * i el logger (SLF4J + Logback) per als missatges tècnics.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * true: la base de dades es torna a crear a cada execució (útil per a la demostració).
     * false: comportament d'una aplicació real, només s'inicialitza si l'arxiu no existeix.
     */
    private static final boolean REINICIAR_BD = true;

    public static void main(String[] args) {
        String filePath = BaseDadesExemple.obtenirRuta();

        try {
            if (REINICIAR_BD || !new File(filePath).exists()) {
                BaseDadesExemple.inicialitzar(filePath);
            }

            // try-with-resources: la connexió es tanca automàticament en sortir del bloc
            try (Connection conn = UtilsSQLite.connect(filePath)) {

                // Llistar les taules (DatabaseMetaData)
                List<String> taules = UtilsSQLite.listTables(conn);
                System.out.println("Taules existents: " + taules);

                // Informació de les columnes d'un resultat (ResultSetMetaData)
                try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM warehouses")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    System.out.println("\nInformació de la taula 'warehouses':");
                    for (int col = 1; col <= rsmd.getColumnCount(); col++) {
                        System.out.println("    Columna " + col + ": " + rsmd.getColumnLabel(col)
                                + " (Nom: " + rsmd.getColumnName(col)
                                + ", Tipus: " + rsmd.getColumnTypeName(col) + ")");
                    }
                }

                System.out.println("\nContingut inicial de la taula:");
                BaseDadesExemple.mostrarContingut(conn);

                // =================================================================
                // UPDATE i DELETE amb PreparedStatement (segurs davant la injecció SQL)
                // =================================================================
                logger.info("Executant UPDATE i DELETE amb PreparedStatement...");

                // Afegim " Spain" al nom existent ('||' és l'operador SQL de concatenació)
                int filesAfectades = UtilsSQLite.queryUpdatePS(conn,
                        "UPDATE warehouses SET name = name || ? WHERE id = ?", " Spain", 2); // El Corte Inglés
                logger.info("Files actualitzades per afegir el sufix: {}", filesAfectades);

                filesAfectades = UtilsSQLite.queryUpdatePS(conn,
                        "DELETE FROM warehouses WHERE id = ?", 3); // Mecalux
                logger.info("Files esborrades: {}", filesAfectades);

                // Consulta amb paràmetre: el valor viatja separat de l'SQL
                System.out.println("\nMagatzems que contenen 'Spain':");
                try (ResultSet rs = UtilsSQLite.querySelectPS(conn,
                        "SELECT id, name FROM warehouses WHERE name LIKE ?", "%Spain%")) {
                    while (rs.next()) {
                        System.out.println("    " + rs.getInt("id") + ", " + rs.getString("name"));
                    }
                }

                System.out.println("\nContingut de la taula modificada:");
                BaseDadesExemple.mostrarContingut(conn);
            }

        } catch (SQLException e) {
            logger.error("S'ha produït un error en operar amb la base de dades.", e);
        }
    }
}
