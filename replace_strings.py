import os
import glob
import re
import xml.etree.ElementTree as ET

tr_en_map = {
    "Alt karın aktif!": ("msg_lower_abs_active", "Alt karın aktif!", "Lower abs active!"),
    "Açılıyor...": ("msg_opening", "Açılıyor...", "Opening..."),
    "Ağırlığı göğsüne doğru dik çekme! Hareketi bir kanca gibi kalçana doğru (geriye) yay çizerek yap": ("err_pull_to_chest", "Ağırlığı göğsüne doğru dik çekme! Hareketi bir kanca gibi kalçana doğru (geriye) yay çizerek yap", "Do not pull straight to chest! Arc back toward your hip."),
    "Ağırlığı kulak/omuz hizasına kadar indir": ("err_lower_to_ear", "Ağırlığı kulak/omuz hizasına kadar indir", "Lower weight to ear/shoulder level"),
    "Ağırlığı sadece yukarı çekme, sanki yan duvarlara dokunacakmış gibi uzağa it": ("err_pull_only_up", "Ağırlığı sadece yukarı çekme, sanki yan duvarlara dokunacakmış gibi uzağa it", "Don't just pull up, push away like touching the walls"),
    "Ağırlığı çok fazla düşürüp omuzlarında batma yaratma": ("err_drop_too_low", "Ağırlığı çok fazla düşürüp omuzlarında batma yaratma", "Don't drop too low to avoid shoulder pinching"),
    "Aşağıda kolunu ve omuzunu tam sal, sırt/kanat kasının esnemesine izin ver": ("err_let_shoulder_hang", "Aşağıda kolunu ve omuzunu tam sal, sırt/kanat kasının esnemesine izin ver", "Let arm hang fully at bottom to stretch back"),
    "Bacaklarınızı 90 derece dik tutun": ("err_legs_90_degrees", "Bacaklarınızı 90 derece dik tutun", "Keep legs straight at 90 degrees"),
    "Bacağınız yere çok yakın": ("err_leg_too_close_to_floor", "Bacağınız yere çok yakın", "Leg is too close to the floor"),
    "Bacağınızı daha aşağı indirin": ("err_lower_leg_more", "Bacağınızı daha aşağı indirin", "Lower your leg more"),
    "Başını dik tut": ("err_head_up", "Başını dik tut", "Keep your head up"),
    "Başını nötr tut": ("err_head_neutral", "Başını nötr tut", "Keep head neutral"),
    "Başını çok yukarı diktin, tam ellerinin ortasına bak": ("err_head_too_high", "Başını çok yukarı diktin, tam ellerinin ortasına bak", "Head too high, look right between your hands"),
    "Belinden destek alma, dik dur": ("err_no_momentum_back", "Belinden destek alma, dik dur", "Don't use back momentum, stand tall"),
    "Belini düz tut, karnını sık!": ("err_flat_back_tight_core", "Belini düz tut, karnını sık!", "Keep back flat, tight core!"),
    "Belini sabit tut": ("err_keep_waist_stable", "Belini sabit tut", "Keep your waist stable"),
    "Belinin aşağı doğru çökmesine izin verme": ("err_dont_let_waist_sag", "Belinin aşağı doğru çökmesine izin verme", "Don't let your waist sag"),
    "Belinizi aşağı düşürmeyin": ("err_dont_drop_waist", "Belinizi aşağı düşürmeyin", "Don't drop your waist"),
    "Bent Over Raise için hazır": ("msg_ready_bent_raise", "Bent Over Raise için hazır", "Ready for Bent Over Raise"),
    "Bent Over Row için hazır": ("msg_ready_bent_row", "Bent Over Row için hazır", "Ready for Bent Over Row"),
    "Bileklerini bükme, ön kolunu yere tam dik (dik açılı) tut": ("err_dont_bend_wrists", "Bileklerini bükme, ön kolunu yere tam dik (dik açılı) tut", "Don't bend wrists, keep forearm vertical"),
    "Bileğini düz tut": ("err_keep_wrist_straight", "Bileğini düz tut", "Keep wrist straight"),
    "Boynunu geriye bükme, matına bak": ("err_dont_bend_neck", "Boynunu geriye bükme, matına bak", "Don't bend neck back, look at your mat"),
    "Boynunu çekme": ("err_dont_pull_neck", "Boynunu çekme", "Don't pull your neck"),
    "Daha aşağı in": ("err_go_lower", "Daha aşağı in", "Go lower"),
    "Daha derine in, göğsün yere yaklaşsın": ("err_go_deeper", "Daha derine in, göğsün yere yaklaşsın", "Go deeper, chest closer to ground"),
    "Dirsek açını daralt": ("err_narrow_elbow_angle", "Dirsek açını daralt", "Narrow your elbow angle"),
    "Dirseklerin tam omuzlarının altında olmalı": ("err_elbows_under_shoulders", "Dirseklerin tam omuzlarının altında olmalı", "Elbows must be right under shoulders"),
    "Dirseklerini geriye çek": ("err_pull_elbows_back", "Dirseklerini geriye çek", "Pull elbows back"),
    "Dirseklerini sabit tut": ("err_keep_elbows_stable", "Dirseklerini sabit tut", "Keep elbows stable"),
    "Dirseklerini sabitle, savurma yapma": ("err_lock_elbows", "Dirseklerini sabitle, savurma yapma", "Lock elbows, no swinging"),
    "Dirseklerini vücuduna yaklaştır (Ok ucu formu)": ("err_elbows_close", "Dirseklerini vücuduna yaklaştır (Ok ucu formu)", "Tuck elbows closer (Arrow shape)"),
    "Dizlerini bükme, karın ve kalça kaslarını sık": ("err_dont_bend_knees", "Dizlerini bükme, karın ve kalça kaslarını sık", "Don't bend knees, squeeze core and glutes"),
    "Dizlerini dışarı doğru yönlendir": ("err_knees_out", "Dizlerini dışarı doğru yönlendir", "Point knees outward"),
    "Dumbbell'ı omzuna kadar çek": ("err_pull_dumbbell_to_shoulder", "Dumbbell'ı omzuna kadar çek", "Pull dumbbell up to your shoulder"),
    "Esneme hissederek yavaşça sal...": ("msg_stretch_slowly", "Esneme hissederek yavaşça sal...", "Feel the stretch and lower slowly..."),
    "Formu düzeltin": ("err_fix_form", "Formu düzeltin", "Fix your form"),
    "Formun çok iyi, bozmadan bekle!": ("msg_great_form_hold", "Formun çok iyi, bozmadan bekle!", "Great form, hold it!"),
    "Formunuz düzgün!": ("msg_form_correct", "Formunuz düzgün!", "Your form is correct!"),
    "Geriye doğru aç": ("msg_open_back", "Geriye doğru aç", "Open backward"),
    "Geriye çek": ("msg_pull_back", "Geriye çek", "Pull back"),
    "Gövdeni yere daha paralel tut (masa gibi), çok dik durarak omuza yük bindirme": ("err_torso_parallel", "Gövdeni yere daha paralel tut (masa gibi), çok dik durarak omuza yük bindirme", "Keep torso more parallel to floor, avoid upright stress"),
    "Göğsüne veya ayaklarına bakma, boynunu düz tut": ("err_dont_look_at_chest", "Göğsüne veya ayaklarına bakma, boynunu düz tut", "Don't look at chest or feet, keep neck straight"),
    "Göğsünü daha dik tut": ("err_chest_up", "Göğsünü daha dik tut", "Keep your chest up"),
    "Güçlü bir şekilde yukarı it!": ("msg_push_strong", "Güçlü bir şekilde yukarı it!", "Push up strong!"),
    "Hammer Curl için hazır": ("msg_ready_hammer_curl", "Hammer Curl için hazır", "Ready for Hammer Curl"),
    "Hareketi tamamla": ("err_complete_movement", "Hareketi tamamla", "Complete the movement"),
    "Harika form!": ("msg_great_form", "Harika form!", "Great form!"),
    "Harika ritim!": ("msg_great_rhythm", "Harika ritim!", "Great rhythm!"),
    "Harika rotasyon!": ("msg_great_rotation", "Harika rotasyon!", "Great rotation!"),
    "Harika yükseliş!": ("msg_great_rise", "Harika yükseliş!", "Great rise!"),
    "Harika! İnin": ("msg_great_go_down", "Harika! İnin", "Great! Go down"),
    "Harika! Çıkın": ("msg_great_go_up", "Harika! Çıkın", "Great! Go up"),
    "Hazır": ("msg_ready", "Hazır", "Ready"),
    "Hazır pozisyon": ("msg_ready_position", "Hazır pozisyon", "Ready position"),
    "Hazır, kaldırın": ("msg_ready_lift", "Hazır, kaldırın", "Ready, lift"),
    "Hazır... Dirseği tavana doğru çek!": ("msg_ready_pull_elbow", "Hazır... Dirseği tavana doğru çek!", "Ready... Pull elbow to ceiling!"),
    "Hazır... Yana ve uzağa it!": ("msg_ready_push_away", "Hazır... Yana ve uzağa it!", "Ready... Push sideways and away!"),
    "Kaldır...": ("msg_lift", "Kaldır...", "Lift..."),
    "Kaldırın": ("msg_lift_cmd", "Kaldırın", "Lift"),
    "Kalkıyorsunuz...": ("msg_rising", "Kalkıyorsunuz...", "Rising..."),
    "Kalçanı çok kaldırma, vücudun düz olsun": ("err_hips_too_high", "Kalçanı çok kaldırma, vücudun düz olsun", "Don't raise hips too high, keep body straight"),
    "Kalçanı çok yukarı kaldırma, dümdüz bir çizgi ol": ("err_hips_straight_line", "Kalçanı çok yukarı kaldırma, dümdüz bir çizgi ol", "Don't raise hips, stay in a straight line"),
    "Kalçayı çok yukarı kaldırmayın": ("err_dont_raise_hips", "Kalçayı çok yukarı kaldırmayın", "Don't raise hips too much"),
    "Kanatlarını (lats) tam sık!": ("msg_squeeze_lats", "Kanatlarını (lats) tam sık!", "Squeeze your lats!"),
    "Karşıdaki aynaya bakmak için boynunu kırma, başın sırtınla aynı hizada nötr kalsın": ("err_neck_alignment", "Karşıdaki aynaya bakmak için boynunu kırma, başın sırtınla aynı hizada nötr kalsın", "Don't break neck to look up, keep it neutral"),
    "Kasılmayı hisset ve dön": ("msg_feel_contraction", "Kasılmayı hisset ve dön", "Feel the contraction and return"),
    "Kişi bulunamadı": ("err_person_not_found", "Kişi bulunamadı", "Person not found"),
    "Kollar tam görünmüyor!": ("err_arms_not_visible", "Kollar tam görünmüyor!", "Arms not fully visible!"),
    "Kolları uzatın (Başlangıç)": ("msg_extend_arms", "Kolları uzatın (Başlangıç)", "Extend arms (Start)"),
    "Kollarını dümdüz kilitleme, dirseklerini çok hafif bükülü tut": ("err_dont_lock_arms", "Kollarını dümdüz kilitleme, dirseklerini çok hafif bükülü tut", "Don't lock out elbows, keep slight bend"),
    "Kollarını tam uzat": ("err_fully_extend_arms", "Kollarını tam uzat", "Fully extend arms"),
    "Kollarını vücudun tam yanından değil, 20-30 derece daha önünden aç (Skapular uçuş)": ("err_scapular_plane", "Kollarını vücudun tam yanından değil, 20-30 derece daha önünden aç (Skapular uçuş)", "Open arms slightly forward, 20-30 degrees (Scapular plane)"),
    "Kolunu tamamen aç": ("err_open_arm_fully", "Kolunu tamamen aç", "Open arm fully"),
    "Kontrollü bir şekilde indir": ("msg_lower_control", "Kontrollü bir şekilde indir", "Lower with control"),
    "Kontrollü dönüş...": ("msg_controlled_return", "Kontrollü dönüş...", "Controlled return..."),
    "Kontrollü indir": ("msg_lower_controlled", "Kontrollü indir", "Lower controlled"),
    "Kontrollü inin": ("msg_go_down_controlled", "Kontrollü inin", "Go down controlled"),
    "Kontrollü iniyor...": ("msg_going_down_controlled", "Kontrollü iniyor...", "Going down controlled..."),
    "Kontrollü iniş...": ("msg_controlled_descent", "Kontrollü iniş...", "Controlled descent..."),
    "Kürek kemiklerini sık": ("msg_squeeze_shoulder_blades", "Kürek kemiklerini sık", "Squeeze shoulder blades"),
    "Momentum kullanma": ("err_no_momentum", "Momentum kullanma", "Don't use momentum"),
    "Oblikleri sıkıştırın!": ("msg_squeeze_obliques", "Oblikleri sıkıştırın!", "Squeeze the obliques!"),
    "Omuz hizasını geçme": ("err_dont_pass_shoulder", "Omuz hizasını geçme", "Don't pass shoulder level"),
    "Omuz presi için hazır": ("msg_ready_shoulder_press", "Omuz presi için hazır", "Ready for Shoulder Press"),
    "Omuzdan aç, dirseklerini çok bükme": ("err_open_from_shoulder", "Omuzdan aç, dirseklerini çok bükme", "Open from shoulder, don't bend elbows much"),
    "Omuzdan kaldır": ("err_lift_from_shoulder", "Omuzdan kaldır", "Lift from shoulder"),
    "Omuzlar görünmüyor": ("err_shoulders_not_visible", "Omuzlar görünmüyor", "Shoulders not visible"),
    "Pozisyonunu koru": ("msg_hold_position", "Pozisyonunu koru", "Hold position"),
    "Serçe parmağın baş parmağına göre hafif yukarıda olsun. Avuç içi yeri göstersin": ("err_pinky_up", "Serçe parmağın baş parmağına göre hafif yukarıda olsun. Avuç içi yeri göstersin", "Pinky slightly up, palms facing down"),
    "Squat yapmaya başlayın": ("msg_start_squat", "Squat yapmaya başlayın", "Start squatting"),
    "Süper form!": ("msg_super_form", "Süper form!", "Super form!"),
    "Sürahiden su boşaltır gibi bileğini aşırı bükme, omuz sıkışabilir": ("err_dont_pour_pitcher", "Sürahiden su boşaltır gibi bileğini aşırı bükme, omuz sıkışabilir", "Don't pour pitcher motion with wrist, might pinch shoulder"),
    "Sırt çok geride": ("err_back_too_far", "Sırt çok geride", "Back is too far back"),
    "Sırtını sıkıştır": ("msg_squeeze_back", "Sırtını sıkıştır", "Squeeze your back"),
    "Sırtını tam sıkıştıramıyorsun (yarı yolda), dirseğini tavana doğru daha sert çek": ("err_squeeze_back_harder", "Sırtını tam sıkıştıramıyorsun (yarı yolda), dirseğini tavana doğru daha sert çek", "Not squeezing back fully, pull elbow harder to ceiling"),
    "Sırtınız kambur duruyor, dikleşin": ("err_hunchback", "Sırtınız kambur duruyor, dikleşin", "Back is hunched, straighten up"),
    "TOP": ("msg_top", "TOP", "TOP"),
    "Takip zayıf — kameraya tam görünün": ("err_poor_tracking_camera", "Takip zayıf — kameraya tam görünün", "Poor tracking — stay fully in camera"),
    "Takip zayıf — tam görünün": ("err_poor_tracking_full", "Takip zayıf — tam görünün", "Poor tracking — show full body"),
    "Takip zayıf": ("err_poor_tracking", "Takip zayıf", "Poor tracking"),
    "Tam açılma yap": ("err_full_extension", "Tam açılma yap", "Do full extension"),
    "Triceps Extension için hazır": ("msg_ready_triceps_ext", "Triceps Extension için hazır", "Ready for Triceps Extension"),
    "Triceps Kickback için hazır": ("msg_ready_triceps_kickback", "Triceps Kickback için hazır", "Ready for Triceps Kickback"),
    "Uzağa it": ("msg_push_away", "Uzağa it", "Push away"),
    "Vücut net görünmüyor": ("err_body_not_clear", "Vücut net görünmüyor", "Body is not clear"),
    "Yana aç": ("msg_open_side", "Yana aç", "Open to the side"),
    "Yatay pozisyon — hazır": ("msg_horizontal_ready", "Yatay pozisyon — hazır", "Horizontal position — ready"),
    "Yavaş ve kontrollü yap": ("msg_slow_controlled", "Yavaş ve kontrollü yap", "Do it slow and controlled"),
    "Yavaşça indir": ("msg_lower_slowly", "Yavaşça indir", "Lower slowly"),
    "Yavaşça sal": ("msg_release_slowly", "Yavaşça sal", "Release slowly"),
    "Yukarı it": ("msg_push_up", "Yukarı it", "Push up"),
    "Yukarıdasınız": ("msg_you_are_up", "Yukarıdasınız", "You are up"),
    "Zirvede hisset, yavaşça indir": ("msg_feel_peak", "Zirvede hisset, yavaşça indir", "Feel at peak, lower slowly"),
    "belirsiz": ("msg_uncertain", "belirsiz", "uncertain"),
    "sıkışma": ("msg_squeeze", "sıkışma", "squeeze"),
    "çek...": ("msg_pull", "çek...", "pull..."),
    "çekiyorsun...": ("msg_pulling", "çekiyorsun...", "pulling..."),
    "çekiyorsunuz...": ("msg_pulling_you", "çekiyorsunuz...", "pulling..."),
    "çok iyi! Çıkın": ("msg_very_good_up", "çok iyi! Çıkın", "Very good! Go up"),
    "Öne eğilmeyi koru": ("err_keep_forward_lean", "Öne eğilmeyi koru", "Keep the forward lean"),
    "Öne eğilmeyi koru ve sırtını düz tut": ("err_lean_flat_back", "Öne eğilmeyi koru ve sırtını düz tut", "Lean forward and keep back flat"),
    "Üst kolunu sabit tut": ("err_keep_upper_arm_stable", "Üst kolunu sabit tut", "Keep upper arm stable"),
    "İniyor...": ("msg_going_down", "İniyor...", "Going down..."),
    "İniyorsunuz...": ("msg_going_down_you", "İniyorsunuz...", "Going down..."),
    "İtiyorsun...": ("msg_pushing", "İtiyorsun...", "Pushing..."),
    "İyi kasıldın! Yavaşça indir": ("msg_good_contraction", "İyi kasıldın! Yavaşça indir", "Good contraction! Lower slowly"),
    "Şınav pozisyonuna geçin": ("msg_pushup_position", "Şınav pozisyonuna geçin", "Get in pushup position"),
    "Mekik pozisyonu - hazır": ("msg_situp_position", "Mekik pozisyonu - hazır", "Situp position - ready"),
    "Şınav için hazır": ("msg_ready_pushup", "Şınav için hazır", "Ready for pushup")
}

