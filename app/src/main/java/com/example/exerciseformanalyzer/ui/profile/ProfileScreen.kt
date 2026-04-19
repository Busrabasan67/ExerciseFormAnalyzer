package com.example.exerciseformanalyzer.ui.profile

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.ui.dashboard.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: DashboardViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val user by viewModel.observeCurrentUser().collectAsState(initial = null)
    val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
    // Gerçek aktif dili AppCompatDelegate'den oku (DataStore değil, bu her zaman doğru)
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back_button))
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
        ) {
            // Kullanıcı bilgileri
            user?.let { u ->
                Text(
                    text = u.fullName,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = u.email, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = "${stringResource(R.string.role_label)}: ${u.role}")

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text("${stringResource(R.string.age_label)}: ${u.age ?: "-"}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("${stringResource(R.string.weight_label)}: ${u.weightKg ?: "-"} ${stringResource(R.string.kg_unit)}")
                Spacer(modifier = Modifier.height(6.dp))
                Text("${stringResource(R.string.height_label)}: ${u.heightCm ?: "-"} ${stringResource(R.string.cm_unit)}")

            } ?: run {
                Text(stringResource(R.string.loading))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // ── AYARLAR BÖLÜMÜ ───────────────────────────────────────────────
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Gece Modu Toggle
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

            // Dil Seçimi
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
