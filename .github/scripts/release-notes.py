import json
import re

with open("version.json", encoding="utf-8") as f:
    changelog = json.load(f)["changelog"]

first_block = re.split(r"\r?\n[0-9]+\.[0-9]+\.[0-9]+ [-—]", changelog, maxsplit=1)[0]
out = []
for i, line in enumerate(first_block.strip().split("\n")):
    line = line.strip()
    if not line:
        continue
    if i == 0:
        m = re.match(r"([0-9]+\.[0-9]+\.[0-9]+) [-—] (.+)", line)
        out.append(f"### {m.group(1)} \u2014 {m.group(2)}" if m else f"### {line}")
    else:
        m = re.match(r"([A-Z]+):\s*(.+)", line)
        out.append(f"- **{m.group(1)}:** {m.group(2)}" if m else f"- {line}")
print("\n".join(out))
