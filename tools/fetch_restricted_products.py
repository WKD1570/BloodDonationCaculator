#!/usr/bin/env python3
"""
Fetches every marketed product that contains an ingredient on the official 헌혈 제한 약물 list
(prohibited_drugs.json) from 식품의약품안전처's 「의약품 제품 허가정보」 API (공공데이터포털
15095677, 이용허락범위 제한 없음), so 상품명 search finds 프로페시아, 아보다트, 로아큐탄 and the rest.

Writes tools/data/mfds_restricted_products_<YYYYMMDD>.json. Then run
    python3 tools/build_drug_products.py
to merge it into the app's product dictionary. The JSON is committed, so the app builds without
the API; re-run this only to refresh it.

The service key is the 공공데이터포털 일반 인증키 of an account whose 활용신청 for that API is
approved. It is read from, in order:
  - the DATA_GO_KR_SERVICE_KEY environment variable
  - dataGoKrServiceKey=... in local.properties (git-ignored)
and never from the command line or any tracked file, so it can't end up in git.

Usage (from the project root, on a machine that can reach apis.data.go.kr):
    python3 tools/fetch_restricted_products.py --probe   # one raw page, to check the fields
    python3 tools/fetch_restricted_products.py
"""
import datetime
import http.client
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROHIBITED = os.path.join(ROOT, "app/src/main/assets/prohibited_drugs.json")
OUT_DIR = os.path.join(ROOT, "tools/data")
LOCAL_PROPERTIES = os.path.join(ROOT, "local.properties")

# getDrugPrdtPrmsnInq08 = 의약품 제품 허가 목록, filterable by 주성분 (item_ingr_name).
# MFDS_ENDPOINT overrides it for offline testing against a local server only.
ENDPOINT = os.environ.get(
    "MFDS_ENDPOINT",
    "https://apis.data.go.kr/1471000/DrugPrdtPrmsnInfoService08/getDrugPrdtPrmsnInq08",
)
PAGE_SIZE = 100
# The 의약품 제품 허가 목록 request parameter that filters by 주성분.
INGREDIENT_PARAM = "item_ingr_name"
# One ingredient has at most a few hundred products. Thousands means the filter wasn't applied
# and the API is listing everything - stop rather than spend the daily quota on it.
MAX_PER_QUERY = 3000

# Official list entry (its ingredientName) -> the ingredient names to search 식약처 data for.
# ITEM_INGR_NAME is English ("Aspirin Enteric Granules", "Glucose/Sodium Chloride") and
# item_ingr_name is a case-sensitive partial match: "Aspirin" finds 120 products, "aspirin" and
# "아스피린" none (checked 2026-11-03). Each term is also tried in lower case in case some entries
# are written that way. The app maps these English names back to the official Korean ones
# (INGREDIENT_SPELLINGS in DrugProductSearch.kt).
QUERIES = {
    "아스피린": ["Aspirin"],
    "항생제/스테로이드/보톡스/클로피도그렐": ["Clopidogrel", "Botulinum"],
    "티클로피딘": ["Ticlopidine"],
    "피나스테라이드": ["Finasteride"],
    "이소트레티노인": ["Isotretinoin"],
    "알리트레티노인": ["Alitretinoin"],
    "탈리도마이드": ["Thalidomide"],
    "두타스테라이드": ["Dutasteride"],
    "비스모데깁": ["Vismodegib"],
    "아시트레틴": ["Acitretin"],
    "에트레티네이트": ["Etretinate"],
}
# Entries that name kinds of drug rather than ingredients, so there's nothing to search for.
# (항생제 and 스테로이드 injections are handled by the app's injection rule instead.)
NOT_SEARCHABLE = {"태반주사제/혈액응고인자", "사람뇌하수체 유래 성장호르몬 등"}
# Queries whose every product is an injection. The app only applies "보톡스(주사)" to injections,
# and these products have no 주성분코드 to read the route from, so the route is written out.
INJECTION_ONLY = {"botulinum"}


class ApiError(Exception):
    pass


AUTH_HINT = (
    "\n\n→ 이 인증키가 「식품의약품안전처_의약품 제품 허가정보」 API에 대해 아직 쓸 수 없는 상태예요.\n"
    "  공공데이터포털 마이페이지 > 데이터활용 > Open API > 활용신청 현황에서 이 API가 '승인'인지 확인하세요.\n"
    "  신청 전이면 활용신청을, 방금 승인됐다면 반영될 때까지(보통 1시간 안팎) 기다렸다가 다시 실행하세요."
)


