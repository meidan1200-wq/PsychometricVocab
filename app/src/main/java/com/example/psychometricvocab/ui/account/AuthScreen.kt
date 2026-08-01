package com.example.psychometricvocab.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.psychometricvocab.LocalAppState
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.YellowButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onCreateAccount: (String, String) -> Unit,
    onContinueAsGuest: () -> Unit
) {
    val appState = LocalAppState.current
    val isHebrew = appState.isHebrew

    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OffWhite)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // App Logo / Illustration
        Icon(
            imageVector = Icons.Default.MenuBook,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = Yellow
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (isHebrew) "ברוכים הבאים!" else "Welcome!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = TextPrimary
        )
        
        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isHebrew) "צור חשבון כדי לשמור את ההתקדמות שלך" else "Create an account to save your progress",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        OutlinedTextField(
            value = fullName,
            onValueChange = { fullName = it },
            label = { Text(if (isHebrew) "שם מלא" else "Full Name") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Yellow)
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text(if (isHebrew) "אימייל" else "Email") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Yellow)
        )

        Spacer(modifier = Modifier.height(48.dp))

        YellowButton(
            text = if (isHebrew) "צור חשבון" else "Create Account",
            onClick = {
                if (fullName.isNotBlank()) {
                    onCreateAccount(fullName, email)
                }
            }
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onContinueAsGuest) {
            Text(
                text = if (isHebrew) "המשך כאורח" else "Continue as Guest",
                color = TextSecondary,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}
