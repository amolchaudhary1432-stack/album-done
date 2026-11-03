"""Downloads a Google Drive folder (every sub-folder kept) or one file, from an "anyone with the link"
share, for a real couple's wedding: the photographer's raw photos, or the printed album's PDF.

    python3 drive_fetch.py LINK OUT_DIR [--max-long-side N]

Uses Google's Drive API with the service account in GOOGLE_APPLICATION_CREDENTIALS (the Test Lab
key): needs the Drive API switched on for that project. Without it (or for a single file) it falls
back to gdown, which reads a shared file, or a shared folder of up to 50 files per folder.
--max-long-side makes a smaller copy of each photo straight away and drops the original (EXIF kept),
for folders too big for the build machine's disk. Prints counts only, never names (the log may be
public while the repo is).
"""
import io
import os
import re
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

PHOTO = (".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp")
FOLDER = "application/vnd.google-apps.folder"
API = "https://www.googleapis.com/drive/v3/files"


class ApiOff(Exception):
    """The Drive API is not switched on for the service account's project."""


def ids(link):
    """(folder id, file id, resource key) from a Drive link."""
    folder = re.search(r"/folders/([\w-]+)", link)
    file = re.search(r"/file/d/([\w-]+)", link) or re.search(r"[?&]id=([\w-]+)", link)
    key = re.search(r"[?&]resourcekey=([\w-]+)", link)
    return (folder and folder[1], None if folder else (file and file[1]), key and key[1])


def session():
    from google.oauth2 import service_account
    from google.auth.transport.requests import AuthorizedSession
    creds = service_account.Credentials.from_service_account_file(
        os.environ["GOOGLE_APPLICATION_CREDENTIALS"], scopes=["https://www.googleapis.com/auth/drive.readonly"])
    return AuthorizedSession(creds)


def check(r):
    if r.status_code == 403 and ("accessNotConfigured" in r.text or "SERVICE_DISABLED" in r.text or "has not been used" in r.text):
        raise ApiOff()
    r.raise_for_status()
    return r


def listing(s, folder, keys, path=""):
    """Every file under folder: (file id, path inside the folder, size)."""
    token = None
    while True:
        params = {"q": f"'{folder}' in parents and trashed=false", "pageSize": 1000,
                  "fields": "nextPageToken,files(id,name,mimeType,size,resourceKey)",
                  "supportsAllDrives": "true", "includeItemsFromAllDrives": "true"}
        if token:
            params["pageToken"] = token
        r = check(s.get(API, params=params, headers=keys))
        data = r.json()
        for f in data.get("files", []):
            name = f["name"].replace("/", "_")
            sub = keys
            if f.get("resourceKey"):
                sub = {"X-Goog-Drive-Resource-Keys": f"{f['id']}/{f['resourceKey']}"}
            if f["mimeType"] == FOLDER:
                yield from listing(s, f["id"], sub, os.path.join(path, name))
            else:
                yield f["id"], os.path.join(path, name), int(f.get("size") or 0), sub
        token = data.get("nextPageToken")
        if not token:
            return


def shrink(data, dst, long_side):
    from PIL import Image
    try:
        import pillow_heif
        pillow_heif.register_heif_opener()
    except ImportError:
        pass
    im = Image.open(io.BytesIO(data))
    exif = im.info.get("exif")
    im = im.convert("RGB")
    im.thumbnail((long_side, long_side))
    kwargs = {"quality": 92}
    if exif:
        kwargs["exif"] = exif
    im.save(os.path.splitext(dst)[0] + ".jpg", **kwargs)


def via_api(link, out, long_side):
    folder, file, key = ids(link)
    s = session()
    keys = {"X-Goog-Drive-Resource-Keys": f"{folder or file}/{key}"} if key else {}
    if file:
        r = check(s.get(f"{API}/{file}", params={"fields": "name", "supportsAllDrives": "true"}, headers=keys))
        items = [(file, r.json()["name"], 0, keys)]
    else:
        items = list(listing(s, folder, keys))
    photos = [i for i in items if i[1].lower().endswith(PHOTO) or file]
    print(f"{len(items)} files in the share, {len(photos)} to download, "
          f"{sum(i[2] for i in photos) / 1e9:.1f} GB", flush=True)

    def get(item):
        fid, rel, _, hdr = item
        dst = os.path.join(out, rel)
        os.makedirs(os.path.dirname(dst) or out, exist_ok=True)
        for attempt in range(4):
            try:
                r = check(s.get(f"{API}/{fid}", params={"alt": "media", "supportsAllDrives": "true"},
                                headers=hdr, timeout=300))
                if long_side and rel.lower().endswith(PHOTO):
                    shrink(r.content, dst, long_side)
                else:
                    open(dst, "wb").write(r.content)
                return True
            except ApiOff:
                raise
            except Exception as e:  # a photo that cannot be read is counted, not fatal
                last = e
        print("could not get one file:", type(last).__name__, file=sys.stderr)
        return False
    done = 0
    with ThreadPoolExecutor(8) as ex:
        for k, ok in enumerate(ex.map(get, photos), 1):
            done += ok
            if k % 250 == 0:
                print(f"{k} of {len(photos)}", flush=True)
    print(f"{done} of {len(photos)} downloaded")
    return done


def via_gdown(link, out):
    folder, file, _ = ids(link)
    cmd = ["gdown", "--quiet", "--fuzzy"]
    cmd += ["--folder", link, "-O", out] if folder else [link, "-O", out + "/"]
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        too_many = "more than" in r.stderr and "50" in r.stderr
        print("gdown could not download the share" + (" (more than 50 files in a folder)" if too_many else ""))
        return 0
    n = sum(len(f) for _, _, f in os.walk(out))
    print(f"{n} files downloaded (gdown)")
    return n


def main():
    link, out = sys.argv[1].strip(), sys.argv[2]
    long_side = int(sys.argv[sys.argv.index("--max-long-side") + 1]) if "--max-long-side" in sys.argv else 0
    os.makedirs(out, exist_ok=True)
    try:
        n = via_api(link, out, long_side)
    except ApiOff:
        print("The Drive API is off for the project: trying without it")
        n = via_gdown(link, out)
        if n == 0:
            print("NEEDS: switch on the Drive API, https://console.cloud.google.com/apis/library/drive.googleapis.com?project=album-done-test")
    sys.exit(0 if n > 0 else 1)


if __name__ == "__main__":
    main()
