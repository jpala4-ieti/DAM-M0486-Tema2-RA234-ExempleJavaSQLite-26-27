package cat.iesesteveterradas.suport;

import java.nio.file.Path;

/**
 * Canvia temporalment la propietat user.dir, que BaseDadesExemple fa servir per
 * calcular la ruta de data/database.db. Així els tests que executen Main i
 * MainTransaccions treballen en una carpeta temporal i no toquen el projecte.
 */
public final class DirectoriTreball implements AutoCloseable {

    private final String original;

    private DirectoriTreball(Path directori) {
        original = System.getProperty("user.dir");
        System.setProperty("user.dir", directori.toAbsolutePath().toString());
    }

    public static DirectoriTreball canviarA(Path directori) {
        return new DirectoriTreball(directori);
    }

    @Override
    public void close() {
        System.setProperty("user.dir", original);
    }
}
