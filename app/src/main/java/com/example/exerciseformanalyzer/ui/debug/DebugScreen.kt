package com.example.exerciseformanalyzer.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun DebugScreen(viewModel: DebugViewModel = viewModel()) {
    val logText by viewModel.logText.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        Text(
            text = "Faz 1 Doğrulama Ekranı",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Log ekranı
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFE0E0E0))
                .padding(16.dp)
        ) {
            Text(
                text = logText,
                color = Color.Black,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Firebase Auth Register
        Button(
            onClick = { viewModel.testRegister() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("1. Test Firebase Register")
        }

        // 2. Firebase Auth Login
        Button(
            onClick = { viewModel.testLogin() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("2. Test Firebase Login")
        }

        // 3. Firestore User Write
        Button(
            onClick = { viewModel.testFirestoreWrite() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("3. Test Firestore User Write")
        }

        // 4. Firestore User Read
        Button(
            onClick = { viewModel.testFirestoreRead() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("4. Test Firestore User Read")
        }

        // 5. Room Insert
        Button(
            onClick = { viewModel.testRoomInsert() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("5. Test Room Insert")
        }

        // 6. Room Read
        Button(
            onClick = { viewModel.testRoomRead() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("6. Test Room Read")
        }

        // 7. DataStore Save
        Button(
            onClick = { viewModel.testDataStoreSave() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("7. Test DataStore Save")
        }

        // 8. DataStore Read
        Button(
            onClick = { viewModel.testDataStoreRead() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("8. Test DataStore Read")
        }

        // 9. Offline Save
        Button(
            onClick = { viewModel.testOfflineSave() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) {
            Text("9. Test Offline Save")
        }

        // 10. Manual Sync
        Button(
            onClick = { viewModel.testManuelSync() },
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            Text("10. Test Manual Sync")
        }
    }
}
