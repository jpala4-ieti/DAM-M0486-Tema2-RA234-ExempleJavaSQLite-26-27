#!/bin/bash
#
# run.sh - Compila el projecte (codi i tests) i executa una classe principal amb Maven.
#
# Ús:   ./run.sh [classe.principal] [param1] [param2] ...
#       Si no s'indica la classe principal, s'executa cat.iesesteveterradas.Main
#
# Exemples:
#       ./run.sh
#       ./run.sh cat.iesesteveterradas.MainTransaccions
#       ./run.sh cat.iesesteveterradas.Main param1 "param amb espais"
#
# El codi de sortida és el de Maven: 0 si tot ha anat bé, diferent de 0 si
# la compilació o el programa han fallat.

# Ens situem a la carpeta de l'script (l'arrel del projecte)
cd "$(dirname "$0")" || exit 1

if ! command -v mvn > /dev/null 2>&1; then
    echo "Error: no s'ha trobat l'ordre 'mvn'. Instal·la Maven i afegeix-lo al PATH." >&2
    exit 1
fi

# Classe principal: primer paràmetre o, si no n'hi ha, la classe per defecte
mainClass="${1:-cat.iesesteveterradas.Main}"
if [ $# -gt 0 ]; then
    shift
fi

# Paràmetres del programa: cadascun entre cometes perquè els que contenen espais
# arribin sencers al programa Java. Maven (exec.args) no té cap caràcter d'escapament:
# fem servir cometes simples i, si el paràmetre conté un apòstrof, cometes dobles.
execArgs=""
for arg in "$@"; do
    case "$arg" in
        *\'*\"* | *\"*\'*)
            echo "Error: un paràmetre no pot contenir alhora cometes simples i dobles: $arg" >&2
            exit 1
            ;;
        *\'*)
            execArgs+="\"$arg\" "
            ;;
        *)
            execArgs+="'$arg' "
            ;;
    esac
done

echo "Classe principal: $mainClass"
echo "Paràmetres:       ${execArgs:-(cap)}"

mavenArgs=(clean test-compile exec:java "-Dexec.mainClass=$mainClass")
if [ -n "$execArgs" ]; then
    mavenArgs+=("-Dexec.args=$execArgs")
fi

mvn "${mavenArgs[@]}"
