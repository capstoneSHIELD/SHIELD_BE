from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path


SCHEMA_VERSION = "wrong-selected-cross-l1-testcases.v1"
DESCRIPTION = (
    "사용자가 서로 다른 L1의 분야 2개를 잘못 선택했을 때, 자연어 입력만 보고 "
    "LLM이 실제 법률 분야를 탐지하는지 평가하기 위한 테스트 케이스."
)
SELECTION_RULE = "selectedLabels는 항상 서로 다른 L1 2개로 구성하며, goldLabels의 L1과 겹치지 않는다."
SOURCE_ONTOLOGY_PATH = r"C:\SHIELD_BE\.codex\ref_docs\ai-rag-v2.2\checklist-evidence"


@dataclass(frozen=True)
class Label:
    node_id: str
    l1: str
    l2: str
    l3: str

    def to_json(self) -> dict[str, str]:
        return {
            "nodeId": self.node_id,
            "l1": self.l1,
            "l2": self.l2,
            "l3": self.l3,
        }


PROFILE_BY_L1 = {
    "부동산 거래": {
        "subject": "부동산 계약과 권리관계",
        "counterparty": "상대방과 중개인이",
        "asset": ["아파트", "상가 점포", "토지", "오피스텔", "다가구주택"],
        "evidence": "계약서, 등기부등본, 중개대상물 확인서, 문자 기록",
        "relief": "계약 책임, 등기·인도 절차, 손해배상 가능성",
    },
    "이혼·위자료·재산분할": {
        "subject": "혼인관계 정리와 가족 문제",
        "counterparty": "배우자 측이",
        "asset": ["공동 주거지", "예금과 대출", "자녀 양육 일정", "혼인 중 형성한 재산", "별거 이후 생활비"],
        "evidence": "혼인관계 자료, 계좌 내역, 대화 기록, 가족관계 서류",
        "relief": "청구 범위, 책임 소재, 가정법원 절차",
    },
    "상속·유류분·유언": {
        "subject": "상속재산과 유언 관련 분쟁",
        "counterparty": "다른 상속인이",
        "asset": ["부친 명의 부동산", "예금과 보험금", "생전 증여 재산", "유언장", "상속채무"],
        "evidence": "가족관계증명서, 등기부등본, 금융거래 내역, 유언 관련 자료",
        "relief": "상속분 계산, 반환 청구, 분할 또는 무효 다툼",
    },
    "근로계약·해고·임금": {
        "subject": "직장 내 근로조건과 고용 문제",
        "counterparty": "회사 측이",
        "asset": ["근로계약서", "급여와 수당", "해고 통보", "근무표", "인사평가 자료"],
        "evidence": "근로계약서, 급여명세서, 출퇴근 기록, 사내 메신저",
        "relief": "노동청 진정, 노동위원회 구제, 미지급 금품 청구",
    },
    "손해배상·불법행위": {
        "subject": "불법행위로 인한 손해배상 문제",
        "counterparty": "상대방이",
        "asset": ["사고 현장", "게시글과 댓글", "진료 기록", "수리 견적", "개인정보 자료"],
        "evidence": "사진, 캡처 화면, 진단서, 견적서, 목격자 진술",
        "relief": "손해액 산정, 위자료, 책임 비율과 소송 가능성",
    },
    "채무·보증·개인파산·회생": {
        "subject": "채권 회수와 채무 정리 문제",
        "counterparty": "채무자 또는 보증인이",
        "asset": ["차용금", "보증채무", "압류 대상 재산", "변제계획", "연체 채무"],
        "evidence": "계좌이체 내역, 차용 관련 대화, 독촉장, 재산 자료",
        "relief": "지급명령, 보전처분, 강제집행, 파산·회생 절차",
    },
    "임대차보호": {
        "subject": "임대차 보증금과 임차인 보호 문제",
        "counterparty": "임대인이",
        "asset": ["전세보증금", "상가 임대차계약", "갱신 요구", "임차권등기", "차임 인상 통보"],
        "evidence": "임대차계약서, 확정일자 자료, 문자 기록, 보증금 입금 내역",
        "relief": "보증금 회수, 갱신 요구, 임차권등기명령, 분쟁조정",
    },
    "기업·상사거래": {
        "subject": "상사계약과 회사 운영 분쟁",
        "counterparty": "거래처 또는 주주 측이",
        "asset": ["공급계약", "용역계약", "주주간 계약", "가맹계약", "영업비밀 자료"],
        "evidence": "계약서, 발주서, 세금계산서, 이메일, 회의록",
        "relief": "계약 이행, 대금 청구, 손해배상, 주주권 행사",
    },
}

