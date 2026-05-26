#!/usr/bin/env python3
"""Phase P5.5 Commit 4 — Cohere × HyperCLOVA X chat shadow 오프라인 비교.

평가셋 v1.6에서 각 item의 query를 Cohere chat API와 HyperCLOVA X chat completions에
동일한 system + user prompt로 던지고, 두 응답을 비교한다.

비교 지표:
- 평균 응답 길이 (chars)
- 평균 input/output 토큰
- GuardrailFilter regex hit rate
- 한국 법령 인용 정규식 매치 수
- 한국 판례 인용 정규식 매치 수
- 호출 latency p50/p95 (ms)
- 추정 비용 (모델별 단가 적용)

LLM-as-judge (Claude/GPT-4) 점수는 본 스크립트에서는 미구현 (별도 단계). 대신 두 응답을
JSONL로 덤프하여 후속 검토에서 활용 가능.

사용법:
  COHERE_API_KEY=... HYPERCLOVA_API_KEY=... python3 scripts/eval_chat.py \\
      --eval eval/eval-set.v1.6.jsonl \\
      --output docs/ai-rag-v2.2/reports/p5_5-chat-shadow.md \\
      --limit 50

  옵션:
    --skip-hyperclova        Cohere만 실행 (baseline 캡처용)
    --skip-cohere            HyperCLOVA만 실행
    --sample-only N          앞 N개만 평가
    --models cohere=command-a-03-2025,hyperclova=HCX-005

설계 메모:
- 평가셋의 query 필드를 그대로 user message로 전송 (single-turn).
- system prompt는 SHIELD chat 프롬프트와 유사한 비조언적 안내 톤 한 단락만 사용
  (실제 production system prompt는 길이가 커서 본 스크립트에서는 간략 버전).
- 비용은 application.yml pricing 섹션과 동기화된 단가 사용 (스크립트 상수).
- 한 번 호출 후 응답 텍스트를 .responses.jsonl 로 저장 → 후속 LLM-as-judge에 재사용 가능.
"""
from __future__ import annotations

import argparse
import json
import math
import os
import re
import statistics
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any

# === Pricing (USD per 1M tokens) — application.yml 동기화 ===
PRICING = {
    "command-a-03-2025": {"input": 2.50, "output": 10.00},
    "HCX-005":           {"input": 5.00, "output": 10.00},
    "HCX-DASH-002":      {"input": 0.50, "output": 1.50},
}

DEFAULT_MODELS = {
    "cohere": "command-a-03-2025",
    "hyperclova": "HCX-005",
}

# === Regex patterns (Java ChatProviderShadowComparator와 동등) ===
STATUTE_REF = re.compile(
    r"(?:민법|상법|형법|민사소송법|형사소송법|근로기준법|주택임대차보호법|상가건물임대차보호법)\s*제?\d+조"
)
CASE_REF = re.compile(r"대?법원?\s*\d{2,4}[가-힣]+\d+")

# Java GuardrailFilter의 핵심 패턴 일부만 미러링 (간략화). 더 완전한 검사는 backend integration.
FORBIDDEN_PATTERNS = [
    re.compile(r"승소\s*가능성"),
    re.compile(r"패소\s*가능성"),
    re.compile(r"이길\s*수\s*있"),
    re.compile(r"인정\s*됩니다"),
    re.compile(r"인정되는\s*경향"),
    re.compile(r"법원이\s*인정"),
    re.compile(r"변호사\s*추천"),
    re.compile(r"소장을\s*제출하세요"),
]

SYSTEM_PROMPT = (
    "당신은 한국 법률 상담 챗봇입니다. 변호사가 아니므로 법적 조언이나 단정적 결론을 "
    "직접 제공하지 않고, 사실관계 정리와 절차 안내, 일반 법령 정보만 제공합니다. "
    "사용자 메시지에 대해 다음 질문 1개를 한국어 존댓말로 짧게 제시하세요."
)


def cohere_chat(api_key: str, model: str, user_text: str) -> dict[str, Any]:
    payload = {
        "model": model,
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_text},
        ],
        "temperature": 0.3,
        "max_tokens": 512,
    }
    req = urllib.request.Request(
        "https://api.cohere.com/v2/chat",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
        },
        method="POST",
    )
    start = time.monotonic()
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    latency_ms = int((time.monotonic() - start) * 1000)

    text = ""
    if body.get("message") and body["message"].get("content"):
        parts = body["message"]["content"]
        if isinstance(parts, list) and parts:
            text = parts[0].get("text", "")

    usage = body.get("usage") or body.get("meta") or {}
    billed = usage.get("billed_units") or {}
    tokens_in = billed.get("input_tokens")
    tokens_out = billed.get("output_tokens")
    return {
        "text": text,
        "tokens_input": tokens_in,
        "tokens_output": tokens_out,
        "latency_ms": latency_ms,
    }


