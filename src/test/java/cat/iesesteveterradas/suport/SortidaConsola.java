package cat.iesesteveterradas.suport;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Captura el que s'escriu a System.out mentre està obert.
 *
 * <pre>
 * try (SortidaConsola sortida = SortidaConsola.capturar()) {
 *     Main.main(new String[0]);
 *     assertThat(sortida.text()).contains("...");
 * }
 * </pre>
 */
public final class SortidaConsola implements AutoCloseable {

    private final PrintStream original;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    private SortidaConsola() {
        original = System.out;
        System.setOut(new PrintStream(bytes, true, StandardCharsets.UTF_8));
    }

    public static SortidaConsola capturar() {
        return new SortidaConsola();
    }

    /** Tot el text escrit fins ara. */
    public String text() {
        return bytes.toString(StandardCharsets.UTF_8);
    }

    /**
     * Línies que segueixen una línia de títol, fins a la primera línia buida
     * o que no comenci amb espais (és el format de BaseDadesExemple.mostrarContingut).
     */
    public List<String> seccio(String titol) {
        List<String> linies = Arrays.asList(text().split("\\R"));
        int inici = linies.indexOf(titol);
        if (inici < 0) {
            throw new AssertionError("No s'ha trobat la línia '" + titol + "' a la sortida:\n" + text());
        }
        List<String> resultat = new ArrayList<>();
        for (int i = inici + 1; i < linies.size(); i++) {
            String linia = linies.get(i);
            if (linia.isBlank() || !linia.startsWith(" ")) {
                if (linia.startsWith("(")) {
                    continue; // línia explicativa entre parèntesis
                }
                break;
            }
            resultat.add(linia.trim());
        }
        return resultat;
    }

    @Override
    public void close() {
        System.setOut(original);
    }
}
