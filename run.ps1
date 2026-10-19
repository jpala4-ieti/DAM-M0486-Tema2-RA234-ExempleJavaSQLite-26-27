# run.ps1 - Compila el projecte (codi i tests) i executa una classe principal amb Maven.
#
# Ús:   .\run.ps1 [classe.principal] [param1] [param2] ...
#       Si no s'indica la classe principal, s'executa cat.iesesteveterradas.Main
#
# Exemples:
#       .\run.ps1
#       .\run.ps1 cat.iesesteveterradas.Main
#       .\run.ps1 cat.iesesteveterradas.Main param1 "param amb espais"

# Ens situem a la carpeta de l'script (l'arrel del projecte)
Set-Location $PSScriptRoot

# Opcions de la JVM de Maven (exec:java executa el programa dins d'aquesta JVM).
# Permeten l'accés per reflexió que necessiten algunes llibreries del mòdul.
$env:MAVEN_OPTS = "--add-opens java.base/java.lang=ALL-UNNAMED --add-opens java.base/java.nio=ALL-UNNAMED --add-opens java.base/java.util=ALL-UNNAMED"

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

# Cadascun entre cometes simples perquè els que contenen espais arribin sencers
$execArgs = ($programArgs | ForEach-Object { "'$_'" }) -join ' '

Write-Host "Classe principal: $mainClass"
if ($execArgs) { Write-Host "Paràmetres:       $execArgs" } else { Write-Host "Paràmetres:       (cap)" }

# Les propietats -D van entre cometes perquè PowerShell no les parteixi pel punt
$mavenArgs = @("clean", "test-compile", "exec:java", "-Dexec.mainClass=$mainClass")
if ($execArgs) {
    $mavenArgs += "-Dexec.args=$execArgs"
}

mvn @mavenArgs
