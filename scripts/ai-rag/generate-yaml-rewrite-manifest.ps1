param(
    [string]$OntologyPath = "src/main/resources/ontology/legal-ontology-slim.json",
    [string]$OutputPath = "docs/ai-rag-v2.2/yaml-rewrite-manifest.jsonl",
    [string]$ChecklistNodeDir = "src/main/resources/ai/checklists/nodes",
    [string]$EvidenceDir = "docs/ai-rag-v2.2/checklist-evidence",
    [switch]$ValidateOnly
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

function Add-ValidationError([System.Collections.Generic.List[string]]$errors, [string]$message) {
    $errors.Add($message) | Out-Null
}

function Assert-NodeId(
    [System.Collections.Generic.List[string]]$errors,
    [hashtable]$seen,
    [string]$id,
    [string]$name,
    [string]$pattern,
    [string]$kind,
    [string]$parentId
) {
    if ([string]::IsNullOrWhiteSpace($id)) {
        Add-ValidationError $errors "$kind id is blank: $name"
        return
    }
    if ($id -notmatch $pattern) {
        Add-ValidationError $errors "$kind id pattern mismatch: $id ($name)"
    }
    if (-not [string]::IsNullOrWhiteSpace($parentId) -and -not $id.StartsWith("$parentId-")) {
        Add-ValidationError $errors "$kind id parent prefix mismatch: $id under $parentId ($name)"
    }
    if ($seen.ContainsKey($id)) {
        Add-ValidationError $errors "duplicate ontology id: $id ($name)"
    } else {
        $seen[$id] = $true
    }
    if ([string]::IsNullOrWhiteSpace($name)) {
        Add-ValidationError $errors "$kind name is blank: $id"
    }
}

function Get-Priority([string]$l1Id, [string]$l2Id, [int]$dfsOrdinal) {
    if ($l2Id -eq "law-001-02") {
        return 1000 + $dfsOrdinal
    }
    if ($l1Id -eq "law-007") {
        return 2000 + $dfsOrdinal
    }
    if ($l1Id -eq "law-004") {
        return 3000 + $dfsOrdinal
    }
    return 9000 + $dfsOrdinal
}

function Get-ReviewStatus([string]$evidencePath) {
    if (-not (Test-Path $evidencePath)) {
        return ""
    }
    $line = Get-Content -Encoding UTF8 -Path $evidencePath |
            Where-Object { $_ -match "검토 상태\s*:\s*(.+)$" } |
            Select-Object -First 1
    if ($line -match "검토 상태\s*:\s*(.+)$") {
        return $matches[1].Trim()
    }
    return ""
}

$ontologyFullPath = Resolve-RepoPath $OntologyPath
$outputFullPath = Resolve-RepoPath $OutputPath
$nodeDirFullPath = Resolve-RepoPath $ChecklistNodeDir
$evidenceDirFullPath = Resolve-RepoPath $EvidenceDir

if (-not (Test-Path $ontologyFullPath)) {
    throw "Ontology file not found: $ontologyFullPath"
}

$ontology = Get-Content -Encoding UTF8 -Raw -Path $ontologyFullPath | ConvertFrom-Json
$errors = [System.Collections.Generic.List[string]]::new()
$seen = @{}
$rows = [System.Collections.Generic.List[object]]::new()

Assert-NodeId $errors $seen $ontology.id $ontology.name '^law-000$' "root" ""

$dfsOrdinal = 1
foreach ($l1 in @($ontology.c)) {
    Assert-NodeId $errors $seen $l1.id $l1.name '^law-\d{3}$' "L1" ""
    foreach ($l2 in @($l1.c)) {
        Assert-NodeId $errors $seen $l2.id $l2.name '^law-\d{3}-\d{2}$' "L2" $l1.id
        foreach ($l3 in @($l2.c)) {
            Assert-NodeId $errors $seen $l3.id $l3.name '^law-\d{3}-\d{2}-\d{2}$' "L3" $l2.id

            $nodeId = [string]$l3.id
            $yamlPath = Join-Path $nodeDirFullPath "$nodeId.yaml"
            $evidencePath = Join-Path $evidenceDirFullPath "$nodeId.md"
            $hasYaml = Test-Path $yamlPath
            $hasEvidence = Test-Path $evidencePath
            $status = if ($hasYaml -and $hasEvidence) {
                "draft"
            } elseif ($hasYaml -or $hasEvidence) {
                "partial"
            } else {
                "todo"
            }

            $rows.Add([pscustomobject][ordered]@{
                dfsOrdinal = $dfsOrdinal
                nodeId = $nodeId
                l1Id = [string]$l1.id
                l1 = [string]$l1.name
                l2Id = [string]$l2.id
                l2 = [string]$l2.name
                l3Id = $nodeId
                l3 = [string]$l3.name
                priority = Get-Priority ([string]$l1.id) ([string]$l2.id) $dfsOrdinal
                status = $status
                reviewStatus = Get-ReviewStatus $evidencePath
                assignedBatch = [string]$l2.id
                batchName = [string]$l2.name
                yamlPath = "src/main/resources/ai/checklists/nodes/$nodeId.yaml"
                evidencePath = "docs/ai-rag-v2.2/checklist-evidence/$nodeId.md"
                hasYaml = $hasYaml
                hasEvidence = $hasEvidence
            }) | Out-Null
            $dfsOrdinal++
        }
    }
}

if ($errors.Count -gt 0) {
    Write-Host "Ontology validation failed:" -ForegroundColor Red
    foreach ($errorMessage in $errors) {
        Write-Host "  - $errorMessage" -ForegroundColor Red
    }
    exit 1
}

$total = $rows.Count
$draft = @($rows | Where-Object { $_.status -eq "draft" }).Count
$partial = @($rows | Where-Object { $_.status -eq "partial" }).Count
$todo = @($rows | Where-Object { $_.status -eq "todo" }).Count

Write-Host "Ontology validation passed."
Write-Host "Manifest rows: $total (draft=$draft, partial=$partial, todo=$todo)"

if ($ValidateOnly) {
    exit 0
}

$outputDir = Split-Path $outputFullPath -Parent
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null
$lines = $rows | Sort-Object dfsOrdinal | ForEach-Object { $_ | ConvertTo-Json -Compress -Depth 8 }
[System.IO.File]::WriteAllLines($outputFullPath, [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
Write-Host "Wrote manifest: $outputFullPath"
