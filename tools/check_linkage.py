"""Audit extension DEX references against the exact APK, before making a patch ZIP.

Android/JDK symbols are platform-provided. Everything else must exist in the host
or the extension. Reports are conservative: references in annotations and method
prototypes are retained so missing symbols are not silently ignored.
"""
from __future__ import annotations
import argparse
import json
import hashlib
from pathlib import Path

PLATFORM = ("Ljava/", "Ljavax/", "Landroid/", "Ldalvik/", "Lorg/xml/", "Lorg/w3c/", "Lorg/json/", "Lsun/")
OBJECT_METHODS = {
    "equals(Ljava/lang/Object;)Z", "hashCode()I", "toString()Ljava/lang/String;",
    "getClass()Ljava/lang/Class;", "notify()V", "notifyAll()V",
    "wait()V", "wait(J)V", "wait(JI)V", "clone()Ljava/lang/Object;", "finalize()V",
}
ACTIVITY_METHODS = {"dispatchTouchEvent(Landroid/view/MotionEvent;)Z"}
DIALOG_METHODS = {"show()V"}
VIEW_METHODS = {
    "getWidth()I", "getHeight()I",
    "getResources()Landroid/content/res/Resources;",
    "setImportantForAccessibility(I)V", "setPadding(IIII)V",
    "onAttachedToWindow()V", "onDetachedFromWindow()V",
}
VIEW_GROUP_METHODS = VIEW_METHODS | {
    "dispatchTouchEvent(Landroid/view/MotionEvent;)Z",
    "drawChild(Landroid/graphics/Canvas;Landroid/view/View;J)Z",
}
PLATFORM_INHERITED = {
    "Ljava/lang/Enum;": {"ordinal()I", "name()Ljava/lang/String;"},
    "Ljava/io/IOException;": {"getMessage()Ljava/lang/String;"},
    # Public View, Activity, and Dialog APIs referenced through app-defined subclasses.
    "Landroid/view/View;": VIEW_METHODS,
    "Landroid/view/ViewGroup;": VIEW_GROUP_METHODS,
    "Landroid/app/Activity;": ACTIVITY_METHODS,
    "Landroid/app/Dialog;": DIALOG_METHODS,
}
# Stripped build-time annotations preserved in host class attributes but not packaged.
OPTIONAL_TYPES = {
    "Lcom/squareup/anvil/annotations/ContributesBinding;",
}


def audit(host: dict, extension: dict, refs: dict, allow_duplicates: bool = False) -> dict:
    classes = host | extension

    def platform(t: str) -> bool:
        return t.startswith(PLATFORM)

    def member_exists(owner: str, member: str, kind: str, seen: set | None = None) -> bool:
        seen = set() if seen is None else seen
        if owner in seen:
            return False
        seen.add(owner)
        # Java arrays expose Object.clone; their element type is audited separately.
        if owner.startswith("["):
            return kind == "methods" and member in OBJECT_METHODS
        if platform(owner):
            # Reaching Object does not make every unresolved method valid.
            return kind == "methods" and member in (OBJECT_METHODS | PLATFORM_INHERITED.get(owner, set()))
        cls = classes.get(owner)
        if cls is None:
            return False
        if member in cls[kind]:
            return True
        # Constructors are not inherited.
        if kind == "methods" and member.startswith("<init>("):
            return False
        parents = [cls.get("super"), *cls.get("interfaces", [])]
        return any(parent and member_exists(parent, member, kind, seen) for parent in parents)

    missing_types = []
    for t in refs["types"]:
        t = t.lstrip("[")
        if t.startswith("L") and not platform(t) and t not in classes and t not in OPTIONAL_TYPES:
            missing_types.append(t)
    missing_methods = []
    missing_fields = []
    for kind, missing in (("methods", missing_methods), ("fields", missing_fields)):
        for ref in refs[kind]:
            owner, member = ref.split("->", 1)
            if platform(owner):
                continue
            if not member_exists(owner, member, kind):
                missing.append(ref)
    return {
        "passed": not (missing_types or missing_methods or missing_fields or (not allow_duplicates and host.keys() & extension.keys())),
        "hostClasses": len(host),
        "extensionClasses": len(extension),
        "duplicateClasses": sorted(host.keys() & extension.keys()),
        "missingTypes": sorted(set(missing_types)),
        "missingMethods": missing_methods,
        "missingFields": missing_fields,
    }


def main() -> int:
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--host", required=True, type=Path)
    p.add_argument("--extension", required=True, type=Path)
    p.add_argument("--refs", required=True, type=Path)
    p.add_argument("--report", required=True, type=Path)
    p.add_argument("--dex", type=Path, help="Bind the successful report to these exact extension bytes")
    p.add_argument("--allow-duplicates", action="store_true", help="Allow overlay classes to duplicate host classes")
    args = p.parse_args()
    report = audit(*(json.loads(path.read_text()) for path in (args.host, args.extension, args.refs)), allow_duplicates=args.allow_duplicates)
    if args.dex:
        report["sourceDexSha256"] = hashlib.sha256(args.dex.read_bytes()).hexdigest()
    if report["duplicateClasses"] and not args.allow_duplicates:
        report["passed"] = False
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({k: len(v) if isinstance(v, list) else v for k, v in report.items()}))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
