package cat.iesesteveterradas;

import cat.iesesteveterradas.utils.UtilsSQLite;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    private BaseDadesExemple() {
    }

    /**
     * Ruta de l'arxiu de la base de dades: [directori d'execució]/data/database.db
     * Els scripts run.sh i run.ps1 s'executen des de l'arrel del projecte.
     */
    public static String obtenirRuta() {
        return System.getProperty("user.dir") + File.separator + "data" + File.separator + "database.db";
    }

    /**
     * Crea (o torna a crear) la taula 'warehouses' amb unes dades inicials.
     *
     * @param filePath Ruta de l'arxiu de la base de dades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static void inicialitzar(String filePath) throws SQLException {
        // SQLite crea l'arxiu si no existeix, però no la carpeta que el conté
        new File(filePath).getAbsoluteFile().getParentFile().mkdirs();

        try (Connection conn = UtilsSQLite.connect(filePath)) {
            // DDL: sentències fixes, sense dades de l'usuari
            UtilsSQLite.queryUpdate(conn, "DROP TABLE IF EXISTS warehouses");
            UtilsSQLite.queryUpdate(conn, "CREATE TABLE warehouses ("
                    + " id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + " name TEXT NOT NULL)");

            logger.info("Inserint dades inicials...");
            String sqlInsert = "INSERT INTO warehouses (name) VALUES (?)";
            UtilsSQLite.queryUpdatePS(conn, sqlInsert, "Amazon");
            UtilsSQLite.queryUpdatePS(conn, sqlInsert, "El Corte Inglés");
            UtilsSQLite.queryUpdatePS(conn, sqlInsert, "Mecalux");
            logger.info("Dades inicials inserides correctament.");
        }
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
