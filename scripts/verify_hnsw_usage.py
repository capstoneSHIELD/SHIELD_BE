"""
HNSW index usage verification — runs EXPLAIN ANALYZE against the production
search SQL and a control "pure HNSW" pattern, then reports whether the
production query is actually hitting the HNSW index.

Read-only. No data modification.
"""
import os
import re
import sys
from pathlib import Path

# Load .env
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

# 0) Sanity
cur.execute("SELECT count(*) FROM legal_chunks WHERE embedding IS NOT NULL;")
n = cur.fetchone()[0]
print(f"[0] legal_chunks rows with embedding: {n}")

cur.execute("""
    SELECT indexname, indexdef
      FROM pg_indexes
     WHERE tablename = 'legal_chunks'
       AND indexdef ILIKE '%hnsw%'
""")
hnsw_indexes = cur.fetchall()
print(f"[0] HNSW indexes on legal_chunks: {len(hnsw_indexes)}")
for name, defn in hnsw_indexes:
    print(f"    - {name}: {defn}")
print()

# 1) Get one real embedding to use as a query vector
cur.execute("""
    SELECT embedding::text
      FROM legal_chunks
     WHERE embedding IS NOT NULL
     ORDER BY id
     LIMIT 1;
""")
query_vec_literal = cur.fetchone()[0]


def explain(label, sql, params):
    print(f"=== {label} ===")
    cur.execute("EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT) " + sql, params)
    rows = cur.fetchall()
    text = "\n".join(r[0] for r in rows)
    print(text)
    print()
    return text


# 2) Production query — current search3Way (no law filter)
prod_sql = """
SELECT lc.law_name AS lawName,
       lc.article_no AS articleNo,
       lc.article_title AS articleTitle,
       lc.content AS content,
       to_char(lc.effective_date, 'YYYY-MM-DD') AS effectiveDate,
       lc.source_url AS sourceUrl,
       ( COALESCE(CASE WHEN lc.embedding IS NULL THEN 0
                       ELSE 1 - (lc.embedding <=> CAST(%s AS vector))
                   END, 0) * %s
        + ts_rank(lc.content_tsv, to_tsquery('simple', %s), 1) * %s
        + similarity(lc.content, %s) * %s) AS score
  FROM legal_chunks lc
 WHERE lc.abolition_date IS NULL
   AND ( lc.content_tsv @@ plainto_tsquery('simple', %s)
      OR lc.content_tsv @@ to_tsquery('simple', %s)
      OR lc.content %% CAST(%s AS text)
      OR lc.embedding IS NOT NULL )
 ORDER BY score DESC
 LIMIT %s
"""
v_query = "전세 보증금 미반환"
kw_query = "전세권:* | 전세금:* | 보증금:* | 반환:*"
prod_params = (query_vec_literal, 0.5, kw_query, 0.3, v_query, 0.2,
               v_query, kw_query, v_query, 5)

# Set the same session knob the app uses
cur.execute("SET LOCAL hnsw.ef_search = 40")

plan_prod = explain("[1] PRODUCTION search3Way (current code)", prod_sql, prod_params)

# 3) Control — pure HNSW pattern (what pgvector's index can actually serve)
control_sql = """
SELECT id, law_name, article_no,
       embedding <=> CAST(%s AS vector) AS distance
  FROM legal_chunks
 WHERE abolition_date IS NULL
   AND embedding IS NOT NULL
 ORDER BY embedding <=> CAST(%s AS vector)
 LIMIT 5
"""
plan_control = explain("[2] CONTROL - pure ORDER BY embedding distance LIMIT K",
                       control_sql, (query_vec_literal, query_vec_literal))

# 4) Verdict
def uses_hnsw(plan_text):
    return bool(re.search(r"Index Scan using \S*hnsw", plan_text, re.IGNORECASE)) \
        or "hnsw" in plan_text.lower() and "Index Scan" in plan_text


def has_seq_scan_on_legal_chunks(plan_text):
    return bool(re.search(r"Seq Scan on legal_chunks", plan_text))


print("=" * 60)
print("VERDICT")
print("=" * 60)
print(f"  Production query uses HNSW : {uses_hnsw(plan_prod)}")
print(f"  Production query Seq Scan  : {has_seq_scan_on_legal_chunks(plan_prod)}")
print(f"  Control query  uses HNSW   : {uses_hnsw(plan_control)}")
print(f"  Control query  Seq Scan    : {has_seq_scan_on_legal_chunks(plan_control)}")

m_prod = re.search(r"Execution Time: ([\d.]+) ms", plan_prod)
m_ctrl = re.search(r"Execution Time: ([\d.]+) ms", plan_control)
if m_prod:
    print(f"  Production exec time       : {m_prod.group(1)} ms")
if m_ctrl:
    print(f"  Control    exec time       : {m_ctrl.group(1)} ms")

cur.close()
conn.close()
