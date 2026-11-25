package cat.iesesteveterradas;

import cat.iesesteveterradas.utils.UtilsSQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Operacions comunes sobre la base de dades d'exemple (taula 'warehouses').
 *
 * Les fan servir Main i MainTransaccions, que treballen amb el mateix arxiu
 * i, per tant, necessiten exactament el mateix esquema.
 */
public final class BaseDadesExemple {

    private static final Logger logger = LoggerFactory.getLogger(BaseDadesExemple.class);

    private static final String TAULA = "warehouses";

    private BaseDadesExemple() {
    }

    /**
     * Ruta de l'arxiu de la base de dades: [directori d'execució]/data/database.db
     * Els scripts run.sh i run.ps1 s'executen des de l'arrel del projecte.
     */
    public static String obtenirRuta() {
        return Path.of("data", "database.db").toAbsolutePath().toString();
    }

    /**
     * Crea la carpeta que ha de contenir l'arxiu de la base de dades.
     * SQLite crea l'arxiu si no existeix, però no la carpeta.
     *
     * @param filePath Ruta de l'arxiu de la base de dades.
     * @throws SQLException Si no es pot crear la carpeta (la causa és la IOException original).
     */
    public static void crearCarpeta(String filePath) throws SQLException {
        Path carpeta = Path.of(filePath).toAbsolutePath().getParent();
        if (carpeta == null) {
            return; // l'arxiu és a l'arrel del sistema d'arxius: no cal crear res
        }
        try {
            Files.createDirectories(carpeta);
        } catch (IOException e) {
            throw new SQLException("No s'ha pogut crear la carpeta de la base de dades: " + carpeta, e);
        }
    }

    /**
     * Indica si la base de dades ja té la taula 'warehouses'.
     *
     * No n'hi ha prou de mirar si l'arxiu existeix: SQLite crea un arxiu buit
     * només en connectar-s'hi, i un arxiu buit no és una base de dades inicialitzada.
     *
     * @param conn Connexió oberta.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static boolean estaInicialitzada(Connection conn) throws SQLException {
        return UtilsSQLite.listTables(conn).contains(TAULA);
    }

    /**
     * Crea (o torna a crear) la taula 'warehouses' amb unes dades inicials,
     * creant abans la carpeta de l'arxiu si no existeix.
     *
     * @param filePath Ruta de l'arxiu de la base de dades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static void inicialitzar(String filePath) throws SQLException {
        crearCarpeta(filePath);
        try (Connection conn = UtilsSQLite.connect(filePath)) {
            inicialitzar(conn);
        }
    }

    /**
     * Crea (o torna a crear) la taula 'warehouses' amb unes dades inicials.
     *
     * Tot es fa dins d'una transacció (a SQLite el DDL també és transaccional):
     * si alguna sentència falla, la base de dades queda exactament com estava
     * i no amb la taula esborrada o a mig omplir.
     *
     * @param conn Connexió oberta en mode autocommit.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static void inicialitzar(Connection conn) throws SQLException {
        logger.info("Creant la taula '{}' amb les dades inicials...", TAULA);
        UtilsSQLite.executarEnTransaccio(conn, c -> {
            // DDL: sentències fixes, sense dades de l'usuari
            UtilsSQLite.queryUpdate(c, "DROP TABLE IF EXISTS warehouses");
            UtilsSQLite.queryUpdate(c, "CREATE TABLE warehouses ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " name TEXT NOT NULL)");

            String sqlInsert = "INSERT INTO warehouses (name) VALUES (?)";
            UtilsSQLite.queryUpdatePS(c, sqlInsert, "Amazon");
            UtilsSQLite.queryUpdatePS(c, sqlInsert, "El Corte Inglés");
            UtilsSQLite.queryUpdatePS(c, sqlInsert, "Mecalux");
        });
        logger.info("Dades inicials inserides correctament.");
    }

    /**
     * Mostra per pantalla el contingut de la taula 'warehouses', ordenat per id.
     *
     * @param conn Connexió oberta.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static void mostrarContingut(Connection conn) throws SQLException {
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT id, name FROM warehouses ORDER BY id")) {
            while (rs.next()) {
                System.out.println("    " + rs.getInt("id") + ", " + rs.getString("name"));
            }
        }
    }
}
