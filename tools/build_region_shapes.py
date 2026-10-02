#!/usr/bin/env python3
"""
Builds app/src/main/assets/restricted_region_shapes.json: the boundary of every region a stay can be
recorded in (blood_donation_rules.xml), keyed by its StayRegion name, so Google Timeline visits can
be matched to regions on the device without sending coordinates anywhere.

Sources (downloaded when run):
  - Natural Earth 1:10m admin 0 map units (public domain)
      https://github.com/nvkelso/natural-earth-vector
  - KOSTAT 2013 municipal boundaries via southkorea-maps
      https://github.com/southkorea/southkorea-maps

Usage: python3 tools/build_region_shapes.py
Re-run after adding a region to blood_donation_rules.xml; it fails when a region has no boundary.
"""
import json
import os
import re
import sys
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RULES = os.path.join(ROOT, "app/src/main/assets/blood_donation_rules.xml")
OUT = os.path.join(ROOT, "app/src/main/assets/restricted_region_shapes.json")

NATURAL_EARTH = "https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/geojson/ne_10m_admin_0_map_units.geojson"
KOSTAT = "https://raw.githubusercontent.com/southkorea/southkorea-maps/master/kostat/2013/json/skorea_municipalities_geo_simple.json"

# Rule region name -> Natural Earth map unit codes (GU_A3). Most countries are a single unit;
# these are the ones whose name doesn't match NAME_KO or that span several units.
NATURAL_EARTH_UNITS = {
    "도미니카공화국": ["DOM"],
    "모리타니아": ["MRT"],
    "상투메프린시페": ["STP"],
    "적도기니": ["GNQ"],
    "중앙아프리카공화국": ["CAF"],
    "콩고공화국(브라자빌)": ["COG"],
    "콩고민주공화국(킨샤샤)": ["COD"],
    "남아프리카공화국": ["ZAF"],
    "에스와티니(스와질란드)": ["SWZ"],
    "버마(미얀마)": ["MMR"],
    "솔로몬제도": ["SLB"],
    "티모르레스테(동티모르)": ["TLS"],
    "마요트(프랑스령)": ["MYT"],
    "아이티(라바디항구 포함)": ["HTI"],
    "에콰도르(갈라파고스 제도 포함)": ["ECU"],
    "소말리아": ["SOX", "SOP", "SOL"],
    "파푸아뉴기니": ["PNX", "PNB"],
    "탄자니아": ["TZA", "TZZ"],
    "북한": ["PRK", "KNZ"],
    # vCJD: 영국 and its listed areas (맨 섬, 채널 제도 = Jersey + Guernsey, 지브롤터, 포클랜드 섬).
    "영국": ["ENG", "SCT", "WLS", "NIR", "IMN", "JEY", "GGY", "GIB", "FLK"],
    # vCJD 프랑스 is metropolitan France; its overseas units are separate 말라리아 regions.
    "프랑스": ["FXX"],
    "아일랜드": ["IRL"],
}

# Excluded from a region: (lat, lng, radius km). 북한 is restricted "백두산 제외 전지역".
EXCLUSIONS = {"북한": [[41.9936, 128.0778, 20.0]]}

COUNTRY_TOLERANCE = 0.01  # degrees, ~1km
LAND_TOLERANCE = 0.02  # degrees, ~2km: only has to say "this is land" away from restricted coasts
MUNICIPAL_TOLERANCE = 0.0005  # degrees, ~50m: these borders run through towns


def fetch(url):
    with urllib.request.urlopen(url) as response:
        return json.load(response)


def rule_regions():
    xml = open(RULES, encoding="utf-8").read()
    countries = re.findall(r"<Country>(.*?)</Country>", xml)
    domestic = []
    for province, body in re.findall(r'<Province name="(.*?)"[^>]*>(.*?)</Province>', xml, re.S):
        for city in re.findall(r"<City>(.*?)</City>", body):
            domestic.append((province, city))
    vcjd = []
    for country in re.findall(r'<Region country="(.*?)"', xml):
        vcjd += [c.strip() for c in re.split(r"[·,]", country) if c.strip()]
    return countries, domestic, vcjd


