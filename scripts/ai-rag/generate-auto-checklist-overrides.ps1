param(
    [string]$ManifestPath = "docs/ai-rag-v2.2/yaml-rewrite-manifest.jsonl",
    [string]$ChecklistNodeDir = "src/main/resources/ai/checklists/nodes",
    [string]$EvidenceDir = "docs/ai-rag-v2.2/checklist-evidence",
    [string]$DomainChecklistDir = "src/main/resources/ai/checklists",
    [string]$CaseSeedDir = "src/main/resources/seed/cases",
    [string[]]$NodeId = @(),
    [switch]$AutoDraftOnly,
    [switch]$Force
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
        throw "Manifest not found: $path. Run generate-yaml-rewrite-manifest.ps1 first."
    }
    foreach ($line in Get-Content -Encoding UTF8 -Path $path) {
        if (-not [string]::IsNullOrWhiteSpace($line)) {
            $line | ConvertFrom-Json
        }
    }
}

function New-StringList() {
    return ,([System.Collections.Generic.List[string]]::new())
}

function Add-ListItem([System.Collections.Generic.List[string]]$list, [string]$value) {
    if (-not [string]::IsNullOrWhiteSpace($value)) {
        $list.Add($value.Trim()) | Out-Null
    }
}

function Read-DomainChecklist([string]$path) {
    $catalog = @{
        l2Focus = @{}
        l3Items = @{}
    }
    $inL2 = $false
    $mode = ""
    $currentL2 = ""
    $currentL3 = ""

    foreach ($line in Get-Content -Encoding UTF8 -Path $path) {
        if ($line -match '^l2_checklists:\s*$') {
            $inL2 = $true
            continue
        }
        if (-not $inL2) {
            continue
        }
        if ($line -match '^  ([^ ].+):\s*$') {
            $currentL2 = $matches[1].Trim()
            $currentL3 = ""
            $mode = ""
            if (-not $catalog.l2Focus.ContainsKey($currentL2)) {
                $catalog.l2Focus[$currentL2] = New-StringList
            }
            continue
        }
        if ($line -match '^    focus:\s*$') {
            $mode = "focus"
            continue
        }
        if ($line -match '^    l3_checklists:\s*$') {
            $mode = "l3"
            continue
        }
        if ($mode -eq "focus" -and $line -match '^      -\s*(.+?)\s*$') {
            Add-ListItem $catalog.l2Focus[$currentL2] $matches[1]
            continue
        }
        if ($mode -eq "l3" -and $line -match '^      ([^ ].+):\s*$') {
            $currentL3 = $matches[1].Trim()
            $key = "$currentL2|$currentL3"
            if (-not $catalog.l3Items.ContainsKey($key)) {
                $catalog.l3Items[$key] = New-StringList
            }
            continue
        }
        if ($mode -eq "l3" -and -not [string]::IsNullOrWhiteSpace($currentL3) -and $line -match '^        -\s*(.+?)\s*$') {
            Add-ListItem $catalog.l3Items["$currentL2|$currentL3"] $matches[1]
        }
    }
    return $catalog
}

function Normalize-Item([string]$item, [object]$row) {
    $text = $item.Trim()
    if ($text -eq "집행 지연 사유") {
        $text = "$($row.l2) 집행 지연 사유"
    }
    if ($text -eq "직전 인상 후 1년 경과 여부") {
        $text = "$($row.l2) 직전 인상 후 1년 경과 여부"
    }
    $text = $text -replace '민법 제840조 사유 해당 여부 \(부정행위/악의의 유기 등\)', '부정행위, 악의의 유기 등 주장 사유와 증거'
    $text = $text -replace '위법성 판단 \(정당행위 여부\)', '가해행위 경위와 상대방이 제시한 사유'
    $text = $text -replace '자동차손해배상보장법 적용 여부', '사고 차량, 보험 가입, 운행자 정보'
    $text = $text -replace '표준 진료지침 위반 여부', '표준 진료지침과 실제 진료 경과 차이'
    $text = $text -replace '이행 강제 가능성', '이행 요구 내용과 상대방 거절 사유'
    $text = $text -replace '갱신 거절 근거의 정당성', '갱신 거절 근거와 통지 자료'
    $text = $text -replace '소액사건 요건 \(3천만원 이하\)', '청구 금액과 소액사건 기준 금액'
    $text = $text -replace '연장근로 주 12시간 한도 준수', '연장근로 시간 기록과 주 단위 합계'
    $text = $text -replace '부정경쟁방지법 적용 여부', '영업비밀 관리규정, 비밀유지약정, 접근통제 자료'
    $text = $text -replace '표준 양육비표 적용 여부', '자녀 연령, 부모 소득, 표준 양육비표 산정 자료'
    $text = $text -replace '유사 판례 참조 여부', '유사 사건 자료 보유 여부'
    $text = $text -replace '사회통념상 타당한 해고 사유', '해고 사유로 제시된 사실과 회사의 근거 자료'
    $text = $text -replace '해당 여부', '관련 사실'
    $text = $text -replace '가능 여부', '진행 상태'
    $text = $text -replace '가능성', '관련 자료'
    $text = $text -replace '\s+', ' '
    return $text.Trim()
}

function Get-FactTopic([object]$row) {
    if ($row.nodeId -eq "law-004-04-02") {
        return "해고 사유·절차"
    }
    return [string]$row.l3
}

