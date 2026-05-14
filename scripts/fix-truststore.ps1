# Cohere/Supabase 서버에 직접 TLS 연결해 받은 cert chain (KMU CA 포함) 을
# JDK truststore 로 import. cert store 위치에 의존하지 않음 — .NET 의 chain
# builder 가 시스템 전체에서 chain 을 빌드하므로 KMU 가 어디 등록됐든 발견됨.
#
# Usage:
#   .\scripts\fix-truststore.ps1
#
# 이후 .\scripts\run-baseline.ps1 다시 실행.

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$keytool = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\keytool.exe"
$ts      = "C:\GradleHome\jdk-cacerts-merged"
$tmpDir  = "C:\GradleHome\ca-import"

if (-not (Test-Path $keytool)) {
    Write-Error "keytool not found at $keytool"
    exit 1
}

# base truststore 가 없으면 JDK 21 cacerts 복사
if (-not (Test-Path $ts)) {
    $base = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\lib\security\cacerts"
    New-Item -ItemType Directory -Force (Split-Path $ts) | Out-Null
    Copy-Item $base $ts -Force
    Write-Host "Base truststore copied from $base"
}

New-Item -ItemType Directory -Force $tmpDir | Out-Null

# Cohere + Supabase 의 cert chain 을 모두 export
$hosts = @("api.cohere.com", "aws-1-ap-northeast-2.pooler.supabase.com")
$totalImported = 0

foreach ($hostName in $hosts) {
    Write-Host ""
    Write-Host "=== $hostName ===" -ForegroundColor Cyan

    # 1) TLS 연결로 leaf cert 획득
    $req = [System.Net.WebRequest]::Create("https://$hostName/")
    try { $req.GetResponse().Close() } catch {}
    if (-not $req.ServicePoint.Certificate) {
        Write-Warning "  $hostName : 인증서 획득 실패 — DNS/방화벽 차단 가능"
        continue
    }
    $leafRaw = $req.ServicePoint.Certificate.GetRawCertData()
    $leaf2 = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2(,$leafRaw)

    # 2) chain build — Windows 의 모든 cert store 를 참조
    $chain = New-Object System.Security.Cryptography.X509Certificates.X509Chain
    $chain.ChainPolicy.RevocationMode = "NoCheck"
    [void]$chain.Build($leaf2)

    Write-Host "  chain depth: $($chain.ChainElements.Count)"
    foreach ($el in $chain.ChainElements) {
        $c = $el.Certificate
        Write-Host "    - $($c.Subject)  [issuer: $($c.Issuer)]"
    }

    # 3) 각 chain 원소를 PEM 으로 export + keytool import
    foreach ($el in $chain.ChainElements) {
        $c = $el.Certificate
        $thumb = $c.Thumbprint
        $pem = Join-Path $tmpDir "chain-$thumb.cer"
        $b64 = [Convert]::ToBase64String($c.RawData, 'InsertLineBreaks')
        "-----BEGIN CERTIFICATE-----`n$b64`n-----END CERTIFICATE-----" | Out-File $pem -Encoding ASCII

        $alias = "chain-$thumb"
        # keytool 출력 언어 무관하게 exit code 로 판단
        $out = & $keytool -importcert -file $pem -keystore $ts -storepass changeit -alias $alias -noprompt 2>&1
        if ($LASTEXITCODE -eq 0) {
            $totalImported++
            Write-Host "      imported: $alias  ($($c.Subject -replace ',.*$', ''))" -ForegroundColor Green
        } else {
            # 보통 "alias already exists" — 의도된 상태이므로 정상
            $msg = ($out -join " ").Trim()
            if ($msg -match "already" -or $msg -match "이미") {
                Write-Host "      skip (already exists): $alias" -ForegroundColor DarkGray
            } else {
                Write-Host "      FAILED: $alias  -> $msg" -ForegroundColor Red
            }
        }
    }
}

Write-Host ""
Write-Host "=== 결과 ===" -ForegroundColor Green
Write-Host "  신규 import (chain 원소): $totalImported"
$entry = & $keytool -list -keystore $ts -storepass changeit 2>&1 |
         Select-String -Pattern "keystore contains|항목" | Select-Object -First 1
if ($entry) { Write-Host "  $($entry.Line)" }

# KMU/kookmin alias 검증
$found = & $keytool -list -keystore $ts -storepass changeit -v 2>&1 |
         Select-String -Pattern "KMU|kookmin"
if ($found) {
    Write-Host ""
    Write-Host "  KMU CA 매칭:" -ForegroundColor Yellow
    $found | ForEach-Object { Write-Host "    $($_.Line.Trim())" }
} else {
    Write-Host ""
    Write-Host "  KMU CA 매칭 없음 — 그러나 chain 자체는 truststore 에 들어갔으니 SSL 핸드셰이크는 통과해야 함." -ForegroundColor DarkYellow
}
