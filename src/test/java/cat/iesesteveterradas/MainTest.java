package cat.iesesteveterradas;

import cat.iesesteveterradas.suport.DirectoriTreball;
import cat.iesesteveterradas.suport.SortidaConsola;
import cat.iesesteveterradas.utils.UtilsSQLite;
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
 * Proves d'integració: executen Main.main() en una carpeta temporal i en comproven la sortida.
 */
@Tag("integracio")
@DisplayName("Main")
class MainTest {

    @TempDir
    Path tempDir;

    @SuppressWarnings("try")
    private String executarMain() {
        try (DirectoriTreball dir = DirectoriTreball.canviarA(tempDir);
             SortidaConsola sortida = SortidaConsola.capturar()) {
            Main.main(new String[0]);
            return sortida.text();
        }
    }

    @SuppressWarnings("try")
    private SortidaConsola executarMainCapturant() {
        SortidaConsola sortida = SortidaConsola.capturar();
        try (DirectoriTreball dir = DirectoriTreball.canviarA(tempDir)) {
            Main.main(new String[0]);
        } finally {
            sortida.close();
        }
        return sortida;
    }

    @Test
    @DisplayName("Llista només la taula warehouses")
    void llistaLesTaules() {
        assertThat(executarMain()).contains("Taules existents: [warehouses]");
    }

    @Test
    @DisplayName("Mostra les columnes de la taula amb el seu tipus (ResultSetMetaData)")
    void mostraLesColumnes() {
        SortidaConsola sortida = executarMainCapturant();
        assertThat(sortida.seccio("Informació de la taula 'warehouses':")).containsExactly(
                "Columna 1: id (Nom: id, Tipus: INTEGER)",
                "Columna 2: name (Nom: name, Tipus: TEXT)");
    }

    @Test
    @DisplayName("Mostra el contingut inicial, el filtre amb LIKE i el contingut modificat")
    void mostraElsResultats() {
        SortidaConsola sortida = executarMainCapturant();
        assertThat(sortida.seccio("Contingut inicial de la taula:")).containsExactly("1, Amazon", "2, El Corte Inglés", "3, Mecalux");
        assertThat(sortida.seccio("Magatzems que contenen 'Spain':")).containsExactly("2, El Corte Inglés Spain");
        assertThat(sortida.seccio("Contingut de la taula modificada:")).containsExactly("1, Amazon", "2, El Corte Inglés Spain");
    }

    @Test
    @DisplayName("Crea data/database.db (i la carpeta data) amb l'estat final")
    void creaLaBaseDeDades() throws SQLException {
        executarMain();
        Path arxiu = tempDir.resolve("data").resolve("database.db");
        assertThat(arxiu).exists();
        try (Connection conn = UtilsSQLite.connect(arxiu.toString())) {
            assertThat(files(conn)).containsExactly("1, Amazon", "2, El Corte Inglés Spain");
        }
    }

    @Test
    @DisplayName("Executar-lo dues vegades dona el mateix resultat (la BD es reinicia)")
    void esRepetible() {
        String primera = executarMain();
        String segona = executarMain();
        assertThat(segona).isEqualTo(primera);
    }
}
