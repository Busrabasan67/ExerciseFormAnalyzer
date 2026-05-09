package com.example.exerciseformanalyzer.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.exerciseformanalyzer.model.LeaderboardMetric
import com.example.exerciseformanalyzer.model.LeaderboardPeriod
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.border

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    onNavigateBack: () -> Unit
) {
    val viewModel: LeaderboardViewModel = viewModel()
    val rankings by viewModel.rankings.collectAsState()
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val selectedMetric by viewModel.selectedMetric.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val customRange by viewModel.customDateRange.collectAsState()

    val dateRangePickerState = rememberDateRangePickerState()
    var showDatePicker by remember { mutableStateOf(false) }

    val badgeDefinitions by viewModel.badgeDefinitions.collectAsState()
    val myBadgeProgress by viewModel.myBadgeProgress.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchMyBadges()
    }

    val periods = LeaderboardPeriod.values()
    val metrics = LeaderboardMetric.values()
    val dateFormatter = remember { SimpleDateFormat("dd MMM", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Liderlik Tablosu") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        }
    ) { paddingVals ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
        ) {
            // Periyot Sekmeleri
            ScrollableTabRow(
                selectedTabIndex = selectedPeriod.ordinal,
                edgePadding = 16.dp,
                containerColor = MaterialTheme.colorScheme.surface,
                divider = {}
            ) {
                periods.forEach { period ->
                    Tab(
                        selected = selectedPeriod == period,
                        onClick = { 
                            viewModel.setPeriod(period)
                            if (period == LeaderboardPeriod.CUSTOM) showDatePicker = true
                        },
                        text = { Text(period.displayName) }
                    )
                }
            }

            // Tarih Seçici Diyaloğu
            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                val start = dateRangePickerState.selectedStartDateMillis
                                val end = dateRangePickerState.selectedEndDateMillis
                                if (start != null && end != null) {
                                    viewModel.setCustomRange(start, end)
                                    showDatePicker = false
                                }
                            }
                        ) { Text("Tamam") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("İptal") }
                    }
                ) {
                    DateRangePicker(
                        state = dateRangePickerState,
                        title = { Text("Tarih Aralığı Seçin", modifier = Modifier.padding(16.dp)) },
                        headline = { Text("Liderlik tablosu için tarihleri belirleyin", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 16.dp)) },
                        showModeToggle = false,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Seçili Tarih Aralığı Göstergesi (Sadece Özel sekmesinde)
            if (selectedPeriod == LeaderboardPeriod.CUSTOM) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = customRange?.let {
                                "${dateFormatter.format(Date(it.first))} - ${dateFormatter.format(Date(it.second))}"
                            } ?: "Tarih Seçilmedi",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        TextButton(onClick = { showDatePicker = true }) {
                            Text("Değiştir")
                        }
                    }
                }
            }

            // Ölçüm Filtreleri (Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                metrics.forEach { metric ->
                    FilterChip(
                        selected = selectedMetric == metric,
                        onClick = { viewModel.setMetric(metric) },
                        label = { Text(metric.displayName) }
                    )
                }
            }
            
            // Sadece kazanılmış olan rozetleri filtrele
            val earnedBadges = badgeDefinitions.filter { (id, _) ->
                myBadgeProgress.any { it.badgeId == id && it.isUnlocked }
            }
            
            // Rozetlerim (My Badges) Vitrini
            if (earnedBadges.isNotEmpty()) {
                Text(
                    text = "Rozetlerim",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(earnedBadges) { (id, def) ->
                        val isUnlocked = true // Çünkü filtreledik
                        
                        val colors = listOf(
                            Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA),
                            Color(0xFF5E35B1), Color(0xFF3949AB), Color(0xFF1E88E5),
                            Color(0xFF039BE5), Color(0xFF00ACC1), Color(0xFF00897B),
                            Color(0xFF43A047), Color(0xFFF4511E)
                        )
                        val badgeColor = colors[kotlin.math.abs(id.hashCode()) % colors.size]

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(80.dp)
                        ) {
                            RosetteBadge(
                                iconVector = if (isUnlocked) Icons.Default.EmojiEvents else Icons.Default.Lock,
                                baseColor = badgeColor,
                                isUnlocked = isUnlocked,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            val currentLang = java.util.Locale.getDefault().language
                            val isEn = currentLang == "en"
                            val displayTitle = if (isEn && def.nameEn.isNotBlank()) def.nameEn else def.name
                            
                            Text(
                                text = displayTitle,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isUnlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                maxLines = 2,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider(modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (rankings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Henüz veri yok", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(rankings) { _, entry ->
                        LeaderboardItem(
                            rank = entry.rank,
                            name = entry.fullName,
                            value = when(selectedMetric) {
                                LeaderboardMetric.XP -> "${entry.value.toInt()} XP"
                                LeaderboardMetric.CALORIES -> "${entry.value.toInt()} kcal"
                                LeaderboardMetric.LEVEL -> "Lv ${entry.value.toInt()}"
                                else -> "${entry.value.toInt()}"
                            },
                            isMe = entry.isMe
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LeaderboardItem(rank: Int, name: String, value: String, isMe: Boolean) {
    val bgColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val rankColor = when (rank) {
        1 -> Color(0xFFFFD700) 
        2 -> Color(0xFFC0C0C0) 
        3 -> Color(0xFFCD7F32) 
        else -> MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = bgColor)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#$rank",
                style = MaterialTheme.typography.titleLarge,
                color = rankColor,
                modifier = Modifier.width(44.dp)
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isMe) "$name (Siz)" else name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isMe) FontWeight.Bold else FontWeight.Normal
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun RosetteBadge(
    iconVector: androidx.compose.ui.graphics.vector.ImageVector,
    baseColor: Color,
    isUnlocked: Boolean,
    modifier: Modifier = Modifier
) {
    val displayColor = if (isUnlocked) baseColor else baseColor.copy(alpha = 0.3f)
    val contentColor = if (isUnlocked) baseColor else baseColor.copy(alpha = 0.5f)
    
    Box(modifier = modifier, contentAlignment = Alignment.TopCenter) {
        // Kurdele Kuyrukları (Ribbon tails)
        Canvas(modifier = Modifier.size(50.dp, 70.dp).offset(y = 20.dp)) {
            val path = Path().apply {
                // Sol kuyruk
                moveTo(size.width * 0.15f, 0f)
                lineTo(size.width * 0.15f, size.height)
                lineTo(size.width * 0.35f, size.height * 0.8f) // Kesik uç
                lineTo(size.width * 0.5f, size.height)
                lineTo(size.width * 0.5f, 0f)
                close()
                // Sağ kuyruk
                moveTo(size.width * 0.5f, 0f)
                lineTo(size.width * 0.5f, size.height)
                lineTo(size.width * 0.65f, size.height * 0.8f) // Kesik uç
                lineTo(size.width * 0.85f, size.height)
                lineTo(size.width * 0.85f, 0f)
                close()
            }
            drawPath(path, color = displayColor)
        }
        
        // Ortadaki Yuvarlak (Rosette center)
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(displayColor, androidx.compose.foundation.shape.CircleShape)
                .border(2.dp, Color.White, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color.White, androidx.compose.foundation.shape.CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
