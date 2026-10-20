# Exemple d'accés a SQLite amb Java (JDBC) · DAM MP0486 (curs 2026-2027)

Projecte d'exemple del **Tema 2 (RA2)** del mòdul **MP0486 Accés a dades**. Mostra:

- Connexió a una base de dades SQLite amb JDBC i tancament de recursos amb *try-with-resources*.
- Sentències DDL i DML amb `executeUpdate()` i consultes amb `executeQuery()`.
- Metadades: `DatabaseMetaData` (llistar taules) i `ResultSetMetaData` (columnes d'un resultat).
- `PreparedStatement` per evitar la injecció SQL.
- Transaccions amb `setAutoCommit(false)`, `commit()` i `rollback()`.
- Logging amb SLF4J + Logback per als missatges tècnics (i `System.out` per a la sortida de l'usuari).
- Tests amb JUnit 5 i AssertJ sobre una base de dades temporal.

## Requisits

- JDK 21
- Maven 3.9 o superior
- Git

## Estructura

```
.
├── pom.xml                         Dependències (sqlite-jdbc, SLF4J, Logback, JUnit, AssertJ) i plugins
├── run.sh / run.ps1                Scripts de compilació i execució (Linux/macOS i Windows)
├── data/                           Aquí es crea database.db en executar el programa
├── doc/
│   ├── logback-consola-fitxer.xml  Configuració alternativa del log: consola + fitxer logs/app.log
│   └── Logging vs System.out.println.xlsx
└── src/
    ├── main/java/cat/iesesteveterradas/
    │   ├── Main.java               Metadades, consultes, UPDATE/DELETE amb PreparedStatement
    │   ├── MainTransaccions.java   Transacció que falla (rollback) i transacció correcta (commit)
    │   ├── BaseDadesExemple.java   Creació de la taula 'warehouses' i visualització del contingut
    │   └── utils/UtilsSQLite.java  Mètodes reutilitzables per connectar i executar SQL
    ├── main/resources/logback.xml  Configuració del log (consola)
    └── test/java/.../utils/UtilsSQLiteTest.java
```

## Execució

### Amb els scripts del projecte

Compilen el projecte i executen la classe indicada (per defecte `cat.iesesteveterradas.Main`).

**Linux / macOS**

```bash
./run.sh
./run.sh cat.iesesteveterradas.MainTransaccions
```

Si dona un error de permisos: `chmod +x run.sh`

**Windows (PowerShell)**

```powershell
.\run.ps1
.\run.ps1 cat.iesesteveterradas.MainTransaccions
```

Si PowerShell no permet executar scripts: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`

### Directament amb Maven

```bash
mvn clean compile
mvn compile exec:java
mvn compile exec:java "-Dexec.mainClass=cat.iesesteveterradas.MainTransaccions"
```

A PowerShell, les cometes de `-Dexec.mainClass=...` són obligatòries.

### Directament amb java

`mvn package` genera un .jar que ja inclou totes les dependències (driver d'SQLite i Logback):

```bash
mvn clean package
java -jar target/ams2-mp0486-ra2-sqlite-1.0.0.jar
java -cp target/ams2-mp0486-ra2-sqlite-1.0.0.jar cat.iesesteveterradas.MainTransaccions
```

> Per a la demostració, la base de dades es torna a crear a cada execució. A `Main.java`,
> la constant `REINICIAR_BD = false` mostra el comportament d'una aplicació real:
> només s'inicialitza la base de dades si l'arxiu encara no existeix.

## Comprovar el contingut de la base de dades

- **DBeaver** (https://dbeaver.io/): *Nova connexió → SQLite* i selecciona `data/database.db`.
- **sqlite3** (línia d'ordres):

```bash
sqlite3 data/database.db ".tables"
sqlite3 data/database.db "SELECT * FROM warehouses;"
```

## Logging

- La configuració és a `src/main/resources/logback.xml`.
- Per veure l'SQL que executa `UtilsSQLite`, canvia el nivell de `root` a `debug`.
- Per desar també el log a `logs/app.log`, copia `doc/logback-consola-fitxer.xml` a
  `src/main/resources/logback.xml`.

## Tests

La suite té 67 tests (JUnit 5 + AssertJ). Cada test treballa amb una base de dades nova
dins d'una carpeta temporal, de manera que els tests són independents i no toquen `data/database.db`.

| Classe de test | Què comprova |
|---|---|
| `utils/UtilsSQLiteTest` | Connexió, metadades (`listTables`), `Statement` i `PreparedStatement`, paràmetres (tipus, `NULL`, nombre incorrecte), textos amb accents i apòstrofs, injecció SQL (casos vulnerables i casos segurs) i transaccions (commit, rollback, atomicitat i aïllament) |
| `BaseDadesExempleTest` | Ruta de la base de dades, creació de la taula i les dades inicials, creació de la carpeta, reinicialització i sortida de `mostrarContingut()` |
| `MainTransaccionsTest` | Transacció que falla (rollback), transacció correcta (commit), restauració de l'autocommit i missatges de log |
| `MainTest` | Execució completa de `Main` en una carpeta temporal: sortida per pantalla i estat final de la base de dades |

Les classes de `suport/` ajuden a fer els tests: `SortidaConsola` captura `System.out`,
`DirectoriTreball` fa que `Main` treballi en una carpeta temporal i `LogCapturat` permet
comprovar els missatges de log.

```bash
mvn test                                                    # tots els tests
mvn test -Dtest=UtilsSQLiteTest                             # una classe de test
mvn test "-Dtest=UtilsSQLiteTest$InjeccioSQL"               # un grup (@Nested) d'una classe
mvn test "-Dtest=MainTransaccionsTest#transaccioQueFalla*"  # els mètodes que comencen per...
mvn test -DexcludedGroups=integracio                        # sense els tests d'integració (més ràpid)
mvn test -Dgroups=integracio                                # només els tests d'integració (Main i MainTransaccions.main)
```

## Ajuda Visual Studio Code: reinici de l'entorn Java

Si Visual Studio Code no es comporta com esperem i no ho hem pogut solucionar, podem provar:

* **Recarregar la finestra**: obre la paleta d'ordres (**Ctrl+Maj+P**), escriu **Developer: Reload Window** i prem Enter.
* **Netejar l'espai de treball**: si recarregar no funciona, obre la paleta d'ordres (**Ctrl+Maj+P**),
  escriu **Java: Clean Java Language Server Workspace** i prem Enter. És una solució molt eficaç per a
  molts problemes de Java a VS Code. Et demanarà que recarreguis la finestra i tornarà a analitzar el projecte.

## Llicència

GNU General Public License v3.0. Vegeu el fitxer [LICENSE](LICENSE).