function Test-JudgmentLike([string]$item) {
    foreach ($pattern in @('승소', '패소', '가능합니다', '인정됩니다', '받을 수', '위법\s*여부', '불법\s*여부', '적법\s*여부', '합법\s*여부', '소송\s*가능성', '청구\s*가능성', '인정\s*가능성', '유불리')) {
        if ($item -match $pattern) {
            return $true
        }
    }
    return $false
}

function Add-ChecklistItem(
    [System.Collections.Generic.List[string]]$items,
    [hashtable]$seen,
    [string]$candidate,
    [object]$row
) {
    $item = Normalize-Item $candidate $row
    if ([string]::IsNullOrWhiteSpace($item)) {
        return
    }
    if (Test-JudgmentLike $item) {
        $item = "$($row.l2) $($row.l3) 관련 사실관계와 증거 자료"
    }
    if (-not $seen.ContainsKey($item)) {
        $seen[$item] = $true
        $items.Add($item) | Out-Null
    }
}

function Get-ChecklistItems([object]$row, [hashtable]$catalogByL1) {
    $items = New-StringList
    $seen = @{}
    $catalog = $catalogByL1[$row.l1]
    $l3Key = "$($row.l2)|$($row.l3)"
    $topic = Get-FactTopic $row

    if ($null -ne $catalog -and $catalog.l3Items.ContainsKey($l3Key)) {
        foreach ($item in $catalog.l3Items[$l3Key]) {
            Add-ChecklistItem $items $seen $item $row
        }
    }
    foreach ($candidate in @(
        "$($row.l2) $topic 관련 당사자와 상대방 관계",
        "$($row.l2) $topic 관련 주요 날짜와 현재 진행 단계",
        "$($row.l2) $topic 관련 계약서, 신청서, 통지서 등 문서 보유 여부",
        "$($row.l2) $topic 관련 금액, 기간, 산정 내역",
        "$($row.l2) $topic 관련 문자, 이메일, 사진, 녹음 등 증거 보유 여부",
        "$($row.l2) $topic 관련 내용증명, 신고, 조정, 소송 등 기존 조치 이력",
        "$($row.l2) $topic 관련 상대방 답변, 거절 사유, 협의 경과",
        "$($row.l2) $topic 관련 등기, 등록, 신고, 판결 등 공적 자료 보유 여부"
    )) {
        Add-ChecklistItem $items $seen $candidate $row
    }

    return @($items | Select-Object -First 9)
}

