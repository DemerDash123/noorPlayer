#!/usr/bin/env python3
"""
Static checks that catch the usual Android runtime crashes without a device:
  1. every view tag in the layouts is a real framework class (or one of ours)
  2. every findViewById(R.id.x) / click(R.id.x) in a Java file exists in that file's layouts,
     and the cast type matches the view class in the layout (ClassCastException check)
  3. English and Arabic strings have the same keys and the same format placeholders
  4. every component named in the manifest exists in the compiled classes
  5. no compile-only stub classes ended up in classes.dex
  6. lists every framework API the app calls, flagging anything outside the public SDK packages
"""
import os
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
RES = os.path.join(ROOT, "res")
SRC = os.path.join(ROOT, "src", "com", "ahmed", "noorplayer")
CLASSES = os.path.join(ROOT, "build", "apk", "classes")
STUBS = os.path.join(ROOT, "build", "apk", "stubs")
DEX = os.path.join(ROOT, "build", "apk", "classes.dex")
AJ = "/usr/lib/android-sdk/platforms/android-23/android.jar"
A = "{http://schemas.android.com/apk/res/android}"

problems = []


def fail(msg):
    problems.append(msg)
    print("  PROBLEM:", msg)


_super_cache = {}


def superclass_chain(cls):
    """Fully qualified class -> list of itself and its superclasses (via javap)."""
    if cls in _super_cache:
        return _super_cache[cls]
    chain = []
    cur = cls
    while cur and cur != "java.lang.Object":
        chain.append(cur)
        cp = AJ + ":" + CLASSES + ":" + STUBS
        out = subprocess.run(["javap", "-cp", cp, cur], capture_output=True, text=True).stdout
        m = re.search(r"class\s+[\w.$]+(?:<[^>]*>)?\s+extends\s+([\w.$]+)", out)
        if not m:
            if "class " not in out and "interface " not in out:
                _super_cache[cls] = None
                return None
            break
        cur = m.group(1)
    chain.append("java.lang.Object")
    _super_cache[cls] = chain
    return chain


def resolve_view_class(tag):
    if "." in tag:
        return tag
    for pkg in ("android.widget.", "android.view.", "android.webkit."):
        if superclass_chain(pkg + tag):
            return pkg + tag
    return None


def parse_layouts():
    layouts = {}
    ldir = os.path.join(RES, "layout")
    for f in sorted(os.listdir(ldir)):
        if not f.endswith(".xml"):
            continue
        name = f[:-4]
        ids = {}
        for el in ET.parse(os.path.join(ldir, f)).getroot().iter():
            tag = el.tag
            cls = resolve_view_class(tag)
            if cls is None:
                fail("layout %s: unknown view class <%s>" % (name, tag))
                continue
            vid = el.get(A + "id")
            if vid and vid.startswith("@+id/"):
                ids[vid[5:]] = cls
        layouts[name] = ids
    return layouts


def check_ids(layouts):
    print("[2] view ids and casts")
    uses = {
        "MainActivity.java": ["activity_main", "dialog_url"],
        "PlayerActivity.java": ["activity_player"],
        "LibraryAdapter.java": ["item_video", "item_folder"],
    }
    simple_names = {}
    pattern = re.compile(r"(?:\(\s*([A-Z]\w*)\s*\)\s*)?(?:\w+\.)?(?:findViewById|click)\(\s*R\.id\.(\w+)")
    count = 0
    for jf, lnames in uses.items():
        text = open(os.path.join(SRC, jf), encoding="utf-8").read()
        for m in pattern.finditer(text):
            cast, vid = m.group(1), m.group(2)
            count += 1
            found = [(ln, layouts[ln][vid]) for ln in lnames if vid in layouts[ln]]
            if not found:
                fail("%s uses R.id.%s which is not in %s" % (jf, vid, ", ".join(lnames)))
                continue
            if cast:
                cast_fq = simple_names.get(cast) or resolve_view_class(cast)
                if cast_fq is None:
                    fail("%s: unknown cast type %s" % (jf, cast))
                    continue
                simple_names[cast] = cast_fq
                for ln, cls in found:
                    chain = superclass_chain(cls) or []
                    if cast_fq not in chain:
                        fail("%s: R.id.%s is %s in %s but is cast to %s" % (jf, vid, cls, ln, cast_fq))
    # Adapter: item_folder must have every id the adapter reads for all rows
    for vid in ("thumb", "item_title", "item_meta", "thumb_frame"):
        if vid not in layouts["item_folder"]:
            fail("item_folder lacks %s" % vid)
    print("  checked %d lookups" % count)


def string_placeholders(s):
    return sorted(set(re.findall(r"%(\d+\$)?[sdf]", s or "")))