def service_key():
    key = os.environ.get("DATA_GO_KR_SERVICE_KEY", "").strip()
    if not key and os.path.exists(LOCAL_PROPERTIES):
        for line in open(LOCAL_PROPERTIES, encoding="utf-8"):
            name, _, value = line.partition("=")
            if name.strip() == "dataGoKrServiceKey":
                key = value.strip()
    if not key:
        sys.exit(
            "No service key. Set DATA_GO_KR_SERVICE_KEY, or add a line\n"
            "    dataGoKrServiceKey=<공공데이터포털 일반 인증키>\n"
            "to local.properties (it is git-ignored)."
        )
    return key


def squash(text):
    return re.sub(r"\s+", "", text or "").lower()


def field(item, *names):
    """Case-insensitive lookup, since the API's field casing has changed between versions."""
    lowered = {k.lower(): v for k, v in item.items()}
    for name in names:
        value = lowered.get(name.lower())
        if value not in (None, ""):
            return str(value).strip()
    return ""


def clean_ingredients(text):
    """'[M040702]피나스테리드|[M...]...' -> '피나스테리드/...': drops MFDS's own 성분 codes."""
    text = re.sub(r"\[[^\]]*\]", "", text or "")
    parts = [p.strip() for p in re.split(r"[|/,]", text) if p.strip()]
    return "/".join(parts)


def fetch_page(key, ingredient, page, rows=PAGE_SIZE):
    params = {"pageNo": page, "numOfRows": rows, INGREDIENT_PARAM: ingredient}
    return request_json(key, params, f"{ingredient} page {page}")


def request_json(key, params, label):
    # A key that is already URL-encoded (contains %) goes in as is; a decoded one gets encoded.
    encoded_key = key if "%" in key else urllib.parse.quote(key, safe="")
    query = urllib.parse.urlencode({"type": "json", **params})
    url = f"{ENDPOINT}?serviceKey={encoded_key}&{query}"
    request = urllib.request.Request(url, headers={
        "Accept": "application/json",
        # urllib's default "Python-urllib/3.x" is refused by some gateways; name the tool instead.
        "User-Agent": "BloodDonationCalculator-tools/1.0 (fetch_restricted_products.py)",
    })
    for attempt in range(3):
        try:
            with urllib.request.urlopen(request, timeout=30) as response:
                body = response.read().decode("utf-8")
            break
        except urllib.error.HTTPError as e:
            # The server answered; retrying won't change a refusal, so explain it instead.
            detail = e.read().decode("utf-8", "replace").strip()[:500]
            message = f"{label}: HTTP {e.code} {e.reason}" + (f"\n{detail}" if detail else "")
            if e.code in (401, 403):
                message += AUTH_HINT
            raise ApiError(message)
        except (urllib.error.URLError, TimeoutError, ConnectionError, http.client.HTTPException) as e:
            if attempt == 2:
                raise ApiError(f"{label}: {e}")
            time.sleep(2 * (attempt + 1))
    try:
        return json.loads(body)
    except json.JSONDecodeError:
        # Key and quota errors come back as XML even when JSON was asked for.
        raise ApiError(f"Not a JSON response for {label}:\n{body[:800]}")


def items_and_total(data):
    """Accepts both {"header","body"} and {"response": {"header","body"}}, and items given as a
    list, as {"item": [...]}, or as a single object."""
    root = data.get("response", data)
    header = root.get("header") or {}
    code = str(header.get("resultCode", "00"))
    if code not in ("00", "0"):
        raise ApiError(f"API error {code}: {header.get('resultMsg', '')}")
    body = root.get("body") or {}
    items = body.get("items") or []
    if isinstance(items, dict):
        items = items.get("item") or []
    if isinstance(items, dict):
        items = [items]
    return items, int(body.get("totalCount") or 0)


def is_cancelled(item):
    status = field(item, "CANCEL_NAME")
    return bool(field(item, "CANCEL_DATE")) or (status not in ("", "정상"))