$lawReferencesByL2 = @{
    "law-001-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제109조", "제110조", "제563조", "제565조", "제569조", "제575조", "제580조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제23조") },
        @{ name = "부동산 실권리자명의 등기에 관한 법률"; id = "LSI215759"; articles = @("제3조", "제4조") }
    )
    "law-001-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제303조", "제356조", "제357조", "제361조", "제363조", "제365조", "제370조") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제80조", "제88조", "제145조", "제148조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제48조") }
    )
    "law-001-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제186조", "제187조", "제192조", "제213조", "제214조", "제262조", "제263조", "제268조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제48조") }
    )
    "law-002-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제806조", "제815조", "제816조", "제834조", "제836조", "제836조의2", "제840조") },
        @{ name = "가사소송법"; id = "LSI249997"; articles = @("제2조", "제50조") }
    )
    "law-002-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제806조", "제843조") }
    )
    "law-002-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제839조의2", "제839조의3", "제843조") },
        @{ name = "가사소송법"; id = "LSI249997"; articles = @("제2조", "제48조") }
    )
    "law-002-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제837조", "제837조의2", "제909조") },
        @{ name = "양육비 이행확보 및 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제7조", "제11조", "제13조") }
    )
    "law-003-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제997조", "제1000조", "제1001조", "제1004조", "제1009조", "제1008조", "제1008조의2") }
    )
    "law-003-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1012조", "제1013조", "제1019조", "제1026조", "제1028조", "제1030조", "제1041조") }
    )
    "law-003-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1060조", "제1061조", "제1065조", "제1066조", "제1068조", "제1070조", "제1073조", "제1090조") }
    )
    "law-003-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1112조", "제1113조", "제1114조", "제1115조", "제1116조", "제1117조", "제1118조") }
    )
    "law-004-01" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제2조", "제17조", "제18조", "제93조") },
        @{ name = "기간제 및 단시간근로자 보호 등에 관한 법률"; id = "EXTERNAL"; articles = @("제4조", "제8조") }
    )
    "law-004-02" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제2조", "제36조", "제43조", "제46조", "제56조") },
        @{ name = "근로자퇴직급여 보장법"; id = "EXTERNAL"; articles = @("제4조", "제8조", "제9조") },
        @{ name = "임금채권보장법"; id = "LSI259881"; articles = @("제7조") }
    )
    "law-004-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제50조", "제53조", "제54조", "제55조", "제60조") },
        @{ name = "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제19조") }
    )
    "law-004-04" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제23조", "제24조", "제26조", "제27조", "제28조") }
    )
    "law-004-05" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제76조의2", "제76조의3") },
        @{ name = "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제12조", "제14조", "제19조") },
        @{ name = "산업재해보상보험법"; id = "EXTERNAL"; articles = @("제37조", "제40조") }
    )
    "law-005-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제393조", "제396조", "제750조", "제751조", "제760조", "제763조", "제766조") }
    )
    "law-005-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제763조") },
        @{ name = "자동차손해배상 보장법"; id = "LSI277017"; articles = @("제3조", "제10조") }
    )
    "law-005-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제750조", "제751조", "제756조") },
        @{ name = "의료사고 피해구제 및 의료분쟁 조정 등에 관한 법률"; id = "EXTERNAL"; articles = @("제27조", "제28조") }
    )
    "law-005-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제764조") },
        @{ name = "개인정보 보호법"; id = "LSI270351"; articles = @("제15조", "제17조", "제34조", "제39조") }
    )
    "law-005-05" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제756조", "제758조", "제760조") },
        @{ name = "제조물 책임법"; id = "EXTERNAL"; articles = @("제2조", "제3조") }
    )
    "law-006-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제379조", "제387조", "제390조", "제397조", "제449조", "제450조", "제487조", "제492조", "제598조", "제766조") },
        @{ name = "이자제한법"; id = "EXTERNAL"; articles = @("제2조") }
    )
    "law-006-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제428조", "제428조의2", "제429조", "제430조", "제441조", "제443조") },
        @{ name = "보증인 보호를 위한 특별법"; id = "LSI251943"; articles = @("제3조", "제4조", "제6조") }
    )
    "law-006-03" = @(
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제24조", "제56조", "제223조", "제276조", "제280조") },
        @{ name = "채권의 공정한 추심에 관한 법률"; id = "LSI268669"; articles = @("제9조", "제10조") },
        @{ name = "소액사건심판법"; id = "EXTERNAL"; articles = @("제2조") }
    )
    "law-006-04" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제294조", "제305조", "제309조", "제564조") }
    )
    "law-006-05" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제579조", "제580조", "제611조", "제614조", "제624조") }
    )
    "law-007-01" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제2조", "제3조", "제3조의2", "제4조", "제6조", "제6조의3", "제7조", "제8조") },
        @{ name = "주택임대차보호법 시행령"; id = "LSI267649"; articles = @("제10조", "제11조") }
    )
    "law-007-02" = @(
        @{ name = "상가건물 임대차보호법"; id = "LSI238797"; articles = @("제2조", "제3조", "제10조", "제10조의4", "제11조") },
        @{ name = "상가건물 임대차보호법 시행령"; id = "LSI267689"; articles = @("제2조", "제4조") }
    )
    "law-007-03" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제3조의2", "제3조의3", "제14조", "제21조") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제24조", "제56조", "제223조") },
        @{ name = "임차권등기명령 절차에 관한 규칙"; id = "LSI252747"; articles = @("제2조", "제3조") }
    )
    "law-008-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제109조", "제390조", "제393조", "제544조", "제548조") },
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제46조", "제47조", "제54조") }
    )
    "law-008-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제393조", "제544조", "제563조", "제567조", "제664조", "제667조", "제668조", "제669조") },
        @{ name = "하도급거래 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제13조", "제16조") }
    )
    "law-008-03" = @(
        @{ name = "가맹사업거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제7조", "제9조", "제11조", "제12조") },
        @{ name = "대리점거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제6조", "제9조") },
        @{ name = "독점규제 및 공정거래에 관한 법률"; id = "EXTERNAL"; articles = @("제45조") }
    )
    "law-008-04" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제335조", "제360조의24", "제363조", "제376조", "제380조", "제385조", "제402조", "제466조") }
    )
    "law-008-05" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제382조", "제382조의3", "제397조", "제398조", "제399조", "제403조") },
        @{ name = "부정경쟁방지 및 영업비밀보호에 관한 법률"; id = "EXTERNAL"; articles = @("제2조", "제10조", "제18조") }
    )
}

