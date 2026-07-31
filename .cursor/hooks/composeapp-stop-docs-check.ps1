# Reminds the agent to run post-change documentation checklist for composeApp
$ErrorActionPreference = 'Stop'
try {
    $null = [Console]::In.ReadToEnd()
} catch {}

$followup = @(
    'If this turn modified files under composeApp/ (excluding pure doc sync you already finished),'
    'execute HARNESS H3 now: check whether ARCHITECTURE.md, PLANNING.md, composeApp/AGENTS.md,'
    'root AGENTS.md, README.md, or .cursor/rules need updates. Update them if stale, or state'
    '"文档检查：无需更新" with a one-line reason.'
) -join ' '

@{ followup_message = $followup } | ConvertTo-Json -Compress
exit 0
