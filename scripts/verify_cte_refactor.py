"""
CTE-split refactor verification.

Compares four query shapes against legal_chunks:

  [A] Original search3Way                       (baseline — confirmed Seq Scan)
  [B] CTE-split, no category filter             (expect HNSW Index Scan)
  [C] CTE-split + selective category filter, no iterative_scan
  [D] CTE-split + selective category filter,    hnsw.iterative_scan=relaxed_order

For each: plan summary, HNSW used?, Seq Scan?, buffers, execution time,
and result-set size — so we also see whether the category filter
causes recall loss (Gemini/Claude's concern).

Read-only.
"""
import os
import re
import sys
from pathlib import Path

env_path = Path(__file__).resolve().parent.parent / ".env"
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


def show(title):
    print("\n" + "=" * 72)
    print(title)
    print("=" * 72)


# --- 0. Environment ----------------------------------------------------------
show("[0] Environment")

cur.execute("SHOW server_version;")
print(f"  PostgreSQL: {cur.fetchone()[0]}")

cur.execute("SELECT extname, extversion FROM pg_extension WHERE extname IN ('vector','pg_trgm');")
for ext, ver in cur.fetchall():
    print(f"  ext {ext} = {ver}")

cur.execute("SELECT count(*) FROM legal_chunks WHERE embedding IS NOT NULL;")
total_rows = cur.fetchone()[0]
print(f"  legal_chunks with embedding: {total_rows}")

# Top categories by frequency — pick a selective but non-empty one
cur.execute("""
    SELECT unnest(category_ids) AS cat, count(*) AS n
      FROM legal_chunks
     WHERE abolition_date IS NULL
  GROUP BY cat
  ORDER BY n
     LIMIT 15;
""")
print("  smallest 15 category buckets:")
small_cats = cur.fetchall()
for cat, n in small_cats:
    print(f"    {cat:<40s} {n}")

# Pick a selective category: smallest with at least 20 rows
selective_cat = None
for cat, n in small_cats:
    if n >= 20:
        selective_cat = cat
        selective_n = n
        break
if selective_cat is None:
    # fallback: any cat with >= 5
    for cat, n in small_cats:
        if n >= 5:
            selective_cat = cat
            selective_n = n
            break
print(f"\n  → selective category for tests: {selective_cat} ({selective_n} rows, {selective_n/total_rows:.1%} of corpus)")

# --- 1. Get a real query embedding -------------------------------------------
cur.execute("""
    SELECT embedding::text
      FROM legal_chunks
     WHERE embedding IS NOT NULL
     ORDER BY id
     LIMIT 1;
""")
qvec = cur.fetchone()[0]

V_QUERY = "전세 보증금 미반환"
KW = "전세권:* | 전세금:* | 보증금:* | 반환:*"


