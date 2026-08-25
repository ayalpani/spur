#!/usr/bin/env python3
"""Collect official competitor screenshots and build a local visual index."""

from __future__ import annotations

import html
import json
import mimetypes
import re
import shutil
import urllib.request
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Iterable


ROOT = Path(__file__).resolve().parent
IMAGES = ROOT / "images"
MANIFEST = ROOT / "manifest.json"
INDEX = ROOT / "index.html"
OVERVIEW = ROOT / "overview.html"
MAX_SCREENSHOTS = 6
USER_AGENT = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139 Safari/537.36"
)


@dataclass(frozen=True)
class App:
    slug: str
    name: str
    category: str
    package: str
    note: str


@dataclass
class Entry:
    slug: str
    name: str
    category: str
    source: str
    note: str
    images: list[str]


APPS = [
    App("strava", "Strava", "Running & Training", "com.strava", "Social feed, recording, route and activity storytelling"),
    App("nike-run-club", "Nike Run Club", "Running & Training", "com.nike.plusgps", "Guided runs, coaching and achievement language"),
    App("adidas-running", "adidas Running", "Running & Training", "com.runtastic.android", "Tracking, goals, challenges and community"),
    App("runkeeper", "ASICS Runkeeper", "Running & Training", "com.fitnesskeeper.runkeeper.pro", "Approachable tracking, plans and progress"),
    App("mapmyrun", "MapMyRun", "Running & Training", "com.mapmyrun.android2", "Classic route recording and workout analytics"),
    App("runna", "Runna", "Running & Training", "com.runbuddy.prod", "Plan-led coaching and workout guidance"),
    App("garmin-connect", "Garmin Connect", "Running & Training", "com.garmin.android.apps.connectmobile", "Dense history, health metrics and device ecosystem"),
    App("suunto", "Suunto", "Running & Training", "com.stt.android.suunto", "Map-first sports tracking and route planning"),
    App("komoot", "Komoot", "Outdoor & Navigation", "de.komoot.android", "Route discovery, planning and turn-by-turn navigation"),
    App("alltrails", "AllTrails", "Outdoor & Navigation", "com.alltrails.alltrails", "Trail discovery, trust signals and navigation"),
    App("outdooractive", "Outdooractive", "Outdoor & Navigation", "com.outdooractive.Outdooractive", "Professional maps, route planning and offline use"),
    App("wikiloc", "Wikiloc", "Outdoor & Navigation", "com.wikiloc.wikilocandroid", "Community routes, recording, waypoints and photos"),
    App("relive", "Relive", "Outdoor & Navigation", "cc.relive.reliveapp", "Turns tracked movement and photos into a shareable story"),
    App("mapy", "Mapy.com", "Outdoor & Navigation", "cz.seznam.mapy", "Strong cartography, outdoor layers and offline maps"),
    App("osmand", "OsmAnd", "Outdoor & Navigation", "net.osmand", "Power-user map controls, layers and offline navigation"),
    App("fog-of-world", "Fog of World", "Exploration & Game", "com.ollix.fogofworld", "Persistent fog-of-war exploration map"),
    App("zombies-run", "Zombies, Run!", "Exploration & Game", "com.sixtostart.zombiesrunclient", "Narrative motivation layered onto running"),
    App("runiverse", "Runiverse", "Exploration & Game", "com.imagine_x.Runiverse", "Territory capture on a living 3D map"),
    App("city-wanderer", "City Wanderer", "Exploration & Game", "com.funspiritgames.rpgcityexplorer", "City walking with persistent fog-of-war"),
]

WEB_ENTRIES = [
    Entry(
        "citystrides",
        "CityStrides",
        "Street Coverage",
        "https://citystrides.com/",
        "Every-street completion, LifeMap and city progress",
        ["images/web/citystrides.png"],
    ),
    Entry(
        "wandrer",
        "Wandrer",
        "Street Coverage",
        "https://wandrer.earth/",
        "Unique-road completion across roads and trails",
        ["images/web/wandrer.png"],
    ),
    Entry(
        "citywalker",
        "CityWalker",
        "Street Coverage",
        "https://citywalker.app/",
        "On-device street coverage with live exploration",
        ["images/web/citywalker.png"],
    ),
    Entry(
        "streets",
        "Streets",
        "Street Coverage",
        "https://streets-app.com/",
        "iPhone walk tracking built around covering every street",
        ["images/web/streets.png"],
    ),
]


SCREENSHOT_RE = re.compile(
    r'<img[^>]+srcset="([^"]+)"[^>]+alt="Screenshot image"'
    r'[^>]+data-screenshot-index="(\d+)"'
)