AMOUNTS = [
    "1천8백만 원",
    "3천2백만 원",
    "5천만 원",
    "8천5백만 원",
    "1억2천만 원",
    "2억 원",
    "월 250만 원",
    "분기별 4천만 원",
    "계약금 10%",
    "잔금 70%",
]
TIMES = [
    "지난 1월부터",
    "계약 체결 후 3개월 뒤",
    "올해 4월 말부터",
    "통지를 받은 다음 날부터",
    "잔금일 직후",
    "별거가 시작된 뒤",
    "상대방이 입장을 바꾼 이후",
    "기한이 지난 지 두 달째",
    "자료를 확인한 지난주부터",
    "분쟁이 커진 최근 한 달 동안",
]
CONCERNS = [
    "상대방이 책임을 부인하고 있습니다.",
    "처리 기한을 놓칠까 봐 걱정됩니다.",
    "자료를 어떻게 정리해야 할지 모르겠습니다.",
    "먼저 내용증명을 보내야 하는지도 궁금합니다.",
    "소송 전 보전 조치가 가능한지 확인하고 싶습니다.",
    "협의가 결렬될 경우 다음 절차를 알고 싶습니다.",
    "금액 산정 기준을 객관적으로 정리해야 합니다.",
    "상대방이 일부 자료를 숨기는 것 같습니다.",
    "증거를 더 확보해야 하는지 판단이 필요합니다.",
    "실무적으로 어떤 청구가 현실적인지 알고 싶습니다.",
]


def main() -> None:
    parser = argparse.ArgumentParser(description="Generate wrong-selected testcase JSON files.")
    parser.add_argument(
        "--ontology",
        type=Path,
        default=Path("src/main/resources/ontology/legal-ontology-slim.json"),
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=Path("src/test/testcases/wrong"),
    )
    parser.add_argument("--start", type=int, default=31)
    parser.add_argument("--end", type=int, default=300)
    args = parser.parse_args()

    labels = load_leaf_labels(args.ontology)
    labels_by_l1 = group_by_l1(labels)
    args.output_dir.mkdir(parents=True, exist_ok=True)

    created = 0
    for case_number in range(args.start, args.end + 1):
        file_name = f"wrong-x1-{case_number:03d}.json"
        gold = choose_gold(labels, case_number)
        selected = choose_selected(labels_by_l1, gold, case_number)
        payload = build_case(case_number, gold, selected)
        (args.output_dir / file_name).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n",
            encoding="utf-8-sig",
        )
        created += 1

    print(f"generated={created}")
    print(f"range=wrong-x1-{args.start:03d}.json..wrong-x1-{args.end:03d}.json")


def load_leaf_labels(path: Path) -> list[Label]:
    ontology = json.loads(path.read_text(encoding="utf-8-sig"))
    labels: list[Label] = []
    for l1 in ontology.get("c", []):
        for l2 in l1.get("c", []):
            for l3 in l2.get("c", []):
                labels.append(
                    Label(
                        node_id=str(l3["id"]),
                        l1=str(l1["name"]),
                        l2=str(l2["name"]),
                        l3=str(l3["name"]),
                    )
                )
    return sorted(labels, key=lambda label: label.node_id)


def group_by_l1(labels: list[Label]) -> dict[str, list[Label]]:
    grouped: dict[str, list[Label]] = {}
    for label in labels:
        grouped.setdefault(label.l1, []).append(label)
    return {l1: sorted(items, key=lambda item: item.node_id) for l1, items in grouped.items()}


