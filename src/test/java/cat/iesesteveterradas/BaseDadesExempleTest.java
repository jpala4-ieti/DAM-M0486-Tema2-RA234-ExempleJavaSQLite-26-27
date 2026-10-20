package cat.iesesteveterradas;

import cat.iesesteveterradas.suport.DirectoriTreball;
import cat.iesesteveterradas.suport.SortidaConsola;
import cat.iesesteveterradas.utils.UtilsSQLite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BaseDadesExemple")
class BaseDadesExempleTest {

    @TempDir
    Path tempDir;

    /** Files de la taula warehouses com a "id, name", ordenades per id. */
    static List<String> files(Connection conn) throws SQLException {
        List<String> files = new ArrayList<>();
        try (ResultSet rs = UtilsSQLite.querySelect(conn, "SELECT id, name FROM warehouses ORDER BY id")) {
            while (rs.next()) {
                files.add(rs.getInt("id") + ", " + rs.getString("name"));
            }
        }
        return files;
    }

    @Test
    @SuppressWarnings("try")
    @DisplayName("obtenirRuta() apunta a data/database.db dins del directori de treball")
    void obtenirRuta_dinsDeDataDelDirectoriDeTreball() {
        try (DirectoriTreball dir = DirectoriTreball.canviarA(tempDir)) {
            String ruta = BaseDadesExemple.obtenirRuta();
            assertThat(ruta).isEqualTo(tempDir.toAbsolutePath() + File.separator + "data" + File.separator + "database.db");
        }
    }

    @Test
    @DisplayName("inicialitzar() crea la taula amb les 3 files inicials")
    void inicialitzar_creaTaulaAmbDadesInicials() throws SQLException {
        String ruta = tempDir.resolve("bd.db").toString();
        BaseDadesExemple.inicialitzar(ruta);
        try (Connection conn = UtilsSQLite.connect(ruta)) {
            assertThat(UtilsSQLite.listTables(conn)).containsExactly("warehouses");
            assertThat(files(conn)).containsExactly("1, Amazon", "2, El Corte Inglés", "3, Mecalux");
        }
    }

    @Test
    @DisplayName("inicialitzar() crea la carpeta de l'arxiu si no existeix")
    void inicialitzar_creaLaCarpeta() throws SQLException {
        Path ruta = tempDir.resolve("carpeta").resolve("nova").resolve("bd.db");
        BaseDadesExemple.inicialitzar(ruta.toString());
        assertThat(ruta).exists();
    }

    @Test
    @DisplayName("Tornar a inicialitzar deixa la taula com al principi (i els ids tornen a començar per 1)")
    void inicialitzar_esRepetible() throws SQLException {
        String ruta = tempDir.resolve("bd.db").toString();
        BaseDadesExemple.inicialitzar(ruta);
        try (Connection conn = UtilsSQLite.connect(ruta)) {
            UtilsSQLite.queryUpdatePS(conn, "INSERT INTO warehouses (name) VALUES (?)", "Decathlon");
            UtilsSQLite.queryUpdatePS(conn, "DELETE FROM warehouses WHERE id = ?", 1);
        }

        BaseDadesExemple.inicialitzar(ruta);

        try (Connection conn = UtilsSQLite.connect(ruta)) {
            assertThat(files(conn)).containsExactly("1, Amazon", "2, El Corte Inglés", "3, Mecalux");
        }
    }

    @Test
    @DisplayName("mostrarContingut() escriu una línia per fila, ordenada per id")
    void mostrarContingut_escriuLesFiles() throws SQLException {
        String ruta = tempDir.resolve("bd.db").toString();
        BaseDadesExemple.inicialitzar(ruta);

        try (Connection conn = UtilsSQLite.connect(ruta);
             SortidaConsola sortida = SortidaConsola.capturar()) {
            System.out.println("Contingut:");
            BaseDadesExemple.mostrarContingut(conn);
            assertThat(sortida.seccio("Contingut:")).containsExactly("1, Amazon", "2, El Corte Inglés", "3, Mecalux");
        }
    }
}
