#!/usr/bin/env python3
"""Add a stable sync `id` to every entry in the bundled vocab asset's "flat" list.

Run ONCE (already run for this commit; re-running is safe and idempotent — see below — but
there's normally no reason to). Phase 3 (cloud sync) needs a per-word identity that survives
reinstalls and is never reused: the Room row id is per-install, and the raw headword collides
(e.g. "admit" appears twice in the English list with different definitions), so neither can be
the sync document key. This generates one instead: a short hash of
track|unit|word|definition, with a numeric suffix appended on a hash collision.

Idempotent: an entry that already has an "id" keeps it unchanged (recomputing would be a no-op
for unchanged entries anyway, since the hash is deterministic, but skipping makes that explicit
and makes a partial/interrupted previous run safe to resume).

Usage:
    python tools/generate_stable_ids.py
Writes app/src/main/assets/psychometric_vocab_CLEAN.json in place (UTF-8 with BOM, matching the
file's existing encoding).
"""
import hashlib
import json
import sys
from pathlib import Path

ASSET_PATH = Path(__file__).resolve().parent.parent / "app" / "src" / "main" / "assets" / "psychometric_vocab_CLEAN.json"
HASH_LENGTH = 12  # hex chars; 48 bits — collisions are only ever handled defensively, not expected


def track_for(language: str) -> str:
    return "hebrew" if language == "עברית" else "english"


def base_id(entry: dict) -> str:
    key = f"{track_for(entry['language'])}|{entry['unit']}|{entry['word']}|{entry['definition']}"
    return hashlib.sha256(key.encode("utf-8")).hexdigest()[:HASH_LENGTH]


def main() -> None:
    with ASSET_PATH.open(encoding="utf-8-sig") as f:
        data = json.load(f)

    flat = data["flat"]
    seen_ids: set[str] = set()
    collisions = 0
    newly_assigned = 0

    for entry in flat:
        existing = entry.get("id")
        if existing:
            seen_ids.add(existing)

    for entry in flat:
        if entry.get("id"):
            continue  # already has a stable id from a previous run; never reassign
        candidate = base_id(entry)
        if candidate in seen_ids:
            collisions += 1
            suffix = 2
            while f"{candidate}-{suffix}" in seen_ids:
                suffix += 1
            candidate = f"{candidate}-{suffix}"
        seen_ids.add(candidate)
        entry["id"] = candidate
        newly_assigned += 1

    assert len(seen_ids) == len(flat), "stable id count must match entry count"

    with ASSET_PATH.open("w", encoding="utf-8-sig") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

    print(f"{len(flat)} entries total, {newly_assigned} id(s) newly assigned, {collisions} hash collision(s) resolved with a suffix.")
    if collisions:
        print("NOTE: collisions found — see the printed count above. Re-run with -v for details if needed.", file=sys.stderr)


if __name__ == "__main__":
    main()