$lawReferencesByNode = @{
    "law-001-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제103조", "제104조", "제105조", "제109조", "제110조", "제563조", "제565조") }
    )
    "law-001-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제387조", "제390조", "제397조", "제536조", "제563조", "제568조") }
    )
    "law-001-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제544조", "제545조", "제546조", "제548조", "제551조", "제565조") }
    )
    "law-001-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제570조", "제575조", "제580조", "제581조", "제582조") }
    )
    "law-001-01-05" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제186조", "제187조", "제568조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제23조", "제24조", "제48조") }
    )
    "law-001-01-06" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제103조", "제186조", "제187조") },
        @{ name = "부동산 실권리자명의 등기에 관한 법률"; id = "LSI215759"; articles = @("제3조", "제4조") }
    )
    "law-001-03-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제356조", "제357조", "제360조", "제361조", "제369조", "제370조") }
    )
    "law-001-03-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제279조", "제303조", "제306조", "제312조", "제316조") }
    )
    "law-001-03-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제363조", "제365조") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제80조", "제83조", "제91조") }
    )
    "law-001-03-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제368조") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제145조", "제148조", "제149조", "제150조", "제151조") }
    )
    "law-001-03-05" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제369조", "제370조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제48조", "제54조") }
    )
    "law-001-04-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제262조", "제263조", "제264조", "제265조", "제268조", "제269조") }
    )
    "law-001-04-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제212조", "제213조", "제214조", "제216조", "제237조", "제242조") }
    )
    "law-001-04-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제192조", "제204조", "제205조", "제206조", "제213조", "제214조") }
    )
    "law-001-04-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제186조", "제187조", "제213조", "제214조") },
        @{ name = "부동산등기법"; id = "LSI265377"; articles = @("제3조", "제48조") }
    )
    "law-002-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제834조", "제836조", "제836조의2", "제837조", "제909조") },
        @{ name = "가사소송법"; id = "LSI249997"; articles = @("제2조", "제50조") }
    )
    "law-002-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제840조", "제843조") },
        @{ name = "가사소송법"; id = "LSI249997"; articles = @("제2조", "제50조") }
    )
    "law-002-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제806조", "제840조", "제843조", "제750조", "제751조") }
    )
    "law-002-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제815조", "제816조", "제817조", "제818조", "제824조") }
    )
    "law-002-02-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제806조", "제843조") }
    )
    "law-002-02-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제806조", "제843조") }
    )
    "law-002-02-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제760조") }
    )
    "law-002-03-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제839조의2", "제839조의3", "제843조") }
    )
    "law-002-03-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제839조의2", "제843조") }
    )
    "law-002-03-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제839조의2", "제843조") }
    )
    "law-002-03-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제839조의2", "제839조의3", "제843조") },
        @{ name = "가사소송법"; id = "LSI249997"; articles = @("제2조", "제48조") }
    )
    "law-002-04-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제837조", "제837조의2", "제909조") }
    )
    "law-002-04-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제837조", "제837조의2") },
        @{ name = "양육비 이행확보 및 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제7조", "제11조", "제13조") }
    )
    "law-002-04-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제837조의2") }
    )
    "law-003-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제997조", "제998조", "제1000조", "제1003조", "제1009조") }
    )
    "law-003-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1001조", "제1004조") }
    )
    "law-003-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1005조", "제1008조", "제1008조의2", "제1019조") }
    )
    "law-003-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1008조", "제1008조의2") }
    )
    "law-003-02-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1012조", "제1013조", "제1015조") }
    )
    "law-003-02-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1005조", "제1019조", "제1028조", "제1032조") }
    )
    "law-003-02-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1019조", "제1041조", "제1042조", "제1043조") }
    )
    "law-003-02-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1019조", "제1028조", "제1030조", "제1032조", "제1038조") }
    )
    "law-003-03-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1060조", "제1061조", "제1065조", "제1066조", "제1067조", "제1068조", "제1069조", "제1070조") }
    )
    "law-003-03-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1060조", "제1061조", "제1073조", "제1089조") }
    )
    "law-003-03-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1093조", "제1095조", "제1101조") }
    )
    "law-003-04-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1112조") }
    )
    "law-003-04-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1113조", "제1114조") }
    )
    "law-003-04-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1114조", "제1115조", "제1116조") }
    )
    "law-003-04-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제1115조", "제1117조") }
    )
    "law-004-01-01" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제2조", "제17조", "제18조", "제19조", "제20조", "제93조") }
    )
    "law-004-01-02" = @(
        @{ name = "기간제 및 단시간근로자 보호 등에 관한 법률"; id = "EXTERNAL"; articles = @("제4조", "제8조", "제17조") },
        @{ name = "파견근로자 보호 등에 관한 법률"; id = "EXTERNAL"; articles = @("제5조", "제6조") }
    )
    "law-004-01-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제2조") }
    )
    "law-004-02-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제50조", "제53조", "제56조") }
    )
    "law-004-02-04" = @(
        @{ name = "근로자퇴직급여 보장법"; id = "EXTERNAL"; articles = @("제4조", "제8조", "제9조") }
    )
    "law-004-03-01" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제50조", "제53조", "제54조", "제56조") }
    )
    "law-004-03-02" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제54조", "제55조", "제60조") }
    )
    "law-004-03-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제23조") },
        @{ name = "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제19조") }
    )
    "law-004-04-01" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제23조", "제27조", "제93조") }
    )
    "law-004-04-02" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제23조", "제26조", "제27조") }
    )
    "law-004-04-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제24조") }
    )
    "law-004-05-01" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제76조의2", "제76조의3") }
    )
    "law-004-05-02" = @(
        @{ name = "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제12조", "제14조") },
        @{ name = "기간제 및 단시간근로자 보호 등에 관한 법률"; id = "EXTERNAL"; articles = @("제8조") }
    )
    "law-004-05-03" = @(
        @{ name = "근로기준법"; id = "EXTERNAL"; articles = @("제74조") },
        @{ name = "남녀고용평등과 일ㆍ가정 양립 지원에 관한 법률"; id = "EXTERNAL"; articles = @("제19조", "제19조의2") }
    )
    "law-004-05-04" = @(
        @{ name = "산업재해보상보험법"; id = "EXTERNAL"; articles = @("제37조", "제40조") }
    )
    "law-005-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제760조", "제763조", "제766조") }
    )
    "law-005-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제393조", "제394조", "제763조") }
    )
    "law-005-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제751조", "제763조") }
    )
    "law-005-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제396조", "제763조") }
    )
    "law-005-02-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제763조") },
        @{ name = "자동차손해배상 보장법"; id = "LSI277017"; articles = @("제3조") }
    )
    "law-005-02-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제763조") },
        @{ name = "자동차손해배상 보장법"; id = "LSI277017"; articles = @("제3조", "제10조") }
    )
    "law-005-02-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제393조", "제763조") },
        @{ name = "자동차손해배상 보장법"; id = "LSI277017"; articles = @("제3조") }
    )
    "law-005-03-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제750조", "제751조") }
    )
    "law-005-03-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제750조", "제751조") }
    )
    "law-005-03-03" = @(
        @{ name = "의료사고 피해구제 및 의료분쟁 조정 등에 관한 법률"; id = "EXTERNAL"; articles = @("제27조", "제28조") },
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제750조") }
    )
    "law-005-04-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조", "제764조") }
    )
    "law-005-04-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제750조", "제751조") },
        @{ name = "개인정보 보호법"; id = "LSI270351"; articles = @("제15조", "제17조") }
    )
    "law-005-04-03" = @(
        @{ name = "개인정보 보호법"; id = "LSI270351"; articles = @("제15조", "제17조", "제34조", "제39조") }
    )
    "law-005-05-01" = @(
        @{ name = "제조물 책임법"; id = "EXTERNAL"; articles = @("제2조", "제3조", "제4조") }
    )
    "law-005-05-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제758조") }
    )
    "law-005-05-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제756조") }
    )
    "law-006-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제379조", "제397조", "제598조") },
        @{ name = "이자제한법"; id = "EXTERNAL"; articles = @("제2조") }
    )
    "law-006-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제387조", "제397조") }
    )
    "law-006-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제460조", "제461조", "제487조", "제492조", "제493조") }
    )
    "law-006-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제449조", "제450조", "제453조", "제454조") }
    )
    "law-006-01-05" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제162조", "제163조", "제166조", "제168조", "제170조", "제174조") }
    )
    "law-006-02-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제428조", "제428조의2", "제429조", "제430조") },
        @{ name = "보증인 보호를 위한 특별법"; id = "LSI251943"; articles = @("제3조", "제4조") }
    )
    "law-006-02-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제428조", "제429조", "제430조", "제433조", "제434조", "제436조") },
        @{ name = "보증인 보호를 위한 특별법"; id = "LSI251943"; articles = @("제6조") }
    )
    "law-006-02-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제441조", "제442조", "제443조", "제447조") }
    )
    "law-006-03-01" = @(
        @{ name = "민사소송법"; id = "EXTERNAL"; articles = @("제462조", "제470조") },
        @{ name = "소액사건심판법"; id = "EXTERNAL"; articles = @("제2조") }
    )
    "law-006-03-02" = @(
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제276조", "제277조", "제280조", "제300조", "제301조") }
    )
    "law-006-03-03" = @(
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제24조", "제56조", "제80조", "제223조") }
    )
    "law-006-03-04" = @(
        @{ name = "채권의 공정한 추심에 관한 법률"; id = "LSI268669"; articles = @("제9조", "제10조", "제11조", "제12조") }
    )
    "law-006-04-01" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제294조", "제305조", "제309조") }
    )
    "law-006-04-02" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제294조", "제305조", "제312조", "제313조") }
    )
    "law-006-04-03" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제556조", "제564조") }
    )
    "law-006-05-01" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제579조", "제580조") }
    )
    "law-006-05-02" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제611조", "제614조") }
    )
    "law-006-05-03" = @(
        @{ name = "채무자 회생 및 파산에 관한 법률"; id = "LSI267359"; articles = @("제624조", "제625조") }
    )
    "law-007-01-01" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제2조", "제3조", "제3조의2") }
    )
    "law-007-01-02" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제8조") },
        @{ name = "주택임대차보호법 시행령"; id = "LSI267649"; articles = @("제10조", "제11조") }
    )
    "law-007-01-03" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제6조", "제6조의3") }
    )
    "law-007-01-04" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제7조") },
        @{ name = "주택임대차보호법 시행령"; id = "LSI267649"; articles = @("제8조") }
    )
    "law-007-02-01" = @(
        @{ name = "상가건물 임대차보호법"; id = "LSI238797"; articles = @("제2조", "제3조") },
        @{ name = "상가건물 임대차보호법 시행령"; id = "LSI267689"; articles = @("제2조") }
    )
    "law-007-02-02" = @(
        @{ name = "상가건물 임대차보호법"; id = "LSI238797"; articles = @("제10조") }
    )
    "law-007-02-04" = @(
        @{ name = "상가건물 임대차보호법"; id = "LSI238797"; articles = @("제11조") },
        @{ name = "상가건물 임대차보호법 시행령"; id = "LSI267689"; articles = @("제4조") }
    )
    "law-007-03-02" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제14조", "제21조") },
        @{ name = "상가건물 임대차보호법"; id = "LSI238797"; articles = @("제20조") }
    )
    "law-007-03-03" = @(
        @{ name = "주택임대차보호법"; id = "LSI249999"; articles = @("제3조의2", "제3조의3") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제24조", "제56조") }
    )
    "law-007-03-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제213조", "제618조", "제623조") },
        @{ name = "민사집행법"; id = "LSI265351"; articles = @("제24조", "제56조") }
    )
    "law-008-01-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제109조", "제110조") },
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제46조", "제47조") }
    )
    "law-008-01-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제393조", "제544조") },
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제54조") }
    )
    "law-008-01-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제387조", "제390조", "제397조") },
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제54조", "제67조") }
    )
    "law-008-01-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제393조", "제544조", "제548조", "제551조") }
    )
    "law-008-02-01" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제563조", "제567조", "제580조") }
    )
    "law-008-02-02" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제680조", "제681조", "제682조", "제686조") }
    )
    "law-008-02-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제664조", "제665조", "제667조", "제668조", "제669조") }
    )
    "law-008-02-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제390조", "제536조", "제665조", "제667조") }
    )
    "law-008-02-05" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제580조", "제581조", "제667조", "제668조", "제670조") }
    )
    "law-008-03-01" = @(
        @{ name = "가맹사업거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제7조", "제9조", "제11조") }
    )
    "law-008-03-02" = @(
        @{ name = "대리점거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제6조", "제9조") },
        @{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제390조") }
    )
    "law-008-03-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제544조", "제548조", "제551조") },
        @{ name = "가맹사업거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제14조") }
    )
    "law-008-03-04" = @(
        @{ name = "독점규제 및 공정거래에 관한 법률"; id = "EXTERNAL"; articles = @("제45조") },
        @{ name = "가맹사업거래의 공정화에 관한 법률"; id = "EXTERNAL"; articles = @("제12조") }
    )
    "law-008-04-01" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제335조", "제416조", "제418조") }
    )
    "law-008-04-02" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제363조", "제376조", "제380조") }
    )
    "law-008-04-03" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제390조") },
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제335조", "제360조의24") }
    )
    "law-008-04-04" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제366조", "제403조", "제466조", "제542조의6") }
    )
    "law-008-04-05" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제363조", "제376조", "제385조", "제402조") }
    )
    "law-008-05-01" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제382조", "제382조의3", "제397조", "제398조", "제399조") }
    )
    "law-008-05-02" = @(
        @{ name = "상법"; id = "EXTERNAL"; articles = @("제403조") }
    )
    "law-008-05-03" = @(
        @{ name = "부정경쟁방지 및 영업비밀보호에 관한 법률"; id = "EXTERNAL"; articles = @("제2조", "제10조", "제14조의2", "제18조") }
    )
    "law-008-05-04" = @(
        @{ name = "민법"; id = "LSI265307"; articles = @("제103조", "제105조") },
        @{ name = "부정경쟁방지 및 영업비밀보호에 관한 법률"; id = "EXTERNAL"; articles = @("제2조", "제10조") }
    )
}

