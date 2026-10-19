#!/bin/bash
#
# run.sh - Compila el projecte (codi i tests) i executa una classe principal amb Maven.
#
# Ús:   ./run.sh [classe.principal] [param1] [param2] ...
#       Si no s'indica la classe principal, s'executa cat.iesesteveterradas.Main
#
# Exemples:
#       ./run.sh
#       ./run.sh cat.iesesteveterradas.Main
#       ./run.sh cat.iesesteveterradas.Main param1 "param amb espais"

# Ens situem a la carpeta de l'script (l'arrel del projecte)
cd "$(dirname "$0")" || exit 1

# Opcions de la JVM de Maven (exec:java executa el programa dins d'aquesta JVM).
# Permeten l'accés per reflexió que necessiten algunes llibreries del mòdul.
export MAVEN_OPTS="--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"

# Classe principal: primer paràmetre o, si no n'hi ha, la classe per defecte
mainClass="${1:-cat.iesesteveterradas.Main}"
if [ $# -gt 0 ]; then
    shift
fi

# Paràmetres del programa: cadascun entre cometes simples perquè
# els que contenen espais arribin sencers al programa Java
execArgs=""
for arg in "$@"; do
    execArgs+="'$arg' "
done

echo "Classe principal: $mainClass"
echo "Paràmetres:       ${execArgs:-(cap)}"

mavenArgs=(clean test-compile exec:java "-Dexec.mainClass=$mainClass")
if [ -n "$execArgs" ]; then
    mavenArgs+=("-Dexec.args=$execArgs")
fi

mvn "${mavenArgs[@]}"
