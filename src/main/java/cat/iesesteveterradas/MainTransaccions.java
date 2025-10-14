package cat.iesesteveterradas;

import cat.iesesteveterradas.utils.UtilsSQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * Aquest exemple mostra com executar una transacció a SQLite amb Java,
 * incloent un exemple de rollback forçat.
 */
public class MainTransaccions {

    private static final Logger logger = LoggerFactory.getLogger(MainTransaccions.class);

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

                // Mostra l'estat inicial de la taula
                System.out.println("\nContingut de la taula abans de qualsevol transacció:");
                printWarehouses(conn);

                // =================================================================
                // 1. TRANSACCIÓ FALLIDA (PROVOCA ROLLBACK)
                // =================================================================
                logger.warn("Iniciant transacció dissenyada per FALLAR...");
                try {
                    conn.setAutoCommit(false);
                    UtilsSQLite.queryUpdatePS(conn, "UPDATE warehouses SET name = ? WHERE id = ?", "Nom Temporal", 1);
                    logger.error("Intentant inserir un valor invàlid (NULL) per provocar un error...");
                    UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", (Object) null);
                    conn.commit();
                } catch (SQLException e) {
                    logger.error("S'ha produït l'error esperat. Fent rollback...", e.getMessage());
                    if (conn != null) {
                        try {
                            conn.rollback();
                            logger.info("Rollback realitzat correctament.");
                        } catch (SQLException ex) {
                            logger.error("Error crític en intentar fer rollback.", ex);
                        }
                    }
                } finally {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                    }
                }

                // Mostra l'estat de la taula DESPRÉS del rollback
                System.out.println("\nContingut de la taula després del ROLLBACK:");
                System.out.println("(hauria de ser igual que l'estat inicial)");
                printWarehouses(conn);

                // =================================================================
                // 2. TRANSACCIÓ CORRECTA (PROVOCA COMMIT)
                // =================================================================
                logger.info("Iniciant transacció dissenyada per funcionar CORRECTAMENT...");
                try {
                    conn.setAutoCommit(false);

                    // MODIFICACIÓ: En lloc de substituir el nom, l'actualitzem per afegir " Spain"
                    // L'operador '||' és l'estàndard de SQL per concatenar strings.
                    logger.info("Actualitzant magatzems per afegir la localització ' Spain'");
                    UtilsSQLite.queryUpdatePS(conn, "UPDATE warehouses SET name = name || ? WHERE id = ?", " Spain", 1); // Amazon -> Amazon Spain
                    UtilsSQLite.queryUpdatePS(conn, "UPDATE warehouses SET name = name || ? WHERE id = ?", " Spain", 2); // El Corte Inglés -> El Corte Inglés Spain
                    
                    // Aquestes operacions es mantenen igual
                    UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "Decathlon");
                    UtilsSQLite.queryUpdatePS(conn, "DELETE FROM warehouses WHERE id = ?", 3); // Esborra Mecalux
                    
                    conn.commit();
                    logger.info("Transacció completada amb èxit (COMMIT).");

                } catch (SQLException e) {
                    logger.error("Error INESPERAT durant la transacció correcta, fent rollback.", e);
                    if (conn != null) {
                        conn.rollback();
                    }
                } finally {
                    if (conn != null) {
                        conn.setAutoCommit(true);
                    }
                }

                // Mostra l'estat final de la taula
                System.out.println("\nContingut final de la taula després del COMMIT:");
                printWarehouses(conn);
            }

        } catch (SQLException e) {
            logger.error("S'ha produït un error de base de dades.", e);
        }
    }

    /**
     * Mètode auxiliar per imprimir el contingut de la taula 'warehouses'.
     */
    static void printWarehouses(Connection conn) throws SQLException {
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM warehouses ORDER BY id;")) {
            while (rs.next()) {
                System.out.println("    " + rs.getInt("id") + ", " + rs.getString("name"));
            }
        }
    }

    /**
     * Inicialitza la base de dades.
     */
    static void initDatabase(String filePath) throws SQLException {
        try (Connection conn = UtilsSQLite.connect(filePath)) {
            UtilsSQLite.queryUpdate(conn, "DROP TABLE IF EXISTS warehouses;");
            UtilsSQLite.queryUpdate(conn, "CREATE TABLE warehouses (id integer PRIMARY KEY, name text NOT NULL);");
            
            logger.info("Inserint dades inicials...");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "Amazon");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "El Corte Inglés");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?);", "Mecalux");
            logger.info("Dades inicials inserides correctament.");
        }
    }
}