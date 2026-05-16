# SSL 진단 — Cohere API 도달성 + 인증서 issuer + JDK truststore 머지 검증
#
# Usage:
#   .\scripts\diagnose-ssl.ps1

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# [1] Cohere 도달성 — .NET = Windows-ROOT 사용
try {
    $r = Invoke-WebRequest "https://api.cohere.com" -UseBasicParsing -TimeoutSec 5
    "[1] Cohere reachable: HTTP $($r.StatusCode)"
} catch {
    "[1] Cohere FAILED: $($_.Exception.Message)"
}

# [2] Cohere 서버 인증서 — issuer 가 commercial CA 인지 corporate proxy CA 인지
$req = [System.Net.WebRequest]::Create("https://api.cohere.com/")
try { $req.GetResponse().Close() } catch {}
$cert = $req.ServicePoint.Certificate
if ($cert) {
    "[2] Subject: $($cert.Subject)"
    "[2] Issuer:  $($cert.Issuer)"
} else {
    "[2] Certificate retrieval failed"
}

# [3] JDK cacerts 머지 검증 — entries 수
$keytool = "C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot\bin\keytool.exe"
$ts = "C:\GradleHome\jdk-cacerts-merged"
if (Test-Path $ts) {
    $line = & $keytool -list -keystore $ts -storepass changeit 2>&1 |
            Select-String "keystore contains" | Select-Object -First 1
    if ($line) { "[3] $($line.Line)" } else { "[3] keytool produced no entry count" }
} else {
    "[3] Merged truststore not found at $ts (run-baseline.ps1 가 한 번도 안 돌아갔거나 머지 실패)"
}
