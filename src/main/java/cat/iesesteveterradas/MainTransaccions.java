package cat.iesesteveterradas;

import cat.iesesteveterradas.utils.UtilsSQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * Exemple de transaccions amb JDBC i SQLite:
 * <ol>
 *   <li>Una transacció que falla a mitges i es desfà sencera (rollback).</li>
 *   <li>Una transacció correcta que es confirma (commit).</li>
 * </ol>
 *
 * Per defecte JDBC treballa en mode autocommit (cada sentència és una transacció).
 * Amb setAutoCommit(false) agrupem diverses sentències en una sola transacció.
 */
public class MainTransaccions {

    private static final Logger logger = LoggerFactory.getLogger(MainTransaccions.class);

    public static void main(String[] args) {
        String filePath = BaseDadesExemple.obtenirRuta();

        try {
            // Per a la demostració, partim sempre de les mateixes dades inicials
            BaseDadesExemple.inicialitzar(filePath);

            try (Connection conn = UtilsSQLite.connect(filePath)) {

                System.out.println("\nContingut de la taula abans de cap transacció:");
                BaseDadesExemple.mostrarContingut(conn);

                transaccioQueFalla(conn);

                System.out.println("\nContingut de la taula després del ROLLBACK:");
                System.out.println("(ha de ser igual que l'estat inicial)");
                BaseDadesExemple.mostrarContingut(conn);

                transaccioCorrecta(conn);

                System.out.println("\nContingut final de la taula després del COMMIT:");
                BaseDadesExemple.mostrarContingut(conn);
            }

        } catch (SQLException e) {
            logger.error("S'ha produït un error de base de dades.", e);
        }
    }

    /**
     * Transacció dissenyada per FALLAR: el primer UPDATE s'executa, però l'INSERT
     * viola la restricció NOT NULL. El rollback desfà també l'UPDATE (atomicitat).
     */
    static void transaccioQueFalla(Connection conn) throws SQLException {
        logger.info("Iniciant una transacció dissenyada per FALLAR...");
        conn.setAutoCommit(false);
        try {
            UtilsSQLite.queryUpdatePS(conn, "UPDATE warehouses SET name = ? WHERE id = ?", "Nom Temporal", 1);

            logger.info("Inserint un NULL a una columna NOT NULL per provocar un error...");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?)", (Object) null);

            conn.commit(); // No s'hi arriba mai
        } catch (SQLException e) {
            logger.warn("S'ha produït l'error esperat ({}). Fent rollback...", e.getMessage());
            conn.rollback();
            logger.info("Rollback realitzat correctament.");
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * Transacció que funciona CORRECTAMENT: totes les operacions es confirmen juntes.
     */
    static void transaccioCorrecta(Connection conn) throws SQLException {
        logger.info("Iniciant una transacció dissenyada per funcionar CORRECTAMENT...");
        conn.setAutoCommit(false);
        try {
            // '||' és l'operador estàndard d'SQL per concatenar textos
            String sqlSufix = "UPDATE warehouses SET name = name || ? WHERE id = ?";
            UtilsSQLite.queryUpdatePS(conn, sqlSufix, " Spain", 1); // Amazon -> Amazon Spain
            UtilsSQLite.queryUpdatePS(conn, sqlSufix, " Spain", 2); // El Corte Inglés -> El Corte Inglés Spain

            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?)", "Decathlon");
            UtilsSQLite.queryUpdatePS(conn, "DELETE FROM warehouses WHERE id = ?", 3); // Mecalux

            conn.commit();
            logger.info("Transacció completada amb èxit (COMMIT).");
        } catch (SQLException e) {
            logger.error("Error inesperat durant la transacció. Fent rollback...", e);
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
