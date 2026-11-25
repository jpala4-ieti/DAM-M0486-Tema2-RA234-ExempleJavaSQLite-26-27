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
 *
 * <p>Aquí el patró està escrit pas a pas perquè es vegi què fa cada crida.
 * UtilsSQLite.executarEnTransaccio() encapsula aquest mateix patró per reutilitzar-lo.
 *
 * <p>ATENCIÓ: setAutoCommit(true) amb una transacció oberta la CONFIRMA. Per això
 * cal fer rollback amb qualsevol excepció (no només SQLException) abans d'arribar
 * al finally; si no, un error no previst deixaria els canvis desats a mitges.
 */
public class MainTransaccions {

    private static final Logger logger = LoggerFactory.getLogger(MainTransaccions.class);

    public static void main(String[] args) {
        try {
            executar(BaseDadesExemple.obtenirRuta());
        } catch (SQLException e) {
            logger.error("S'ha produït un error de base de dades.", e);
            // Codi de sortida diferent de 0: qui ha llançat el programa sap que ha fallat
            System.exit(1);
        }
    }

    /**
     * Executa tot l'exemple sobre l'arxiu indicat.
     * Està separat de main() perquè els tests el puguin executar en una carpeta temporal.
     *
     * @param filePath Ruta de l'arxiu de la base de dades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    static void executar(String filePath) throws SQLException {
        BaseDadesExemple.crearCarpeta(filePath);

        try (Connection conn = UtilsSQLite.connect(filePath)) {
            // Per a la demostració, partim sempre de les mateixes dades inicials
            BaseDadesExemple.inicialitzar(conn);

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
            // L'error que volíem provocar: el tractem aquí i el programa continua
            logger.warn("S'ha produït l'error esperat ({}). Fent rollback...", e.getMessage());
            conn.rollback();
            logger.info("Rollback realitzat correctament.");
        } catch (RuntimeException e) {
            // Un error no previst: també s'ha de desfer la transacció abans del finally
            UtilsSQLite.rollback(conn, e);
            throw e;
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
        } catch (SQLException | RuntimeException e) {
            logger.error("Error inesperat durant la transacció. Fent rollback...", e);
            // Si el rollback també falla, l'error s'afegeix a 'e' en lloc d'amagar-lo
            UtilsSQLite.rollback(conn, e);
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }
}