def load_strings(path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if el.tag == "string":
            out[name] = ("string", "".join(el.itertext()))
        elif el.tag == "plurals":
            items = {i.get("quantity"): "".join(i.itertext()) for i in el}
            out[name] = ("plurals", items)
    return out


def check_strings():
    print("[3] translations")
    en = load_strings(os.path.join(RES, "values", "strings.xml"))
    ar = load_strings(os.path.join(RES, "values-ar", "strings.xml"))
    for k in sorted(set(en) - set(ar)):
        fail("Arabic is missing string %s" % k)
    for k in sorted(set(ar) - set(en)):
        fail("Arabic has extra string %s (no English default)" % k)
    for k in sorted(set(en) & set(ar)):
        te, ve = en[k]
        ta, va = ar[k]
        if te != ta:
            fail("%s is %s in English but %s in Arabic" % (k, te, ta))
            continue
        if te == "string":
            if string_placeholders(ve) != string_placeholders(va):
                fail("%s placeholders differ: %s vs %s" % (k, string_placeholders(ve), string_placeholders(va)))
            for txt, lang in ((ve, "en"), (va, "ar")):
                bare = re.findall(r"%[sdf]", txt)
                if len(bare) > 1:
                    fail("%s (%s) uses several non-positional placeholders" % (k, lang))
        else:
            need = {"zero", "one", "two", "few", "many", "other"}
            if not need <= set(va):
                fail("Arabic plurals %s lacks %s" % (k, sorted(need - set(va))))
            if "other" not in ve or "one" not in ve:
                fail("English plurals %s needs one/other" % k)
            allowed = set(string_placeholders(ve["other"]))
            for q, txt in list(ve.items()) + list(va.items()):
                if not set(string_placeholders(txt)) <= allowed:
                    fail("plurals %s/%s has unexpected placeholders" % (k, q))
    print("  %d strings compared" % len(set(en) & set(ar)))


def check_manifest():
    print("[4] manifest components")
    root = ET.parse(os.path.join(ROOT, "AndroidManifest.xml")).getroot()
    pkg = root.get("package")
    app = root.find("application")
    names = [app.get(A + "name")] + [c.get(A + "name") for c in app if c.get(A + "name")]
    for n in names:
        fq = pkg + n if n.startswith(".") else n
        path = os.path.join(CLASSES, *fq.split(".")) + ".class"
        if not os.path.isfile(path):
            fail("manifest names %s but the class does not exist" % fq)
    for c in app:
        if c.tag in ("activity", "receiver", "service", "provider") and c.find("intent-filter") is not None:
            if c.get(A + "exported") is None:
                fail("%s has an intent-filter but no android:exported (required on Android 12+)" % c.get(A + "name"))
        for f in c.iter("data"):
            mt = f.get(A + "mimeType")
            if mt and mt != mt.lower():
                fail("mimeType %s must be lower case for intent matching" % mt)
    print("  %d components" % len(names))


def check_dex_and_apis():
    print("[5] dex contents")
    out = subprocess.run(["dexdump", DEX], capture_output=True, text=True).stdout
    classes = re.findall(r"Class descriptor\s*:\s*'L([^;]+);'", out)
    bad = [c for c in classes if not c.startswith("com/ahmed/noorplayer/")]
    for c in bad:
        fail("unexpected class in dex: " + c)
    print("  %d classes in dex, all in com.ahmed.noorplayer" % len(classes))

    print("[6] framework APIs used")
    refs = set()
    for dirpath, _, files in os.walk(CLASSES):
        for f in files:
            if not f.endswith(".class"):
                continue
            p = subprocess.run(["javap", "-c", "-p", os.path.join(dirpath, f)], capture_output=True, text=True).stdout
            for m in re.finditer(r"// (Method|InterfaceMethod|Field|class)\s+([\w/$]+)[.:]?([\w<>$\"]*)", p):
                owner = m.group(2)
                if owner.startswith("com/ahmed/"):
                    continue
                refs.add((m.group(1), owner.replace("/", "."), m.group(3)))
    hidden_pkgs = ("com.android.internal", "dalvik.system", "sun.", "libcore", "android.os.SystemProperties")
    for kind, owner, member in sorted(refs):
        if owner.startswith(hidden_pkgs):
            fail("uses non-public API %s.%s" % (owner, member))
    owners = sorted({o for _, o, _ in refs})
    print("  %d distinct references across %d framework classes" % (len(refs), len(owners)))
    listing = os.path.join(ROOT, "build", "api_refs.txt")
    with open(listing, "w") as fh:
        for kind, owner, member in sorted(refs, key=lambda r: (r[1], r[2])):
            fh.write("%-15s %s.%s\n" % (kind, owner, member))
    print("  full list written to build/api_refs.txt")


def main():
    print("[1] layout view classes")
    layouts = parse_layouts()
    print("  %d layouts, %d ids" % (len(layouts), sum(len(v) for v in layouts.values())))
    check_ids(layouts)
    check_strings()
    check_manifest()
    check_dex_and_apis()
    print()
    if problems:
        print("FAILED: %d problem(s)" % len(problems))
        return 1
    print("ALL CHECKS PASSED")
    return 0


if __name__ == "__main__":
    sys.exit(main())
