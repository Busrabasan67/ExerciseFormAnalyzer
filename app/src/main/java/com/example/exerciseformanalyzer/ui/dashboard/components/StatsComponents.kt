package com.example.exerciseformanalyzer.ui.dashboard.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.exerciseformanalyzer.R
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
    val primaryColor = Color(0xFF00C853).toArgb() // Green

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
            val dataSet = LineDataSet(entries, "Form Score Trend").apply {
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
    val primaryColor = Color(0xFF2E7D32).toArgb() // Deep Green
    val dailyCaloriesLabel = stringResource(R.string.ui_daily_calories)

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

            val dataSet = BarDataSet(entries, dailyCaloriesLabel).apply {
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
    val green = Color(0xFF00C853).toArgb()
    val amber = Color(0xFFFFB300).toArgb()
    val red = Color(0xFFE53935).toArgb()
    val gray = Color.Gray.toArgb()

    // Capture labels in composable scope
    val labelCompleted = stringResource(R.string.ui_completed)
    val labelInProgress = stringResource(R.string.ui_ongoing_tasks)
    val labelMissed = stringResource(R.string.task_status_missed)
    val labelPending = stringResource(R.string.ui_waiting)
    val taskDistLabel = stringResource(R.string.ui_task_distribution)

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
                val label = when (status) {
                    "COMPLETED" -> labelCompleted
                    "IN_PROGRESS" -> labelInProgress
                    "MISSED" -> labelMissed
                    "PENDING", "ASSIGNED" -> labelPending
                    else -> status
                }
                PieEntry(count.toFloat(), label)
            }

            val dataSet = PieDataSet(entries, taskDistLabel).apply {
                colors = listOf(green, amber, red, gray)
                valueTextColor = textColor
                valueTextSize = 13f
            }

            chart.data = PieData(dataSet)
            chart.invalidate()
        }
    )
}
