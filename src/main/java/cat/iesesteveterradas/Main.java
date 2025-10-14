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
 * Aquest exemple mostra com fer una connexió a SQLite amb Java
 * seguint bones pràctiques i utilitzant un logger per a missatges operacionals.
 */
public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String basePath = System.getProperty("user.dir") + "/data/";
        String filePath = basePath + "database.db";

        try {
            // Per a la demostració, reiniciem la base de dades a cada execució.
            initDatabase(filePath);

            // IMPORTANT PER PRODUCCIÓ: Si no hi ha l'arxiu creat, el crea i li posa dades
            /*
            File fDatabase = new File(filePath);
            if (!fDatabase.exists()) {
                initDatabase(filePath);
            }
            */             

            try (Connection conn = UtilsSQLite.connect(filePath)) {

                // Llistar les taules (sortida d'aplicació)
                List<String> taules = UtilsSQLite.listTables(conn);
                System.out.println("Taules existents: " + taules);

                // Demanar informació de la taula (sortida d'aplicació)
                try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM warehouses;")) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    System.out.println("\nInformació de la taula 'warehouses':");
                    for (int cnt = 1; cnt <= rsmd.getColumnCount(); cnt++) {
                        String label = rsmd.getColumnLabel(cnt);
                        String name = rsmd.getColumnName(cnt);
                        String typeName = rsmd.getColumnTypeName(cnt);
                        System.out.println("    Columna " + cnt + ": " + label + " (Nom: " + name + ", Tipus: " + typeName + ")");
                    }
                }

                // SELECT inicial (sortida d'aplicació)
                System.out.println("\nContingut inicial de la taula:");
                try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM warehouses;")) {
                    while (rs.next()) {
                        System.out.println("    " + rs.getInt("id") + ", " + rs.getString("name"));
                    }
                }

                // =================================================================
                // SECCIÓ D'ACTUALITZACIÓ AMB PREPARED STATEMENTS
                // =================================================================
                logger.info("Executant UPDATE i DELETE amb PreparedStatement...");

                // MODIFICACIÓ: En lloc de substituir, afegim " Spain" al nom existent.
                String sufixPerAfegir = " Spain";
                int idPerActualitzar = 2; // Actualitzarem 'El Corte Inglés'
                int filesAfectades = UtilsSQLite.queryUpdatePS(conn, "UPDATE warehouses SET name = name || ? WHERE id = ?", sufixPerAfegir, idPerActualitzar);
                logger.info("Files actualitzades per afegir sufix: {}", filesAfectades);

                // Esborrar una fila de forma segura
                int idPerEsborrar = 3; // Esborrarem 'Mecalux'
                filesAfectades = UtilsSQLite.queryUpdatePS(conn, "DELETE FROM warehouses WHERE id = ?", idPerEsborrar);
                logger.info("Files esborrades: {}", filesAfectades);
                // =================================================================

                // SELECT final (sortida d'aplicació)
                System.out.println("\nContingut de la taula modificada:");
                try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM warehouses;")) {
                    while (rs.next()) {
                        System.out.println("    " + rs.getInt("id") + ", " + rs.getString("name"));
                    }
                }
            }

        } catch (SQLException e) {
            logger.error("S'ha produït un error en operar amb la base de dades.", e);
        }
    }

    /**
     * Inicialitza la base de dades.
     */
    static void initDatabase(String filePath) throws SQLException {
        try (Connection conn = UtilsSQLite.connect(filePath)) {
            UtilsSQLite.queryUpdate(conn, "DROP TABLE IF EXISTS warehouses;");
            UtilsSQLite.queryUpdate(conn, "CREATE TABLE IF NOT EXISTS warehouses ("
                    + " id integer PRIMARY KEY AUTOINCREMENT,"
                    + " name text NOT NULL);");

            logger.info("Inserint dades inicials...");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "Amazon");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "El Corte Inglés");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "Mecalux");
            logger.info("Dades inicials inserides correctament.");
        }
    }
}