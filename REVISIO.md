# Revisió del codi (novembre 2026)

Resum dels canvis respecte de la versió anterior. Aquest arxiu és informatiu: es pot esborrar.

## Estat de la verificació

- El codi principal i els tests **compilen** amb JDK 21 i `-Xlint:all` sense cap avís.
- Els **tests no s'han executat**: l'entorn on s'ha fet la revisió no tenia accés a Maven Central
  per descarregar el driver d'SQLite, JUnit, AssertJ ni Logback. Abans de publicar-lo, cal executar
  `mvn clean package` i comprovar que passen els 75 tests.
- `run.sh` s'ha provat (paràmetres amb espais i apòstrofs, codis de sortida). `run.ps1` no s'ha pogut provar.

## Errors corregits

| Problema | Solució |
|---|---|
| Les transaccions podien confirmar canvis a mitges: `finally { setAutoCommit(true) }` fa commit si l'excepció no és `SQLException` o si falla el rollback | `MainTransaccions` fa rollback amb qualsevol excepció; nou `UtilsSQLite.executarEnTransaccio()` i `UtilsSQLite.rollback()`, que no amaga l'error original |
| `REINICIAR_BD = false` fallava amb un arxiu buit («no such table») | `BaseDadesExemple.estaInicialitzada()` comprova si existeix la taula, no l'arxiu |
| `inicialitzar()` podia deixar la base de dades a mitges | Tot el DDL i els INSERT van dins d'una transacció |
| Amb `REINICIAR_BD = false`, cada execució tornava a modificar les dades («Spain Spain») | `Main` busca les files pel nom i no per l'id |
| Les claus foranes no es comprovaven | `connect()` executa `PRAGMA foreign_keys = ON` |
| El programa acabava amb codi 0 encara que fallés | `main()` acaba amb `System.exit(1)` si hi ha un error |
| Fuites de recursos en casos límit | `connect()` tanca la connexió si falla la configuració; `querySelect*()` tanquen el statement amb qualsevol excepció |
| `mkdirs()` sense comprovar el resultat | `Files.createDirectories()`, que informa de l'error |
| Els tests modificaven la propietat global `user.dir` | `Main.executar(ruta)` i `MainTransaccions.executar(ruta)`; s'ha eliminat `suport/DirectoriTreball` |
| `run.sh` es trencava amb paràmetres amb apòstrof | Tria el tipus de cometes segons el paràmetre |
| `run.ps1` deixava el terminal en una altra carpeta i no propagava el codi d'error | `Push-Location`/`Pop-Location` i `exit $LASTEXITCODE` |
| Els scripts sobreescrivien `MAVEN_OPTS` amb `--add-opens` innecessaris | Eliminat |
| README: `"-Dtest=UtilsSQLiteTest$InjeccioSQL"` executava tota la classe | Cometes simples i explicació |

## Canvis que cal revisar

- **`doc/Logging vs System.out.println.xlsx`**: el README el citava però no era al zip. S'ha tret
  la referència; si l'arxiu existeix al repositori, cal tornar-la a posar.
- **`MAVEN_OPTS`**: si altres projectes del mòdul necessiten els `--add-opens`, s'han de mantenir
  als scripts d'aquells projectes, no en aquest.
- **`System.exit(1)`**: amb `mvn exec:java`, en cas d'error Maven s'atura sense el resum «BUILD».
- **Log del driver**: el nom i la versió del driver ara surten amb nivell `debug` (abans `info` a cada connexió).

## No s'ha modificat

- `pom.xml`: no s'han pogut comprovar versions més noves de les dependències ni dels plugins.
- `logback.xml`, `logback-test.xml`, `doc/logback-consola-fitxer.xml`, `LICENSE`.
- Els noms dels mètodes públics existents de `UtilsSQLite` (els alumnes ja els fan servir).
