package cat.iesesteveterradas;

import cat.iesesteveterradas.suport.DirectoriTreball;
import cat.iesesteveterradas.suport.LogCapturat;
import cat.iesesteveterradas.suport.SortidaConsola;
import cat.iesesteveterradas.utils.UtilsSQLite;
import ch.qos.logback.classic.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;

import static cat.iesesteveterradas.BaseDadesExempleTest.files;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MainTransaccions")
class MainTransaccionsTest {

    private static final String[] INICIALS = {"1, Amazon", "2, El Corte Inglés", "3, Mecalux"};

    @TempDir
    Path tempDir;

    private Connection conn;

    @BeforeEach
    void setUp() throws SQLException {
        String ruta = tempDir.resolve("bd.db").toString();
        BaseDadesExemple.inicialitzar(ruta);
        conn = UtilsSQLite.connect(ruta);
    }

    @AfterEach
    void tearDown() {
        UtilsSQLite.disconnect(conn);
    }

    @Test
    @DisplayName("Transacció que falla: el rollback deixa les dades com estaven")
    @SuppressWarnings("try")
    void transaccioQueFalla_noCanviaLesDades() throws SQLException {
        try (LogCapturat log = LogCapturat.de(MainTransaccions.class)) {
            MainTransaccions.transaccioQueFalla(conn);
        }
        assertThat(files(conn)).containsExactly(INICIALS);
    }

    @Test
    @DisplayName("Transacció que falla: torna a activar l'autocommit")
    @SuppressWarnings("try")
    void transaccioQueFalla_restauraAutocommit() throws SQLException {
        try (LogCapturat log = LogCapturat.de(MainTransaccions.class)) {
            MainTransaccions.transaccioQueFalla(conn);
        }
        assertThat(conn.getAutoCommit()).isTrue();
    }

    @Test
    @DisplayName("Transacció que falla: registra l'error esperat (WARN) amb el motiu i el rollback (INFO)")
    void transaccioQueFalla_registraErrorIRollback() throws SQLException {
        try (LogCapturat log = LogCapturat.de(MainTransaccions.class)) {
            MainTransaccions.transaccioQueFalla(conn);

            assertThat(log.missatges(Level.WARN)).hasSize(1);
            assertThat(log.missatges(Level.WARN).get(0)).contains("NOT NULL");
            assertThat(log.missatges(Level.INFO)).contains("Rollback realitzat correctament.");
        }
    }

    @Test
    @DisplayName("Transacció correcta: es confirmen tots els canvis")
    void transaccioCorrecta_confirmaTotsElsCanvis() throws SQLException {
        MainTransaccions.transaccioCorrecta(conn);
        assertThat(files(conn)).containsExactly("1, Amazon Spain", "2, El Corte Inglés Spain", "4, Decathlon");
    }

    @Test
    @DisplayName("Transacció correcta: torna a activar l'autocommit i registra el COMMIT")
    void transaccioCorrecta_restauraAutocommitIRegistraCommit() throws SQLException {
        try (LogCapturat log = LogCapturat.de(MainTransaccions.class)) {
            MainTransaccions.transaccioCorrecta(conn);
            assertThat(conn.getAutoCommit()).isTrue();
            assertThat(log.missatges(Level.INFO)).contains("Transacció completada amb èxit (COMMIT).");
            assertThat(log.missatges(Level.ERROR)).isEmpty();
        }
    }

    @Test
    @DisplayName("Transacció correcta amb un error inesperat: llança l'excepció i restaura l'autocommit")
    void transaccioCorrecta_ambError_llancaExcepcio() throws SQLException {
        UtilsSQLite.queryUpdate(conn, "DROP TABLE warehouses");

        try (LogCapturat log = LogCapturat.de(MainTransaccions.class)) {
            assertThatThrownBy(() -> MainTransaccions.transaccioCorrecta(conn)).isInstanceOf(SQLException.class);
            assertThat(log.missatges(Level.ERROR)).hasSize(1);
        }
        assertThat(conn.getAutoCommit()).isTrue();
    }

    @Test
    @Tag("integracio")
    @SuppressWarnings("try")
    @DisplayName("main(): mostra l'estat inicial, el mateix estat després del rollback i l'estat final")
    void main_mostraTotsElsPassos() throws SQLException {
        try (DirectoriTreball dir = DirectoriTreball.canviarA(tempDir);
             LogCapturat log = LogCapturat.de(MainTransaccions.class);
             SortidaConsola sortida = SortidaConsola.capturar()) {

            MainTransaccions.main(new String[0]);

            assertThat(log.missatges(Level.ERROR)).isEmpty();
            assertThat(sortida.seccio("Contingut de la taula abans de cap transacció:")).containsExactly(INICIALS);
            assertThat(sortida.seccio("Contingut de la taula després del ROLLBACK:")).containsExactly(INICIALS);
            assertThat(sortida.seccio("Contingut final de la taula després del COMMIT:"))
                    .containsExactly("1, Amazon Spain", "2, El Corte Inglés Spain", "4, Decathlon");
        }

        // L'arxiu queda a <directori de treball>/data/database.db amb l'estat final
        try (Connection bd = UtilsSQLite.connect(tempDir.resolve("data").resolve("database.db").toString())) {
            assertThat(files(bd)).containsExactly("1, Amazon Spain", "2, El Corte Inglés Spain", "4, Decathlon");
        }
    }
}