def choose_gold(labels: list[Label], case_number: int) -> Label:
    # 37 is coprime with the current leaf count, so this walks the ontology broadly before repeating.
    return labels[((case_number - 31) * 37) % len(labels)]


def choose_selected(labels_by_l1: dict[str, list[Label]], gold: Label, case_number: int) -> list[Label]:
    wrong_l1s = sorted(l1 for l1 in labels_by_l1 if l1 != gold.l1)
    first_l1 = wrong_l1s[(case_number * 3) % len(wrong_l1s)]
    second_l1 = wrong_l1s[(case_number * 5 + 2) % len(wrong_l1s)]
    if first_l1 == second_l1:
        second_l1 = wrong_l1s[(wrong_l1s.index(second_l1) + 1) % len(wrong_l1s)]
    first = labels_by_l1[first_l1][case_number % len(labels_by_l1[first_l1])]
    second = labels_by_l1[second_l1][(case_number * 2 + 1) % len(labels_by_l1[second_l1])]
    return [first, second]


def build_case(case_number: int, gold: Label, selected: list[Label]) -> dict:
    case_id = f"WRONG-X1-{case_number:03d}"
    turns = build_turns(case_number, gold)
    return {
        "schemaVersion": SCHEMA_VERSION,
        "description": DESCRIPTION,
        "sourceOntologyPath": SOURCE_ONTOLOGY_PATH,
        "selectionRule": SELECTION_RULE,
        "case": {
            "caseId": case_id,
            "group": "wrong_selected_cross_l1",
            "selectedLabels": [label.to_json() for label in selected],
            "goldLabels": [gold.to_json()],
            "turns": turns,
        },
    }


def build_turns(case_number: int, gold: Label) -> list[dict]:
    profile = PROFILE_BY_L1[gold.l1]
    asset = profile["asset"][case_number % len(profile["asset"])]
    amount = AMOUNTS[case_number % len(AMOUNTS)]
    time = TIMES[case_number % len(TIMES)]
    concern = CONCERNS[case_number % len(CONCERNS)]
    evidence = profile["evidence"]
    relief = profile["relief"]
    counterparty = profile["counterparty"]
    subject = profile["subject"]
    node_id = gold.node_id

    user_inputs = [
        "상담받고 싶은 일이 있어 내용을 차례대로 말씀드리겠습니다.",
        f"{subject}와 관련해 {asset}에서 문제가 생겼고, 핵심은 {gold.l2} 중 {gold.l3}에 해당하는 사안입니다.",
        f"{time} 문제가 본격화됐고 관련 금액이나 부담은 {amount} 정도로 보고 있습니다.",
        f"{counterparty} 제 요구를 받아들이지 않거나 전혀 다른 설명을 하고 있어 법적 판단이 필요합니다.",
        f"현재 쟁점은 {gold.l3}에 관한 권리와 책임을 어떻게 정리할지입니다.",
        f"제가 가진 자료는 {evidence}이고, 일부 자료는 상대방에게 추가로 요청해야 합니다.",
        f"상대방은 구두 합의나 관행을 이유로 책임을 줄이려 하지만 저는 문서와 실제 진행 경과가 더 중요하다고 봅니다.",
        f"{concern}",
        f"가능하다면 {relief}까지 한 번에 검토하고 싶습니다.",
        f"최종적으로 이 사안이 {gold.l1}의 {gold.l2}, 특히 {gold.l3} 문제로 분류되는지 확인하고 대응 순서를 정리하고 싶습니다.",
    ]

    turns: list[dict] = []
    for index, user_input in enumerate(user_inputs, start=1):
        evaluation_target = index != 1
        turns.append(
            {
                "turnIndex": index,
                "userInput": user_input,
                "observableGoldNodeIds": [node_id] if evaluation_target else [],
                "evaluationTarget": evaluation_target,
            }
        )
    return turns


if __name__ == "__main__":
    main()
