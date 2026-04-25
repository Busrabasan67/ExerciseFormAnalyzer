package com.example.exerciseformanalyzer.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: DashboardViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val user by viewModel.observeCurrentUser().collectAsState(initial = null)
    val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
    
    var isEditMode by remember { mutableStateOf(false) }
    
    // Düzenleme state'leri
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var ageStr by remember { mutableStateOf("") }
    var weightStr by remember { mutableStateOf("") }
    var diseaseInfo by remember { mutableStateOf("") }
    var hasHernia by remember { mutableStateOf(false) }
    var hasMeniscus by remember { mutableStateOf(false) }
    var activityLevel by remember { mutableStateOf("medium") }
    var goal by remember { mutableStateOf("general_health") }
    var exerciseLevel by remember { mutableStateOf("beginner") }

    // Kullanıcı değiştiğinde ve edit mode kapalıyken state'leri güncelle
    LaunchedEffect(user, isEditMode) {
        if (user != null && !isEditMode) {
            firstName = user?.firstName ?: ""
            lastName = user?.lastName ?: ""
            ageStr = user?.age?.toString() ?: ""
            weightStr = user?.weightKg?.toString() ?: ""
            diseaseInfo = user?.diseaseInfo ?: ""
            hasHernia = user?.hasHernia ?: false
            hasMeniscus = user?.hasMeniscus ?: false
            activityLevel = user?.activityLevel ?: "medium"
            goal = user?.goal ?: "general_health"
            exerciseLevel = user?.exerciseLevel ?: "beginner"
        }
    }

    val currentLanguage = remember {
        val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        if (tags.startsWith("en")) "en" else "tr"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(onClick = {
                            user?.let { u ->
                                val updated = u.copy(
                                    firstName = firstName,
                                    lastName = lastName,
                                    fullName = "$firstName $lastName".trim(),
                                    age = ageStr.toIntOrNull(),
                                    weightKg = weightStr.toFloatOrNull(),
                                    diseaseInfo = diseaseInfo,
                                    hasHernia = hasHernia,
                                    hasMeniscus = hasMeniscus,
                                    activityLevel = activityLevel,
                                    goal = goal,
                                    exerciseLevel = exerciseLevel
                                )
                                viewModel.updateProfile(updated) { success ->
                                    if (success) isEditMode = false
                                }
                            }
                        }) {
                            Text("Kaydet")
                        }
                    } else {
                        TextButton(onClick = { isEditMode = true }) {
                            Text("Düzenle")
                        }
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (user != null) {
                if (isEditMode) {
                    Text("Profili Düzenle", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Ad") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Soyad") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ageStr,
                            onValueChange = { ageStr = it },
                            label = { Text("Yaş") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it },
                            label = { Text("Kilo (kg)") },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text("Hedef", style = MaterialTheme.typography.labelMedium)
                    val goals = listOf("lose_weight", "gain_muscle", "rehab", "general_health")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        goals.forEach { g ->
                            FilterChip(
                                selected = goal == g,
                                onClick = { goal = g },
                                label = { Text(g.replace("_", " ").capitalize()) }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Egzersiz Seviyesi", style = MaterialTheme.typography.labelMedium)
                    val levels = listOf("beginner", "intermediate", "advanced")
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        levels.forEach { l ->
                            FilterChip(
                                selected = exerciseLevel == l,
                                onClick = { exerciseLevel = l },
                                label = { Text(l.capitalize()) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = diseaseInfo,
                        onValueChange = { diseaseInfo = it },
                        label = { Text("Hastalık Bilgisi") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasHernia, onCheckedChange = { hasHernia = it })
                        Text("Fıtık var mı?")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = hasMeniscus, onCheckedChange = { hasMeniscus = it })
                        Text("Menisküs var mı?")
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { isEditMode = false }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) {
                        Text("İptal")
                    }
                } else {
                    // İzleme Modu
                    Text(
                        text = user?.fullName ?: "",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = user?.email ?: "", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = "${stringResource(R.string.role_label)}: ${user?.role}")

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    Text("Ad Soyad: ${user?.firstName ?: ""} ${user?.lastName ?: ""}")
                    Text("${stringResource(R.string.age_label)}: ${user?.age ?: "-"}")
                    Text("${stringResource(R.string.weight_label)}: ${user?.weightKg ?: "-"} ${stringResource(R.string.kg_unit)}")
                    Text("Hastalık: ${user?.diseaseInfo ?: "-"}")
                    Text("Fıtık: ${if (user?.hasHernia == true) "Var" else "Yok"}")
                    Text("Menisküs: ${if (user?.hasMeniscus == true) "Var" else "Yok"}")

                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // --- GAMIFICATION STATS ---
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Seviye", style = MaterialTheme.typography.labelSmall)
                                Text("${user?.level}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("XP", style = MaterialTheme.typography.labelSmall)
                                Text("${user?.xp}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Seri (Gün)", style = MaterialTheme.typography.labelSmall)
                                Text("🔥 ${user?.streak}", style = MaterialTheme.typography.headlineSmall, color = Color(0xFFFF5722))
                            }
                        }
                    }
                }
            } else {
                Text(stringResource(R.string.loading))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // Ayarlar Bölümü (Sadece izleme modunda veya her zaman görünebilir, izleme modunda kalsın)
            if (!isEditMode) {
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(stringResource(R.string.dark_mode))
                    Switch(
                        checked = isDarkMode,
                        onCheckedChange = { mainViewModel.setDarkMode(it) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(stringResource(R.string.language_label), style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = currentLanguage == "tr",
                        onClick = { mainViewModel.setLanguage("tr") },
                        label = { Text(stringResource(R.string.language_turkish)) }
                    )
                    FilterChip(
                        selected = currentLanguage == "en",
                        onClick = { mainViewModel.setLanguage("en") },
                        label = { Text(stringResource(R.string.language_english)) }
                    )
                }
            }
        }
    }
}

@Composable
fun BadgeProgressItem(name: String, current: Int, target: Int) {
    val progress = current.toFloat() / target
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text("$current / $target", style = MaterialTheme.typography.bodySmall)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = progress,
            modifier = Modifier.fillMaxWidth().height(8.dp),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}
