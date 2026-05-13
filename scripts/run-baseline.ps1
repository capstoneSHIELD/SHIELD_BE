# Phase 0 baseline 측정 IT 실행 헬퍼 (PowerShell)
#
# Usage:
#   .\scripts\run-baseline.ps1                       # smoke 2건 (default)
#   .\scripts\run-baseline.ps1 -SampleCount 10
#   .\scripts\run-baseline.ps1 -SampleCount 100 -ConsultationIds "uuid1,uuid2,..."
#   .\scripts\run-baseline.ps1 -SkipTrustStore       # JDK21 cacerts only
#
# Prereq:
#   - JDK 21 at  C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot
#   - JDK 25 cacerts at  C:\Program Files\Java\jdk-25\lib\security\cacerts (truststore fallback)
#   - .env  COHERE_API_KEY / DB_* / etc.
#   - subst S:  -> project root  (Gradle worker argfile encoding workaround)

param(
    [int]$SampleCount = 2,
    [string]$ConsultationIds = "",
    [switch]$SkipTrustStore,
    [switch]$Quiet
)

$ErrorActionPreference = "Stop"

# Force UTF-8 console so Korean log lines aren't mojibake.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding           = [System.Text.Encoding]::UTF8

# 1) subst drive (harmless if already mapped)
$projectRoot = "C:\Users\이총명\Documents\GitHub\SHIELD_BE"
$null = subst S: $projectRoot 2>&1

# 2) Truststore copy.
#    JAVA_TOOL_OPTIONS splits on whitespace -- the JVM cannot handle
#    "-Djavax.net.ssl.trustStore=C:\Program Files\..." because of the space.
#    Solution: copy cacerts to a space-free path the first time.
$tsTarget = "C:\GradleHome\jdk25-cacerts"
if (-not $SkipTrustStore) {
    $tsSource = "C:\Program Files\Java\jdk-25\lib\security\cacerts"
    if (-not (Test-Path $tsSource)) {
        Write-Warning "JDK25 cacerts not found at $tsSource ; falling back to system truststore."
        $SkipTrustStore = $true
    } else {
        if (-not (Test-Path (Split-Path $tsTarget -Parent))) {
            New-Item -ItemType Directory -Force (Split-Path $tsTarget -Parent) | Out-Null
        }
        if ((-not (Test-Path $tsTarget)) -or ((Get-Item $tsSource).LastWriteTime -gt (Get-Item $tsTarget).LastWriteTime)) {
            Copy-Item $tsSource $tsTarget -Force
        }
    }
}

# 3) Environment variables -- inherited by gradlew / testJVM
$env:BASELINE_REAL          = "true"
$env:BASELINE_SAMPLE_COUNT  = $SampleCount.ToString()
if ($ConsultationIds -ne "") {
    $env:BASELINE_CONSULTATION_IDS = $ConsultationIds
} else {
    Remove-Item Env:BASELINE_CONSULTATION_IDS -ErrorAction SilentlyContinue
}
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot"
if ($SkipTrustStore) {
    Remove-Item Env:JAVA_TOOL_OPTIONS -ErrorAction SilentlyContinue
} else {
    # Space-free path -- safe for JAVA_TOOL_OPTIONS tokenization.
    $env:JAVA_TOOL_OPTIONS = "-Djavax.net.ssl.trustStore=$tsTarget -Djavax.net.ssl.trustStorePassword=changeit"
}

if (-not $Quiet) {
    Write-Host "=== Phase 0 baseline IT ===" -ForegroundColor Cyan
    Write-Host ("  BASELINE_REAL         = " + $env:BASELINE_REAL)
    Write-Host ("  BASELINE_SAMPLE_COUNT = " + $env:BASELINE_SAMPLE_COUNT)
    if ($env:BASELINE_CONSULTATION_IDS) {
        Write-Host ("  BASELINE_CONSULTATION_IDS = " + $env:BASELINE_CONSULTATION_IDS)
    } else {
        Write-Host "  BASELINE_CONSULTATION_IDS = (unset -- fixture will be auto-created)"
    }
    Write-Host ("  JAVA_HOME             = " + $env:JAVA_HOME)
    if ($env:JAVA_TOOL_OPTIONS) {
        Write-Host ("  JAVA_TOOL_OPTIONS     = " + $env:JAVA_TOOL_OPTIONS)
    } else {
        Write-Host "  JAVA_TOOL_OPTIONS     = (unset)"
    }
    Write-Host ""
}

# 4) Run gradlew from S:
Set-Location S:\
& .\gradlew -g C:\GradleHome test --tests "org.example.shield.baseline.BaselineMetricsRealIT" --console=plain
$exitCode = $LASTEXITCODE

# 5) Show latest result file
$reportDir = "S:\build\reports"
if (Test-Path $reportDir) {
    $latest = Get-ChildItem $reportDir -Filter "baseline-result-*.md" -ErrorAction SilentlyContinue |
              Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($latest) {
        Write-Host ""
        Write-Host "=== Result file ===" -ForegroundColor Green
        Write-Host ("  " + $latest.FullName)
        Write-Host ""
        Get-Content $latest.FullName
    }
}

# 6) On failure, surface causes
if ($exitCode -ne 0) {
    Write-Host ""
    Write-Host "=== Failure causes (Caused by:) ===" -ForegroundColor Yellow
    $xml = "S:\build\test-results\test\TEST-org.example.shield.baseline.BaselineMetricsRealIT.xml"
    if (Test-Path $xml) {
        Get-Content $xml | Select-String -Pattern "Caused by:" | Select-Object -First 10 | ForEach-Object {
            Write-Host ("  " + $_.Line.Trim())
        }
    } else {
        Write-Host "  (test XML not found at $xml)"
    }
}

exit $exitCode
