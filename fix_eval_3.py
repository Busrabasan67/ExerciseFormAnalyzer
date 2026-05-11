import glob
import re

files = glob.glob(r'app\src\main\java\com\example\exerciseformanalyzer\analysis\evaluator\*.kt')

pattern1 = r'else -> "stringProvider\.getString\(R\.string\.msg_auto_.*?"Takip zayıf(?: — tam görünün)?(?: — kameraya tam görünün)?", confidence = 0\.2f\n\s*\)'

replacement1 = """else -> ""
    }

    private fun poorTrackingFeedback() = FormFeedback(
        isCorrect = false, score = 0, primaryError = null,
        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking), confidence = 0.2f
    )"""

for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    if re.search(pattern1, content):
        new_content = re.sub(pattern1, replacement1, content, flags=re.DOTALL)
        with open(f, 'w', encoding='utf-8') as file:
            file.write(new_content)
        print(f"Fixed {f}")
