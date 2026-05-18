param(
    [Parameter(Mandatory = $true)]
    [string[]]$NodeId,
    [string]$ManifestPath = "docs/ai-rag-v2.2/yaml-rewrite-manifest.jsonl",
    [string]$ChecklistNodeDir = "src/main/resources/ai/checklists/nodes",
    [string]$EvidenceDir = "docs/ai-rag-v2.2/checklist-evidence",
    [int]$ItemCount = 5,
    [switch]$Force
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

if ($ItemCount -lt 5 -or $ItemCount -gt 9) {
    throw "ItemCount must be between 5 and 9."
}

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

function Resolve-RepoPath([string]$path) {
    if ([System.IO.Path]::IsPathRooted($path)) {
        return $path
    }
    return Join-Path $repoRoot $path
}

function Read-Jsonl([string]$path) {
    if (-not (Test-Path $path)) {
        throw "Manifest not found: $path. Run scripts/ai-rag/generate-yaml-rewrite-manifest.ps1 first."
    }
    foreach ($line in Get-Content -Encoding UTF8 -Path $path) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $line | ConvertFrom-Json
        }
    }
}

$manifestFullPath = Resolve-RepoPath $ManifestPath
$nodeDirFullPath = Resolve-RepoPath $ChecklistNodeDir
$evidenceDirFullPath = Resolve-RepoPath $EvidenceDir
$manifestRows = @(Read-Jsonl $manifestFullPath)

New-Item -ItemType Directory -Force -Path $nodeDirFullPath | Out-Null
New-Item -ItemType Directory -Force -Path $evidenceDirFullPath | Out-Null

foreach ($id in $NodeId) {
    $row = $manifestRows | Where-Object { $_.nodeId -eq $id } | Select-Object -First 1
    if ($null -eq $row) {
        throw "Node id not found in manifest: $id"
    }

    $yamlPath = Join-Path $nodeDirFullPath "$id.yaml"
    $evidencePath = Join-Path $evidenceDirFullPath "$id.md"

    if ((Test-Path $yamlPath) -and -not $Force) {
        Write-Host "Skip existing YAML: $yamlPath"
    } else {
        $yamlLines = [System.Collections.Generic.List[string]]::new()
        $yamlLines.Add("items:") | Out-Null
        for ($i = 1; $i -le $ItemCount; $i++) {
            $yamlLines.Add("  - `"TODO: 사용자에게 확인할 사실관계 $i`"") | Out-Null
        }
        [System.IO.File]::WriteAllLines($yamlPath, [string[]]$yamlLines, [System.Text.UTF8Encoding]::new($false))
        Write-Host "Wrote YAML scaffold: $yamlPath"
    }

    if ((Test-Path $evidencePath) -and -not $Force) {
        Write-Host "Skip existing evidence: $evidencePath"
    } else {
        $today = Get-Date -Format "yyyy-MM-dd"
        $rows = [System.Collections.Generic.List[string]]::new()
        for ($i = 1; $i -le $ItemCount; $i++) {
            $rows.Add("| TODO: 사용자에게 확인할 사실관계 $i | TODO: 법령/판례 근거 연결 | required |") | Out-Null
        }

        $content = @"
# $id $($row.l3) 체크리스트 근거

작성일: $today
검토 상태: draft

## 1. Ontology Path

- L1: $($row.l1) (``$($row.l1Id)``)
- L2: $($row.l2) (``$($row.l2Id)``)
- L3: $($row.l3) (``$id``)
- Node ID: ``$id``

## 2. 참조한 검색 Query

| 구분 | Query |
|---|---|
| 법령 | TODO |
| 판례 | TODO |

## 3. 참조 법령

| 법령명 | 법령 ID | 조문 | 반영한 사실요건 |
|---|---|---|---|
| TODO | TODO | TODO | TODO |

## 4. 참조 판례

| 판례 | 쟁점 | 반복 등장 사실관계 | 반영 여부 |
|---|---|---|---|
| TODO | TODO | TODO | TODO |

## 5. 최종 YAML Items

| item | 근거 | required 판단 |
|---|---|---|
$($rows -join "`n")

## 6. 제외한 후보

| 후보 | 제외 사유 |
|---|---|
| TODO | 법적 판단 또는 승패 예측 표현이면 제외 |

## 7. Source Links

- TODO
"@
        [System.IO.File]::WriteAllText($evidencePath, $content, [System.Text.UTF8Encoding]::new($false))
        Write-Host "Wrote evidence scaffold: $evidencePath"
    }
}
