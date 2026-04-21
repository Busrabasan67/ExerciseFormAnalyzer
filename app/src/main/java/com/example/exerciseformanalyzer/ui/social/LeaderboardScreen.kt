package com.example.exerciseformanalyzer.ui.social

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
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
