"""Finds freely licensed photo sets of Indian events on Wikimedia Commons, for teaching the brain.

Runs on the GitHub build machine (it needs the internet; the app itself never does). A "set" is
what one photographer uploaded from one day: one wedding, one birthday, one function, like a
folder a family would give the app. Only free licences (CC0, CC BY, CC BY-SA, public domain).

    python3 crawl.py OUT/sets.jsonl [MAX_CATEGORIES]

Each line: {"id", "user", "day", "occasion", "categories", "files": [{title, thumb, taken, licence, artist, page}]}
"""
import collections, json, os, re, sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "test-photos"))
from commons import FREE, day, file_counts, files_in, search_categories, subcats  # noqa: E402

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
    ("KIDS_BIRTHDAY", re.compile(r"birthday", re.I)),
    ("KIDS_BIRTHDAY", re.compile(r"annaprash|naming|namkaran|mundan|baby|godh", re.I)),  # a child's function
    ("ENGAGEMENT", re.compile(r"engagement", re.I)),
    ("WEDDING", re.compile(r"wedding|marriage|bride|groom|haldi|mehndi|mehendi|holud|sangeet|baraat|vidaai|reception", re.I)),
]


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
    os.makedirs(os.path.dirname(os.path.abspath(out)), exist_ok=True)
    n_sets = n_files = 0
    with open(out, "w") as fh:
        for (user, d), fs in sorted(groups.items(), key=lambda kv: -len(kv[1])):
            if len(fs) < 10 or d == "unknown":
                continue
            catlist = sorted(where[(user, d)])
            fh.write(json.dumps({
                "id": re.sub(r"[^A-Za-z0-9_-]+", "_", f"{user}_{d}")[:80], "user": user, "day": d,
                "occasion": occasion_of(catlist), "categories": catlist,
                "files": [{k: f.get(k, "") for k in ("title", "thumb", "taken", "licence", "artist", "page")} for f in fs],
            }) + "\n")
            n_sets += 1; n_files += len(fs)
    print(f"{n_sets} sets, {n_files} photos", file=sys.stderr)


if __name__ == "__main__":
    main()
