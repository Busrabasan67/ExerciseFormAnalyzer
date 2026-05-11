import os
import glob
import re

directory = r"app\src\main\java\com\example\exerciseformanalyzer\analysis\evaluator"
files = glob.glob(os.path.join(directory, "*.kt"))
strings = set()
for filepath in files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
        found = re.findall(r'"([^"]+)"', content)
        strings.update(found)

for s in sorted(strings):
    print(s)
