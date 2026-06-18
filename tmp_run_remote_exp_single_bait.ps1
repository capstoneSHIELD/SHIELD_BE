$ErrorActionPreference = 'Stop'

function Invoke-External {
  param(
    [Parameter(Mandatory = $true)][string]$Label,
    [Parameter(Mandatory = $true)][string]$FilePath,
    [Parameter()][string[]]$Arguments = @()
  )

  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "$Label failed with exit code $LASTEXITCODE."
  }
}

$repoRoot = (Resolve-Path '.').Path
$outputRoot = Join-Path $repoRoot 'eval\complex-law-classification-experiment\output'
$keyPath = Join-Path $HOME 'Downloads\shield-key.pem'
$remoteHost = 'ec2-user@43.203.9.181'
$commit = (git rev-parse --short=8 HEAD).Trim()
if (-not $commit) {
  throw 'Unable to resolve git commit.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$runId = "aws-ec2-18080-single-bait-$stamp-$commit-r1"
$localArchive = Join-Path $env:TEMP "$runId-src.tgz"
$localRemoteScript = Join-Path $env:TEMP "run-$runId.sh"
$localBundle = Join-Path $outputRoot "$runId-bundle.tgz"
$localExtractDir = Join-Path $outputRoot "$runId-bundle"
$remoteArchive = "/home/ec2-user/$runId-src.tgz"
$remoteScript = "/home/ec2-user/run-$runId.sh"
$remoteBundle = "/home/ec2-user/$runId-bundle.tgz"

$tarPaths = @(
  'build.gradle',
  'settings.gradle',
  'gradle.properties',
  'gradlew',
  'gradlew.bat',
  'gradle',
  'src/main',
  'eval/complex-law-classification-experiment/runner',
  'eval/complex-law-classification-experiment/input'
)

$remoteScriptTemplate = @'
#!/bin/bash
set -euo pipefail
RUN_ID="__RUN_ID__"
WORKDIR="/home/ec2-user/shield-exp-${RUN_ID}"
SRCARCHIVE="/home/ec2-user/__RUN_ID__-src.tgz"
ARCHIVE="/home/ec2-user/__RUN_ID__-bundle.tgz"
APP_PID=""

cleanup() {
  set +e
  if [ -n "${APP_PID:-}" ] && kill -0 "$APP_PID" 2>/dev/null; then
    kill "$APP_PID" 2>/dev/null || true
    sleep 5
    kill -9 "$APP_PID" 2>/dev/null || true
  fi
  if [ -d "$WORKDIR" ]; then
    mkdir -p "$WORKDIR/bundle"
    [ -d "$WORKDIR/eval/complex-law-classification-experiment/output/$RUN_ID" ] && cp -R "$WORKDIR/eval/complex-law-classification-experiment/output/$RUN_ID" "$WORKDIR/bundle/output"
    [ -f "$WORKDIR/artifacts/build.log" ] && cp "$WORKDIR/artifacts/build.log" "$WORKDIR/bundle/build.log"
    [ -f "$WORKDIR/artifacts/app.log" ] && cp "$WORKDIR/artifacts/app.log" "$WORKDIR/bundle/app.log"
    [ -f "$WORKDIR/artifacts/runner.log" ] && cp "$WORKDIR/artifacts/runner.log" "$WORKDIR/bundle/runner.log"
    [ -f "$WORKDIR/artifacts/preflight-body.json" ] && cp "$WORKDIR/artifacts/preflight-body.json" "$WORKDIR/bundle/preflight-body.json"
    [ -f "$WORKDIR/artifacts/preflight.status" ] && cp "$WORKDIR/artifacts/preflight.status" "$WORKDIR/bundle/preflight.status"
    [ -f "$WORKDIR/eval/complex-law-classification-experiment/runner/runner.config.temp.json" ] && cp "$WORKDIR/eval/complex-law-classification-experiment/runner/runner.config.temp.json" "$WORKDIR/bundle/runner.config.temp.json"
    tar -C "$WORKDIR" -czf "$ARCHIVE" bundle 2>/dev/null || true
  fi
  rm -f "$SRCARCHIVE"
  chmod -R u+w "$WORKDIR" 2>/dev/null || true
  rm -rf "$WORKDIR"
}

load_env_file() {
  local envfile="$1"
  [ -r "$envfile" ] || return 0
  while IFS= read -r line || [ -n "$line" ]; do
    line=${line%$'\r'}
    case "$line" in
      ''|'#'*) continue ;;
      *=*)
        key=${line%%=*}
        value=${line#*=}
        export "$key=$value"
        ;;
    esac
  done < "$envfile"
}

trap cleanup EXIT

mkdir -p "$WORKDIR/artifacts"
tar -xzf "$SRCARCHIVE" -C "$WORKDIR"
cd "$WORKDIR"
chmod +x gradlew

if ! ./gradlew bootJar -x test --no-daemon > "$WORKDIR/artifacts/build.log" 2>&1; then
  tail -n 120 "$WORKDIR/artifacts/build.log" || true
  exit 1
fi

TOKEN=$(python3 -c 'import secrets; print(secrets.token_hex(24))')

load_env_file /etc/shield/shield.env
load_env_file /home/ec2-user/shield/.env-canary

export SPRING_PROFILES_ACTIVE=prod
export SHIELD_EXPERIMENT_ADAPTER_ENABLED=true
export SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN="$TOKEN"
export APP_AI_OPENAI_STRUCTURED_OUTPUT_ENABLED=true

