package cat.iesesteveterradas.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de UtilsSQLite.
 * Cada test treballa amb una base de dades nova dins d'una carpeta temporal,
 * de manera que els tests són independents entre ells i no toquen data/database.db.
 */
class UtilsSQLiteTest {

    // JUnit crea una carpeta temporal nova per a cada test i l'esborra en acabar
    @TempDir
    File tempDir;

    private Connection conn;

    /**
     * S'executa abans de CADA test: obre la connexió i crea una taula de proves.
     */
    @BeforeEach
    void setUp() throws SQLException {
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        conn = UtilsSQLite.connect(dbPath);

        String createTableSQL = "CREATE TABLE users ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT)";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    /**
     * S'executa després de CADA test: tanca la connexió.
     */
    @AfterEach
    void tearDown() throws SQLException {
        if (conn != null && !conn.isClosed()) {
            UtilsSQLite.disconnect(conn);
        }
        assertThat(conn.isClosed()).isTrue();
    }

    /** Compta les files de la taula 'users'. */
    private int countUsers() throws SQLException {
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // ----------------------------------------------------------------- connexió

    @Test
    @DisplayName("connect() hauria de retornar una connexió oberta")
    void connect_shouldReturnOpenConnection() throws SQLException {
        assertThat(conn).isNotNull();
        assertThat(conn.isClosed()).isFalse();
    }

    @Test
    @DisplayName("disconnect() amb connexió nul·la no hauria de llançar excepció")
    void disconnect_withNullConnection_shouldNotThrowException() {
        assertThatCode(() -> UtilsSQLite.disconnect(null)).doesNotThrowAnyException();
    }

    // --------------------------------------------------------------- metadades

    @Test
    @DisplayName("listTables() hauria de retornar la taula 'users'")
    void listTables_shouldReturnUsersTable() throws SQLException {
        List<String> tables = UtilsSQLite.listTables(conn);
        assertThat(tables).isNotNull().contains("users");
    }

    @Test
    @DisplayName("listTables() no hauria de retornar les taules internes d'SQLite")
    void listTables_shouldNotReturnInternalTables() throws SQLException {
        // AUTOINCREMENT fa que SQLite creï la taula interna sqlite_sequence
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Anna");
        List<String> tables = UtilsSQLite.listTables(conn);
        assertThat(tables).containsExactly("users");
    }

    // ------------------------------------------------- PreparedStatement (segur)

    @Test
    @DisplayName("queryUpdatePS() hauria d'inserir una fila correctament")
    void queryUpdatePS_shouldInsertOneRow() throws SQLException {
        String sql = "INSERT INTO users(name, email) VALUES(?, ?)";
        int affectedRows = UtilsSQLite.queryUpdatePS(conn, sql, "John Doe", "john.doe@example.com");
        assertThat(affectedRows).isEqualTo(1);
        try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT COUNT(*) FROM users WHERE name = ?", "John Doe")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("queryUpdatePS() hauria d'actualitzar una fila existent")
    void queryUpdatePS_shouldUpdateExistingRow() throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", "Jane Doe", "jane.doe@example.com");
        String newEmail = "jane.d@newdomain.com";
        int affectedRows = UtilsSQLite.queryUpdatePS(conn, "UPDATE users SET email = ? WHERE name = ?", newEmail, "Jane Doe");
        assertThat(affectedRows).isEqualTo(1);
        try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT email FROM users WHERE name = ?", "Jane Doe")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("email")).isEqualTo(newEmail);
        }
    }

    @Test
    @DisplayName("queryUpdatePS() hauria d'esborrar una fila")
    void queryUpdatePS_shouldDeleteRow() throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", "UserToDelete", "delete@me.com");
        int affectedRows = UtilsSQLite.queryUpdatePS(conn, "DELETE FROM users WHERE name = ?", "UserToDelete");
        assertThat(affectedRows).isEqualTo(1);
        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("querySelectPS() hauria de recuperar les dades correctes")
    void querySelectPS_shouldRetrieveCorrectData() throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", "Alice", "alice@wonderland.com");
        try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", "Alice")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Alice");
            assertThat(rs.getString("email")).isEqualTo("alice@wonderland.com");
            assertThat(rs.next()).isFalse();
        }
    }

    @Test
    @DisplayName("querySelectPS() hauria de tancar el statement en tancar el ResultSet")
    void querySelectPS_shouldCloseStatementWhenResultSetIsClosed() throws SQLException {
        ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", "Ningú");
        Statement stmt = rs.getStatement();
        rs.close();
        assertThat(stmt.isClosed()).isTrue();
    }

    @Test
    @DisplayName("queryUpdatePS() amb SQL invàlid hauria de llançar SQLException")
    void queryUpdatePS_withInvalidSQL_shouldThrowSQLException() {
        String invalidSql = "INSERTT INTO users(name) VALUES(?)";
        assertThatThrownBy(() -> UtilsSQLite.queryUpdatePS(conn, invalidSql, "Test"))
                .isInstanceOf(SQLException.class);
    }

    // ------------------------------------------------------------ injecció SQL

    @Test
    @DisplayName("Statement amb SQL concatenat ÉS vulnerable a la injecció SQL")
    void querySelect_withConcatenatedInput_isVulnerableToSqlInjection() throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Alice");
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Bob");

        String entradaMaliciosa = "' OR '1'='1";
        String sql = "SELECT * FROM users WHERE name = '" + entradaMaliciosa + "'";

        int files = 0;
        try (ResultSet rs = UtilsSQLite.querySelect(conn, sql)) {
            while (rs.next()) {
                files++;
            }
        }
        // La condició WHERE sempre és certa: es retornen TOTS els usuaris
        assertThat(files).isEqualTo(2);
    }

    @Test
    @DisplayName("PreparedStatement NO és vulnerable a la injecció SQL")
    void querySelectPS_withMaliciousInput_shouldPreventSqlInjection() throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Alice");
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Bob");

        String entradaMaliciosa = "' OR '1'='1";
        try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", entradaMaliciosa)) {
            // El valor es tracta com un text literal: cap usuari es diu així
            assertThat(rs.next()).isFalse();
        }
    }

    // ------------------------------------------------------------- transaccions

    @Test
    @DisplayName("rollback() hauria de desfer tots els canvis de la transacció")
    void transaction_rollback_shouldUndoAllChanges() throws SQLException {
        conn.setAutoCommit(false);
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Temporal 1");
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Temporal 2");
        conn.rollback();
        conn.setAutoCommit(true);

        assertThat(countUsers()).isZero();
    }

    @Test
    @DisplayName("commit() hauria de confirmar tots els canvis de la transacció")
    void transaction_commit_shouldKeepAllChanges() throws SQLException {
        conn.setAutoCommit(false);
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Definitiu 1");
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name) VALUES(?)", "Definitiu 2");
        conn.commit();
        conn.setAutoCommit(true);

        assertThat(countUsers()).isEqualTo(2);
    }

    // ------------------------------------------- Statement (ús no recomanat)

    @Test
    @DisplayName("queryUpdate() hauria d'inserir una fila (ús no recomanat amb dades d'usuari)")
    void queryUpdate_shouldInsertOneRow() throws SQLException {
        String sql = "INSERT INTO users(name, email) VALUES('Bob', 'bob@builder.com')";
        int affectedRows = UtilsSQLite.queryUpdate(conn, sql);
        assertThat(affectedRows).isEqualTo(1);
    }

    @Test
    @DisplayName("queryUpdate() amb DDL hauria de retornar 0 files afectades")
    void queryUpdate_withDDL_shouldReturnZero() throws SQLException {
        int affectedRows = UtilsSQLite.queryUpdate(conn, "CREATE TABLE logs (id INTEGER PRIMARY KEY, msg TEXT)");
        assertThat(affectedRows).isZero();
        assertThat(UtilsSQLite.listTables(conn)).contains("logs");
    }

    @Test
    @DisplayName("querySelect() hauria de recuperar dades (ús no recomanat amb dades d'usuari)")
    void querySelect_shouldRetrieveData() throws SQLException {
        UtilsSQLite.queryUpdate(conn, "INSERT INTO users(name, email) VALUES('Charlie', 'charlie@factory.com')");
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM users WHERE name = 'Charlie'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Charlie");
        }
    }
}
