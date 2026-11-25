package cat.iesesteveterradas;

import cat.iesesteveterradas.suport.SortidaConsola;
import cat.iesesteveterradas.utils.UtilsSQLite;
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

/**
 * Proves d'integració: executen tot l'exemple de Main sobre una base de dades
 * dins d'una carpeta temporal i en comproven la sortida.
 */
@Tag("integracio")
@DisplayName("Main")
class MainTest {

    @TempDir
    Path tempDir;

    private Path arxiuBD;

    @BeforeEach
    void setUp() {
        // La carpeta 'data' encara no existeix: l'ha de crear el programa
        arxiuBD = tempDir.resolve("data").resolve("database.db");
    }

    /** Executa l'exemple i retorna tot el que ha escrit per pantalla. */
    private SortidaConsola executarMain() throws SQLException {
        try (SortidaConsola sortida = SortidaConsola.capturar()) {
            Main.executar(arxiuBD.toString());
            return sortida;
        }
    }

    @Test
    @DisplayName("Llista només la taula warehouses")
    void llistaLesTaules() throws SQLException {
        assertThat(executarMain().text()).contains("Taules existents: [warehouses]");
    }

    @Test
    @DisplayName("Mostra les columnes de la taula amb el seu tipus (ResultSetMetaData)")
    void mostraLesColumnes() throws SQLException {
        SortidaConsola sortida = executarMain();
        assertThat(sortida.seccio("Informació de la taula 'warehouses':")).containsExactly(
                "Columna 1: id (Nom: id, Tipus: INTEGER)",
                "Columna 2: name (Nom: name, Tipus: TEXT)");
    }

    @Test
    @DisplayName("Mostra el contingut inicial, el filtre amb LIKE i el contingut modificat")
    void mostraElsResultats() throws SQLException {
        SortidaConsola sortida = executarMain();
        assertThat(sortida.seccio("Contingut inicial de la taula:")).containsExactly("1, Amazon", "2, El Corte Inglés", "3, Mecalux");
        assertThat(sortida.seccio("Magatzems que contenen 'Spain':")).containsExactly("2, El Corte Inglés Spain");
        assertThat(sortida.seccio("Contingut de la taula modificada:")).containsExactly("1, Amazon", "2, El Corte Inglés Spain");
    }

    @Test
    @DisplayName("Crea l'arxiu de la base de dades (i la carpeta que el conté) amb l'estat final")
    void creaLaBaseDeDades() throws SQLException {
        executarMain();
        assertThat(arxiuBD).exists();
        try (Connection conn = UtilsSQLite.connect(arxiuBD.toString())) {
            assertThat(files(conn)).containsExactly("1, Amazon", "2, El Corte Inglés Spain");
        }
    }

    @Test
    @DisplayName("Executar-lo dues vegades dona el mateix resultat (la BD es reinicia)")
    void esRepetible() throws SQLException {
        String primera = executarMain().text();
        String segona = executarMain().text();
        assertThat(segona).isEqualTo(primera);
    }
}
