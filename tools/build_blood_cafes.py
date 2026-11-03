#!/usr/bin/env python3
"""
Builds app/src/main/assets/blood_cafes.json - the 헌혈카페 the 헌혈 장소 tab lists next to the
헌혈의 집 - from tools/data/hanmaeum_blood_cafes_*.csv (newest wins).

헌혈카페 are run by 한마음혈액원, not 대한적십자사. The CSV is transcribed from each cafe's page under
「헌혈카페 찾기」 on bloodnet.or.kr (header: 지역, 지점명, 주소, 연락처, 헌혈종류, 평일, 주말·공휴일,
점심시간, Amicus (혈소판), MCS (혈소판), Trima (혈소판), 혈소판 합계, 주차, 주차장 위치, 정보 업데이트,
상세 페이지, 비고). The site lists only each cafe's 혈소판 machines, so a cafe gets
"machines": {"platelet": {...}} and no "plasma" at all, which the app shows as 혈장 정보 없음 rather
than 없음.

The output has the same shape as blood_centers.json, plus "hours" and "parking", so the app reads
both with one parser. The date in the CSV's file name is shown under the list.

Usage: python3 tools/build_blood_cafes.py [cafes.csv]
"""
import csv
import glob
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/blood_cafes.json")
REQUIRED = ["지점명", "주소", "연락처", "평일", "주말·공휴일", "점심시간", "주차", "주차장 위치"]
MODELS = ["Amicus", "MCS", "Trima"]  # "<model> (혈소판)" columns, in this order


def newest(pattern):
    candidates = sorted(glob.glob(os.path.join(ROOT, "tools/data", pattern)))
    return candidates[-1] if candidates else None


def clean(text):
    return " ".join((text or "").split())


def address(text):
    """'옥정로 218. 신운정튼튼프라자 2층' -> '옥정로 218, 신운정튼튼프라자 2층': the site separates the
    building number with a full stop, which map search doesn't take for the end of the road address."""
    return re.sub(r"(\d)\.\s+", r"\1, ", clean(text))


def hours(row):
    lunch = row["점심시간"]
    lines = [f"평일 {row['평일']}", f"주말·공휴일 {row['주말·공휴일']}",
             "점심시간 없음" if lunch == "없음" else f"점심시간 {lunch}"]
    return "\n".join(lines)


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else newest("hanmaeum_blood_cafes_*.csv")
    if not path:
        sys.exit("No CSV given and none found in tools/data/")
    with open(path, encoding="utf-8-sig", newline="") as f:
        rows = [{k.strip(): clean(v) for k, v in row.items() if k} for row in csv.DictReader(f)]
    if not rows:
        sys.exit(f"{path}: no rows")
    missing = [c for c in REQUIRED + [f"{m} (혈소판)" for m in MODELS] if c not in rows[0]]
    if missing:
        sys.exit(f"{path}: missing columns {missing}")

    cafes, seen = [], set()
    for line, row in enumerate(rows, start=2):
        name = row["지점명"]
        if not name or not row["주소"]:
            sys.exit(f"line {line}: no name or address")
        if name in seen:
            sys.exit(f"line {line}: duplicate {name}")
        seen.add(name)
        platelet = {}
        for model in MODELS:
            count = int(row[f"{model} (혈소판)"] or 0)
            if count > 0:
                platelet[model] = count
        cafes.append({
            "bloodBank": "한마음",
            "name": name,
            "type": "헌혈카페",
            "address": address(row["주소"]),
            "phone": row["연락처"],
            "hours": hours(row),
            "parking": f"{row['주차']} · {row['주차장 위치']}" if row["주차장 위치"] else row["주차"],
            "machines": {"platelet": platelet},
        })

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({"source": os.path.basename(path), "centers": cafes}, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"Wrote {len(cafes)} 헌혈카페 to {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
