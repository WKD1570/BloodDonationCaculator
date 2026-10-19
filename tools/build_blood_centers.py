#!/usr/bin/env python3
"""
Builds app/src/main/assets/blood_centers.json - every 헌혈의 집 the 헌혈의 집 tab lists - from
대한적십자사's open data CSV (공공데이터포털 "대한적십자사_헌혈의집 정보").

The CSV ships in CP949 with the columns 혈액원, 헌혈의 집, 구분, 주소지, 전화번호. Its file name
(kept as "source" in the JSON) carries the date the app shows the list as of.

Usage: python3 tools/build_blood_centers.py [path/to/csv]
Defaults to the newest tools/data/*헌혈의집*.csv. Re-run after dropping in a newer CSV.
"""
import csv
import glob
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/blood_centers.json")

COLUMNS = {
    "혈액원": "bloodBank",
    "헌혈의 집": "name",
    "구분": "type",
    "주소지": "address",
    "전화번호": "phone",
}


def read_text(path):
    raw = open(path, "rb").read()
    for encoding in ("utf-8-sig", "cp949"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            pass
    sys.exit(f"{path}: neither UTF-8 nor CP949")


def default_csv():
    candidates = sorted(glob.glob(os.path.join(ROOT, "tools/data/*헌혈의집*.csv")))
    if not candidates:
        sys.exit("No CSV given and none found in tools/data/")
    return candidates[-1]


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else default_csv()
    rows = list(csv.DictReader(io.StringIO(read_text(path))))
    if not rows:
        sys.exit(f"{path}: no rows")
    header = {h.strip() for h in rows[0].keys()}
    missing = [c for c in COLUMNS if c not in header]
    if missing:
        sys.exit(f"{path}: missing columns {missing}")

    centers, seen = [], set()
    for line, row in enumerate(rows, start=2):
        row = {k.strip(): (v or "").strip() for k, v in row.items() if k}
        center = {key: " ".join(row[col].split()) for col, key in COLUMNS.items()}
        if not center["name"] or not center["address"]:
            print(f"line {line}: skipped, no name or address", file=sys.stderr)
            continue
        ident = (center["bloodBank"], center["name"])
        if ident in seen:
            sys.exit(f"line {line}: duplicate {ident}")
        seen.add(ident)
        centers.append(center)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"source": os.path.basename(path), "centers": centers}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"Wrote {len(centers)} centers to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
