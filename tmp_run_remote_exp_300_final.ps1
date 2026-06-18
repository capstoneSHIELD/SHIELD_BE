$ErrorActionPreference = 'Stop'

$baseScriptPath = Join-Path (Resolve-Path '.').Path 'tmp_run_remote_exp_single_bait.ps1'
$tempScriptPath = Join-Path $env:TEMP "tmp-run-remote-exp-300-final-$PID.ps1"

try {
  $scriptBody = [System.IO.File]::ReadAllText($baseScriptPath)
  $scriptBody = $scriptBody.Replace('single-bait', '300-final')
  $scriptBody = $scriptBody.Replace(
    'config.wrong-selected.single-bait.aws.json',
    'config.wrong-selected.300-final.aws.json'
  )
  [System.IO.File]::WriteAllText($tempScriptPath, $scriptBody, (New-Object System.Text.UTF8Encoding($false)))
  & $tempScriptPath
  if ($LASTEXITCODE -ne 0) {
    throw "300-final remote experiment failed with exit code $LASTEXITCODE."
  }
}
finally {
  if (Test-Path $tempScriptPath) {
    Remove-Item $tempScriptPath -Force
  }
}
