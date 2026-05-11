import re

paths = [r'app\src\main\res\values\strings.xml', r'app\src\main\res\values-en\strings.xml']

for p in paths:
    with open(p, 'r', encoding='utf-8') as f:
        content = f.read()
    
    def repl(m):
        inner = m.group(2)
        # first unescape to prevent double escaping
        inner = inner.replace("\\'", "'").replace("'", "\\'")
        return '<string name="' + m.group(1) + '">' + inner + '</string>'
    
    content = re.sub(r'<string name="([^"]+)">(.*?)</string>', repl, content)
    
    with open(p, 'w', encoding='utf-8') as f:
        f.write(content)

print("Fixed apostrophes.")
