#!/usr/bin/env python3
"""Rewrites Grafana dashboard JSON with one panel per line (HU-40).

A dashboard exported from Grafana takes over a thousand lines, and the PR size check counts JSON.
With one panel per line the file stays short and each changed panel shows up as one line of diff.

    python3 observability/grafana/format_dashboard.py observability/grafana/dashboards/*.json
"""
import json
import sys


def format_dashboard(dashboard):
    panels = dashboard.pop("panels", [])
    head = json.dumps(dashboard, ensure_ascii=False, indent=2)
    lines = [head[:-2] + ","] if dashboard else ["{"]
    lines.append('  "panels": [')
    lines.append(",\n".join("    " + json.dumps(p, ensure_ascii=False) for p in panels))
    lines.append("  ]")
    lines.append("}")
    return "\n".join(lines) + "\n"


def main(paths):
    if not paths:
        sys.exit(__doc__)
    for path in paths:
        with open(path, encoding="utf-8") as source:
            dashboard = json.load(source)
        dashboard.pop("id", None)  # instance-specific; the uid identifies the dashboard
        text = format_dashboard(dashboard)
        json.loads(text)
        with open(path, "w", encoding="utf-8", newline="\n") as target:
            target.write(text)
        print(f"{path}: {text.count(chr(10))} lines")


if __name__ == "__main__":
    main(sys.argv[1:])
