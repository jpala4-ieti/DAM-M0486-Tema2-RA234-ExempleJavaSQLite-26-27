package cat.iesesteveterradas.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests de UtilsSQLite.
 * Cada test treballa amb una base de dades nova dins d'una carpeta temporal,
 * de manera que els tests són independents entre ells i no toquen data/database.db.
 */
@DisplayName("UtilsSQLite")
class UtilsSQLiteTest {

    private Path carpeta;
    private Path arxiuBD;
    private Connection conn;

    /** S'executa abans de CADA test: crea una BD amb la taula 'users'. */
    @BeforeEach
    void setUp(@TempDir Path tempDir) throws SQLException {
        carpeta = tempDir;
        arxiuBD = tempDir.resolve("test.db");
        conn = UtilsSQLite.connect(arxiuBD.toString());
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE users ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, email TEXT)");
        }
    }

    /** S'executa després de CADA test: tanca la connexió (encara que el test ja l'hagi tancat). */
    @AfterEach
    void tearDown() {
        UtilsSQLite.disconnect(conn);
    }

    // ------------------------------------------------------------------ utilitats dels tests

    private void inserir(String name, String email) throws SQLException {
        UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", name, email);
    }

    private int comptarUsuaris(Connection connexio) throws SQLException {
        try (ResultSet rs = UtilsSQLite.querySelect(connexio, "SELECT COUNT(*) FROM users")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /** Llegeix la columna 'name' de totes les files d'un ResultSet i el tanca. */
    private static List<String> noms(ResultSet rs) throws SQLException {
        List<String> noms = new ArrayList<>();
        try (rs) {
            while (rs.next()) {
                noms.add(rs.getString("name"));
            }
        }
        return noms;
    }

    // =================================================================================
    @Nested
    @DisplayName("Connexió")
    class Connexio {

        @Test
        @DisplayName("connect() retorna una connexió oberta")
        void connect_retornaConnexioOberta() throws SQLException {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }

        @Test
        @DisplayName("connect() crea l'arxiu de la BD si no existeix")
        void connect_creaArxiuSiNoExisteix() throws SQLException {
            Path nova = carpeta.resolve("nova.db");
            assertThat(nova).doesNotExist();
            try (Connection c = UtilsSQLite.connect(nova.toString())) {
                assertThat(c.isClosed()).isFalse();
            }
            assertThat(nova).exists();
        }

        @Test
        @DisplayName("connect() falla si la carpeta de l'arxiu no existeix")
        void connect_carpetaInexistent_llancaSQLException() {
            String ruta = carpeta.resolve("no-existeix").resolve("test.db").toString();
            assertThatThrownBy(() -> UtilsSQLite.connect(ruta)).isInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("disconnect() tanca la connexió")
        void disconnect_tancaLaConnexio() throws SQLException {
            UtilsSQLite.disconnect(conn);
            assertThat(conn.isClosed()).isTrue();
        }

        @Test
        @DisplayName("disconnect() amb una connexió nul·la no llança cap excepció")
        void disconnect_ambNull_noLlancaExcepcio() {
            assertThatCode(() -> UtilsSQLite.disconnect(null)).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("disconnect() dues vegades no llança cap excepció")
        void disconnect_dosCops_noLlancaExcepcio() {
            UtilsSQLite.disconnect(conn);
            assertThatCode(() -> UtilsSQLite.disconnect(conn)).doesNotThrowAnyException();
        }
    }

    // =================================================================================
    @Nested
    @DisplayName("Metadades: listTables()")
    class Metadades {

        @Test
        @DisplayName("Retorna les taules d'usuari")
        void listTables_retornaLaTaulaUsers() throws SQLException {
            assertThat(UtilsSQLite.listTables(conn)).containsExactly("users");
        }

        @Test
        @DisplayName("Una BD buida no té taules")
        void listTables_bdBuida_retornaLlistaBuida() throws SQLException {
            try (Connection buida = UtilsSQLite.connect(carpeta.resolve("buida.db").toString())) {
                assertThat(UtilsSQLite.listTables(buida)).isEmpty();
            }
        }

        @Test
        @DisplayName("No inclou les taules internes d'SQLite (sqlite_sequence, sqlite_schema)")
        void listTables_noInclouTaulesInternes() throws SQLException {
            inserir("Anna", null); // AUTOINCREMENT crea la taula interna sqlite_sequence
            assertThat(UtilsSQLite.listTables(conn)).containsExactly("users");
        }

        @Test
        @DisplayName("No inclou les vistes")
        void listTables_noInclouVistes() throws SQLException {
            UtilsSQLite.queryUpdate(conn, "CREATE VIEW v_noms AS SELECT name FROM users");
            assertThat(UtilsSQLite.listTables(conn)).containsExactly("users");
        }

        @Test
        @DisplayName("Inclou les taules creades després")
        void listTables_inclouTaulesNoves() throws SQLException {
            UtilsSQLite.queryUpdate(conn, "CREATE TABLE logs (id INTEGER PRIMARY KEY, msg TEXT)");
            assertThat(UtilsSQLite.listTables(conn)).containsExactlyInAnyOrder("users", "logs");
        }
    }

    // =================================================================================
    @Nested
    @DisplayName("Statement: queryUpdate() i querySelect()")
    class AmbStatement {

        @Test
        @DisplayName("Un INSERT retorna 1 fila afectada")
        void queryUpdate_insert_retornaUnaFila() throws SQLException {
            int files = UtilsSQLite.queryUpdate(conn, "INSERT INTO users(name, email) VALUES('Bob', 'bob@builder.com')");
            assertThat(files).isEqualTo(1);
        }

        @Test
        @DisplayName("Un UPDATE retorna el nombre de files modificades")
        void queryUpdate_updateDiversesFiles_retornaNombreDeFiles() throws SQLException {
            inserir("Anna", null);
            inserir("Bernat", null);
            inserir("Carla", null);
            int files = UtilsSQLite.queryUpdate(conn, "UPDATE users SET email = 'sense@correu.cat'");
            assertThat(files).isEqualTo(3);
        }

        @Test
        @DisplayName("Un DELETE sense coincidències retorna 0")
        void queryUpdate_deleteSenseCoincidencies_retornaZero() throws SQLException {
            inserir("Anna", null);
            assertThat(UtilsSQLite.queryUpdate(conn, "DELETE FROM users WHERE name = 'Ningú'")).isZero();
            assertThat(comptarUsuaris(conn)).isEqualTo(1);
        }

        @Test
        @DisplayName("Una sentència DDL retorna 0 files afectades")
        void queryUpdate_ddl_retornaZero() throws SQLException {
            int files = UtilsSQLite.queryUpdate(conn, "CREATE TABLE logs (id INTEGER PRIMARY KEY, msg TEXT)");
            assertThat(files).isZero();
            assertThat(UtilsSQLite.listTables(conn)).contains("logs");
        }

        @Test
        @DisplayName("querySelect() recupera les dades")
        void querySelect_recuperaDades() throws SQLException {
            UtilsSQLite.queryUpdate(conn, "INSERT INTO users(name, email) VALUES('Charlie', 'charlie@factory.com')");
            try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM users WHERE name = 'Charlie'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("email")).isEqualTo("charlie@factory.com");
                assertThat(rs.next()).isFalse();
            }
        }

        @Test
        @DisplayName("querySelect() tanca el Statement en tancar el ResultSet")
        void querySelect_tancaStatementEnTancarResultSet() throws SQLException {
            ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT * FROM users");
            Statement stmt = rs.getStatement();
            rs.close();
            assertThat(stmt.isClosed()).isTrue();
        }

        @Test
        @DisplayName("Un SQL invàlid llança SQLException")
        void sqlInvalid_llancaSQLException() {
            assertThatThrownBy(() -> UtilsSQLite.queryUpdate(conn, "INSERTT INTO users(name) VALUES('x')"))
                    .isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> UtilsSQLite.querySelect(conn, "SELEC * FROM users"))
                    .isInstanceOf(SQLException.class);
        }

        @Test
        @DisplayName("Consultar una taula inexistent llança SQLException")
        void querySelect_taulaInexistent_llancaSQLException() {
            assertThatThrownBy(() -> UtilsSQLite.querySelect(conn, "SELECT * FROM no_existeix"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("no_existeix");
        }
    }

    // =================================================================================
    @Nested
    @DisplayName("PreparedStatement: queryUpdatePS() i querySelectPS()")
    class AmbPreparedStatement {

        @Test
        @DisplayName("Insereix una fila")
        void queryUpdatePS_insert() throws SQLException {
            int files = UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", "John Doe", "john.doe@example.com");
            assertThat(files).isEqualTo(1);
            assertThat(noms(UtilsSQLite.querySelectPS(conn, "SELECT name FROM users WHERE email = ?", "john.doe@example.com")))
                    .containsExactly("John Doe");
        }

        @Test
        @DisplayName("Actualitza una fila existent")
        void queryUpdatePS_update() throws SQLException {
            inserir("Jane Doe", "jane.doe@example.com");
            int files = UtilsSQLite.queryUpdatePS(conn, "UPDATE users SET email = ? WHERE name = ?", "jane.d@newdomain.com", "Jane Doe");
            assertThat(files).isEqualTo(1);
            try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT email FROM users WHERE name = ?", "Jane Doe")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("email")).isEqualTo("jane.d@newdomain.com");
            }
        }

        @Test
        @DisplayName("Esborra una fila")
        void queryUpdatePS_delete() throws SQLException {
            inserir("UserToDelete", "delete@me.com");
            assertThat(UtilsSQLite.queryUpdatePS(conn, "DELETE FROM users WHERE name = ?", "UserToDelete")).isEqualTo(1);
            assertThat(comptarUsuaris(conn)).isZero();
        }

        @Test
        @DisplayName("Consulta sense paràmetres")
        void querySelectPS_senseParametres() throws SQLException {
            inserir("Anna", null);
            inserir("Bernat", null);
            assertThat(noms(UtilsSQLite.querySelectPS(conn, "SELECT name FROM users ORDER BY id")))
                    .containsExactly("Anna", "Bernat");
        }

        @Test
        @DisplayName("Consulta amb diversos paràmetres")
        void querySelectPS_diversosParametres() throws SQLException {
            inserir("Anna", "anna@a.cat");
            inserir("Anna", "anna@b.cat");
            assertThat(noms(UtilsSQLite.querySelectPS(conn,
                    "SELECT name FROM users WHERE name = ? AND email = ?", "Anna", "anna@b.cat"))).hasSize(1);
        }

        @Test
        @DisplayName("Una consulta sense resultats retorna un ResultSet buit")
        void querySelectPS_senseResultats() throws SQLException {
            try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", "Ningú")) {
                assertThat(rs.next()).isFalse();
            }
        }

        @Test
        @DisplayName("Desa i recupera enters, reals i textos amb el tipus correcte")
        void queryUpdatePS_tipusDiferents() throws SQLException {
            UtilsSQLite.queryUpdate(conn, "CREATE TABLE productes (id INTEGER PRIMARY KEY, nom TEXT, preu REAL, estoc INTEGER)");
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO productes(nom, preu, estoc) VALUES(?, ?, ?)", "Teclat", 19.95, 7);
            try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM productes WHERE nom = ?", "Teclat")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getDouble("preu")).isEqualTo(19.95);
                assertThat(rs.getInt("estoc")).isEqualTo(7);
            }
        }

        @Test
        @DisplayName("Un paràmetre null desa NULL en una columna opcional")
        void queryUpdatePS_ambNull_desaNull() throws SQLException {
            inserir("Sense correu", null);
            try (ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT email FROM users WHERE name = ?", "Sense correu")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("email")).isNull();
            }
        }

        @Test
        @DisplayName("Un null en una columna NOT NULL llança SQLException")
        void queryUpdatePS_nullEnColumnaNotNull_llancaSQLException() {
            assertThatThrownBy(() -> inserir(null, "x@x.cat"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("NOT NULL");
        }

        @Test
        @DisplayName("Si falten paràmetres llança SQLException (el driver els deixaria a NULL)")
        void queryUpdatePS_massaPocsParametres_llancaSQLException() throws SQLException {
            assertThatThrownBy(() -> UtilsSQLite.queryUpdatePS(conn, "INSERT INTO users(name, email) VALUES(?, ?)", "Anna"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("2 paràmetres");
            assertThat(comptarUsuaris(conn)).isZero();
        }

        @Test
        @DisplayName("Si sobren paràmetres llança SQLException")
        void querySelectPS_massaParametres_llancaSQLException() {
            assertThatThrownBy(() -> UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", "Anna", "sobra"))
                    .isInstanceOf(SQLException.class);
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"El Corte Inglés", "l’honor", "O'Brien", "Col·legi Pinyol àèéíòóú", "Preu: 10 €", "Joc 🎮"})
        @DisplayName("Desa i recupera textos amb accents, apòstrofs i símbols sense canvis")
        void queryUpdatePS_textosEspecials(String text) throws SQLException {
            inserir(text, null);
            assertThat(noms(UtilsSQLite.querySelectPS(conn, "SELECT name FROM users WHERE name = ?", text)))
                    .containsExactly(text);
        }

        @Test
        @DisplayName("querySelectPS() tanca el PreparedStatement en tancar el ResultSet")
        void querySelectPS_tancaStatementEnTancarResultSet() throws SQLException {
            ResultSet rs = UtilsSQLite.querySelectPS(conn, "SELECT * FROM users WHERE name = ?", "Ningú");
            Statement stmt = rs.getStatement();
            rs.close();
            assertThat(stmt.isClosed()).isTrue();
        }

        @Test
        @DisplayName("Un SQL invàlid llança SQLException")
        void queryUpdatePS_sqlInvalid_llancaSQLException() {
            assertThatThrownBy(() -> UtilsSQLite.queryUpdatePS(conn, "INSERTT INTO users(name) VALUES(?)", "Test"))
                    .isInstanceOf(SQLException.class);
        }
    }

    // =================================================================================
    @Nested
    @DisplayName("Injecció SQL")
    class InjeccioSQL {

        @BeforeEach
        void dades() throws SQLException {
            inserir("Alice", "alice@a.cat");
            inserir("Bob", "bob@b.cat");
        }

        @Test
        @DisplayName("Concatenar l'entrada a l'SQL ÉS vulnerable: ' OR '1'='1 retorna tots els usuaris")
        void concatenacio_ambOr_esVulnerable() throws SQLException {
            String entrada = "' OR '1'='1";
            String sql = "SELECT name FROM users WHERE name = '" + entrada + "'";
            assertThat(noms(UtilsSQLite.querySelect(conn, sql))).containsExactly("Alice", "Bob");
        }

        @Test
        @DisplayName("Concatenar l'entrada a l'SQL ÉS vulnerable: un comentari -- anul·la la resta de la condició")
        void concatenacio_ambComentari_esVulnerable() throws SQLException {
            String entrada = "Alice' --";
            String sql = "SELECT name FROM users WHERE name = '" + entrada + "' AND email = 'correu-incorrecte'";
            // La comprovació de l'email ha quedat comentada: retorna Alice sense saber-ne l'email
            assertThat(noms(UtilsSQLite.querySelect(conn, sql))).containsExactly("Alice");
        }

        @ParameterizedTest(name = "[{index}] {0}")
        @ValueSource(strings = {"' OR '1'='1", "' OR 1=1 --", "Alice' --", "Alice' AND '1'='1"})
        @DisplayName("Amb PreparedStatement, l'entrada maliciosa es tracta com un text literal")
        void preparedStatement_noEsVulnerable(String entrada) throws SQLException {
            assertThat(noms(UtilsSQLite.querySelectPS(conn, "SELECT name FROM users WHERE name = ?", entrada))).isEmpty();
        }

        @Test
        @DisplayName("Amb PreparedStatement, un valor amb SQL es desa tal qual i la taula continua existint")
        void preparedStatement_desaElValorTalQual() throws SQLException {
            String entrada = "Robert'); DROP TABLE users; --";
            inserir(entrada, null);
            assertThat(UtilsSQLite.listTables(conn)).contains("users");
            assertThat(noms(UtilsSQLite.querySelectPS(conn, "SELECT name FROM users WHERE name = ?", entrada)))
                    .containsExactly(entrada);
        }
    }

    // =================================================================================
    @Nested
    @DisplayName("Transaccions")
    class Transaccions {

        @Test
        @DisplayName("Per defecte la connexió està en mode autocommit")
        void perDefecte_autocommit() throws SQLException {
            assertThat(conn.getAutoCommit()).isTrue();
        }

        @Test
        @DisplayName("commit() confirma tots els canvis de la transacció")
        void commit_confirmaElsCanvis() throws SQLException {
            conn.setAutoCommit(false);
            inserir("Definitiu 1", null);
            inserir("Definitiu 2", null);
            conn.commit();
            conn.setAutoCommit(true);
            assertThat(comptarUsuaris(conn)).isEqualTo(2);
        }

        @Test
        @DisplayName("rollback() desfà tots els canvis de la transacció")
        void rollback_desfaElsCanvis() throws SQLException {
            conn.setAutoCommit(false);
            inserir("Temporal 1", null);
            inserir("Temporal 2", null);
            conn.rollback();
            conn.setAutoCommit(true);
            assertThat(comptarUsuaris(conn)).isZero();
        }

        @Test
        @DisplayName("Atomicitat: si una sentència falla, el rollback desfà també les anteriors")
        void errorAMitges_rollbackDesfaLesAnteriors() throws SQLException {
            inserir("Existent", null);
            conn.setAutoCommit(false);
            try {
                UtilsSQLite.queryUpdatePS(conn, "UPDATE users SET name = ? WHERE name = ?", "Modificat", "Existent");
                inserir(null, null); // viola NOT NULL
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
            assertThat(noms(UtilsSQLite.querySelect(conn, "SELECT name FROM users"))).containsExactly("Existent");
        }

        @Test
        @DisplayName("Aïllament: una altra connexió no veu els canvis fins al commit")
        void altraConnexio_noVeuElsCanvisFinsAlCommit() throws SQLException {
            try (Connection altra = UtilsSQLite.connect(arxiuBD.toString())) {
                conn.setAutoCommit(false);
                inserir("Anna", null);
                assertThat(comptarUsuaris(altra)).isZero();
                conn.commit();
                conn.setAutoCommit(true);
                assertThat(comptarUsuaris(altra)).isEqualTo(1);
            }
        }
    }
}
