#!/usr/bin/env python3
"""
Builds app/src/main/assets/drug_products.json - the product -> ingredient dictionary behind
상품명 search in 약물 복용 이력 확인 - from two sources:

1. A drug list CSV (tools/data/drug_products_*.csv, newest wins) with the columns
     약품한글명, 성분한글명, 성분코드 (and optionally 약품영문명)
   성분코드 is the 9-character 주성분코드, whose 7th character gives the route (A 내복, B 주사, C 외용).
   The CSV may be CP949 or UTF-8.
2. Products containing the official list's ingredients, fetched from 식약처 「의약품 제품 허가정보」
   by tools/fetch_restricted_products.py (tools/data/mfds_restricted_products_*.json, newest
   wins; optional). These have no 주성분코드, so a route is carried only where the fetch script
   knows it.

The official 헌혈 제한 약물 list (prohibited_drugs.json) names ingredients, and people know their
medicine by its product name. This dictionary bridges the two: search finds the product, the app
compares its ingredient against the official list. It carries no restrictions itself.

The dates in the source file names become the "sourceNote" the app shows under the results.

Usage: python3 tools/build_drug_products.py [path/to/csv]
"""
import csv
import glob
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/assets/drug_products.json")
REQUIRED = ["약품한글명", "성분한글명", "성분코드"]


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
    return candidates[-1] if candidates else None


def file_date(path):
    """'..._20260720.csv' -> '2026.07.20', or None."""
    match = re.search(r"(20\d{2})(\d{2})(\d{2})", os.path.basename(path))
    return ".".join(match.groups()) if match else None


def clean(text):
    return " ".join((text or "").split())


# Either bracket style, one level of nested parentheses inside, and a closing bracket the source
# sometimes leaves off at the end of the name.
EXPORT_NAMES = [
    re.compile(r"\s*\[수출명\s*:[^\]]*(?:\]|$)"),
    re.compile(r"\s*\(수출명\s*:(?:[^()]|\([^()]*\))*(?:\)|$)"),
]


def without_export_names(name):
    """Drops '[수출명:뉴로녹스주…, 시악스주…]' and '(수출명:클로진정100mg)': names the product is
    exported under, which no one here buys it by, and which run some names past 200 characters."""
    for pattern in EXPORT_NAMES:
        name = pattern.sub("", name)
    return clean(name)


def product_name(korean_name):
    """'한미아스피린장용정100밀리그램_(0.1g/1정)' -> '한미아스피린장용정100밀리그램': what follows
    '_(' (or '-(') is only the per-unit strength. A doubled closing bracket with nothing to close
    ('루리드정(록시트로마이신))') is a typo in the source; nested ones ('(아리피프라졸(미분화))') are not."""
    name = korean_name
    for separator in ("_(", "_ (", "-("):
        name = name.split(separator)[0]
    name = clean(name).rstrip("-")
    while name.count(")") > name.count("(") and "))" in name:
        name = name.replace("))", ")", 1)
    return without_export_names(name)


def csv_products(path):
    rows = list(csv.DictReader(io.StringIO(read_text(path))))
    if not rows:
        sys.exit(f"{path}: no rows")
    header = {h.strip() for h in rows[0].keys() if h}
    missing = [c for c in REQUIRED if c not in header]
    if missing:
        sys.exit(f"{path}: missing columns {missing}")

    products = []
    for line, row in enumerate(rows, start=2):
        row = {k.strip(): clean(v) for k, v in row.items() if k}
        name = product_name(row["약품한글명"])
        ingredient = row["성분한글명"]
        if not name or not ingredient:
            print(f"{os.path.basename(path)} line {line}: skipped, no product name or ingredient", file=sys.stderr)
            continue
        products.append({
            "name": name,
            "englishName": row.get("약품영문명", ""),
            "ingredient": ingredient,
            "ingredientCode": row["성분코드"],
        })
    return products


def mfds_products(path):
    data = json.load(open(path, encoding="utf-8"))
    products = []
    for item in data.get("products", []):
        name, ingredient = without_export_names(clean(item.get("name"))), clean(item.get("ingredient"))
        if not name or not ingredient:
            continue
        product = {"name": name, "englishName": clean(item.get("englishName")), "ingredient": ingredient}
        if item.get("route"):
            product["route"] = item["route"]
        products.append(product)
    return products


def main():
    csv_path = sys.argv[1] if len(sys.argv) > 1 else newest("drug_products_*.csv")
    mfds_path = newest("mfds_restricted_products_*.json")
    if not csv_path and not mfds_path:
        sys.exit("No source found in tools/data/")

    products, seen, notes, counts = [], set(), [], []
    sources = []
    if csv_path:
        sources.append((csv_path, csv_products(csv_path), "의약품 목록"))
    if mfds_path:
        sources.append((mfds_path, mfds_products(mfds_path), "식약처 의약품 제품 허가정보"))
    for path, found, label in sources:
        added = 0
        for product in found:
            key = (product["name"], product["ingredient"])
            if key in seen:
                continue
            seen.add(key)
            products.append(product)
            added += 1
        date = file_date(path)
        notes.append(f"{label}({date})" if date else label)
        counts.append(f"{os.path.basename(path)}: {added}")

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump({
            "source": os.path.basename(csv_path or mfds_path),
            "sourceNote": ", ".join(notes),
            "products": products,
        }, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"Wrote {len(products)} products to {os.path.relpath(OUT, ROOT)} ({'; '.join(counts)})")


if __name__ == "__main__":
    main()
