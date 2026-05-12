package org.example.shield.ai.application;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 온톨로지 L1 한글 이름 → 체크리스트 YAML slug 매핑 (Issue #40).
 *
 * <p>지시서({@code SHIELD_AI_bunryucegye_jageobjisiseo.md}) 고정 매핑이며,
 * {@code src/main/resources/ai/checklists/<slug>.yaml} 파일명과 1:1 대응됨.</p>
 *
 * <p>프론트엔드의 카테고리 선택 라벨이 정식 L1 이름과 다른 축약형
 * ("부동산", "이혼", "임대차" 등)일 수 있어 alias 매핑을 추가했다.
 * 정식 이름 매핑이 실패하면 alias 표를 한 번 더 조회한다.</p>
 *
 * <p>이 상수는 프로덕션 코드와 스키마 검증 테스트가 동일 소스를 참조하도록
 * 공유 상수로 추출되었다.</p>
 */
public final class ChecklistSlugMap {

    /** L1 한글 이름 → slug (삽입 순서 보존). */
    public static final Map<String, String> L1_TO_SLUG;

    /** 축약형/별칭 → 정식 L1 한글 이름. */
    private static final Map<String, String> ALIAS_TO_L1;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("부동산 거래", "real-estate");
        m.put("이혼·위자료·재산분할", "divorce");
        m.put("상속·유류분·유언", "inheritance");
        m.put("근로계약·해고·임금", "labor");
        m.put("손해배상·불법행위", "damages-tort");
        m.put("채무·보증·개인파산·회생", "debt");
        m.put("임대차보호", "lease-protection");
        m.put("기업·상사거래", "commercial");
        L1_TO_SLUG = Collections.unmodifiableMap(m);

        Map<String, String> a = new LinkedHashMap<>();
        // 부동산 거래
        a.put("부동산", "부동산 거래");
        a.put("부동산거래", "부동산 거래");
        // 이혼·위자료·재산분할
        a.put("이혼", "이혼·위자료·재산분할");
        a.put("위자료", "이혼·위자료·재산분할");
        a.put("재산분할", "이혼·위자료·재산분할");
        a.put("가사", "이혼·위자료·재산분할");
        // 상속·유류분·유언
        a.put("상속", "상속·유류분·유언");
        a.put("유언", "상속·유류분·유언");
        a.put("유류분", "상속·유류분·유언");
        // 근로계약·해고·임금
        a.put("근로", "근로계약·해고·임금");
        a.put("노동", "근로계약·해고·임금");
        a.put("해고", "근로계약·해고·임금");
        a.put("임금", "근로계약·해고·임금");
        a.put("근로계약", "근로계약·해고·임금");
        // 손해배상·불법행위
        a.put("손해배상", "손해배상·불법행위");
        a.put("불법행위", "손해배상·불법행위");
        a.put("교통사고", "손해배상·불법행위");
        a.put("의료사고", "손해배상·불법행위");
        // 채무·보증·개인파산·회생
        a.put("채무", "채무·보증·개인파산·회생");
        a.put("보증", "채무·보증·개인파산·회생");
        a.put("파산", "채무·보증·개인파산·회생");
        a.put("회생", "채무·보증·개인파산·회생");
        a.put("개인파산", "채무·보증·개인파산·회생");
        a.put("개인회생", "채무·보증·개인파산·회생");
        // 임대차보호
        a.put("임대차", "임대차보호");
        a.put("전세", "임대차보호");
        a.put("월세", "임대차보호");
        // 기업·상사거래
        a.put("기업", "기업·상사거래");
        a.put("상사", "기업·상사거래");
        a.put("상사거래", "기업·상사거래");
        ALIAS_TO_L1 = Collections.unmodifiableMap(a);
    }

    private ChecklistSlugMap() {
    }

    /**
     * L1 한글 이름 → slug.
     *
     * <p>입력은 trim 후, 다음 순서로 매칭한다.</p>
     * <ol>
     *   <li>정식 L1 이름 직접 매칭 ({@link #L1_TO_SLUG})</li>
     *   <li>alias 표를 통한 우회 매칭 ({@link #ALIAS_TO_L1})</li>
     * </ol>
     *
     * @param l1Name 온톨로지 L1 한글 이름 또는 축약형 (예: "부동산 거래", "부동산")
     * @return 대응 slug 또는 null (매핑 없음)
     */
    public static String slugFor(String l1Name) {
        if (l1Name == null) return null;
        String key = l1Name.trim();
        if (key.isEmpty()) return null;

        String direct = L1_TO_SLUG.get(key);
        if (direct != null) return direct;

        String canonical = ALIAS_TO_L1.get(key);
        if (canonical == null) return null;
        return L1_TO_SLUG.get(canonical);
    }

    /**
     * L1 한글 이름 또는 축약형을 정식 L1 이름으로 정규화한다.
     *
     * <p>{@code slugFor(...)} 와 동일한 매칭 규칙을 사용한다. 매핑이 없으면 null.
     * 카테고리 매핑/추천 등 slug 이외의 정식 이름이 필요한 경로에서 사용한다.</p>
     */
    public static String canonicalL1(String l1Name) {
        if (l1Name == null) return null;
        String key = l1Name.trim();
        if (key.isEmpty()) return null;
        if (L1_TO_SLUG.containsKey(key)) return key;
        return ALIAS_TO_L1.get(key);
    }
}