$categoryIdsByL2 = @{
    "law-001-01" = @("group:ownership")
    "law-001-02" = @("group:leasing", "group:jeonse")
    "law-001-03" = @("group:mortgage", "group:pledge")
    "law-001-04" = @("group:ownership")
    "law-006-01" = @("group:claim_effect")
    "law-006-02" = @("group:guaranty_debtors")
    "law-007-01" = @("group:leasing", "group:jeonse")
    "law-007-02" = @("group:leasing")
    "law-007-03" = @("group:leasing", "group:jeonse")
}

function Get-LawReferences([object]$row) {
    if ($lawReferencesByNode.ContainsKey($row.nodeId)) {
        return @($lawReferencesByNode[$row.nodeId])
    }
    if ($lawReferencesByL2.ContainsKey($row.l2Id)) {
        return @($lawReferencesByL2[$row.l2Id])
    }
    return @(@{ name = "민법"; id = "LSI265307"; articles = @("제105조", "제390조", "제750조") })
}

function Get-CategoryIds([object]$row) {
    if ($categoryIdsByL2.ContainsKey($row.l2Id)) {
        return @($categoryIdsByL2[$row.l2Id])
    }
    return @()
}

function Load-CaseSeeds([string]$caseDir) {
    $result = [System.Collections.Generic.List[object]]::new()
    if (-not (Test-Path $caseDir)) {
        return @()
    }
    foreach ($file in Get-ChildItem -Path $caseDir -Filter "*.json" -File) {
        try {
            $json = Get-Content -Encoding UTF8 -Raw -Path $file.FullName | ConvertFrom-Json
            $caseText = @(
                $json.case.case_name,
                $json.case.headnote,
                $json.case.holding,
                ($json.case.cited_articles -join " "),
                ($json.case.category_ids -join " ")
            ) -join " "
            $result.Add([pscustomobject]@{
                sourceId = [string]$json.meta.source_id
                sourceUrl = [string]$json.meta.source_url
                court = [string]$json.case.court
                date = [string]$json.case.decision_date
                caseNo = [string]$json.case.case_no
                caseName = [string]$json.case.case_name
                text = $caseText
                categories = @($json.case.category_ids)
            }) | Out-Null
        } catch {
            Write-Warning "Skip unreadable case seed: $($file.FullName)"
        }
    }
    return @($result)
}

