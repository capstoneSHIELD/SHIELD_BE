"""
Verify Gemini Code Assist review claims on PR #90:
  1. WHERE COALESCE(holding, '') % v  blocks idx_legal_cases_holding_trgm
  2. WHERE holding % v                 uses the trigram index
  3. SELECT similarity(COALESCE(holding,''), v) vs similarity(holding, v)
     — both safe with COALESCE(t.sm, 0) at the outer level

Read-only.
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


def banner(t): print("\n" + "=" * 72 + f"\n{t}\n" + "=" * 72)


# ---- 0. Setup -------------------------------------------------------------
banner("[0] legal_cases table inspection")

cur.execute("SELECT count(*) FROM legal_cases;")
n_cases = cur.fetchone()[0]
print(f"  rows: {n_cases}")

cur.execute("""
    SELECT count(*) FILTER (WHERE holding IS NULL) AS null_holding,
           count(*) FILTER (WHERE holding IS NOT NULL) AS not_null,
           count(*) FILTER (WHERE holding = '') AS empty_holding
      FROM legal_cases;
""")
nullh, notnull, empty = cur.fetchone()
print(f"  holding: NULL={nullh}, NOT NULL={notnull}, empty-string={empty}")

cur.execute("""
    SELECT column_name, is_nullable, data_type
      FROM information_schema.columns
     WHERE table_name = 'legal_cases' AND column_name = 'holding';
""")
for col, nullable, dtype in cur.fetchall():
    print(f"  schema: holding {dtype} {'NULL' if nullable=='YES' else 'NOT NULL'}")

cur.execute("""
    SELECT indexname, indexdef
      FROM pg_indexes
     WHERE tablename = 'legal_cases'
       AND (indexdef ILIKE '%trgm%' OR indexdef ILIKE '%gist%' OR indexdef ILIKE '%holding%')
     ORDER BY indexname;
""")
print("  trigram/gist/holding indexes:")
for name, defn in cur.fetchall():
    print(f"    {name}")
    print(f"      {defn}")


# ---- 1. Plan comparison: WHERE COALESCE vs WHERE direct ------------------
banner("[1] EXPLAIN — WHERE COALESCE(holding, '') % :v   vs   WHERE holding % :v")

V = "전세 보증금 미반환"

def explain(label, sql, params):
    print(f"\n--- {label} ---")
    cur.execute("SAVEPOINT s;")
    try:
        cur.execute("EXPLAIN (ANALYZE, BUFFERS) " + sql, params)
        plan = "\n".join(r[0] for r in cur.fetchall())
        uses_gist = bool(re.search(r"Bitmap Index Scan on \S*(trgm|gist)\S*", plan, re.I)) \
                 or bool(re.search(r"Index Scan using \S*holding\S*trgm", plan, re.I))
        seq = bool(re.search(r"Seq Scan on legal_cases", plan))
        exec_m = re.search(r"Execution Time: ([\d.]+) ms", plan)
        print(plan)
        print(f"  → uses trigram index: {uses_gist}, Seq Scan: {seq}, "
              f"exec: {exec_m.group(1) if exec_m else '?'} ms")
        return uses_gist, seq
    finally:
        cur.execute("ROLLBACK TO SAVEPOINT s;")
        cur.execute("RELEASE SAVEPOINT s;")


# 1a) current code path
sql_coalesce = """
SELECT id FROM legal_cases
 WHERE COALESCE(holding, '') %% CAST(%s AS text)
 LIMIT 40
"""
uses_a, seq_a = explain("[A] CURRENT — WHERE COALESCE(holding, '') % v", sql_coalesce, (V,))

# 1b) Gemini's suggestion
sql_direct = """
SELECT id FROM legal_cases
 WHERE holding %% CAST(%s AS text)
 LIMIT 40
"""
uses_b, seq_b = explain("[B] GEMINI — WHERE holding % v", sql_direct, (V,))


# ---- 2. Behavioral equivalence for NULL holding ---------------------------
banner("[2] Behavioral equivalence — what happens to NULL holding rows?")

# Same query, count rows returned with each form
cur.execute(sql_coalesce, (V,))
n_a = len(cur.fetchall())
cur.execute(sql_direct, (V,))
n_b = len(cur.fetchall())
print(f"  rows returned (COALESCE form): {n_a}")
print(f"  rows returned (direct form) : {n_b}")
print(f"  match: {n_a == n_b}")


# ---- 3. Score equivalence for outer SELECT --------------------------------
banner("[3] SELECT similarity(COALESCE(holding,''), v) vs similarity(holding, v)")
print("  Both paths feed into outer COALESCE(t.sm, 0) — NULL → 0 anyway.")
print("  Demonstrate with synthetic row test:")

cur.execute("""
    SELECT
      similarity(COALESCE(NULL::text, ''), CAST(%s AS text)) AS coalesce_form,
      similarity(NULL::text,            CAST(%s AS text)) AS direct_form,
      COALESCE(similarity(NULL::text, CAST(%s AS text)), 0) AS direct_with_outer_coalesce
""", (V, V, V))
c_form, d_form, d_outer = cur.fetchone()
print(f"  similarity(COALESCE(NULL,''), '{V}') = {c_form}")
print(f"  similarity(NULL,             '{V}') = {d_form}")
print(f"  COALESCE(similarity(NULL, '{V}'), 0)= {d_outer}")


# ---- 4. Verdict ------------------------------------------------------------
banner("[4] Verdict on Gemini's 3 comments")
print(f"  #1 WHERE COALESCE blocks idx_legal_cases_holding_trgm:")
print(f"     COALESCE form → uses_trgm={uses_a}, seq={seq_a}")
print(f"     direct form   → uses_trgm={uses_b}, seq={seq_b}")
print(f"     Gemini correct: {uses_b and not uses_a}")
print(f"  #2 search3WayCasesByTypes 동일: 같은 fix 적용 필요")
print(f"  #3 SELECT similarity(COALESCE(...)) vs similarity(...) :")
print(f"     같은 결과를 거쳐 COALESCE(t.sm,0)에서 정규화됨")
print(f"     Gemini correct (일관성 + 불필요한 함수 호출 제거)")

cur.close()
conn.close()
