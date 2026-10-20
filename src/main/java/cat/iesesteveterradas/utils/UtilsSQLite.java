package cat.iesesteveterradas.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Classe d'utilitats per gestionar la connexió i les operacions amb una
 * base de dades SQLite, aplicant bones pràctiques i logging amb SLF4J.
 */
public final class UtilsSQLite {

    private static final Logger logger = LoggerFactory.getLogger(UtilsSQLite.class);

    // Classe d'utilitats: només té mètodes estàtics i no s'ha d'instanciar
    private UtilsSQLite() {
    }

    /**
     * Estableix una connexió amb la base de dades SQLite.
     * Si l'arxiu no existeix, SQLite en crea un de nou buit.
     *
     * @param filePath La ruta a l'arxiu de la base de dades.
     * @return L'objecte Connection (qui el rep és responsable de tancar-lo).
     * @throws SQLException Si hi ha un error en la connexió.
     */
    public static Connection connect(String filePath) throws SQLException {
        String url = "jdbc:sqlite:" + filePath;
        Connection conn = DriverManager.getConnection(url);

        logger.info("BBDD SQLite connectada a {}", filePath);
        DatabaseMetaData meta = conn.getMetaData();
        logger.info("BBDD driver: {} {}", meta.getDriverName(), meta.getDriverVersion());

        return conn;
    }

    /**
     * Tanca la connexió a la base de dades.
     * Si la connexió s'ha obert amb try-with-resources no cal cridar aquest mètode.
     *
     * @param conn La connexió a tancar (pot ser null).
     */
    public static void disconnect(Connection conn) {
        if (conn != null) {
            try {
                conn.close();
                logger.info("BBDD SQLite desconnectada");
            } catch (SQLException e) {
                logger.error("Error en tancar la connexió: {}", e.getMessage());
            }
        }
    }

    /**
     * Llista les taules d'usuari de la base de dades.
     * No inclou les taules internes d'SQLite (sqlite_schema, sqlite_sequence...)
     * ni les vistes.
     *
     * @param conn La connexió.
     * @return Una llista amb els noms de les taules.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static List<String> listTables(Connection conn) throws SQLException {
        List<String> tables = new ArrayList<>();
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        }
        logger.debug("S'han trobat {} taules.", tables.size());
        return tables;
    }

    // =================================================================
    // Mètodes amb Statement (vulnerables a injecció SQL)
    // Només s'han de fer servir amb SQL fix, mai amb dades de l'usuari.
    // =================================================================

    /**
     * ATENCIÓ: mètode vulnerable a injecció SQL si l'SQL conté dades de l'usuari.
     * Executa una sentència que no retorna files: DDL (CREATE, DROP, ALTER)
     * o DML (INSERT, UPDATE, DELETE). Es recomana queryUpdatePS() per a DML.
     *
     * @param conn La connexió a la BBDD.
     * @param sql  La sentència SQL completa.
     * @return El nombre de files afectades (0 per a les sentències DDL).
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static int queryUpdate(Connection conn, String sql) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            int affectedRows = stmt.executeUpdate(sql);
            logger.debug("Executada queryUpdate. Files afectades: {}. SQL: {}", affectedRows, sql);
            return affectedRows;
        }
    }

    /**
     * ATENCIÓ: mètode vulnerable a injecció SQL si l'SQL conté dades de l'usuari.
     * Executa una consulta de selecció (SELECT).
     *
     * Qui cridi aquest mètode ÉS RESPONSABLE DE TANCAR el ResultSet retornat
     * (millor amb try-with-resources). En tancar-lo, també es tanca el Statement
     * intern gràcies a closeOnCompletion().
     *
     * @param conn La connexió a la BBDD.
     * @param sql  La consulta SQL completa.
     * @return Un objecte ResultSet amb els resultats.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static ResultSet querySelect(Connection conn, String sql) throws SQLException {
        Statement stmt = conn.createStatement();
        try {
            stmt.closeOnCompletion();
            logger.debug("Executant querySelect. SQL: {}", sql);
            return stmt.executeQuery(sql);
        } catch (SQLException e) {
            stmt.close();
            throw e;
        }
    }

    // =================================================================
    // Mètodes amb PreparedStatement (segurs i recomanats)
    // =================================================================

    /**
     * Executa una sentència d'actualització (INSERT, UPDATE, DELETE) de forma segura.
     * Els valors s'envien separats de l'SQL, de manera que mai s'interpreten com a SQL.
     *
     * Nota: setObject(i, null) funciona amb SQLite; amb altres drivers pot caldre
     * setNull(i, java.sql.Types.XXX).
     *
     * @param conn   La connexió a la BBDD.
     * @param sql    La sentència SQL amb marcadors de posició '?'.
     * @param params Els valors per a cada '?', en ordre.
     * @return El nombre de files afectades.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static int queryUpdatePS(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            setParams(pstmt, params);
            int affectedRows = pstmt.executeUpdate();
            logger.debug("Executada queryUpdatePS. Files afectades: {}. SQL: {}", affectedRows, sql);
            return affectedRows;
        }
    }

    /**
     * Executa una consulta de selecció (SELECT) de forma segura.
     *
     * Qui cridi aquest mètode ÉS RESPONSABLE DE TANCAR el ResultSet retornat
     * (millor amb try-with-resources). En tancar-lo, també es tanca el
     * PreparedStatement intern gràcies a closeOnCompletion().
     *
     * @param conn   La connexió a la BBDD.
     * @param sql    La consulta SQL amb marcadors de posició '?'.
     * @param params Els valors per a cada '?', en ordre.
     * @return Un objecte ResultSet amb els resultats.
     * @throws SQLException Si hi ha un error de base de dades.
     */
    public static ResultSet querySelectPS(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement pstmt = conn.prepareStatement(sql);
        try {
            pstmt.closeOnCompletion();
            setParams(pstmt, params);
            logger.debug("Executant querySelectPS. SQL: {}", sql);
            return pstmt.executeQuery();
        } catch (SQLException e) {
            pstmt.close();
            throw e;
        }
    }

    /**
     * Assigna els paràmetres a un PreparedStatement (els índexs JDBC comencen per 1).
     *
     * Abans comprova que n'hi hagi tants com marcadors '?': el driver d'SQLite
     * deixa com a NULL els que falten (sense cap error) i, si en sobren, llança
     * una ArrayIndexOutOfBoundsException en lloc d'una SQLException.
     */
    private static void setParams(PreparedStatement pstmt, Object... params) throws SQLException {
        int esperats = pstmt.getParameterMetaData().getParameterCount();
        if (params.length != esperats) {
            throw new SQLException("La sentència té " + esperats + " paràmetres '?', però se n'han rebut " + params.length);
        }
        for (int i = 0; i < params.length; i++) {
            pstmt.setObject(i + 1, params[i]);
        }
    }
}
