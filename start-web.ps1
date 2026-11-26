$ErrorActionPreference = 'Stop'
$vite = Join-Path $PSScriptRoot 'web\node_modules\vite\bin\vite.js'
if (!(Test-Path $vite)) { throw 'Install web dependencies first: npm --prefix web install' }
Push-Location (Join-Path $PSScriptRoot 'web')
try { & node $vite --host 127.0.0.1 --port 5173 --strictPort } finally { Pop-Location }