JAR=$(find build/libs -name '*.jar' ! -name '*-plain.jar' | head -1)
nohup java \
  -Xms256m -Xmx512m \
  -XX:+UseG1GC \
  -XX:+ExitOnOutOfMemoryError \
  -Dspring.profiles.active=prod \
  -Dfile.encoding=UTF-8 \
  -jar "$JAR" \
  --server.port=18080 > "$WORKDIR/artifacts/app.log" 2>&1 &
APP_PID=$!

for i in $(seq 1 90); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/actuator/health || true)
  if [ "$code" = "200" ]; then
    break
  fi
  sleep 5
done

code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:18080/actuator/health || true)
if [ "$code" != "200" ]; then
  echo "health=$code"
  tail -n 120 "$WORKDIR/artifacts/app.log" || true
  exit 1
fi

curl -s -o "$WORKDIR/artifacts/preflight-body.json" -w '%{http_code}' \
  -X POST http://127.0.0.1:18080/internal/experiments/intent-route/preflight \
  -H 'Content-Type: application/json' \
  -H "X-SHIELD-EXPERIMENT-TOKEN: $TOKEN" \
  -d '{"providers":["openai"]}' > "$WORKDIR/artifacts/preflight.status"

if [ "$(cat "$WORKDIR/artifacts/preflight.status")" != "200" ]; then
  echo "preflight=$(cat "$WORKDIR/artifacts/preflight.status")"
  cat "$WORKDIR/artifacts/preflight-body.json" || true
  tail -n 120 "$WORKDIR/artifacts/app.log" || true
  exit 1
fi

cp eval/complex-law-classification-experiment/runner/config.wrong-selected.single-bait.aws.json eval/complex-law-classification-experiment/runner/runner.config.temp.json
sed -i 's|https://api.shieldai.kr|http://127.0.0.1:18080|' eval/complex-law-classification-experiment/runner/runner.config.temp.json

export SHIELD_EXPERIMENT_ADAPTER_ACCESS_TOKEN="$TOKEN"
export SHIELD_EXPERIMENT_RUN_ID="$RUN_ID"
export SHIELD_EXPERIMENT_HTTP_TIMEOUT_SECONDS=600
export SHIELD_EXPERIMENT_MAX_WORKERS=6
export PYTHONUNBUFFERED=1

if ! python3 ./eval/complex-law-classification-experiment/runner/run_experiment.py --config ./eval/complex-law-classification-experiment/runner/runner.config.temp.json > "$WORKDIR/artifacts/runner.log" 2>&1; then
  tail -n 120 "$WORKDIR/artifacts/runner.log" || true
  exit 1
fi

echo "run_id=$RUN_ID"
echo "archive=$ARCHIVE"
'@

$remoteScriptBody = $remoteScriptTemplate.Replace('__RUN_ID__', $runId)

New-Item -ItemType Directory -Force -Path $outputRoot | Out-Null

Push-Location $repoRoot
try {
  $tarArguments = @('-czf', $localArchive) + $tarPaths

  if (Test-Path $localArchive) {
    Remove-Item $localArchive -Force
  }
  if (Test-Path $localRemoteScript) {
    Remove-Item $localRemoteScript -Force
  }
  if (Test-Path $localBundle) {
    Remove-Item $localBundle -Force
  }
  if (Test-Path $localExtractDir) {
    Remove-Item $localExtractDir -Recurse -Force
  }

  Invoke-External -Label 'tar source archive' -FilePath 'tar' -Arguments $tarArguments
  [System.IO.File]::WriteAllText($localRemoteScript, $remoteScriptBody, (New-Object System.Text.UTF8Encoding($false)))

  Invoke-External -Label 'scp source archive' -FilePath 'scp' -Arguments @('-i', $keyPath, '-o', 'StrictHostKeyChecking=no', $localArchive, "${remoteHost}:$remoteArchive")
  Invoke-External -Label 'scp remote script' -FilePath 'scp' -Arguments @('-i', $keyPath, '-o', 'StrictHostKeyChecking=no', $localRemoteScript, "${remoteHost}:$remoteScript")

  & ssh -i $keyPath -o StrictHostKeyChecking=no $remoteHost "chmod +x $remoteScript && $remoteScript"
  $remoteRunExit = $LASTEXITCODE

  & scp -i $keyPath -o StrictHostKeyChecking=no "${remoteHost}:$remoteBundle" $localBundle
  $bundleCopyExit = $LASTEXITCODE

  if ($bundleCopyExit -eq 0) {
    New-Item -ItemType Directory -Force -Path $localExtractDir | Out-Null
    Invoke-External -Label 'extract bundle' -FilePath 'tar' -Arguments @('-xzf', $localBundle, '-C', $localExtractDir)
  }

  & ssh -i $keyPath -o StrictHostKeyChecking=no $remoteHost "rm -f $remoteScript $remoteBundle"

  if ($remoteRunExit -ne 0) {
    throw "Remote run failed with exit code $remoteRunExit."
  }
  if ($bundleCopyExit -ne 0) {
    throw "Bundle download failed with exit code $bundleCopyExit."
  }

  Write-Output "RUN_ID=$runId"
  Write-Output "LOCAL_BUNDLE=$localBundle"
  Write-Output "LOCAL_EXTRACT=$localExtractDir"
}
finally {
  Pop-Location
  if (Test-Path $localArchive) {
    Remove-Item $localArchive -Force
  }
  if (Test-Path $localRemoteScript) {
    Remove-Item $localRemoteScript -Force
  }
}
