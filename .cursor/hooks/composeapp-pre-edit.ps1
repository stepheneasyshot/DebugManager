# Injects architecture-read reminder before edits under composeApp/
$ErrorActionPreference = 'Stop'
try {
    $raw = [Console]::In.ReadToEnd()
    $payload = $raw | ConvertFrom-Json
} catch {
    Write-Output '{"permission":"allow"}'
    exit 0
}

$path = ''
if ($payload.tool_input) {
    if ($payload.tool_input.path) { $path = [string]$payload.tool_input.path }
    elseif ($payload.tool_input.file_path) { $path = [string]$payload.tool_input.file_path }
    elseif ($payload.tool_input.target_notebook) { $path = [string]$payload.tool_input.target_notebook }
}

$normalized = $path -replace '\\', '/'
$isComposeApp = $normalized -match '(?i)(^|/|\\)composeApp(/|\\|$)'
$isDocsOnly = $normalized -match '(?i)composeApp/docs/'

if (-not $isComposeApp -or $isDocsOnly) {
    Write-Output '{"permission":"allow"}'
    exit 0
}

$msg = @(
    'HARNESS H1: Before editing composeApp code, you MUST Read composeApp/docs/ARCHITECTURE.md'
    '(and PLANNING.md for multi-file work) using the Read tool, then follow its layers/invariants.'
    'After the change, run H3 doc checklist in composeApp/docs/HARNESS.md.'
) -join ' '

$result = [ordered]@{
    permission     = 'allow'
    agent_message  = $msg
}
$result | ConvertTo-Json -Compress
exit 0
