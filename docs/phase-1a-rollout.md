# Phase 1A Rollout — classify LLM 라이트 모델 교체

> 사전 자료: `docs/latency-baseline-2026-05-12.md` (Phase 0 baseline + Phase 1A 결정 근거).
> 본 문서는 **운영에 적용하는 절차** 만 다룬다. 모델 default 변경은 코드/yml 무변경,
> 환경변수 override 만으로 진행 가능.

## 변경 요약

| 항목 | 변경 |
|------|------|
| `application.yml` | **무변경** — `cohere.classify.model: command-a-03-2025` 그대로 |
| `CohereApiConfig.java:48` | **무변경** — 이미 `@Value("${cohere.classify.model:command-a-03-2025}")` placeholder |
| 운영 default | 환경변수 unset 시 그대로 `command-a-03-2025` 유지 |
| **카나리 적용** | `COHERE_CLASSIFY_MODEL=command-r-08-2024` 환경변수 set 만으로 즉시 라이트 모델 사용 |

Spring Boot 의 relaxed binding 으로 `COHERE_CLASSIFY_MODEL` 환경변수가 자동으로 `cohere.classify.model` 키를 override 한다.

## 운영 EC2 적용 절차 (`shield-backend` systemd service)

### 1) systemd EnvironmentFile 추가

기존 `/etc/systemd/system/shield-backend.service` 에 한 줄 추가 (또는 별도 override 파일 생성):

```bash
sudo systemctl edit shield-backend
# 에디터 열림 — 아래 블록만 추가
```

```ini
[Service]
EnvironmentFile=-/home/ec2-user/shield/.env-canary
```

`-` 접두사로 파일이 없어도 실패하지 않게 만든다.

### 2) 카나리 환경변수 파일 작성

```bash
sudo tee /home/ec2-user/shield/.env-canary > /dev/null <<'EOF'
COHERE_CLASSIFY_MODEL=command-r-08-2024
EOF
sudo chown ec2-user:ec2-user /home/ec2-user/shield/.env-canary
sudo chmod 600 /home/ec2-user/shield/.env-canary
```

### 3) 서비스 재시작

```bash
sudo systemctl daemon-reload
sudo systemctl restart shield-backend
sudo journalctl -u shield-backend -n 30 --no-pager | grep -i "cohere\|classify"
```

### 4) 환경변수 적용 검증

```bash
# 프로세스 환경 확인
sudo cat /proc/$(pgrep -f shield-backend)/environ 2>/dev/null | tr '\0' '\n' | grep COHERE_CLASSIFY_MODEL
# 예상: COHERE_CLASSIFY_MODEL=command-r-08-2024
```

## 측정 (1–7일 간 운영 트래픽 관찰)

`/actuator/prometheus` 에 다음 시리즈가 시간 경과에 따라 누적된다:

```bash
curl -s http://localhost:8080/actuator/prometheus | grep -E "^shield_(rag_(classify|pipeline_total|retrieve|cohere_embed)|chat_(cohere_call|send_message))_seconds"
```

핵심 관찰점:

- **`shield_rag_classify_seconds_count{outcome="success"}`** 비율이 100% 에 근접 — 라이트 모델이 timeout 안 걸림 (baseline 학교 환경에서는 failure 100% 였음)
- **`shield_rag_classify_seconds_sum / count`** 평균 latency — 운영 default(`command-a`) 대비 줄어들었는지
- **`shield_chat_send_message_seconds`** 전체 wall-clock — sendMessage p50 가 줄어드는지

Prometheus 서버 있으면:

```promql
# classify p50 (5분 윈도우)
histogram_quantile(0.5, rate(shield_rag_classify_seconds_bucket{outcome="success"}[5m]))

# send_message p50 비교 — 카나리 적용 전후
rate(shield_chat_send_message_seconds_sum{outcome="success"}[5m])
  /
rate(shield_chat_send_message_seconds_count{outcome="success"}[5m])
```

## 회귀 검증 — `scripts/eval_rag.py`

classify 모델이 바뀌면 retrieval 의 입력(`vectorQuery`, `bm25Keywords`, `matchedNodeIds`) 이 바뀌므로 retrieval 정확도(Recall@5, nDCG) 가 간접적으로 영향을 받는다. 카나리 적용된 환경에서 실행:

```bash
COHERE_API_KEY=... DB_PASSWORD=... \
  python3 scripts/eval_rag.py \
    --eval eval/eval-set.v1.jsonl \
    --output docs/phase-1a-canary-{날짜}.md
```

기존 baseline (`docs/phase-c1-baseline.json` 의 운영 default `command-a` 측정치) 과 비교하여 Recall@5/nDCG@5 가 의미 있는 회귀(>5%) 가 아닌지 확인.

## 결정 분기

| 관측 (1–7일 운영) | 다음 행동 |
|------|---------|
| classify p50 명확히 줄고 retrieval 회귀 < 5% | **운영 default 변경 PR** — `application.yml` 의 `classify.model` 을 `command-r-08-2024` 로 영구 변경, `.env-canary` 제거 |
| classify 가 운영 환경에서 원래 빠르고 차이 미미 | **롤백** — `.env-canary` 제거 후 재시작. `command-a` 유지. Phase 3 (speculative 병렬) 검토 |
| retrieval 정확도 회귀 > 5% | **롤백** — 사용자 응답 품질 우선. Phase 1E (prompt 단축) 또는 Phase 3 우선 |

## 롤백 (즉시)

```bash
sudo rm /home/ec2-user/shield/.env-canary
sudo systemctl restart shield-backend
```

서비스 재시작 후 환경변수 unset → `command-a-03-2025` (운영 default) 로 즉시 복귀. 운영 코드/yml 무변경이라 git revert 불필요.

## 보류된 후속 — Phase 0 의 다른 관찰

`docs/latency-baseline-2026-05-12.md` 의 결과 보면 **`chat.cohere.call` 가 16–25s** 로 측정됐다.
classify 교체로 인한 단축은 30s timeout 회피분(학교 환경 한정) 외에는 1–2s 수준일 가능성. 진짜 큰 효과는:

- **Phase 3 (speculative 병렬)** — `chat` 을 RAG 와 병렬 시작. 학교 환경 기준 16s 절감.
- **chat 모델도 라이트화** — `cohere.model.chat` 도 placeholder (`CohereApiConfig.java:30`). `COHERE_MODEL_CHAT=command-r-08-2024` 환경변수로 동일 카나리 가능.

Phase 1A 카나리 결과에 따라 둘 중 하나로 이어진다.