def hyperclova_chat(api_key: str, model: str, user_text: str) -> dict[str, Any]:
    base_url = os.environ.get("HYPERCLOVA_BASE_URL", "https://clovastudio.stream.ntruss.com")
    payload = {
        "messages": [
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_text},
        ],
        "topP": 0.8,
        "temperature": 0.3,
        "maxTokens": 512,
        "repetitionPenalty": 1.1,
        "includeAiFilters": False,
    }
    req = urllib.request.Request(
        f"{base_url}/testapp/v3/chat-completions/{model}",
        data=json.dumps(payload).encode("utf-8"),
        headers={
            "Authorization": f"Bearer {api_key}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    start = time.monotonic()
    with urllib.request.urlopen(req, timeout=60) as resp:
        body = json.loads(resp.read().decode("utf-8"))
    latency_ms = int((time.monotonic() - start) * 1000)

    result = body.get("result") or {}
    msg = result.get("message") or {}
    text = msg.get("content", "")
    return {
        "text": text,
        "tokens_input": result.get("inputLength"),
        "tokens_output": result.get("outputLength"),
        "latency_ms": latency_ms,
    }


def count_regex(pattern: re.Pattern, text: str) -> int:
    if not text:
        return 0
    return sum(1 for _ in pattern.finditer(text))


def guardrail_hits(text: str) -> int:
    if not text:
        return 0
    return sum(1 for p in FORBIDDEN_PATTERNS if p.search(text))


def estimate_cost_usd(model: str, tokens_in: int | None, tokens_out: int | None) -> float:
    p = PRICING.get(model)
    if not p:
        return 0.0
    ti = tokens_in or 0
    to_ = tokens_out or 0
    return (ti / 1_000_000) * p["input"] + (to_ / 1_000_000) * p["output"]


def percentile(values: list[float], pct: float) -> float:
    if not values:
        return 0.0
    s = sorted(values)
    k = (len(s) - 1) * pct
    f = math.floor(k)
    c = math.ceil(k)
    if f == c:
        return s[int(k)]
    return s[f] + (s[c] - s[f]) * (k - f)


def summarize(label: str, model: str, calls: list[dict[str, Any]]) -> dict[str, Any]:
    if not calls:
        return {"label": label, "model": model, "count": 0}
    lengths = [len(c["text"]) for c in calls]
    tokens_in = [c["tokens_input"] for c in calls if c["tokens_input"] is not None]
    tokens_out = [c["tokens_output"] for c in calls if c["tokens_output"] is not None]
    latencies = [c["latency_ms"] for c in calls if c["latency_ms"] is not None]
    statute = [count_regex(STATUTE_REF, c["text"]) for c in calls]
    cases = [count_regex(CASE_REF, c["text"]) for c in calls]
    guardrail = [guardrail_hits(c["text"]) for c in calls]
    total_cost = sum(estimate_cost_usd(model, c["tokens_input"], c["tokens_output"]) for c in calls)
    return {
        "label": label,
        "model": model,
        "count": len(calls),
        "avg_length_chars": round(statistics.fmean(lengths), 1) if lengths else 0,
        "avg_tokens_input": round(statistics.fmean(tokens_in), 1) if tokens_in else None,
        "avg_tokens_output": round(statistics.fmean(tokens_out), 1) if tokens_out else None,
        "p50_latency_ms": round(percentile(latencies, 0.5), 1) if latencies else None,
        "p95_latency_ms": round(percentile(latencies, 0.95), 1) if latencies else None,
        "avg_statute_refs": round(statistics.fmean(statute), 2) if statute else 0,
        "avg_case_refs": round(statistics.fmean(cases), 2) if cases else 0,
        "guardrail_hit_rate": round(sum(1 for g in guardrail if g > 0) / len(guardrail), 3) if guardrail else 0,
        "total_cost_usd_estimated": round(total_cost, 6),
    }


def render_markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Phase P5.5 — Chat Shadow Comparison (Cohere × HyperCLOVA X)",
        "",
        f"- 평가 일시: {report['generated_at']}",
        f"- 평가셋: `{report['eval_set']}` (총 {report['total_items']}개, 평가 대상 {report['evaluated_items']}개)",
        f"- system prompt: 동일 (단락 1개, 비조언적 안내 톤)",
        "",
        "## Summary",
        "",
        "| Metric | Cohere | HyperCLOVA X |",
        "|---|---:|---:|",
    ]
    c = report.get("cohere", {})
    h = report.get("hyperclova", {})
    def fmt(d: dict[str, Any], k: str) -> str:
        v = d.get(k)
        if v is None:
            return "—"
        return f"{v}"
    keys = [
        ("count", "응답 수"),
        ("model", "모델"),
        ("avg_length_chars", "평균 응답 길이 (chars)"),
        ("avg_tokens_input", "평균 input 토큰"),
        ("avg_tokens_output", "평균 output 토큰"),
        ("p50_latency_ms", "p50 latency (ms)"),
        ("p95_latency_ms", "p95 latency (ms)"),
        ("avg_statute_refs", "평균 법령 인용 수"),
        ("avg_case_refs", "평균 판례 인용 수"),
        ("guardrail_hit_rate", "guardrail 위반율"),
        ("total_cost_usd_estimated", "총 추정 비용 (USD)"),
    ]
    for k, label in keys:
        lines.append(f"| {label} | {fmt(c, k)} | {fmt(h, k)} |")
    lines += [
        "",
        "## 판단 가이드",
        "",
        "- **guardrail_hit_rate** : 두 provider 모두 낮을수록 좋음. 한쪽만 높으면 위험.",
        "- **avg_length_chars** : SHIELD chat은 짧은 다음 질문 기대 — 너무 길면 응답 톤 회귀 의심.",
        "- **avg_statute_refs / avg_case_refs** : 한국 법조 모델 기대치 (HyperCLOVA가 더 많을 가능성).",
        "- **p95_latency_ms** : 8s 임계 (PR Blocking Rule). 초과 시 production shadow에서도 제외.",
        "- **total_cost_usd_estimated** : 본 plan은 비용 자동 회로 미구현 — 운영 한도 수동 점검.",
        "",
        "## 다음 단계 (Commit 5 — 최종 결정 입력)",
        "",
        "1. 본 보고서의 `*.responses.jsonl` 을 별도 LLM-as-judge(Claude/GPT-4)에 입력하여 톤·정확성 비교.",
        "2. HyperCLOVA judge PASS rate vs Cohere PASS rate (P5.5 Commit 3 운영 데이터).",
        "3. 위 기준 + 본 보고서를 종합해 `p5_5-final-decision.md` 작성.",
    ]
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description="Cohere × HyperCLOVA X chat shadow 비교")
    parser.add_argument("--eval", required=True, help="평가셋 JSONL 경로 (v1.6)")
    parser.add_argument("--output", required=True, help="Markdown 보고서 출력 경로")
    parser.add_argument("--limit", type=int, default=50, help="앞에서 N개만 평가 (default=50)")
    parser.add_argument("--skip-cohere", action="store_true")
    parser.add_argument("--skip-hyperclova", action="store_true")
    parser.add_argument("--cohere-model", default=DEFAULT_MODELS["cohere"])
    parser.add_argument("--hyperclova-model", default=DEFAULT_MODELS["hyperclova"])
    parser.add_argument("--sleep-ms", type=int, default=200, help="호출 간 sleep (rate-limit 방어)")
    args = parser.parse_args()

    cohere_key = os.environ.get("COHERE_API_KEY")
    hcx_key = os.environ.get("HYPERCLOVA_API_KEY")

    if not args.skip_cohere and not cohere_key:
        print("ERROR: COHERE_API_KEY env required (or use --skip-cohere)", file=sys.stderr)
        return 2
    if not args.skip_hyperclova and not hcx_key:
        print("ERROR: HYPERCLOVA_API_KEY env required (or use --skip-hyperclova)", file=sys.stderr)
        return 2

    eval_path = Path(args.eval)
    if not eval_path.exists():
        print(f"ERROR: eval set not found: {eval_path}", file=sys.stderr)
        return 2

    items: list[dict[str, Any]] = []
    with eval_path.open(encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if not line:
                continue
            items.append(json.loads(line))
    total = len(items)
    items = items[: args.limit]

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    responses_path = output_path.with_suffix(output_path.suffix + ".responses.jsonl")

    cohere_calls: list[dict[str, Any]] = []
    hcx_calls: list[dict[str, Any]] = []

    with responses_path.open("w", encoding="utf-8") as resp_fh:
        for idx, item in enumerate(items):
            query = item.get("query") or ""
            record: dict[str, Any] = {"id": item.get("id"), "query": query}
            if not args.skip_cohere:
                try:
                    c = cohere_chat(cohere_key, args.cohere_model, query)
                    cohere_calls.append(c)
                    record["cohere"] = c
                except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as e:
                    record["cohere_error"] = str(e)
            if not args.skip_hyperclova:
                try:
                    h = hyperclova_chat(hcx_key, args.hyperclova_model, query)
                    hcx_calls.append(h)
                    record["hyperclova"] = h
                except (urllib.error.URLError, urllib.error.HTTPError, TimeoutError) as e:
                    record["hyperclova_error"] = str(e)
            resp_fh.write(json.dumps(record, ensure_ascii=False) + "\n")
            if (idx + 1) % 10 == 0:
                print(f"... evaluated {idx+1}/{len(items)}", file=sys.stderr)
            time.sleep(max(args.sleep_ms, 0) / 1000.0)

    report = {
        "generated_at": time.strftime("%Y-%m-%dT%H:%M:%S"),
        "eval_set": str(eval_path),
        "total_items": total,
        "evaluated_items": len(items),
        "cohere": summarize("cohere", args.cohere_model, cohere_calls),
        "hyperclova": summarize("hyperclova", args.hyperclova_model, hcx_calls),
        "responses_dump": str(responses_path),
    }

    output_path.write_text(render_markdown(report), encoding="utf-8")
    json_path = output_path.with_suffix(output_path.suffix + ".json")
    json_path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote report: {output_path}")
    print(f"Wrote JSON:   {json_path}")
    print(f"Wrote responses: {responses_path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
