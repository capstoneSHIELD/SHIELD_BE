"""Phase 0 baseline 부하 스크립트 — sendMessage 100건을 발사해 Micrometer 타이머에 표본을 채운다.

목적:
  /actuator/prometheus 의 shield.rag.classify / shield.rag.pipeline.total /
  shield.rag.retrieve / shield.chat.cohere.call / shield.chat.send_message
  시리즈에 표본 100건을 누적해 baseline p50/p95 계산이 가능하도록 한다.

사용:
  # 단일 상담에 N 메시지 (10턴 상한 주의 — 한 상담에는 최대 10건 권장)
  python scripts/baseline_load.py \
    --base-url http://localhost:8080 \
    --token "Bearer eyJ..." \
    --consultation-id <uuid> \
    --count 10

  # 또는 여러 상담 ID 를 콤마로 (총 count 가 100 이 되도록 분배)
  python scripts/baseline_load.py \
    --base-url http://localhost:8080 \
    --token "Bearer eyJ..." \
    --consultation-ids id1,id2,id3,...,id10 \
    --per-consultation 10

준비:
  - 백엔드 기동, /actuator/prometheus 노출 확인 (curl localhost:8080/actuator/prometheus | head)
  - 토큰: 프론트에서 카카오 로그인 후 DevTools 로 추출하거나 /api/auth/* 엔드포인트 직접 호출
  - consultationId: 사용자가 L1 분야를 선택한 상태여야 RAG 가 작동 (domainForRag != null)

산출:
  - stdout 에 각 요청의 wall-clock 시간 + 응답 sample
  - 종료 후 prometheus 추출 안내
"""

from __future__ import annotations

import argparse
import json
import sys
import time
from dataclasses import dataclass
from statistics import median, quantiles
from typing import List

import requests


@dataclass
class CallResult:
    consultation_id: str
    turn: int
    ok: bool
    status: int
    latency_ms: float
    response_excerpt: str


SAMPLE_MESSAGES = [
    "전세보증금을 못 받고 있어요 어떻게 해야 하나요",
    "임차인이 월세를 3개월째 안 내고 있어요",
    "이혼하려면 어떤 절차를 거쳐야 하나요",
    "상속 포기를 하려면 언제까지 신청해야 하나요",
    "교통사고가 났는데 합의금이 적정한지 모르겠어요",
    "회사에서 부당해고를 당했어요 어떻게 대응해야 하나요",
    "물건을 사고 환불을 못 받고 있어요",
    "층간소음 분쟁이 심해지고 있어요",
    "사기 피해를 당했는데 형사고소가 가능한가요",
    "유언장이 없는 상태에서 형제간 상속 분쟁이 있어요",
]


def send_message(base_url: str, token: str, consultation_id: str, content: str, timeout: float) -> CallResult:
    url = f"{base_url.rstrip('/')}/api/consultations/{consultation_id}/messages"
    started = time.perf_counter()
    try:
        resp = requests.post(
            url,
            headers={
                "Authorization": token if token.lower().startswith("bearer ") else f"Bearer {token}",
                "Content-Type": "application/json",
            },
            data=json.dumps({"content": content}),
            timeout=timeout,
        )
        elapsed_ms = (time.perf_counter() - started) * 1000
        excerpt = resp.text[:120].replace("\n", " ")
        return CallResult(consultation_id, -1, resp.ok, resp.status_code, elapsed_ms, excerpt)
    except requests.RequestException as e:
        elapsed_ms = (time.perf_counter() - started) * 1000
        return CallResult(consultation_id, -1, False, 0, elapsed_ms, f"EXC {type(e).__name__}: {e}")


def summarize_client_side(results: List[CallResult]) -> None:
    ok_lat = [r.latency_ms for r in results if r.ok]
    if not ok_lat:
        print("⚠ 성공 요청 없음 — 토큰/consultationId/네트워크 확인 필요")
        return
    ok_lat.sort()
    p50 = median(ok_lat)
    # quantiles n=20 → 95-percentile boundary 인덱스 18 (= 95%)
    p95 = quantiles(ok_lat, n=20)[18] if len(ok_lat) >= 20 else max(ok_lat)
    print()
    print(f"=== 클라이언트 사이드 wall-clock latency (성공 {len(ok_lat)}/{len(results)}건) ===")
    print(f"  min : {min(ok_lat):8.1f} ms")
    print(f"  p50 : {p50:8.1f} ms")
    print(f"  p95 : {p95:8.1f} ms")
    print(f"  max : {max(ok_lat):8.1f} ms")
    print()
    print("== 서버 사이드 측정 (정확한 단계별 p50/p95) ==")
    print("백엔드 호스트에서 다음 실행:")
    print("  curl -s http://localhost:8080/actuator/prometheus | grep -E '^shield_(rag|chat)_' | head -60")
    print()
    print("기본 Spring Boot Timer 출력은 count/sum/max 만 노출됩니다.")
    print("p50/p95 추출 2가지 방법:")
    print("  A) 평균 = sum/count 만 일단 보고, Prometheus 서버에서 histogram_quantile 로 정확한 p50/p95 추출")
    print("  B) application.yml 의 management.metrics 에 다음 추가 후 재기동 — 클라이언트사이드 percentile 노출:")
    print("       distribution:")
    print("         percentiles:")
    print("           shield.rag.classify: 0.5, 0.95")
    print("           shield.rag.pipeline.total: 0.5, 0.95")
    print("           shield.rag.retrieve: 0.5, 0.95")
    print("           shield.chat.cohere.call: 0.5, 0.95")
    print("           shield.chat.send_message: 0.5, 0.95")


def main() -> int:
    parser = argparse.ArgumentParser(description="Phase 0 baseline load")
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--token", required=True, help="JWT (Bearer 접두사 자동 부여)")
    grp = parser.add_mutually_exclusive_group(required=True)
    grp.add_argument("--consultation-id", help="단일 상담 ID")
    grp.add_argument("--consultation-ids", help="콤마로 구분된 상담 ID 목록 (10턴 상한 분산)")
    parser.add_argument("--count", type=int, default=10, help="단일 상담 모드의 총 요청 수")
    parser.add_argument("--per-consultation", type=int, default=10, help="다중 상담 모드에서 상담당 요청 수")
    parser.add_argument("--timeout", type=float, default=200.0, help="HTTP 타임아웃 초")
    args = parser.parse_args()

    if args.consultation_id:
        targets = [(args.consultation_id, args.count)]
    else:
        ids = [s.strip() for s in args.consultation_ids.split(",") if s.strip()]
        targets = [(cid, args.per_consultation) for cid in ids]

    total_planned = sum(n for _, n in targets)
    print(f"총 {total_planned}건 발사 — {len(targets)}개 상담")

    results: List[CallResult] = []
    idx = 0
    for cid, n in targets:
        for turn in range(n):
            msg = SAMPLE_MESSAGES[idx % len(SAMPLE_MESSAGES)]
            r = send_message(args.base_url, args.token, cid, msg, args.timeout)
            r.turn = turn + 1
            results.append(r)
            idx += 1
            tag = "OK " if r.ok else "ERR"
            print(f"[{idx:3d}/{total_planned}] {tag} {r.status} {r.latency_ms:7.1f}ms cid={cid[:8]} turn={turn+1} -> {r.response_excerpt}")
            if not r.ok and r.status in (401, 403):
                print("\n⚠ 인증 실패 — 토큰 만료/잘못된 헤더. 중단합니다.")
                summarize_client_side(results)
                return 1

    summarize_client_side(results)
    return 0


if __name__ == "__main__":
    sys.exit(main())