def get(url: str) -> tuple[bytes, str]:
    request = urllib.request.Request(url, headers={"User-Agent": USER_AGENT})
    with urllib.request.urlopen(request, timeout=45) as response:
        return response.read(), response.headers.get_content_type()


def extension(content_type: str) -> str:
    if content_type == "image/jpeg":
        return ".jpg"
    if content_type == "image/png":
        return ".png"
    if content_type == "image/webp":
        return ".webp"
    return mimetypes.guess_extension(content_type) or ".img"


def high_resolution_url(srcset: str) -> str:
    candidates = [part.strip().split()[0] for part in html.unescape(srcset).split(",")]
    return candidates[-1]


def collect_app(app: App) -> Entry:
    source = f"https://play.google.com/store/apps/details?id={app.package}&hl=en_US&gl=US"
    page, _ = get(source)
    matches = sorted(
        ((int(index), high_resolution_url(srcset)) for srcset, index in SCREENSHOT_RE.findall(page.decode("utf-8", "ignore"))),
        key=lambda item: item[0],
    )
    if not matches:
        raise RuntimeError(f"No screenshots found for {app.name}")

    target_dir = IMAGES / app.slug
    if target_dir.exists():
        shutil.rmtree(target_dir)
    target_dir.mkdir(parents=True)
    paths: list[str] = []
    for index, url in matches[:MAX_SCREENSHOTS]:
        data, content_type = get(url)
        output = target_dir / f"{index + 1:02d}{extension(content_type)}"
        output.write_bytes(data)
        paths.append(output.relative_to(ROOT).as_posix())

    return Entry(app.slug, app.name, app.category, source, app.note, paths)


