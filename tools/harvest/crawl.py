"""Finds freely licensed photo sets of Indian events on Wikimedia Commons, for teaching the brain.

Runs on the GitHub build machine (it needs the internet; the app itself never does). A "set" is
what one photographer uploaded from one day: one wedding, one birthday, one function, like a
folder a family would give the app. Only free licences (CC0, CC BY, CC BY-SA, public domain).

    python3 crawl.py OUT/sets.jsonl [MAX_CATEGORIES]

Each line: {"id", "user", "day", "occasion", "categories", "files": [{title, thumb, taken, licence, artist, page}]}
"""
import collections, json, os, re, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-photos"))
from commons import FREE, UA, api, day, file_counts, files_in, infos, search_categories, subcats  # noqa: E402
import time, urllib.parse, urllib.request  # noqa: E402

STATES = ["Andhra Pradesh", "Arunachal Pradesh", "Assam", "Bihar", "Chhattisgarh", "Goa", "Gujarat", "Haryana",
          "Himachal Pradesh", "Jharkhand", "Karnataka", "Kerala", "Madhya Pradesh", "Maharashtra", "Manipur",
          "Meghalaya", "Mizoram", "Nagaland", "Odisha", "Punjab", "Rajasthan", "Sikkim", "Tamil Nadu", "Telangana",
          "Tripura", "Uttar Pradesh", "Uttarakhand", "West Bengal", "Delhi", "Jammu and Kashmir", "Ladakh", "Puducherry"]

SEARCHES = (
    [f"Weddings in {s}" for s in STATES] +
    ["Hindu weddings in India", "Muslim weddings in India", "Sikh weddings", "Christian weddings in India",
     "Bengali Hindu weddings", "Marathi wedding", "Tamil wedding", "Telugu wedding", "Malayali wedding",
     "Kannada wedding", "Gujarati wedding", "Punjabi wedding", "Rajasthani wedding", "Bihari wedding", "Odia wedding",
     "Assamese wedding", "Kashmiri wedding", "Goan wedding", "Navayana Buddhist weddings", "Gaye holud",
     "Haldi ceremony", "Mehndi ceremony", "Sangeet ceremony", "Baraat", "Vidaai", "Wedding receptions in India",
     "Engagement ceremonies in India", "Birthday parties in India", "Birthday celebrations in India",
     "Annaprashana", "Naming ceremonies in India", "Upanayana", "Mundan ceremony", "Godh bharai",
     "Family gatherings in India", "Family photographs of India", "Diwali celebrations", "Holi in India",
     "Ganesh Chaturthi celebrations", "Durga Puja celebrations", "Onam celebrations", "Pongal celebrations",
     "Griha Pravesh", "Sathabhishekam", "Shashtipoorthi"]
)

ON_TOPIC = re.compile(r"wedding|marriage|bride|groom|haldi|mehndi|mehendi|holud|sangeet|baraat|vidaai|reception|"
                      r"engagement|birthday|annaprash|naming|namkaran|upanayan|thread ceremony|mundan|baby|godh|"
                      r"family|famil|diwali|deepavali|holi\b|puja|chaturthi|onam|pongal|festival|celebration|"
                      r"party|parties|ceremon|ritual|griha|pravesh|shashti|sathabhi", re.I)
OFF_TOPIC = re.compile(r"temple|church|mosque|building|architecture|map|logo|poster|stamp|painting|drawing|"
                       r"manuscript|film|movie|actor|actress|politic|minister|book|newspaper|museum|statue", re.I)

OCCASION = [
    ("KIDS_BIRTHDAY", re.compile(r"birthday|janmadin|vadhdivas|cake cutting", re.I)),
    ("KIDS_BIRTHDAY", re.compile(r"annaprash|naming|namkaran|mundan|baby|godh", re.I)),  # a child's function
    ("TRIP", re.compile(r"trip|vacation|honeymoon|travel|tour", re.I)),
    ("ENGAGEMENT", re.compile(r"engagement|pre.?wedding", re.I)),
    ("WEDDING", re.compile(r"wedding|marriage|bride|groom|haldi|mehndi|mehendi|holud|sangeet|baraat|vidaai|reception", re.I)),
]


# Birthday and trip photos are rarely filed in tidy categories: search single files on Commons, and
# freely licensed Flickr photos through Openverse (openverse.org, a search of CC-licensed media).
BIRTHDAY_SEARCHES = ["birthday India", "birthday party India", "first birthday India", "1st birthday", "kids birthday party India",
                     "cake cutting birthday India", "birthday celebration Kerala", "birthday celebration Maharashtra",
                     "birthday Delhi", "birthday Bangalore", "birthday Mumbai", "birthday Chennai", "birthday Kolkata",
                     "annaprashan", "naming ceremony India", "mundan ceremony", "bal birthday", "janmadin", "vadhdivas",
                     # Trips and other occasions families keep albums of.
                     "family trip India", "family vacation India", "honeymoon India", "Goa trip", "Manali trip", "Kerala trip",
                     "Ladakh road trip", "friends trip India", "pre-wedding shoot India", "pre wedding photoshoot",
                     "wedding anniversary India", "housewarming India", "griha pravesh", "baby shower India",
                     "Diwali family", "Ganpati at home", "Durga Puja family"]
