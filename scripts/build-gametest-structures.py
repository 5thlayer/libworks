# SPDX-FileCopyrightText: 2026 5thlayer
# SPDX-License-Identifier: MIT

"""Writes the structure the game tests stand on: a stone floor with air above it.

A game test is placed into a structure template, and its helper's coordinates are relative to
the template's corner. The tests place every block they need themselves, so all the template
holds is the floor. It is generated so the committed .nbt file has a source.

Run it from anywhere: python3 scripts/build-gametest-structures.py
"""

import gzip
import io
import os
import struct

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")


def _mod_id():
    # The structures live under the mod's own namespace, which gradle.properties names.
    with open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8") as handle:
        for line in handle:
            key, _, value = line.partition("=")
            if key.strip() == "mod_id":
                return value.strip()
    raise SystemExit("gradle.properties names no mod_id")


STRUCTURES = os.path.join(ROOT, "src", "main", "resources", "data", _mod_id(), "structure", "gametest")

DATA_VERSION = 4790  # 26.1.2
# Room for a block and what stands round it: a door or a bed, and the blocks either side.
SIZE = (9, 5, 9)
TEMPLATES = {"platform.nbt": SIZE}

TAG_INT, TAG_STRING, TAG_LIST, TAG_COMPOUND = 3, 8, 9, 10


def _string(out, value):
    data = value.encode("utf-8")
    out.write(struct.pack(">H", len(data)))
    out.write(data)


def _tag_type(value):
    if isinstance(value, int):
        return TAG_INT
    if isinstance(value, str):
        return TAG_STRING
    if isinstance(value, list):
        return TAG_LIST
    return TAG_COMPOUND


def _payload(out, value):
    kind = _tag_type(value)
    if kind == TAG_INT:
        out.write(struct.pack(">i", value))
    elif kind == TAG_STRING:
        _string(out, value)
    elif kind == TAG_LIST:
        element = _tag_type(value[0]) if value else TAG_COMPOUND
        out.write(struct.pack(">bi", element, len(value)))
        for item in value:
            _payload(out, item)
    else:
        for key, item in value.items():
            out.write(struct.pack(">b", _tag_type(item)))
            _string(out, key)
            _payload(out, item)
        out.write(b"\x00")


def platform(size):
    width, _, depth = size
    # Air is left out: the runner clears the test's box before placing the template.
    blocks = [{"pos": [x, 0, z], "state": 0} for x in range(width) for z in range(depth)]
    return {
        "DataVersion": DATA_VERSION,
        "size": list(size),
        "palette": [{"Name": "minecraft:stone"}],
        "blocks": blocks,
        "entities": [],
    }


def main():
    os.makedirs(STRUCTURES, exist_ok=True)
    for name, size in TEMPLATES.items():
        out = io.BytesIO()
        out.write(struct.pack(">b", TAG_COMPOUND))
        _string(out, "")
        _payload(out, platform(size))
        path = os.path.join(STRUCTURES, name)
        # mtime 0, so the same input always writes the same bytes.
        with open(path, "wb") as handle, gzip.GzipFile(fileobj=handle, mode="wb", mtime=0) as zipped:
            zipped.write(out.getvalue())
        print("wrote " + os.path.relpath(path, ROOT))


if __name__ == "__main__":
    main()
