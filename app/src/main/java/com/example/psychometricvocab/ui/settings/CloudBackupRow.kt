package com.example.psychometricvocab.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.data.sync.SyncViewModel
import com.example.psychometricvocab.theme.*

/**
 * The one visible piece of the cloud sync (Phase 3): "Cloud backup". Signed out it offers
 * "Sign in with Google"; signed in it shows the account email and a sign-out button. Sign-out
 * only disconnects this phone: the backup in the cloud is never deleted.
 */
@Composable
fun CloudBackupRow(modifier: Modifier = Modifier, vm: SyncViewModel = viewModel()) {
    val isHebrew = LocalAppState.current.isHebrew
    val context = LocalContext.current
    val user by vm.user.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()

    LaunchedEffect(error) {
        if (error != null) {
            Toast.makeText(
                context,
                if (isHebrew) "ההתחברות נכשלה. נסה שוב" else "Sign-in failed. Please try again",
                Toast.LENGTH_LONG
            ).show()
            vm.consumeError()
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = White)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Cloud, contentDescription = null, tint = Yellow)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isHebrew) "גיבוי לענן" else "Cloud backup",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = when {
                        !vm.isAvailable -> if (isHebrew) "לא זמין בגרסה זו" else "Not available in this build"
                        user != null -> user?.email ?: (if (isHebrew) "מחובר" else "Signed in")
                        else -> if (isHebrew) "שמור את ההתקדמות שלך בענן" else "Keep your progress safe in the cloud"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1
                )
            }
            if (vm.isAvailable) {
                if (user != null) {
                    TextButton(onClick = vm::signOut) {
                        Text(if (isHebrew) "התנתק" else "Sign out", color = YellowDark, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(
                        enabled = !busy,
                        onClick = { context.findActivity()?.let(vm::signIn) }
                    ) {
                        Text(
                            if (isHebrew) "התחבר עם Google" else "Sign in with Google",
                            color = YellowDark,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