def perpendicular_distance(p, a, b):
    (x, y), (x1, y1), (x2, y2) = p, a, b
    dx, dy = x2 - x1, y2 - y1
    if dx == 0 and dy == 0:
        return ((x - x1) ** 2 + (y - y1) ** 2) ** 0.5
    return abs(dy * x - dx * y + x2 * y1 - y2 * x1) / (dx * dx + dy * dy) ** 0.5


def simplify(points, tolerance):
    """Douglas-Peucker, iterative so long coastlines don't hit the recursion limit."""
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        index, distance = 0, 0.0
        for i in range(start + 1, end):
            d = perpendicular_distance(points[i], points[start], points[end])
            if d > distance:
                index, distance = i, d
        if distance > tolerance:
            keep[index] = True
            stack += [(start, index), (index, end)]
    return [p for p, k in zip(points, keep) if k]


def rings(geometry):
    if geometry["type"] == "Polygon":
        return geometry["coordinates"]
    return [ring for polygon in geometry["coordinates"] for ring in polygon]


def encode(geometries, tolerance, digits):
    """Every ring as a flat [lng, lat, lng, lat, ...] list. Islands too small to simplify stay as they are."""
    encoded = []
    for geometry in geometries:
        for ring in rings(geometry):
            simplified = simplify(ring, tolerance)
            if len(simplified) < 4:
                simplified = ring
            encoded.append([round(v, digits) for point in simplified for v in point[:2]])
    return encoded


def main():
    countries, domestic, vcjd = rule_regions()
    natural_earth = fetch(NATURAL_EARTH)["features"]
    kostat = fetch(KOSTAT)["features"]

    by_code = {f["properties"]["GU_A3"]: f["geometry"] for f in natural_earth}
    by_korean_name = {}
    for f in natural_earth:
        by_korean_name.setdefault(f["properties"]["NAME_KO"], []).append(f["properties"]["GU_A3"])

    regions = []
    missing = []
    used_codes = set(["KNZ", "PRK"])

    for province, city in domestic:
        if province in NATURAL_EARTH_UNITS:  # 북한: the whole country, not one city
            name, codes = province, NATURAL_EARTH_UNITS[province]
            regions.append({"name": name, "rings": encode([by_code[c] for c in codes], COUNTRY_TOLERANCE, 3)})
            continue
        geometries = [f["geometry"] for f in kostat if f["properties"]["name"] == city]
        if len(geometries) != 1:
            missing.append(f"{province} {city}")
            continue
        regions.append({"name": f"{province} {city}", "rings": encode(geometries, MUNICIPAL_TOLERANCE, 4)})

    for name in countries + vcjd:
        codes = NATURAL_EARTH_UNITS.get(name) or by_korean_name.get(name)
        if not codes or any(c not in by_code for c in codes):
            missing.append(name)
            continue
        used_codes.update(codes)
        regions.append({"name": name, "rings": encode([by_code[c] for c in codes], COUNTRY_TOLERANCE, 3)})

    if missing:
        sys.exit(f"No boundary for: {', '.join(missing)}")

    for region in regions:
        region["exclusions"] = EXCLUSIONS.get(region["name"], [])

    # Every other country's land, so a point there is known to be unrestricted. Without it, a point
    # just off a restricted coastline (the 1:10m coast cuts through some seaside towns) couldn't be
    # told apart from one across a land border, and only the former may snap to the coast.
    land = encode(
        [f["geometry"] for f in natural_earth if f["properties"]["GU_A3"] not in used_codes],
        LAND_TOLERANCE,
        3,
    )

    payload = {
        "source": "Natural Earth 1:10m admin 0 map units (public domain); KOSTAT 2013 municipalities via southkorea-maps",
        "regions": regions,
        "unrestrictedLand": land,
    }
    with open(OUT, "w", encoding="utf-8") as out:
        json.dump(payload, out, ensure_ascii=False, separators=(",", ":"))
    print(f"{len(regions)} regions -> {OUT} ({os.path.getsize(OUT) // 1024} KB)")


if __name__ == "__main__":
    main()
