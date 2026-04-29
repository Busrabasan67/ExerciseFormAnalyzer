package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate

@Composable
fun FormScoreLineChart(
    data: List<Pair<Float, Float>>,
    modifier: Modifier = Modifier.fillMaxWidth().height(250.dp)
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary.toArgb()

    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                description.isEnabled = false
                legend.textColor = textColor
                xAxis.textColor = textColor
                axisLeft.textColor = textColor
                axisRight.isEnabled = false
                xAxis.setDrawGridLines(false)
            }
        },
        modifier = modifier,
        update = { chart ->
            val entries = data.map { Entry(it.first, it.second) }
            val dataSet = LineDataSet(entries, "Form Skoru Trendi").apply {
                color = primaryColor
                setCircleColor(primaryColor)
                lineWidth = 2f
                valueTextColor = textColor
                setDrawFilled(true)
                fillColor = primaryColor
                mode = LineDataSet.Mode.CUBIC_BEZIER
            }
            chart.data = LineData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
fun CalorieBarChart(
    data: List<Pair<String, Float>>,
    modifier: Modifier = Modifier.fillMaxWidth().height(250.dp)
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primaryColor = MaterialTheme.colorScheme.secondary.toArgb()

    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.textColor = textColor
                xAxis.textColor = textColor
                axisLeft.textColor = textColor
                axisRight.isEnabled = false
                xAxis.setDrawGridLines(false)
            }
        },
        modifier = modifier,
        update = { chart ->
            val entries = data.mapIndexed { index, pair -> BarEntry(index.toFloat(), pair.second) }
            val labels = data.map { it.first }
            
            val dataSet = BarDataSet(entries, "Günlük Kalori").apply {
                color = primaryColor
                valueTextColor = textColor
            }
            
            chart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            chart.xAxis.position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
            chart.xAxis.granularity = 1f
            
            chart.data = BarData(dataSet)
            chart.invalidate()
        }
    )
}

@Composable
fun TaskPieChart(
    stats: Map<String, Int>,
    modifier: Modifier = Modifier.fillMaxWidth().height(250.dp)
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val primary = MaterialTheme.colorScheme.primary.toArgb()
    val error = MaterialTheme.colorScheme.error.toArgb()
    val tertiary = MaterialTheme.colorScheme.tertiary.toArgb()

    AndroidView(
        factory = { context ->
            PieChart(context).apply {
                description.isEnabled = false
                legend.textColor = textColor
                setHoleColor(0) // Transparent
                setEntryLabelColor(textColor)
            }
        },
        modifier = modifier,
        update = { chart ->
            val entries = stats.map { (status, count) ->
                val label = when(status) {
                    "COMPLETED" -> "Tamamlandı"
                    "IN_PROGRESS" -> "Devam Ediyor"
                    "MISSED" -> "Kaçırıldı"
                    "PENDING", "ASSIGNED" -> "Bekliyor"
                    else -> status
                }
                PieEntry(count.toFloat(), label)
            }
            
            val dataSet = PieDataSet(entries, "Görev Dağılımı").apply {
                colors = listOf(primary, error, tertiary)
                valueTextColor = textColor
                valueTextSize = 12f
            }
            
            chart.data = PieData(dataSet)
            chart.invalidate()
        }
    )
}
