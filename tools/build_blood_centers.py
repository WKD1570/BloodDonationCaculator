#!/usr/bin/env python3
"""
Builds app/src/main/assets/blood_centers.json - every 헌혈의 집 the 헌혈의 집 tab lists - from
대한적십자사's open data CSV (공공데이터포털 "대한적십자사_헌혈의집 정보"), with each center's
성분헌혈 machines merged in from a second CSV.

The centers CSV ships in CP949 with the columns 혈액원, 헌혈의 집, 구분, 주소지, 전화번호. Its file name
(kept as "source" in the JSON) carries the date the app shows the list as of.

The machines CSV has 권역 (혈액원) and 지점명, then one "<model> (혈장)" or "<model> (혈소판)" column
per machine model holding how many the center has. Its total columns and closing 합계 row are
ignored. A center gets "machines": {"plasma": {model: count}, "platelet": {...}} listing only the
models it has; a center the CSV doesn't cover gets no "machines" at all (기기 정보 없음 in the app).

Usage: python3 tools/build_blood_centers.py [centers.csv [machines.csv]]
Defaults to the newest tools/data/*헌혈의집*.csv and tools/data/*mechanical*.csv. Re-run after
dropping in a newer CSV and read the warnings: a center the two files name differently needs an
entry in NAME_ALIASES.
"""
import csv
import glob
import io
import json
import os
import re
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

MACHINE_COLUMN = re.compile(r"^(.+?)\s*\((혈장|혈소판)\)$")
MACHINE_KINDS = {"혈장": "plasma", "혈소판": "platelet"}

# (권역, 지점명) in the machines CSV -> 헌혈의 집 in the centers CSV, where the two differ.
NAME_ALIASES = {
    ("서울남부", "고속터미널센터"): "고속터미널역센터",
    ("대구경북", "신매센터"): "신매광장센터",
}


def read_text(path):
    raw = open(path, "rb").read()
    for encoding in ("utf-8-sig", "cp949"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            pass
    sys.exit(f"{path}: neither UTF-8 nor CP949")


def newest(pattern):
    candidates = sorted(glob.glob(os.path.join(ROOT, "tools/data", pattern)))
    if not candidates:
        sys.exit(f"No CSV given and no tools/data/{pattern}")
    return candidates[-1]


def read_machines(path):
    """(혈액원, 헌혈의 집) -> {"plasma": {model: count}, "platelet": {...}}, nonzero counts only."""
    rows = list(csv.reader(io.StringIO(read_text(path))))
    if not rows:
        sys.exit(f"{path}: no rows")
    header = [h.strip() for h in rows[0]]
    if "권역 (혈액원)" not in header or "지점명" not in header:
        sys.exit(f"{path}: missing 권역 (혈액원) or 지점명 column")
    bank_col, name_col = header.index("권역 (혈액원)"), header.index("지점명")
    models = []
    for col, title in enumerate(header):
        match = MACHINE_COLUMN.match(title)
        if match:
            models.append((col, MACHINE_KINDS[match.group(2)], match.group(1)))
    if not models:
        sys.exit(f"{path}: no '<model> (혈장)' or '<model> (혈소판)' columns")

    machines = {}
    for line, row in enumerate(rows[1:], start=2):
        row = [cell.strip() for cell in row] + [""] * (len(header) - len(row))
        bank, name = row[bank_col], " ".join(row[name_col].split())
        if not name or bank == "합계":
            continue
        name = NAME_ALIASES.get((bank, name), name)
        center = {"plasma": {}, "platelet": {}}
        for col, kind, model in models:
            try:
                count = int(row[col] or 0)
            except ValueError:
                sys.exit(f"line {line}: {header[col]} is not a count: {row[col]!r}")
            if count > 0:
                center[kind][model] = count
        if (bank, name) in machines:
            sys.exit(f"line {line}: duplicate {(bank, name)}")
        machines[(bank, name)] = center
    return machines


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else newest("*헌혈의집*.csv")
    machines_path = sys.argv[2] if len(sys.argv) > 2 else newest("*mechanical*.csv")
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

    machines = read_machines(machines_path)
    uncovered = []
    for center in centers:
        found = machines.pop((center["bloodBank"], center["name"]), None)
        if found is None:
            uncovered.append(center["name"])
        else:
            center["machines"] = found
    for bank, name in machines:
        print(f"{os.path.basename(machines_path)}: no center {bank} {name}, skipped", file=sys.stderr)
    if uncovered:
        print(f"no machines listed for {', '.join(uncovered)}", file=sys.stderr)

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(
            {"source": os.path.basename(path), "machineSource": os.path.basename(machines_path), "centers": centers},
            f,
            ensure_ascii=False,
            indent=1,
        )
        f.write("\n")
    print(f"Wrote {len(centers)} centers, {len(centers) - len(uncovered)} with machines, to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
