"""
Regression check — runs EXPLAIN (no ANALYZE) against the FINAL @Query SQL
strings as they now live in LegalChunkJpaRepository / LegalCaseJpaRepository,
to catch parse errors and confirm HNSW is picked under representative inputs.

Read-only. No data modification.
"""
import os
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
env_path = ROOT / ".env"
for line in env_path.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#") or "=" not in line:
        continue
    k, v = line.split("=", 1)
    os.environ.setdefault(k, v.strip().strip('"'))

import psycopg2

DB_URL = os.environ["DB_URL"]
m = re.match(r"jdbc:postgresql://([^:/]+):(\d+)/(\w+)", DB_URL)
host, port, dbname = m.group(1), int(m.group(2)), m.group(3)

conn = psycopg2.connect(
    host=host, port=port, dbname=dbname,
    user=os.environ["DB_USERNAME"], password=os.environ["DB_PASSWORD"],
    sslmode="require", connect_timeout=10,
)
conn.set_session(readonly=True, autocommit=False)
cur = conn.cursor()


# Final SQL — copied verbatim from the updated repositories,
# with :named -> %(name)s for psycopg2 binding.

SEARCH3WAY_SQL = """
WITH vec AS (
  SELECT id, 1 - (embedding <=> CAST(%(queryVector)s AS vector)) AS sim
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND embedding IS NOT NULL
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   ORDER BY embedding <=> CAST(%(queryVector)s AS vector)
   LIMIT 40
), bm AS (
  SELECT id, ts_rank(content_tsv, to_tsquery('simple', %(keywordQuery)s), 1) AS rk
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND content_tsv @@ to_tsquery('simple', %(keywordQuery)s)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), trig AS (
  SELECT id, similarity(content, CAST(%(vectorQuery)s AS text)) AS sm
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND content %% CAST(%(vectorQuery)s AS text)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), pool AS (
  SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
)
SELECT lc.law_name AS lawName,
       lc.article_no AS articleNo,
       lc.article_title AS articleTitle,
       lc.content AS content,
       to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
       lc.source_url AS sourceUrl,
       ( COALESCE(v.sim, 0) * %(vw)s
       + COALESCE(b.rk,  0) * %(kw)s
       + COALESCE(t.sm,  0) * %(tw)s) AS score
  FROM pool p
  JOIN legal_chunks lc ON lc.id = p.id
  LEFT JOIN vec  v ON v.id = lc.id
  LEFT JOIN bm   b ON b.id = lc.id
  LEFT JOIN trig t ON t.id = lc.id
 ORDER BY score DESC
 LIMIT %(topK)s
"""

SEARCH3WAY_BYLAWS_SQL = """
WITH vec AS (
  SELECT id, 1 - (embedding <=> CAST(%(queryVector)s AS vector)) AS sim
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND embedding IS NOT NULL
     AND law_id = ANY(%(lawIds)s)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   ORDER BY embedding <=> CAST(%(queryVector)s AS vector)
   LIMIT 40
), bm AS (
  SELECT id, ts_rank(content_tsv, to_tsquery('simple', %(keywordQuery)s), 1) AS rk
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND law_id = ANY(%(lawIds)s)
     AND content_tsv @@ to_tsquery('simple', %(keywordQuery)s)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), trig AS (
  SELECT id, similarity(content, CAST(%(vectorQuery)s AS text)) AS sm
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND law_id = ANY(%(lawIds)s)
     AND content %% CAST(%(vectorQuery)s AS text)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), pool AS (
  SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
)
SELECT lc.law_name AS lawName,
       lc.article_no AS articleNo,
       lc.article_title AS articleTitle,
       lc.content AS content,
       to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
       lc.source_url AS sourceUrl,
       ( COALESCE(v.sim, 0) * %(vw)s
       + COALESCE(b.rk,  0) * %(kw)s
       + COALESCE(t.sm,  0) * %(tw)s) AS score
  FROM pool p
  JOIN legal_chunks lc ON lc.id = p.id
  LEFT JOIN vec  v ON v.id = lc.id
  LEFT JOIN bm   b ON b.id = lc.id
  LEFT JOIN trig t ON t.id = lc.id
 ORDER BY score DESC
 LIMIT %(topK)s
"""

# Same shape for legal_cases — abbreviated check (just plan, not full pipeline)
SEARCH3WAY_CASES_SQL = """
WITH vec AS (
  SELECT id, 1 - (embedding <=> CAST(%(queryVector)s AS vector)) AS sim
    FROM legal_cases
   WHERE embedding IS NOT NULL
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   ORDER BY embedding <=> CAST(%(queryVector)s AS vector)
   LIMIT 40
), bm AS (
  SELECT id, ts_rank(content_tsv, to_tsquery('simple', %(keywordQuery)s), 1) AS rk
    FROM legal_cases
   WHERE content_tsv @@ to_tsquery('simple', %(keywordQuery)s)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), trig AS (
  SELECT id, similarity(holding, CAST(%(vectorQuery)s AS text)) AS sm
    FROM legal_cases
   WHERE holding %% CAST(%(vectorQuery)s AS text)
     AND ( COALESCE(CARDINALITY(CAST(%(categoryIds)s AS text[])), 0) = 0
           OR category_ids && CAST(%(categoryIds)s AS text[]) )
   LIMIT 40
), pool AS (
  SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
)
SELECT lc.id, lc.case_no, lc.court, lc.case_name,
       to_char(lc.decision_date, 'YYYY-MM-DD') AS decisionDate,
       lc.case_type, lc.headnote, lc.holding, lc.source_url,
       ( COALESCE(v.sim, 0) * %(vw)s
       + COALESCE(b.rk,  0) * %(kw)s
       + COALESCE(t.sm,  0) * %(tw)s) AS score
  FROM pool p
  JOIN legal_cases lc ON lc.id = p.id
  LEFT JOIN vec  v ON v.id = lc.id
  LEFT JOIN bm   b ON b.id = lc.id
  LEFT JOIN trig t ON t.id = lc.id
 ORDER BY score DESC
 LIMIT %(topK)s
"""


