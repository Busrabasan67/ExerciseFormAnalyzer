package com.example.exerciseformanalyzer.ui.components

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.example.exerciseformanalyzer.R

@Composable
fun LogoutConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.logout_confirm_title)) },
        text = { Text(text = stringResource(R.string.logout_confirm_message)) },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text(text = stringResource(R.string.yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.no))
            }
        }
    )
}
