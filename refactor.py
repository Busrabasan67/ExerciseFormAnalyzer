import os
import glob

directory = r"c:\Users\Kadir\Desktop\ExerciseFormAnalyzer\app\src\main\java\com\example\exerciseformanalyzer\analysis\evaluator"
files = glob.glob(os.path.join(directory, "*.kt"))

for filepath in files:
    if "ExerciseEvaluator.kt" in filepath:
        continue
    
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    # 1. Add import
    if "import com.example.exerciseformanalyzer.util.StringProvider" not in content:
        # find last import
        import_index = content.rfind("import ")
        if import_index != -1:
            end_of_line = content.find("\n", import_index)
            content = content[:end_of_line] + "\nimport com.example.exerciseformanalyzer.util.StringProvider\nimport com.example.exerciseformanalyzer.R" + content[end_of_line:]
            
    # 2. Modify class declaration
    filename = os.path.basename(filepath)
    classname = filename.replace(".kt", "")
    old_class_decl = f"class {classname} : ExerciseEvaluator"
    old_class_decl2 = f"class {classname}: ExerciseEvaluator"
    
    new_class_decl = f"class {classname}(private val stringProvider: StringProvider) : ExerciseEvaluator"
    
    content = content.replace(old_class_decl, new_class_decl)
    content = content.replace(old_class_decl2, new_class_decl)
    
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)
        
print("Refactoring complete.")