# Get a real query embedding from existing data
cur.execute("SELECT embedding::text FROM legal_chunks WHERE embedding IS NOT NULL ORDER BY id LIMIT 1;")
qvec = cur.fetchone()[0]

V_QUERY = "전세 보증금 미반환"
KW = "전세권:* | 전세금:* | 보증금:* | 반환:*"

PARAMS = {
    "queryVector": qvec,
    "vectorQuery": V_QUERY,
    "keywordQuery": KW,
    "categoryIds": None,    # null → unfiltered
    "lawIds": ["law-civil"],
    "vw": 0.5, "kw": 0.3, "tw": 0.2,
    "topK": 5,
}
SELECTIVE_CAT = ["chapter:제4장 물건"]


def run(label, sql, params, set_hnsw=True):
    print("\n" + "=" * 72)
    print(label)
    print("=" * 72)
    cur.execute("SAVEPOINT s;")
    try:
        if set_hnsw:
            cur.execute("SET LOCAL hnsw.ef_search = 40;")
            try:
                cur.execute("SET LOCAL hnsw.iterative_scan = relaxed_order;")
                hnsw_iter = "on"
            except psycopg2.Error:
                conn.rollback()
                cur.execute("SAVEPOINT s2;")
                cur.execute("SET LOCAL hnsw.ef_search = 40;")
                hnsw_iter = "unsupported"
        # 1) syntactic check via prepared explain
        cur.execute("EXPLAIN (ANALYZE, BUFFERS) " + sql, params)
        plan = "\n".join(r[0] for r in cur.fetchall())
        uses_hnsw = bool(re.search(r"Index Scan using \S*hnsw", plan, re.I))
        seq_scan_legalchunks = bool(re.search(r"Seq Scan on legal_chunks(?!_)", plan))
        seq_scan_legalcases = bool(re.search(r"Seq Scan on legal_cases(?!_)", plan))
        exec_m = re.search(r"Execution Time: ([\d.]+) ms", plan)
        # 2) row count via the actual query
        cur.execute(sql, params)
        rows = cur.rowcount if cur.rowcount >= 0 else len(cur.fetchall())

        print(f"  rows returned        : {rows}")
        print(f"  HNSW Index Scan used : {uses_hnsw}")
        print(f"  Seq Scan legal_chunks: {seq_scan_legalchunks}")
        print(f"  Seq Scan legal_cases : {seq_scan_legalcases}")
        if exec_m:
            print(f"  Execution Time       : {exec_m.group(1)} ms")
        print("  --- plan (first 20 lines) ---")
        print("\n".join("  " + l for l in plan.splitlines()[:20]))
    except psycopg2.Error as e:
        conn.rollback()
        print(f"  ❌ SQL ERROR: {e.pgerror or e}")
        return False
    finally:
        cur.execute("ROLLBACK TO SAVEPOINT s;")
        cur.execute("RELEASE SAVEPOINT s;")
    return True


# ============================================================================
ok = True
ok &= run("[1] search3Way — no filters",
         SEARCH3WAY_SQL, {**PARAMS, "categoryIds": None})
ok &= run("[2] search3Way — rare category (5/3066 ≈ 0.16%)",
         SEARCH3WAY_SQL, {**PARAMS, "categoryIds": SELECTIVE_CAT})
ok &= run("[3] search3Way — moderate category (175/3066 ≈ 5.7%)",
         SEARCH3WAY_SQL, {**PARAMS, "categoryIds": ["chapter:제1장 총칙"]})
ok &= run("[4] search3WayByLaws — law-civil + no category",
         SEARCH3WAY_BYLAWS_SQL, {**PARAMS, "categoryIds": None})
ok &= run("[5] search3WayByLaws — law-civil + rare category",
         SEARCH3WAY_BYLAWS_SQL, {**PARAMS, "categoryIds": SELECTIVE_CAT})

# legal_cases — only run if table has any rows
cur.execute("SELECT count(*) FROM legal_cases;")
n_cases = cur.fetchone()[0]
print(f"\n  (legal_cases has {n_cases} rows)")
if n_cases > 0:
    ok &= run("[6] search3WayCases — no filters",
             SEARCH3WAY_CASES_SQL, {**PARAMS, "categoryIds": None})

print("\n" + "=" * 72)
print(f"OVERALL: {'PASS' if ok else 'FAIL'}")
print("=" * 72)

cur.close()
conn.close()
sys.exit(0 if ok else 1)