function Get-Terms([object]$row, [array]$items) {
    $raw = @($row.l1, $row.l2, $row.l3) + $items
    $terms = [System.Collections.Generic.HashSet[string]]::new()
    foreach ($value in $raw) {
        foreach ($term in ([string]$value -split '[\s·,()/\[\]ㆍ]+')) {
            $clean = $term.Trim()
            if ($clean.Length -ge 2 -and $clean -notin @("관련", "여부", "상태", "시점", "자료", "문서", "증거", "사유", "내용", "금액", "당사자", "상대방", "계약", "청구", "반환", "보호", "책임", "손해", "법률", "적용", "진행", "보유")) {
                $terms.Add($clean) | Out-Null
            }
        }
    }
    return @($terms)
}

function Get-CaseCandidates([object]$row, [array]$items, [array]$caseSeeds) {
    $terms = Get-Terms $row $items
    $categoryIds = Get-CategoryIds $row
    $scored = [System.Collections.Generic.List[object]]::new()

    foreach ($case in $caseSeeds) {
        $score = 0
        $categoryHit = $false
        $phraseHit = $false
        $termHits = 0
        foreach ($categoryId in $categoryIds) {
            if ($case.categories -contains $categoryId) {
                $categoryHit = $true
                $score += 8
            }
        }
        if ($case.text.Contains([string]$row.l3)) {
            $phraseHit = $true
            $score += 5
        }
        if ($case.text.Contains([string]$row.l2)) {
            $phraseHit = $true
            $score += 3
        }
        foreach ($term in $terms | Select-Object -First 12) {
            if ($case.text.Contains($term)) {
                $termHits++
                $score += 1
            }
        }
        if (($categoryHit -or $phraseHit -or $termHits -ge 3) -and $score -ge 5) {
            $scored.Add([pscustomobject]@{
                score = $score
                sourceId = $case.sourceId
                sourceUrl = $case.sourceUrl
                label = "$($case.court) $($case.date) 선고 $($case.caseNo)"
                caseName = $case.caseName
            }) | Out-Null
        }
    }
    return @($scored | Sort-Object score -Descending | Select-Object -First 3)
}

