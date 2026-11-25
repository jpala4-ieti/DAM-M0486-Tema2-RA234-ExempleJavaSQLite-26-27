# run.ps1 - Compila el projecte (codi i tests) i executa una classe principal amb Maven.
#
# Ús:   .\run.ps1 [classe.principal] [param1] [param2] ...
#       Si no s'indica la classe principal, s'executa cat.iesesteveterradas.Main
#
# Exemples:
#       .\run.ps1
#       .\run.ps1 cat.iesesteveterradas.MainTransaccions
#       .\run.ps1 cat.iesesteveterradas.Main param1 "param amb espais"
#
# El codi de sortida és el de Maven: 0 si tot ha anat bé, diferent de 0 si
# la compilació o el programa han fallat.

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "Error: no s'ha trobat l'ordre 'mvn'. Instal·la Maven i afegeix-lo al PATH."
    exit 1
}

# Classe principal: primer paràmetre o, si no n'hi ha, la classe per defecte
if ($args.Count -gt 0) {
    $mainClass = $args[0]
} else {
    $mainClass = "cat.iesesteveterradas.Main"
}

# Paràmetres del programa (tots menys el primer).
# Cal comprovar-ho abans: amb un sol paràmetre, $args[1..0] retornaria $args[0].
$programArgs = @()
if ($args.Count -gt 1) {
    $programArgs = $args[1..($args.Count - 1)]
}

# Cadascun entre cometes simples perquè els que contenen espais arribin sencers.
# Maven (exec.args) no té cap caràcter d'escapament: un apòstrof dins del paràmetre el trencaria.
foreach ($arg in $programArgs) {
    if ("$arg".Contains("'")) {
        Write-Host "Error: els paràmetres no poden contenir apòstrofs: $arg"
        exit 1
    }
}
$execArgs = ($programArgs | ForEach-Object { "'$_'" }) -join ' '

Write-Host "Classe principal: $mainClass"
if ($execArgs) { Write-Host "Paràmetres:       $execArgs" } else { Write-Host "Paràmetres:       (cap)" }

# Les propietats -D van entre cometes perquè PowerShell no les parteixi pel punt
$mavenArgs = @("clean", "test-compile", "exec:java", "-Dexec.mainClass=$mainClass")
if ($execArgs) {
    $mavenArgs += "-Dexec.args=$execArgs"
}

# Ens situem a la carpeta de l'script (l'arrel del projecte) només mentre s'executa Maven:
# en acabar, el terminal torna a la carpeta on era
$codiSortida = 1
Push-Location $PSScriptRoot
try {
    mvn @mavenArgs
    $codiSortida = $LASTEXITCODE
} finally {
    Pop-Location
}

exit $codiSortida