# Add any additional ones we find in files to avoid key error
def get_key(text):
    if text in tr_en_map:
        return tr_en_map[text][0]
    else:
        # Fallback key generation
        import re
        safe = re.sub(r'[^a-zA-Z0-9]', '_', text.lower())[:20]
        key = "msg_auto_" + safe
        tr_en_map[text] = (key, text, text)
        return key

directory = r"app\src\main\java\com\example\exerciseformanalyzer\analysis\evaluator"
files = glob.glob(os.path.join(directory, "*.kt"))

for filepath in files:
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    # Find all strings
    matches = re.findall(r'"([^"]+)"', content)
    # Sort by length descending so we replace longest first
    matches = sorted(list(set(matches)), key=len, reverse=True)

    for match in matches:
        if match == "": continue
        if "com.example" in match: continue
        key = get_key(match)
        # Replace only actual literal strings "match"
        content = content.replace(f'"{match}"', f'stringProvider.getString(R.string.{key})')

    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(content)

# Now write to strings.xml
def append_strings_xml(path, lang="tr"):
    tree = ET.parse(path)
    root = tree.getroot()

    existing_keys = [child.attrib.get("name") for child in root]

    for tr_text, data in tr_en_map.items():
        key, tr_val, en_val = data
        if key not in existing_keys:
            new_elem = ET.Element("string", name=key)
            new_elem.text = tr_val if lang == "tr" else en_val
            root.append(new_elem)

    # pretty formatting
    ET.indent(tree, space="    ", level=0)
    tree.write(path, encoding='utf-8', xml_declaration=True)

append_strings_xml(r"app\src\main\res\values\strings.xml", "tr")
append_strings_xml(r"app\src\main\res\values-en\strings.xml", "en")

print("Evaluators and strings.xml updated.")