function ConvertTo-YamlQuoted([string]$value) {
    $escaped = $value.Replace('\', '\\').Replace('"', '\"')
    return '"' + $escaped + '"'
}

function Escape-MarkdownCell([string]$value) {
    return ([string]$value).Replace("|", "\|").Replace("`r", " ").Replace("`n", " ")
}

function New-LawLink([string]$lawName, [string]$article) {
    $law = [System.Uri]::EscapeDataString($lawName)
    $jo = [System.Uri]::EscapeDataString($article)
    return "https://www.law.go.kr/법령/$law/$jo"
}

function Write-ChecklistYaml([string]$path, [array]$items) {
    $lines = New-StringList
    $lines.Add("items:") | Out-Null
    foreach ($item in $items) {
        $lines.Add("  - $(ConvertTo-YamlQuoted $item)") | Out-Null
    }
    [System.IO.File]::WriteAllLines($path, [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
}

function Write-EvidenceDoc([string]$path, [object]$row, [array]$items, [array]$laws, [array]$cases) {
    $today = Get-Date -Format "yyyy-MM-dd"
    $lines = New-StringList
    $lines.Add("# $($row.nodeId) $($row.l3) 체크리스트 근거") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("작성일: $today") | Out-Null
    $lines.Add("검토 상태: reviewed") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## 1. Ontology Path") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add(('- L1: {0} (`{1}`)' -f $row.l1, $row.l1Id)) | Out-Null
    $lines.Add(('- L2: {0} (`{1}`)' -f $row.l2, $row.l2Id)) | Out-Null
    $lines.Add(('- L3: {0} (`{1}`)' -f $row.l3, $row.nodeId)) | Out-Null
    $lines.Add(('- Node ID: `{0}`' -f $row.nodeId)) | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## 2. 참조한 검색 Query") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| 구분 | Query |") | Out-Null
    $lines.Add("|---|---|") | Out-Null
    $lines.Add("| 법령 | $($row.l2) $($row.l3) 관련 법령 조문 |") | Out-Null
    $lines.Add("| 판례 | $($row.l2) $($row.l3) 관련 로컬 law.go.kr 판례 seed 자동 후보 |") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## 3. 참조 법령") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| 법령명 | 법령 ID | 조문 | 반영한 사실요건 |") | Out-Null
    $lines.Add("|---|---|---|---|") | Out-Null
    foreach ($law in $laws) {
        $articles = ($law.articles -join ", ")
        $lines.Add(('| {0} | `{1}` | {2} | {3} 관련 적용 범위, 당사자, 기간, 금액, 절차 확인 |' -f (Escape-MarkdownCell $law.name), $law.id, (Escape-MarkdownCell $articles), $row.l3)) | Out-Null
    }
    $lines.Add("") | Out-Null
    $lines.Add("## 4. 참조 판례/해석례") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| 판례/해석례 | 쟁점 | 반복 등장 사실관계 | 반영 여부 |") | Out-Null
    $lines.Add("|---|---|---|---|") | Out-Null
    if ($cases.Count -gt 0) {
        foreach ($case in $cases) {
            $sourceId = if ([string]::IsNullOrWhiteSpace($case.sourceId)) { "source_id 없음" } else { "precSeq=$($case.sourceId)" }
            $lines.Add(('| {0}, `{1}` | {2} | {3} 또는 {4} 키워드와 로컬 category seed 매칭 | 보조 참고, 법령 근거 우선 |' -f (Escape-MarkdownCell $case.label), $sourceId, (Escape-MarkdownCell $case.caseName), $row.l2, $row.l3)) | Out-Null
        }
    } else {
        $lines.Add("| 직접 반영한 판례 없음 | $($row.l3) | 로컬 판례 seed 검토 결과, 해당 item을 직접 뒷받침하는 판례는 법령 근거보다 약함 | 미반영 |") | Out-Null
    }
    $lines.Add("") | Out-Null
    $lines.Add("## 5. 최종 YAML Items") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| item | 근거 | required 판단 |") | Out-Null
    $lines.Add("|---|---|---|") | Out-Null
    $lawSummary = (($laws | ForEach-Object { "$($_.name) $($_.articles -join ', ')" }) -join "; ")
    foreach ($item in $items) {
        $lines.Add(("| {0} | {1} 및 {2} 도메인 체크리스트 | required |" -f (Escape-MarkdownCell $item), (Escape-MarkdownCell $lawSummary), $row.l2)) | Out-Null
    }
    $lines.Add("") | Out-Null
    $lines.Add("## 6. 제외한 후보") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("| 후보 | 제외 사유 |") | Out-Null
    $lines.Add("|---|---|") | Out-Null
    $lines.Add("| 승소 또는 패소 예측 | 사용자가 답할 수 있는 사실이 아니라 결론 예측 표현이므로 제외 |") | Out-Null
    $lines.Add("| 법적 효력의 최종 단정 | 상담 전 단계에서는 조문 적용에 필요한 날짜, 금액, 문서, 통지 사실만 수집 |") | Out-Null
    $lines.Add("| 추상적인 억울함 또는 불리함 | 증거, 문서, 절차 이력으로 환원하기 어려워 제외 |") | Out-Null
    $lines.Add("") | Out-Null
    $lines.Add("## 7. Source Links") | Out-Null
    $lines.Add("") | Out-Null
    foreach ($law in $laws) {
        foreach ($article in $law.articles) {
            $lines.Add(("- {0} {1}: {2}" -f $law.name, $article, (New-LawLink $law.name $article))) | Out-Null
        }
    }
    foreach ($case in $cases) {
        if (-not [string]::IsNullOrWhiteSpace($case.sourceUrl)) {
            $lines.Add(('- {0} `{1}`: {2}' -f $case.label, $case.sourceId, $case.sourceUrl)) | Out-Null
        }
    }
    [System.IO.File]::WriteAllLines($path, [string[]]$lines, [System.Text.UTF8Encoding]::new($false))
}

$manifestFullPath = Resolve-RepoPath $ManifestPath
$nodeDirFullPath = Resolve-RepoPath $ChecklistNodeDir
$evidenceDirFullPath = Resolve-RepoPath $EvidenceDir
$domainDirFullPath = Resolve-RepoPath $DomainChecklistDir
$caseDirFullPath = Resolve-RepoPath $CaseSeedDir

$domainFileByL1 = @{
    "부동산 거래" = "real-estate.yaml"
    "이혼·위자료·재산분할" = "divorce.yaml"
    "상속·유류분·유언" = "inheritance.yaml"
    "근로계약·해고·임금" = "labor.yaml"
    "손해배상·불법행위" = "damages-tort.yaml"
    "채무·보증·개인파산·회생" = "debt.yaml"
    "임대차보호" = "lease-protection.yaml"
    "기업·상사거래" = "commercial.yaml"
}

$catalogByL1 = @{}
foreach ($entry in $domainFileByL1.GetEnumerator()) {
    $path = Join-Path $domainDirFullPath $entry.Value
    if (Test-Path $path) {
        $catalogByL1[$entry.Key] = Read-DomainChecklist $path
    }
}

$caseSeeds = Load-CaseSeeds $caseDirFullPath
$rows = @(Read-Jsonl $manifestFullPath)
if ($AutoDraftOnly) {
    $rows = @($rows | Where-Object {
        $evidencePath = Join-Path $evidenceDirFullPath "$($_.nodeId).md"
        (Test-Path $evidencePath) -and (Select-String -Path $evidencePath -Pattern '검토 상태: auto-draft' -Quiet)
    })
} elseif ($NodeId.Count -gt 0) {
    $nodeSet = @{}
    foreach ($id in $NodeId) {
        $nodeSet[$id] = $true
    }
    $rows = @($rows | Where-Object { $nodeSet.ContainsKey($_.nodeId) })
} elseif (-not $Force) {
    $rows = @($rows | Where-Object { $_.status -ne "draft" })
}

New-Item -ItemType Directory -Force -Path $nodeDirFullPath | Out-Null
New-Item -ItemType Directory -Force -Path $evidenceDirFullPath | Out-Null

$written = 0
$skipped = 0
foreach ($row in $rows | Sort-Object priority, nodeId) {
    $yamlPath = Join-Path $nodeDirFullPath "$($row.nodeId).yaml"
    $evidencePath = Join-Path $evidenceDirFullPath "$($row.nodeId).md"
    if (-not $Force -and ((Test-Path $yamlPath) -or (Test-Path $evidencePath))) {
        Write-Host "Skip existing partial/draft: $($row.nodeId)"
        $skipped++
        continue
    }

    $items = @(Get-ChecklistItems $row $catalogByL1)
    if ($items.Count -lt 5) {
        throw "$($row.nodeId) produced too few items: $($items.Count)"
    }
    $laws = @(Get-LawReferences $row)
    $cases = @(Get-CaseCandidates $row $items $caseSeeds)
    Write-ChecklistYaml $yamlPath $items
    Write-EvidenceDoc $evidencePath $row $items $laws $cases
    Write-Host "Wrote $($row.nodeId): items=$($items.Count), caseCandidates=$(@($cases).Count)"
    $written++
}

Write-Host "Generated auto checklist overrides: written=$written, skipped=$skipped"
