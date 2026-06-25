Set-Location -LiteralPath $PSScriptRoot
$PreviewPort = if ($env:PREVIEW_PORT) { $env:PREVIEW_PORT } else { "8091" }
npx http-server . -p $PreviewPort
