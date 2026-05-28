# FE 수정 가이드 — 의뢰서 생성 조기 활성화 대응

## 배경

BE 변경(2026-05-28): `POST /api/consultations/{id}/messages` 의 `allCompleted` 가 **10턴 도달 전에도 `true`** 로 내려올 수 있다. LLM이 의뢰서 작성에 충분한 사실관계가 모였다고 판단하고 체크리스트 커버리지가 임계(기본 0.5) 이상이면 effective `allCompleted=true` 가 반환된다.

**핵심**: 사용자에게는 "옵션"으로 제공되어야 한다. 의뢰서 생성 버튼을 보여주되, 사용자가 더 답하고 싶으면 계속 채팅할 수 있어야 한다.

- 응답 `content` (= `nextQuestion`) 는 LLM이 만든 자연스러운 후속 질문 그대로 유지된다 (10턴 도달 시의 강제 안내 멘트 치환과는 다름).
- `progress.currentTurn` 은 5/10, 6/10 같은 중간 값일 수 있으면서 동시에 `allCompleted=true`.
- BE는 어떤 변경도 강요하지 않는다. FE의 UI/UX 책임은 "조기 종료 옵션"을 자연스럽게 노출하는 것.

## 문제점 — 현재 FE 동작

[ChatPage.tsx:130-138](C:/SHIELD_FE/src/routes/client/ChatPage.tsx#L130-L138):

```tsx
<ChatInput
  onSend={sendMessage}
  disabled={isSending || allCompleted}                          // ← 문제 1
  placeholder={allCompleted ? '상담이 완료되었습니다' : '...'}    // ← 문제 2
  subtext={allCompleted ? undefined : '...'}                    // ← 문제 3
/>
```

이전 BE 동작에서는 `allCompleted=true` ⇒ 10턴 도달 = 강제 종료였으므로 입력창을 닫는 것이 자연스러웠다. 새 BE 동작에서는 5턴째에도 `allCompleted=true` 가 올 수 있어, 위 코드를 그대로 두면 **사용자가 더 답하고 싶어도 입력창이 막힌다.**

## 권장 변경

### 1. "강제 종료" 와 "옵션 제공" 을 구분하는 파생 상태

`allCompleted` 단독으로는 두 케이스를 구별할 수 없다. `progress` 와 함께 봐서 파생.

```ts
// useChat 의 return 안에서 같이 노출하거나 ChatPage 내 로컬로 계산
const isTurnLimitReached =
  progress != null && progress.currentTurn >= progress.maxTurns;

// 의뢰서 생성 버튼 노출 조건은 그대로 allCompleted
const showBriefCta = allCompleted;

// 입력창을 막을지는 "강제 종료(턴 상한 도달)" 일 때만
const lockInput = isTurnLimitReached;
```

`progress.currentTurn === progress.maxTurns` 가 강제 종료의 신호다. BE 가 10턴 도달 시 `nextQuestion` 을 안내 멘트로 치환하는 동작은 그대로이므로, 사용자도 그 시점엔 입력창이 닫혀도 자연스럽다.

### 2. [ChatPage.tsx](C:/SHIELD_FE/src/routes/client/ChatPage.tsx) 수정

```diff
   const {
     messages,
     isLoading,
     isSending,
     allCompleted,
     progress,
     scrollRef,
     sendMessage,
   } = useChat(id);

+  // 강제 종료 (10턴 도달) 와 LLM 자율 종료(조기) 구분
+  const isTurnLimitReached =
+    progress != null && progress.currentTurn >= progress.maxTurns;
+  const lockInput = isTurnLimitReached; // 조기 종료 시엔 입력 유지
+
   ...

   {/* "의뢰서 생성" CTA — shown when allCompleted (조기/강제 모두 노출) */}
   {allCompleted && (
     <div className="px-4 pt-3 pb-1">
       <Button ...>의뢰서 생성</Button>
     </div>
   )}

   <ChatInput
     onSend={sendMessage}
-    disabled={isSending || allCompleted}
-    placeholder={allCompleted ? '상담이 완료되었습니다' : '메시지를 입력하세요...'}
+    disabled={isSending || lockInput}
+    placeholder={
+      lockInput
+        ? '상담이 완료되었습니다'
+        : allCompleted
+          ? '추가로 답하거나, 위 버튼을 눌러 의뢰서를 생성하세요'
+          : '메시지를 입력하세요...'
+    }
     subtext={
-      allCompleted
+      lockInput
         ? undefined
-        : '상담 내용을 입력하면 AI가 법률 분야를 자동으로 분류합니다.'
+        : allCompleted
+          ? undefined
+          : '상담 내용을 입력하면 AI가 법률 분야를 자동으로 분류합니다.'
     }
   />
```

### 3. [ConsultationProgressBar.tsx](C:/SHIELD_FE/src/components/consultation/ConsultationProgressBar.tsx) 라벨 분기

현재 `isCompleted = completed || percent >= 100` 으로 두 케이스가 같은 라벨("정보 수집 완료 — 의뢰서를 생성해주세요")을 쓴다. 조기 완료 시엔 진행률 숫자가 의미 있으므로, 라벨/시각을 약간 분리하는 게 더 정직하다.

```diff
-  const isCompleted = completed || percent >= 100;
+  const isTurnLimitReached = percent >= 100;
+  const isEarlyReady = completed && !isTurnLimitReached;
+  const isCompleted = completed || isTurnLimitReached;
```

라벨:

```diff
   {isCompleted ? (
-    <span className="font-semibold text-emerald-600">
-      정보 수집 완료 — 의뢰서를 생성해주세요
-    </span>
+    isEarlyReady ? (
+      <span className="font-semibold text-emerald-600">
+        의뢰서 생성 준비 완료 — 더 답하거나 바로 생성할 수 있어요
+        <span className="ml-2 font-mono text-gray-400">
+          ({currentTurn} / {maxTurns})
+        </span>
+      </span>
+    ) : (
+      <span className="font-semibold text-emerald-600">
+        정보 수집 완료 — 의뢰서를 생성해주세요
+      </span>
+    )
   ) : (
     <>
       <span className="font-medium text-gray-700">상담 진행률</span>
       <span className="font-mono text-gray-500">
         {currentTurn} / {maxTurns} 단계 ({percent}%)
       </span>
     </>
   )}
```

선택적: 트랙 색을 조기 완료(emerald + pulse 약화)와 강제 완료(emerald + 강한 pulse)로 미세 차별화. 필수는 아님.

### 4. (선택) "의뢰서 생성" 버튼 카피 미세조정

조기 완료 케이스에선 사용자가 망설일 수 있으니 보조 문구를 한 줄 더 줄 수도 있음.

```tsx
{allCompleted && (
  <div className="px-4 pt-3 pb-1">
    <Button onClick={handleRequestAnalyze}>의뢰서 생성</Button>
    {!isTurnLimitReached && (
      <p className="mt-1 text-center text-[11px] text-text-soft">
        더 자세히 알려주실 내용이 있다면 계속 답변하셔도 됩니다.
      </p>
    )}
  </div>
)}
```

## 변경 안 해도 되는 것

- [useChat.ts](C:/SHIELD_FE/src/hooks/useChat.ts) — 이미 `res.allCompleted` 면 `setAllCompleted(true)` 하고 있어서 그대로 동작. 변경 불필요.
- [chatStore.ts](C:/SHIELD_FE/src/stores/chatStore.ts) — 상태 모델 변경 없음.
- [consultationApi.ts](C:/SHIELD_FE/src/lib/consultationApi.ts) / API 타입 — 응답 스키마는 그대로. `SendMessageResponse.allCompleted: boolean` 그대로 유지.
- 페이지 재진입 복원 로직 (useChat L75-79) — 그대로 동작. 단, `ConsultationResponse.allCompleted=true` 로 새로고침 들어왔는데 `progress` 가 없는 케이스에 대비해, 위 #2 의 `isTurnLimitReached` 는 `progress != null` 가드를 반드시 거치도록 작성된 점에 유의.

## 테스트 시나리오

1. **조기 완료 (LLM 자율)** — 임대차 보증금 시나리오로 4~6턴 진행. 핵심 사실관계 다 말한 시점에 응답이 `{ allCompleted: true, content: "...자연스러운 후속질문...", progress: { currentTurn: 5, maxTurns: 10 } }` 로 와야 함. UI는:
   - 진행률 바 라벨: "의뢰서 생성 준비 완료 — 더 답하거나 바로 생성할 수 있어요 (5/10)"
   - "의뢰서 생성" 버튼 노출
   - 입력창 **활성화 유지**, placeholder가 "추가로 답하거나..." 안내로 바뀜
2. **사용자가 더 답함** — 입력창에 1턴 더 입력. 응답이 `allCompleted: true/false` 어느쪽이 와도 입력창은 다음 턴까지 열려 있어야 함.
3. **강제 종료 (10턴)** — 9턴까지 진행 후 10번째 입력. 응답이 `{ allCompleted: true, content: "필요한 정보를 충분히 수집했습니다...", progress: { currentTurn: 10, maxTurns: 10 } }` 로 옴. UI는:
   - 진행률 바 라벨: "정보 수집 완료 — 의뢰서를 생성해주세요"
   - 입력창 비활성화 + placeholder "상담이 완료되었습니다"
4. **11번째 입력 시도** — BE가 400 반환 (`ConsultationTurnLimitExceededException`). 기존 에러 핸들링 그대로.
5. **페이지 새로고침 복원** — 조기 `allCompleted=true` 상태에서 새로고침. `ConsultationResponse.allCompleted=true` 로 와서 chatStore 복원 ✓. `progress` 는 useChat 의 `deriveProgressFromMessages` 로 복원되므로 currentTurn 이 maxTurns 보다 작으면 입력창은 열려 있어야 함.
6. **MSW handlers 갱신** — `C:\SHIELD_FE\src\test\mocks\handlers.ts` 의 mock 응답에 조기 완료 케이스 fixture 1개 추가하면 컴포넌트 테스트에서 회귀 방어 가능.

## QA 체크포인트 한 줄 요약

> **5턴째인데 "의뢰서 생성" 버튼이 떴을 때, 입력창도 동시에 살아 있어야 한다.** 둘 중 하나만 가능한 UI면 회귀.
