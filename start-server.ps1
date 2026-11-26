param([string]$ConfigFile = "$env:LOCALAPPDATA\SpeakFlow\config\database.env")
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $ConfigFile)) { throw "Missing private configuration: $ConfigFile" }
foreach ($line in Get-Content -LiteralPath $ConfigFile) {
    if ($line.Trim() -eq '' -or $line.TrimStart().StartsWith('#')) { continue }
    $pair = $line -split '=', 2
    if ($pair.Count -ne 2 -or $pair[0] -notmatch '^[A-Z][A-Z0-9_]*$') { throw 'Invalid configuration line' }
    [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
}
$javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'User')
if (!$javaHome) { throw 'JAVA_HOME is not configured' }
$jar = Join-Path $PSScriptRoot 'server\target\speakflow-server-0.1.0.jar'
if (!(Test-Path $jar)) { throw 'Build server first: mvn -f server/pom.xml package' }
$env:SERVER_ADDRESS = '127.0.0.1'
$env:AUDIO_DIRECTORY = Join-Path $env:LOCALAPPDATA 'SpeakFlow\audio-cache'
Push-Location (Join-Path $PSScriptRoot 'server')
try { & "$javaHome\bin\java.exe" -jar $jar } finally { Pop-Location }
