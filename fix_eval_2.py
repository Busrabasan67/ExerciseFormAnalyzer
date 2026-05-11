import glob

files = glob.glob(r'app\src\main\java\com\example\exerciseformanalyzer\analysis\evaluator\*.kt')

old_str = 'else -> "stringProvider.getString(R.string.msg_auto_____________private_)Takip zayıf", confidence = 0.2f\n    )'
new_str = 'else -> ""\n    }\n\n    private fun poorTrackingFeedback() = FormFeedback(\n        isCorrect = false, score = 0, primaryError = null,\n        feedbackMessage = stringProvider.getString(R.string.err_poor_tracking), confidence = 0.2f\n    )'

for f in files:
    with open(f, 'r', encoding='utf-8') as file:
        content = file.read()
    
    if old_str in content:
        new_content = content.replace(old_str, new_str)
        with open(f, 'w', encoding='utf-8') as file:
            file.write(new_content)
        print(f"Fixed {f}")