OPENVERSE = "https://api.openverse.org/v1/images/"


def commons_file_search(term, limit=500):
    titles, offset = [], 0
    while len(titles) < limit:
        data = api({"action": "query", "list": "search", "srsearch": term, "srnamespace": 6, "srlimit": 50, "sroffset": offset})
        hits = data.get("query", {}).get("search", [])
        titles += [h["title"] for h in hits if re.search(r"\.(jpe?g|png)$", h["title"], re.I)]
        if "continue" not in data or not hits:
            break
        offset = data["continue"].get("sroffset", offset + 50)
    return list(infos(titles, width=1280).values()) if titles else []


def openverse(term, pages=10):
    out = []
    for page in range(1, pages + 1):
        q = urllib.parse.urlencode({"q": term, "license": "by,by-sa,cc0,pdm", "page_size": 20, "page": page})
        try:
            with urllib.request.urlopen(urllib.request.Request(f"{OPENVERSE}?{q}", headers={"User-Agent": UA}), timeout=60) as r:
                data = json.load(r)
        except Exception as e:  # rate limit or end of results
            print("openverse", term, page, e, file=sys.stderr)
            break
        for it in data.get("results", []):
            out.append({"title": it.get("title") or it.get("id"), "thumb": it.get("url", ""), "taken": "",
                        "licence": f"CC {it.get('license', '').upper()} {it.get('license_version', '')}".strip(),
                        "artist": (it.get("creator") or "")[:120], "page": it.get("foreign_landing_url", ""),
                        "user": it.get("creator") or "unknown", "source": it.get("source", "")})
        if page >= data.get("page_count", 0):
            break
        time.sleep(1.5)
    return out


def occasion_of(categories):
    text = " ".join(categories)
    return next((o for o, rx in OCCASION if rx.search(text)), "OTHER")


def main():
    out = sys.argv[1]
    max_cats = int(sys.argv[2]) if len(sys.argv) > 2 else 1500
    cats = []
    for term in SEARCHES:
        for c in search_categories(term):
            if c not in cats and ON_TOPIC.search(c) and not OFF_TOPIC.search(c):
                cats.append(c)
    # Two levels down, keeping to events and people, not buildings or artworks.
    frontier = list(cats)
    for _ in range(2):
        nxt = []
        for c in frontier:
            for s in subcats(c):
                if s not in cats and ON_TOPIC.search(s) and not OFF_TOPIC.search(s):
                    cats.append(s); nxt.append(s)
            if len(cats) >= max_cats:
                break
        frontier = nxt
        if len(cats) >= max_cats:
            break
    counts = file_counts(cats)
    cats = [c for c in cats if counts.get(c, 0) >= 5]
    print(len(cats), "categories with photos", file=sys.stderr)

    seen, groups, where = set(), collections.defaultdict(list), collections.defaultdict(set)
    for c in cats:
        for f in files_in(c, max_files=1500, width=1280):
            if f["title"] in seen or not FREE.match(f["licence"].strip()):
                continue
            seen.add(f["title"])
            key = (f["user"], day(f))
            groups[key].append(f)
            where[key].add(c)
    # Birthdays: single files on Commons (grouped by photographer and day) and Flickr photos
    # through Openverse (grouped by photographer: usually one party).
    for term in BIRTHDAY_SEARCHES:
        for f in commons_file_search(term):
            if f["title"] in seen or not FREE.match(f["licence"].strip()):
                continue
            seen.add(f["title"])
            key = (f["user"], day({"taken": f["taken"], "uploaded": ""}))
            groups[key].append(f); where[key].add("Search: " + term)
        for f in openverse(term):
            if f["title"] in seen or not f["thumb"]:
                continue
            seen.add(f["title"])
            key = (f["user"], "openverse")
            groups[key].append(f); where[key].add("Search: " + term)

    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    n_sets = n_files = 0
    with open(out, "w") as fh:
        for (user, d), fs in sorted(groups.items(), key=lambda kv: -len(kv[1])):
            catlist = sorted(where[(user, d)])
            searched = all(c.startswith("Search: ") for c in catlist)
            if len(fs) < (6 if searched else 10) or d == "unknown":
                continue
            fh.write(json.dumps({
                "id": re.sub(r"[^A-Za-z0-9_-]+", "_", f"{user}_{d}")[:80], "user": user, "day": d,
                "occasion": occasion_of(catlist), "categories": catlist,
                "files": [{k: f.get(k, "") for k in ("title", "thumb", "taken", "licence", "artist", "page")} for f in fs],
            }) + "\n")
            n_sets += 1; n_files += len(fs)
    print(f"{n_sets} sets, {n_files} photos", file=sys.stderr)


if __name__ == "__main__":
    main()
