#!/usr/bin/env python3
"""Regenerates app/src/main/java/com/nexflow/ui/common/FlowIconCatalog.kt.

The flow icon picker offers every `Icons.Outlined.*` icon from
androidx.compose.material:material-icons-core + material-icons-extended. Those artifacts ship
no resources — each icon is a Kotlin extension property that must be imported and referenced
by name — so the catalog is generated from the icon class names inside the resolved .aars.
Both artifacts publish into the same `icons.outlined` package (core holds the ~40 common icons
such as PlayArrow, extended holds the rest), so the two name sets are merged.

Usage:
    python3 tools/generate_flow_icon_catalog.py [path/to/icons.aar ...]

With no argument the newest core + extended aars in the Gradle cache are used.
"""

from __future__ import annotations

import io
import re
import sys
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
OUT = REPO / "app/src/main/java/com/nexflow/ui/common/FlowIconCatalog.kt"
GRADLE_CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1/androidx.compose.material"
ICON_CLASS = re.compile(r"^androidx/compose/material/icons/outlined/([A-Za-z0-9_]+)Kt\.class$")
AUTO_MIRRORED_CLASS = re.compile(
    r"^androidx/compose/material/icons/automirrored/outlined/([A-Za-z0-9_]+)Kt\.class$"
)

# Chunking exists only to keep every generated method well under the 64KB bytecode limit;
# it has no effect on the keys themselves.
CHUNK_SIZE = 200


def find_aars() -> list[Path]:
    found = []
    for artifact in ("material-icons-core", "material-icons-extended"):
        candidates = sorted(
            c for c in GRADLE_CACHE.rglob(f"{artifact}-*.aar") if "sources" not in c.name
        )
        if not candidates:
            sys.exit(f"no {artifact} aar under {GRADLE_CACHE}; run a build first")
        found.append(candidates[-1])
    return found


def icon_names(aars: list[Path]) -> list[tuple[str, bool]]:
    """(icon name, is auto-mirrored), sorted by key.

    An icon that has an `Icons.AutoMirrored.Outlined` variant is taken from there — the plain
    `Icons.Outlined` property for those is deprecated and does not mirror in RTL layouts.
    """
    names: set[str] = set()
    mirrored: set[str] = set()
    for aar in aars:
        with zipfile.ZipFile(aar) as outer:
            data = outer.read("classes.jar")
        with zipfile.ZipFile(io.BytesIO(data)) as jar:
            for entry in jar.namelist():
                if m := ICON_CLASS.match(entry):
                    names.add(m.group(1))
                elif m := AUTO_MIRRORED_CLASS.match(entry):
                    mirrored.add(m.group(1))
    return sorted(((n, n in mirrored) for n in names | mirrored), key=lambda e: key_of(e[0]))


def key_of(name: str) -> str:
    """`DirectionsCar` -> `directions_car`, `_18UpRating` -> `18_up_rating`."""
    stripped = name.lstrip("_")
    out = re.sub(r"(?<=[a-z0-9])(?=[A-Z])", "_", stripped)
    return out.lower()


def chunks(items: list, size: int) -> list[list]:
    return [items[i:i + size] for i in range(0, len(items), size)]


def render(names: list[tuple[str, bool]]) -> str:
    keys = [key_of(n) for n, _ in names]
    duplicates = {k for k in keys if keys.count(k) > 1}
    if duplicates:
        sys.exit(f"key collision: {sorted(duplicates)}")

    groups = chunks([(k, n, auto) for k, (n, auto) in zip(keys, names)], CHUNK_SIZE)
    lines: list[str] = []
    lines.append("/*")
    lines.append(" * Copyright 2026 NexFlow Contributors")
    lines.append(" *")
    lines.append(' * Licensed under the Apache License, Version 2.0 (the "License");')
    lines.append(" * you may not use this file except in compliance with the License.")
    lines.append(" * You may obtain a copy of the License at")
    lines.append(" *")
    lines.append(" *     https://www.apache.org/licenses/LICENSE-2.0")
    lines.append(" *")
    lines.append(" * Unless required by applicable law or agreed to in writing, software")
    lines.append(' * distributed under the License is distributed on an "AS IS" BASIS,')
    lines.append(" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.")
    lines.append(" * See the License for the specific language governing permissions and")
    lines.append(" * limitations under the License.")
    lines.append(" */")
    lines.append("// GENERATED FILE — do not edit by hand.")
    lines.append("// Regenerate with: python3 tools/generate_flow_icon_catalog.py")
    lines.append("@file:Suppress(\"LongMethod\", \"CyclomaticComplexMethod\", \"MaximumLineLength\")")
    lines.append("")
    lines.append("package com.nexflow.ui.common")
    lines.append("")
    lines.append("import androidx.compose.material.icons.Icons")
    for name, auto in names:
        package = "automirrored.outlined" if auto else "outlined"
        lines.append(f"import androidx.compose.material.icons.{package}.{name}")
    lines.append("import androidx.compose.ui.graphics.vector.ImageVector")
    lines.append("")
    lines.append("/**")
    lines.append(" * The full `Icons.Outlined.*` set (RTL-sensitive icons taken from")
    lines.append(" * `Icons.AutoMirrored.Outlined`), addressed by a stable snake_case key")
    lines.append(" * (`DirectionsCar` -> `directions_car`).")
    lines.append(" *")
    lines.append(" * Keys are persisted in flows and in exported JSON: an icon may be added, but a key")
    lines.append(" * must never be renamed or removed. [vector] resolves lazily — each `Icons.Outlined.X`")
    lines.append(" * property builds its ImageVector on first access, so only the icons actually drawn")
    lines.append(" * are ever constructed.")
    lines.append(" */")
    lines.append("internal object FlowIconCatalog {")
    lines.append("")
    lines.append(f"    /** All {len(keys)} keys, ordered by key. */")
    lines.append(f"    val keys: List<String> = buildList({len(keys)}) {{")
    for index in range(len(groups)):
        lines.append(f"        addAll(keys{index}())")
    lines.append("    }")
    lines.append("")
    lines.append("    /** null when the key is unknown (an alias or an icon from an older release). */")
    lines.append("    fun vector(key: String): ImageVector? =")
    lines.append("        " + " ?:\n            ".join(f"icons{i}(key)" for i in range(len(groups))))
    for index, group in enumerate(groups):
        lines.append("")
        lines.append(f"    private fun keys{index}(): List<String> = listOf(")
        for key, _, _auto in group:
            lines.append(f'        "{key}",')
        lines.append("    )")
    for index, group in enumerate(groups):
        lines.append("")
        lines.append(f"    private fun icons{index}(key: String): ImageVector? = when (key) {{")
        for key, name, auto in group:
            owner = "Icons.AutoMirrored.Outlined" if auto else "Icons.Outlined"
            lines.append(f'        "{key}" -> {owner}.{name}')
        lines.append("        else -> null")
        lines.append("    }")
    lines.append("}")
    lines.append("")
    return "\n".join(lines)


def main() -> None:
    aars = [Path(a) for a in sys.argv[1:]] or find_aars()
    names = icon_names(aars)
    OUT.write_text(render(names))
    sources = ", ".join(a.name for a in aars)
    print(f"{OUT.relative_to(REPO)}: {len(names)} icons from {sources}")


if __name__ == "__main__":
    main()
