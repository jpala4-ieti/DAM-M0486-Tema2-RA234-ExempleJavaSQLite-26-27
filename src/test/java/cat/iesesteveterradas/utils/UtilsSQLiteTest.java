package cat.iesesteveterradas.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir; // <-- 1. Importa TempDir

import java.io.File; // <-- 2. Importa File
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

// Importacions estàtiques per a assercions més netes
import static org.assertj.core.api.Assertions.*;

/**
 * Classe de test exhaustiu per a UtilsSQLite.
 * Fa servir una base de dades en un fitxer temporal per a cada execució.
 */
class UtilsSQLiteTest {

    // 3. JUnit injectarà aquí una carpeta temporal única per a la classe de test.
    @TempDir
    File tempDir;

    private Connection conn;
    
    /**
     * S'executa abans de CADA mètode de test.
     * Estableix la connexió i crea una taula de proves.
     */
    @BeforeEach
    void setUp() throws SQLException {
        // 4. Construïm la ruta a un fitxer de BBDD dins de la carpeta temporal.
        String dbPath = new File(tempDir, "test.db").getAbsolutePath();
        
        // La connexió ara apunta a un fitxer real però temporal.
        conn = UtilsSQLite.connect(dbPath);
        
        // Creem una taula per a les proves
        String createTableSQL = "CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT NOT NULL, email TEXT);";
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(createTableSQL);
        }
    }

    /**
     * S'executa després de CADA mètode de test.
     * Tanca la connexió.
     */
    @AfterEach
    void tearDown() throws SQLException {
        // El mètode tearDown ara hauria de funcionar perquè 'conn' no serà nul.
        if (conn != null && !conn.isClosed()) {
            UtilsSQLite.disconnect(conn);
        }
        assertThat(conn.isClosed()).isTrue();
    }

    // ... ELS MÈTODES DE TEST (@Test) ES MANTENEN EXACTAMENT IGUAL ...
    // ... No cal canviar-los, ja que treballen contra la variable 'conn' que ara és vàlida.

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

    @Test
    @DisplayName("listTables() hauria de retornar la taula 'users'")
    void listTables_shouldReturnUsersTable() throws SQLException {
        List<String> tables = UtilsSQLite.listTables(conn);
        assertThat(tables).isNotNull().contains("users");
    }

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
        try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT COUNT(*) FROM users")) {
            rs.next();
            assertThat(rs.getInt(1)).isZero();
        }
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
    @DisplayName("queryUpdatePS() amb SQL invàlid hauria de llançar SQLException")
    void queryUpdatePS_withInvalidSQL_shouldThrowSQLException() {
        String invalidSql = "INSERTT INTO users(name) VALUES(?)";
        assertThatThrownBy(() -> UtilsSQLite.queryUpdatePS(conn, invalidSql, "Test"))
            .isInstanceOf(SQLException.class);
    }
    
    @Test
    @DisplayName("queryUpdate() hauria d'inserir una fila (ús no recomanat)")
    void queryUpdate_shouldInsertOneRow() throws SQLException {
        String sql = "INSERT INTO users(name, email) VALUES('Bob', 'bob@builder.com')";
        int affectedRows = UtilsSQLite.queryUpdate(conn, sql);
        assertThat(affectedRows).isEqualTo(1);
    }

    @Test
    @DisplayName("querySelect() hauria de recuperar dades (ús no recomanat)")
    void querySelect_shouldRetrieveData() throws SQLException {
        UtilsSQLite.queryUpdate(conn, "INSERT INTO users(name, email) VALUES('Charlie', 'charlie@factory.com')");
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM users WHERE name = 'Charlie'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString("name")).isEqualTo("Charlie");
        }
    }
}