def write_manifest(entries: Iterable[Entry]) -> None:
    MANIFEST.write_text(
        json.dumps([asdict(entry) for entry in entries], ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )


def write_index(entries: list[Entry]) -> None:
    categories = list(dict.fromkeys(entry.category for entry in entries))
    filters = "".join(
        f'<button type="button" data-filter="{html.escape(category)}">{html.escape(category)}</button>'
        for category in categories
    )
    cards = []
    for entry in entries:
        images = "".join(
            f'<a href="{html.escape(path)}"><img loading="lazy" src="{html.escape(path)}" alt="{html.escape(entry.name)} screenshot"></a>'
            for path in entry.images
        )
        cards.append(
            f'''<article class="card" data-category="{html.escape(entry.category)}">
              <header>
                <div><p class="eyebrow">{html.escape(entry.category)}</p><h2>{html.escape(entry.name)}</h2></div>
                <a class="source" href="{html.escape(entry.source)}">Quelle ↗</a>
              </header>
              <p class="note">{html.escape(entry.note)}</p>
              <div class="shots">{images}</div>
            </article>'''
        )

    document = f'''<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Spur · Competitive App Screens</title>
  <style>
    :root {{ color-scheme: light; --ink:#111; --muted:#6e716f; --surface:#f2f3f2; --line:#dadcda; --accent:#b9f36b; }}
    * {{ box-sizing:border-box; }}
    body {{ margin:0; background:#fff; color:var(--ink); font:15px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; }}
    .page {{ max-width:1440px; margin:auto; padding:56px 32px 96px; }}
    .intro {{ max-width:820px; margin-bottom:36px; }}
    h1 {{ margin:0 0 12px; font-size:clamp(42px,7vw,88px); line-height:.92; letter-spacing:-.055em; }}
    .intro p {{ max-width:680px; color:var(--muted); font-size:18px; }}
    .meta {{ display:inline-flex; gap:10px; align-items:center; background:var(--accent); padding:8px 12px; border-radius:99px; color:#17200d; font-weight:650; }}
    .filters {{ display:flex; flex-wrap:wrap; gap:8px; margin:28px 0 44px; }}
    button {{ appearance:none; border:1px solid var(--line); border-radius:99px; background:#fff; padding:10px 14px; font:inherit; cursor:pointer; }}
    button.active {{ background:var(--ink); border-color:var(--ink); color:#fff; }}
    .grid {{ display:grid; gap:64px; }}
    .card {{ min-width:0; }}
    .card header {{ display:flex; align-items:end; justify-content:space-between; gap:16px; border-top:1px solid var(--line); padding-top:18px; }}
    .eyebrow {{ margin:0 0 3px; color:var(--muted); font-size:12px; font-weight:700; letter-spacing:.08em; text-transform:uppercase; }}
    h2 {{ margin:0; font-size:32px; line-height:1; letter-spacing:-.035em; }}
    .source {{ color:var(--ink); text-underline-offset:3px; white-space:nowrap; }}
    .note {{ margin:10px 0 20px; color:var(--muted); }}
    .shots {{ display:flex; gap:14px; overflow-x:auto; padding:0 0 18px; scroll-snap-type:x proximity; scrollbar-color:#bbb transparent; }}
    .shots a {{ flex:0 0 auto; scroll-snap-align:start; display:flex; height:520px; max-width:min(82vw,940px); border-radius:20px; overflow:hidden; background:var(--surface); box-shadow:0 1px 1px rgb(0 0 0/.08),0 10px 28px rgb(0 0 0/.07); }}
    .shots img {{ display:block; width:auto; height:100%; object-fit:contain; }}
    .card[data-category="Street Coverage"] .shots a {{ height:auto; max-height:none; width:min(100%,1280px); }}
    .card[data-category="Street Coverage"] .shots img {{ width:100%; height:auto; }}
    .card[hidden] {{ display:none; }}
    footer {{ margin-top:72px; border-top:1px solid var(--line); padding-top:18px; color:var(--muted); }}
    @media (max-width:700px) {{ .page {{ padding:34px 18px 72px; }} .shots a {{ height:430px; }} .card header {{ align-items:start; }} }}
  </style>
</head>
<body>
  <main class="page">
    <section class="intro">
      <span class="meta">23 Produkte · 2026-08-06</span>
      <h1>Apps in Spurs Fahrwasser.</h1>
      <p>Offizielle Store-Screens und Website-Aufnahmen aus Running, Outdoor-Navigation, Bewegungs-Gamification und Street Coverage. Als visuelle Referenz – nicht als Feature-Backlog.</p>
    </section>
    <nav class="filters" aria-label="Kategorien">
      <button class="active" type="button" data-filter="all">Alle</button>{filters}
    </nav>
    <section class="grid">{''.join(cards)}</section>
    <footer>Nur für interne Produkt- und Designrecherche. Marken und Screenshots gehören den jeweiligen Anbietern. Quellen sind an jeder App verlinkt.</footer>
  </main>
  <script>
    const buttons = document.querySelectorAll('[data-filter]');
    const cards = document.querySelectorAll('.card');
    buttons.forEach(button => button.addEventListener('click', () => {{
      buttons.forEach(item => item.classList.toggle('active', item === button));
      cards.forEach(card => card.hidden = button.dataset.filter !== 'all' && card.dataset.category !== button.dataset.filter);
    }}));
  </script>
</body>
</html>'''
    INDEX.write_text(document, encoding="utf-8")


def write_overview(entries: list[Entry]) -> None:
    cards = "".join(
        f'''<article>
          <div class="image"><img src="{html.escape(entry.images[0])}" alt=""></div>
          <p>{html.escape(entry.category)}</p><h2>{html.escape(entry.name)}</h2>
        </article>'''
        for entry in entries
    )
    document = f'''<!doctype html>
<html lang="de">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Spur · Competitive App Overview</title>
  <style>
    * {{ box-sizing:border-box; }}
    body {{ margin:0; background:#eeefed; color:#111; font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif; }}
    main {{ width:1440px; margin:auto; padding:56px; }}
    header {{ display:flex; justify-content:space-between; align-items:end; margin-bottom:34px; }}
    h1 {{ max-width:800px; margin:0; font-size:64px; line-height:.94; letter-spacing:-.052em; }}
    header span {{ border-radius:99px; background:#b9f36b; padding:9px 13px; font-weight:700; }}
    section {{ display:grid; grid-template-columns:repeat(4,1fr); gap:26px 18px; }}
    article {{ min-width:0; }}
    .image {{ height:390px; display:flex; align-items:center; justify-content:center; overflow:hidden; border-radius:18px; background:#fff; box-shadow:0 1px 1px rgb(0 0 0/.08),0 8px 24px rgb(0 0 0/.08); }}
    img {{ display:block; width:100%; height:100%; object-fit:contain; }}
    p {{ margin:12px 0 2px; color:#6e716f; font-size:11px; font-weight:750; letter-spacing:.07em; text-transform:uppercase; }}
    h2 {{ margin:0; font-size:23px; letter-spacing:-.025em; }}
  </style>
</head>
<body><main><header><h1>Apps in Spurs Fahrwasser.</h1><span>23 Produkte · August 2026</span></header><section>{cards}</section></main></body>
</html>'''
    OVERVIEW.write_text(document, encoding="utf-8")


def main() -> None:
    IMAGES.mkdir(exist_ok=True)
    entries = []
    for app in APPS:
        print(f"Collecting {app.name} …", flush=True)
        entries.append(collect_app(app))
    entries.extend(WEB_ENTRIES)
    write_manifest(entries)
    write_index(entries)
    write_overview(entries)
    print(f"Wrote {len(entries)} entries to {INDEX}")


if __name__ == "__main__":
    main()