def fetch_ingredient(key, ingredient):
    """All marketed products whose 주성분 contains [ingredient], plus counts for the report."""
    products, page, total, skipped = [], 1, None, {"cancelled": 0, "other ingredient": 0}
    while True:
        items, total = items_and_total(fetch_page(key, ingredient, page))
        if total > MAX_PER_QUERY:
            raise ApiError(
                f"{ingredient}: {total} results - the 주성분 filter doesn't seem to apply. "
                "Run with --probe and check the request parameters."
            )
        for item in items:
            ingredients = clean_ingredients(field(item, "ITEM_INGR_NAME", "MAIN_ITEM_INGR", "MAIN_INGR"))
            if is_cancelled(item):
                skipped["cancelled"] += 1
                continue
            # Guard against a partial-match filter returning look-alikes.
            if squash(ingredient) not in squash(ingredients):
                skipped["other ingredient"] += 1
                continue
            name = field(item, "ITEM_NAME")
            if not name:
                continue
            product = {
                "name": " ".join(name.split()),
                "englishName": field(item, "ITEM_ENG_NAME"),
                "company": field(item, "ENTP_NAME"),
                "ingredient": ingredients,
                "itemSeq": field(item, "ITEM_SEQ"),
            }
            if ingredient.lower() in INJECTION_ONLY:
                product["route"] = "injection"
            products.append(product)
        if not items or page * PAGE_SIZE >= total:
            break
        page += 1
        time.sleep(0.2)
    return products, total, skipped


# Ways of asking for aspirin, to find out how the 주성분 filter wants its value written.
PROBES = [
    ("no filter", {}),
    ("item_name=아스피린", {"item_name": "아스피린"}),
    ("item_ingr_name=Aspirin", {"item_ingr_name": "Aspirin"}),
    ("item_ingr_name=Finasteride", {"item_ingr_name": "Finasteride"}),
    ("item_ingr_name=Botulinum", {"item_ingr_name": "Botulinum"}),
    ("item_ingr_name=botulinum", {"item_ingr_name": "botulinum"}),
]


def probe(key):
    """One small request per way of filtering, then one whole product, so the field names and
    the way 주성분 is written can be read off the output."""
    sample = None
    for label, params in PROBES:
        try:
            items, total = items_and_total(request_json(key, {"pageNo": 1, "numOfRows": 1, **params}, label))
        except ApiError as e:
            print(f"{label:28} ERROR {e}")
            continue
        first = items[0] if items else {}
        ingredient = field(first, "ITEM_INGR_NAME", "MAIN_ITEM_INGR", "MAIN_INGR")
        print(f"{label:28} totalCount={total:<7} {field(first, 'ITEM_NAME') or '-'} | 주성분: {ingredient or '-'}")
        if first and (sample is None or label.startswith("item_name")):
            sample = first
    if sample:
        print("\nOne whole product:")
        print(json.dumps(sample, ensure_ascii=False, indent=1)[:3000])


def main():
    key = service_key()
    if "--probe" in sys.argv:
        probe(key)
        return

    official = [d["ingredientName"] for d in json.load(open(PROHIBITED, encoding="utf-8"))]
    uncovered = [n for n in dict.fromkeys(official) if n not in QUERIES and n not in NOT_SEARCHABLE]
    if uncovered:
        print(f"Note: no search terms for {uncovered} - add them to QUERIES.", file=sys.stderr)

    products, seen = [], set()
    for entry in dict.fromkeys(official):
        terms = [variant for term in QUERIES.get(entry, []) for variant in (term, term.lower())]
        for ingredient in dict.fromkeys(terms):
            try:
                found, total, skipped = fetch_ingredient(key, ingredient)
            except ApiError as e:
                sys.exit(str(e))
            added = 0
            for product in found:
                identity = product["itemSeq"] or (product["name"], product["ingredient"])
                if identity in seen:
                    continue
                seen.add(identity)
                products.append(product)
                added += 1
            print(f"{ingredient}: {total} listed, {added} kept "
                  f"(skipped {skipped['cancelled']} cancelled, {skipped['other ingredient']} other ingredient)")

    if not products:
        sys.exit("Nothing fetched - nothing written.")
    today = datetime.date.today()
    out = os.path.join(OUT_DIR, f"mfds_restricted_products_{today:%Y%m%d}.json")
    with open(out, "w", encoding="utf-8") as f:
        json.dump({
            "source": "식품의약품안전처 의약품 제품 허가정보 (공공데이터포털 15095677)",
            "fetched": today.isoformat(),
            "products": products,
        }, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"Wrote {len(products)} products to {os.path.relpath(out, ROOT)}. "
          "Now run: python3 tools/build_drug_products.py")


if __name__ == "__main__":
    main()
