param(
    [string]$ManifestPath = "docs/ai-rag-v2.2/yaml-rewrite-manifest.jsonl",
    [string]$ChecklistNodeDir = "src/main/resources/ai/checklists/nodes",
    [string]$EvidenceDir = "docs/ai-rag-v2.2/checklist-evidence",
    [string]$ReportPath = "docs/ai-rag-v2.2/reports/yaml-rewrite-progress-report.md",
    [int]$MinItems = 5,
    [int]$MaxItems = 9,
    [switch]$UpdateReport
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8

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

function Convert-YamlScalar([string]$raw) {
    $value = $raw.Trim()
    if ($value.StartsWith('"') -and $value.EndsWith('"')) {
        try {
            return ($value | ConvertFrom-Json)
        } catch {
            return $value.Trim('"')
        }
    }
    if ($value.StartsWith("'") -and $value.EndsWith("'")) {
        return $value.Substring(1, $value.Length - 2).Replace("''", "'")
    }
    return $value
}

function Read-ChecklistItems([string]$path) {
    $items = [System.Collections.Generic.List[string]]::new()
    $inItems = $false
    foreach ($line in Get-Content -Encoding UTF8 -Path $path) {
        if ($line -match '^\s*items\s*:\s*$') {
            $inItems = $true
            continue
        }
        if (-not $inItems) {
            continue
        }
        if ($line -match '^\s*-\s*(.+?)\s*$') {
            $items.Add((Convert-YamlScalar $matches[1])) | Out-Null
            continue
        }
        if (-not [string]::IsNullOrWhiteSpace($line) -and $line -match '^\S') {
            break
        }
    }
    return $items
}

function Add-Failure([System.Collections.Generic.List[string]]$failures, [string]$message) {
    $failures.Add($message) | Out-Null
}

function Get-CurrentStatus([string]$nodeId, [string]$nodeDir, [string]$evidenceDir) {
    $yamlPath = Join-Path $nodeDir "$nodeId.yaml"
    $evidencePath = Join-Path $evidenceDir "$nodeId.md"
    $hasYaml = Test-Path $yamlPath
    $hasEvidence = Test-Path $evidencePath
    if ($hasYaml -and $hasEvidence) {
        $evidenceText = [System.IO.File]::ReadAllText($evidencePath, [System.Text.Encoding]::UTF8)
        if ($evidenceText -match '검토 상태:\s*reviewed') {
            return "reviewed"
        }
        return "draft"
    }
    if ($hasYaml -or $hasEvidence) {
        return "partial"
    }
    return "todo"
}

function Write-ProgressReport(
    [array]$rows,
    [array]$validationRows,
    [string]$reportPath
) {
    $today = Get-Date -Format "yyyy-MM-dd"
    $statusRows = $rows | ForEach-Object {
        $status = Get-CurrentStatus $_.nodeId $script:nodeDirFullPath $script:evidenceDirFullPath
        [pscustomobject]@{
            nodeId = $_.nodeId
            l1 = $_.l1
            l2 = $_.l2
            l3 = $_.l3
            priority = $_.priority
            status = $status
            batch = $_.assignedBatch
            batchName = $_.batchName
        }
    }

    $summary = $statusRows | Group-Object status | Sort-Object Name
    $batchSummary = $statusRows |
        Group-Object batch |
        ForEach-Object {
            $done = @($_.Group | Where-Object { $_.status -in @("draft", "reviewed") }).Count
            [pscustomobject]@{
                batch = $_.Name
                batchName = $_.Group[0].batchName
                total = $_.Count
                completed = $done
                remaining = $_.Count - $done
            }
        } |
        Sort-Object batch

    $completed = $statusRows |
        Where-Object { $_.status -in @("draft", "reviewed") } |
        Sort-Object priority, nodeId

    $next = $statusRows |
        Where-Object { $_.status -notin @("draft", "reviewed") } |
        Sort-Object priority, nodeId |
        Select-Object -First 10

    $md = [System.Collections.Generic.List[string]]::new()
    $md.Add("# AI/RAG v2.2 YAML 근거 기반 재작성 진행 보고서") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("작성일: $today") | Out-Null
    $md.Add("작성 목적: manifest와 node override/evidence 파일 상태를 기준으로 YAML 재작성 진행 상황을 자동 집계한다.") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("---") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("## 1. 자동 갱신 기준") | Out-Null
    $md.Add("") | Out-Null
    $md.Add('- source of truth는 `docs/ai-rag-v2.2/yaml-rewrite-manifest.jsonl`이다.') | Out-Null
    $md.Add('- worker는 manifest를 직접 수정하지 않고, 자기 `nodes/<node-id>.yaml`와 `checklist-evidence/<node-id>.md`만 작성한다.') | Out-Null
    $md.Add('- 진행 보고서는 `scripts/ai-rag/validate-checklist-overrides.ps1 -UpdateReport`로 재생성한다.') | Out-Null
    $md.Add("- merge gate는 YAML parse, item 5~9개, evidence 존재, item별 evidence 매핑, 판단형 문구 차단을 모두 통과해야 한다.") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("## 2. 상태 요약") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("| 상태 | 개수 |") | Out-Null
    $md.Add("|---|---:|") | Out-Null
    foreach ($item in $summary) {
        $md.Add("| $($item.Name) | $($item.Count) |") | Out-Null
    }
    $md.Add("") | Out-Null
    $md.Add("## 3. Batch 요약") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("| batch | L2 | total | completed | remaining |") | Out-Null
    $md.Add("|---|---|---:|---:|---:|") | Out-Null
    foreach ($batch in $batchSummary) {
        $md.Add(('| `{0}` | {1} | {2} | {3} | {4} |' -f $batch.batch, $batch.batchName, $batch.total, $batch.completed, $batch.remaining)) | Out-Null
    }
    $md.Add("") | Out-Null
    $md.Add("## 4. 완료/진행 현황") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("| 상태 | node id | L1 | L2 | L3 |") | Out-Null
    $md.Add("|---|---|---|---|---|") | Out-Null
    foreach ($row in $completed) {
        $md.Add(('| 완료 | `{0}` | {1} | {2} | {3} |' -f $row.nodeId, $row.l1, $row.l2, $row.l3)) | Out-Null
    }
    $md.Add("") | Out-Null
    $md.Add("## 5. 다음 DFS 후보") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("| 순위 | node id | L1 | L2 | L3 |") | Out-Null
    $md.Add("|---:|---|---|---|---|") | Out-Null
    $rank = 1
    foreach ($row in $next) {
        $md.Add(('| {0} | `{1}` | {2} | {3} | {4} |' -f $rank, $row.nodeId, $row.l1, $row.l2, $row.l3)) | Out-Null
        $rank++
    }
    $md.Add("") | Out-Null
    $md.Add("## 6. 검증 결과") | Out-Null
    $md.Add("") | Out-Null
    $md.Add("| node id | item count | status |") | Out-Null
    $md.Add("|---|---:|---|") | Out-Null
    foreach ($row in $validationRows | Sort-Object nodeId) {
        $md.Add(('| `{0}` | {1} | {2} |' -f $row.nodeId, $row.itemCount, $row.status)) | Out-Null
    }

    New-Item -ItemType Directory -Force -Path (Split-Path $reportPath -Parent) | Out-Null
    [System.IO.File]::WriteAllLines($reportPath, [string[]]$md, [System.Text.UTF8Encoding]::new($false))
}

if ($MinItems -lt 1 -or $MaxItems -lt $MinItems) {
    throw "Invalid item count bounds: MinItems=$MinItems MaxItems=$MaxItems"
}

$manifestFullPath = Resolve-RepoPath $ManifestPath
$script:nodeDirFullPath = Resolve-RepoPath $ChecklistNodeDir
$script:evidenceDirFullPath = Resolve-RepoPath $EvidenceDir
$reportFullPath = Resolve-RepoPath $ReportPath

$manifestRows = @(Read-Jsonl $manifestFullPath)
$manifestById = @{}
foreach ($row in $manifestRows) {
    $manifestById[$row.nodeId] = $row
}

$failures = [System.Collections.Generic.List[string]]::new()
$warnings = [System.Collections.Generic.List[string]]::new()
$validationRows = [System.Collections.Generic.List[object]]::new()
$allItems = [System.Collections.Generic.List[object]]::new()

$overrideFiles = @(Get-ChildItem -Path $script:nodeDirFullPath -Filter "*.yaml" -File -ErrorAction SilentlyContinue | Sort-Object Name)
foreach ($file in $overrideFiles) {
    $nodeId = $file.BaseName
    if (-not $manifestById.ContainsKey($nodeId)) {
        Add-Failure $failures "override has no manifest row: $nodeId"
    }

    $items = @(Read-ChecklistItems $file.FullName)
    if ($items.Count -lt $MinItems -or $items.Count -gt $MaxItems) {
        Add-Failure $failures "$nodeId item count out of range: $($items.Count) (expected $MinItems..$MaxItems)"
    }

    $seen = @{}
    foreach ($item in $items) {
        if ([string]::IsNullOrWhiteSpace($item)) {
            Add-Failure $failures "$nodeId has blank item"
            continue
        }
        if ($seen.ContainsKey($item)) {
            Add-Failure $failures "$nodeId has duplicate item: $item"
        } else {
            $seen[$item] = $true
        }
        if ($item -match 'TODO') {
            Add-Failure $failures "$nodeId has scaffold placeholder item: $item"
        }
        foreach ($pattern in @('승소', '패소', '가능합니다', '인정됩니다', '받을 수', '위법\s*여부', '불법\s*여부', '적법\s*여부', '합법\s*여부', '소송\s*가능성', '청구\s*가능성', '인정\s*가능성', '유불리')) {
            if ($item -match $pattern) {
                Add-Failure $failures "$nodeId has judgment-like item ($pattern): $item"
            }
        }
        $allItems.Add([pscustomobject]@{ nodeId = $nodeId; item = $item }) | Out-Null
    }

    $evidencePath = Join-Path $script:evidenceDirFullPath "$nodeId.md"
    if (-not (Test-Path $evidencePath)) {
        Add-Failure $failures "$nodeId evidence is missing: $evidencePath"
        $validationRows.Add([pscustomobject]@{ nodeId = $nodeId; itemCount = $items.Count; status = "failed" }) | Out-Null
        continue
    }

    $evidenceText = [System.IO.File]::ReadAllText($evidencePath, [System.Text.Encoding]::UTF8)
    if ($evidenceText -match 'TODO') {
        Add-Failure $failures "$nodeId evidence still contains TODO"
    }
    if (-not $evidenceText.Contains("최종 YAML Items")) {
        Add-Failure $failures "$nodeId evidence lacks final YAML item mapping section"
    }
    if ($evidenceText -notmatch '(LSI\d+|precSeq=\d+|detcSeq=\d+|대법원|헌재|법령|제\d+조)') {
        Add-Failure $failures "$nodeId evidence lacks law/case markers"
    }
    foreach ($item in $items) {
        if (-not $evidenceText.Contains($item)) {
            Add-Failure $failures "$nodeId evidence does not mention item: $item"
        }
    }
    $validationRows.Add([pscustomobject]@{ nodeId = $nodeId; itemCount = $items.Count; status = "checked" }) | Out-Null
}

$evidenceFiles = @(Get-ChildItem -Path $script:evidenceDirFullPath -Filter "*.md" -File -ErrorAction SilentlyContinue | Sort-Object Name)
foreach ($file in $evidenceFiles) {
    $nodeId = $file.BaseName
    if (-not (Test-Path (Join-Path $script:nodeDirFullPath "$nodeId.yaml"))) {
        Add-Failure $failures "evidence has no matching override YAML: $nodeId"
    }
}

$globalDuplicates = $allItems |
    Group-Object item |
    Where-Object { $_.Count -gt 1 }
foreach ($duplicate in $globalDuplicates) {
    $nodes = ($duplicate.Group | Select-Object -ExpandProperty nodeId | Sort-Object -Unique) -join ", "
    $warnings.Add("global duplicate item across nodes [$nodes]: $($duplicate.Name)") | Out-Null
}

Write-Host "Checked override files: $($overrideFiles.Count)"
Write-Host "Checked evidence files: $($evidenceFiles.Count)"
Write-Host "Warnings: $($warnings.Count)"
foreach ($warning in $warnings) {
    Write-Host "  - $warning" -ForegroundColor Yellow
}

if ($UpdateReport) {
    Write-ProgressReport $manifestRows @($validationRows) $reportFullPath
    Write-Host "Updated progress report: $reportFullPath"
}

if ($failures.Count -gt 0) {
    Write-Host "Validation failed:" -ForegroundColor Red
    foreach ($failure in $failures) {
        Write-Host "  - $failure" -ForegroundColor Red
    }
    exit 1
}

Write-Host "Validation passed."