def explain(label, sql, params, knobs=()):
    """Run EXPLAIN ANALYZE under a fresh subtransaction, return summary."""
    cur.execute("SAVEPOINT s;")
    try:
        for knob in knobs:
            cur.execute(knob)
        cur.execute("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql, params)
        plan = "\n".join(r[0] for r in cur.fetchall())
    finally:
        cur.execute("ROLLBACK TO SAVEPOINT s;")
        cur.execute("RELEASE SAVEPOINT s;")

    uses_hnsw = bool(re.search(r"Index Scan using \S*hnsw", plan, re.IGNORECASE))
    seq_scan  = bool(re.search(r"Seq Scan on legal_chunks", plan))
    bitmap    = bool(re.search(r"Bitmap (Heap|Index) Scan", plan))
    buffers   = re.findall(r"shared hit=(\d+)(?: read=(\d+))?", plan)
    total_buf = sum(int(h) + (int(r) if r else 0) for h, r in buffers)
    exec_m    = re.search(r"Execution Time: ([\d.]+) ms", plan)
    plan_m    = re.search(r"Planning Time: ([\d.]+) ms", plan)
    return {
        "label": label,
        "uses_hnsw": uses_hnsw,
        "seq_scan_on_legal_chunks": seq_scan,
        "uses_bitmap": bitmap,
        "total_buffers": total_buf,
        "planning_ms": float(plan_m.group(1)) if plan_m else None,
        "execution_ms": float(exec_m.group(1)) if exec_m else None,
        "plan_first_lines": "\n".join(plan.splitlines()[:8]),
    }


def count_rows(sql, params, knobs=()):
    """Run the actual query (not EXPLAIN) to count rows returned."""
    cur.execute("SAVEPOINT s;")
    try:
        for knob in knobs:
            cur.execute(knob)
        cur.execute(sql, params)
        return cur.rowcount if cur.rowcount >= 0 else len(cur.fetchall())
    finally:
        cur.execute("ROLLBACK TO SAVEPOINT s;")
        cur.execute("RELEASE SAVEPOINT s;")


# --- 2. SQL shapes -----------------------------------------------------------

# [A] Original production query (no category filter)
A_SQL = """
SELECT lc.id
  FROM legal_chunks lc
 WHERE lc.abolition_date IS NULL
   AND ( lc.content_tsv @@ plainto_tsquery('simple', %s)
      OR lc.content_tsv @@ to_tsquery('simple', %s)
      OR lc.content %% CAST(%s AS text)
      OR lc.embedding IS NOT NULL )
 ORDER BY ( COALESCE(CASE WHEN lc.embedding IS NULL THEN 0
                          ELSE 1 - (lc.embedding <=> CAST(%s AS vector))
                      END, 0) * %s
          + ts_rank(lc.content_tsv, to_tsquery('simple', %s), 1) * %s
          + similarity(lc.content, %s) * %s) DESC
 LIMIT %s
"""
A_PARAMS = (V_QUERY, KW, V_QUERY, qvec, 0.5, KW, 0.3, V_QUERY, 0.2, 5)

# [B/C/D] CTE-split — HNSW-eligible vector path + BM25 + trigram pools, rescored
def cte_sql(with_category_filter: bool, pool_k: int = 40, top_k: int = 5,
            push_filter_into_ctes: bool = False):
    """
    Two modes for the category filter:
      - push_filter_into_ctes=False (B/C/D): filter applied only at final JOIN
      - push_filter_into_ctes=True  ([E]) : filter pushed into vec/bm/trig CTEs
        → enables hnsw.iterative_scan to compensate for filter selectivity
    """
    if with_category_filter and push_filter_into_ctes:
        pool_filter = "AND category_ids && CAST(%s AS text[])"
    else:
        pool_filter = ""

    final_filter = (
        "AND ( CARDINALITY(CAST(%s AS text[])) = 0 "
        "      OR lc.category_ids && CAST(%s AS text[]) )"
        if with_category_filter and not push_filter_into_ctes else ""
    )
    return f"""
WITH vec AS (
  SELECT id, 1 - (embedding <=> CAST(%s AS vector)) AS sim
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND embedding IS NOT NULL
     {pool_filter}
   ORDER BY embedding <=> CAST(%s AS vector)
   LIMIT {pool_k}
), bm AS (
  SELECT id, ts_rank(content_tsv, to_tsquery('simple', %s), 1) AS rk
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND content_tsv @@ to_tsquery('simple', %s)
     {pool_filter}
   LIMIT {pool_k}
), trig AS (
  SELECT id, similarity(content, CAST(%s AS text)) AS sm
    FROM legal_chunks
   WHERE abolition_date IS NULL
     AND content %% CAST(%s AS text)
     {pool_filter}
   LIMIT {pool_k}
), pool AS (
  SELECT id FROM vec UNION SELECT id FROM bm UNION SELECT id FROM trig
)
SELECT lc.id,
       (COALESCE(v.sim, 0) * %s
      + COALESCE(b.rk,  0) * %s
      + COALESCE(t.sm,  0) * %s) AS score
  FROM pool p
  JOIN legal_chunks lc ON lc.id = p.id
  LEFT JOIN vec  v ON v.id  = lc.id
  LEFT JOIN bm   b ON b.id  = lc.id
  LEFT JOIN trig t ON t.id  = lc.id
 WHERE lc.abolition_date IS NULL
       {final_filter}
 ORDER BY score DESC
 LIMIT {top_k}
"""


def cte_params(category=None, push_into_ctes=False):
    cat_arr = [category] if category is not None else None
    if push_into_ctes and cat_arr is not None:
        # Param order MUST match %s occurrence in the SQL:
        # vec  : sim_select_qvec, filter_cat, order_qvec
        # bm   : ts_rank_kw,      where_kw,   filter_cat
        # trig : sim_select_v,    where_v,    filter_cat
        # weights: wv, wk, wt
        return (
            qvec, cat_arr, qvec,
            KW, KW, cat_arr,
            V_QUERY, V_QUERY, cat_arr,
            0.5, 0.3, 0.2,
        )
    base = (qvec, qvec, KW, KW, V_QUERY, V_QUERY, 0.5, 0.3, 0.2)
    if cat_arr is not None:
        return base + (cat_arr, cat_arr)
    return base


# --- 3. Run measurements ------------------------------------------------------
results = []

results.append(explain("[A] ORIGINAL search3Way", A_SQL, A_PARAMS))

results.append(explain("[B] CTE-split, no category filter",
                       cte_sql(False), cte_params(None)))

results.append(explain("[C] CTE-split + category filter, no iterative_scan",
                       cte_sql(True), cte_params(selective_cat),
                       knobs=("SET LOCAL hnsw.ef_search = 40",)))

# [D] tries iterative_scan if pgvector supports it
knobs_D = ("SET LOCAL hnsw.ef_search = 40",)
try:
    cur.execute("SAVEPOINT probe;")
    cur.execute("SET LOCAL hnsw.iterative_scan = relaxed_order;")
    cur.execute("ROLLBACK TO SAVEPOINT probe;")
    cur.execute("RELEASE SAVEPOINT probe;")
    knobs_D = knobs_D + ("SET LOCAL hnsw.iterative_scan = relaxed_order;",)
    iterative_supported = True
except psycopg2.Error as e:
    conn.rollback()
    iterative_supported = False
    print(f"  ⚠️  hnsw.iterative_scan not supported: {e.pgerror or e}")

results.append(explain(f"[D] CTE-split + category filter + iterative_scan={'on' if iterative_supported else 'UNSUPPORTED'}",
                       cte_sql(True), cte_params(selective_cat), knobs=knobs_D))

# [E] — filter pushed INTO vec/bm/trig CTEs, with iterative_scan on
if iterative_supported:
    results.append(explain(
        "[E] CTE filter PUSHED into pools + iterative_scan=on",
        cte_sql(True, push_filter_into_ctes=True),
        cte_params(selective_cat, push_into_ctes=True),
        knobs=("SET LOCAL hnsw.ef_search = 40",
               "SET LOCAL hnsw.iterative_scan = relaxed_order;")
    ))

# --- 4. Recall sanity check — does category filter inside CTE pool hurt? -----
show("[4] Recall sanity — rows returned by each shape")

# A returns top-5 from full pool
# B returns top-5 from union pool (no cat)
# C/D return top-5 from union pool, then category-filtered at join
for label, sql, params in [
    ("A original",              A_SQL,           A_PARAMS),
    ("B CTE no-filter",         cte_sql(False),  cte_params(None)),
    ("C CTE +cat",              cte_sql(True),   cte_params(selective_cat)),
]:
    n = count_rows(sql, params)
    print(f"  {label:<25s} → {n} rows")

# [D] with iterative_scan — does it recover the rows [C] missed?
if iterative_supported:
    n_D = count_rows(cte_sql(True), cte_params(selective_cat),
                     knobs=("SET LOCAL hnsw.ef_search = 40",
                            "SET LOCAL hnsw.iterative_scan = relaxed_order;"))
    print(f"  D CTE +cat +iterative_scan         → {n_D} rows")

# [E] filter pushed into pool CTEs + iterative_scan
if iterative_supported:
    n_E = count_rows(cte_sql(True, push_filter_into_ctes=True),
                     cte_params(selective_cat, push_into_ctes=True),
                     knobs=("SET LOCAL hnsw.ef_search = 40",
                            "SET LOCAL hnsw.iterative_scan = relaxed_order;"))
    print(f"  E CTE filter PUSHED + iterative   → {n_E} rows  (★ key recall test)")

# Recall: pool sizes
cur.execute("""
    SELECT count(*) FROM legal_chunks
     WHERE abolition_date IS NULL
       AND category_ids && CAST(%s AS text[])
""", ([selective_cat],))
cat_pop = cur.fetchone()[0]
print(f"\n  selective category total population: {cat_pop}")

# Also test a moderately-selective category (10-30% of corpus)
cur.execute("""
    WITH cnts AS (
      SELECT unnest(category_ids) AS cat, count(*) AS n
        FROM legal_chunks
       WHERE abolition_date IS NULL
    GROUP BY cat
    )
    SELECT cat, n
      FROM cnts
     WHERE n BETWEEN %s AND %s
  ORDER BY n
     LIMIT 1
""", (int(total_rows * 0.05), int(total_rows * 0.20)))
mid = cur.fetchone()
if mid:
    mid_cat, mid_n = mid
    print(f"\n  moderately selective cat: {mid_cat} ({mid_n} rows, {mid_n/total_rows:.1%})")
    n_C_mid = count_rows(cte_sql(True), cte_params(mid_cat),
                          knobs=("SET LOCAL hnsw.ef_search = 40",))
    print(f"    C (no iter)  → {n_C_mid} rows")
    if iterative_supported:
        n_D_mid = count_rows(cte_sql(True), cte_params(mid_cat),
                              knobs=("SET LOCAL hnsw.ef_search = 40",
                                     "SET LOCAL hnsw.iterative_scan = relaxed_order;"))
        print(f"    D (iter on)  → {n_D_mid} rows")

# Pool_k=200 with selective cat — alternative to iterative_scan
n_bigpool = count_rows(cte_sql(True, pool_k=200, top_k=5),
                       cte_params(selective_cat))
print(f"\n  C with pool_k=200 + selective cat → {n_bigpool} rows  (bigger pool alternative)")

# --- 5. Summary table ---------------------------------------------------------
show("[5] Summary")
print(f"  {'shape':<55s} {'HNSW':>6s} {'SeqScan':>8s} {'bufs':>8s} {'exec(ms)':>10s}")
print(f"  {'-'*55} {'-'*6} {'-'*8} {'-'*8} {'-'*10}")
for r in results:
    print(f"  {r['label']:<55s} "
          f"{('YES' if r['uses_hnsw'] else '—'):>6s} "
          f"{('YES' if r['seq_scan_on_legal_chunks'] else '—'):>8s} "
          f"{r['total_buffers']:>8d} "
          f"{r['execution_ms']:>10.1f}")

print("\nFirst lines of each plan:")
for r in results:
    print(f"\n--- {r['label']} ---")
    print(r["plan_first_lines"])

cur.close()
conn.close()
