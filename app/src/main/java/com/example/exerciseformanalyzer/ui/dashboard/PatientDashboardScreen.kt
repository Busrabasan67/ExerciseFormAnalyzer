package com.example.exerciseformanalyzer.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.exerciseformanalyzer.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PatientDashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToCamera: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToGroups: () -> Unit = {},
    onLogout: () -> Unit
) {
    val tasks by viewModel.observeMyTasks().collectAsState(initial = emptyList())
    val reports by viewModel.observeMyReports().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = stringResource(R.string.patient_dashboard_title),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    ) 
                },
                actions = {
                    TextButton(onClick = onNavigateToGroups) { 
                        Text(
                            stringResource(R.string.groups_title),
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        ) 
                    }
                    TextButton(onClick = onNavigateToProfile) { Text(stringResource(R.string.profile_title)) }
                    TextButton(onClick = onLogout) { Text(stringResource(R.string.logout)) }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToCamera) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.start_exercise))
            }
        }
    ) { paddingVals ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .padding(16.dp)
        ) {
            item {
                Text(stringResource(R.string.my_tasks), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (tasks.isEmpty()) {
                    Text(stringResource(R.string.no_tasks))
                }
            }

            items(tasks) { task ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = task.exerciseName, style = MaterialTheme.typography.bodyLarge)
                        Text(text = "${task.targetReps} ${stringResource(R.string.reps_unit)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                Text(stringResource(R.string.my_reports), style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                if (reports.isEmpty()) {
                    Text(stringResource(R.string.no_reports))
                }
            }

            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    elevation = CardDefaults.cardElevation(2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = "${stringResource(R.string.score_label)}: ${report.score} / ${report.reps} ${stringResource(R.string.reps_unit)}", style = MaterialTheme.typography.bodyLarge)
                        Text(text = "${report.caloriesBurned.toInt()} ${stringResource(R.string.calorie_unit)}", style = MaterialTheme.typography.bodyMedium)
                        if (!report.feedback.isNullOrEmpty()) {
                            // Veritabanına Türkçe "Mükemmel Form" olarak kaydedilmişse bunu lokalize et
                            val feedbackText = if (report.feedback == "Mükemmel Form") {
                                stringResource(R.string.perfect_form)
                            } else {
                                report.feedback
                            }
                            Text(text = feedbackText, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
