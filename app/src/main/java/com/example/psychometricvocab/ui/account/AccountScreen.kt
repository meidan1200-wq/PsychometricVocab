package com.example.psychometricvocab.ui.account

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.psychometricvocab.theme.*
import com.example.psychometricvocab.ui.components.VocabTopBar
import com.example.psychometricvocab.ui.components.YellowButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    vm: AccountViewModel = viewModel()
) {
    val profile by vm.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf(profile.fullName) }
    var email by remember { mutableStateOf(profile.email) }
    var imageUri by remember { mutableStateOf<Uri?>(if (profile.profileImageUri.isNotEmpty()) Uri.parse(profile.profileImageUri) else null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            // Grant persistent read permission to avoid SecurityException on restart
            val flag = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flag)
            imageUri = uri
        }
    }

    Scaffold(
        topBar = { VocabTopBar(title = "Account", onBack = onBack) },
        containerColor = OffWhite
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Profile Avatar Picker
            Box(
                modifier = Modifier.size(120.dp),
                contentAlignment = Alignment.Center
            ) {
                // The actual circular avatar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(Yellow.copy(alpha = 0.2f))
                        .clickable { launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentAlignment = Alignment.Center
                ) {
                    if (imageUri != null) {
                        AsyncImage(
                            model = imageUri,
                            contentDescription = "Profile Image",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else if (fullName.isNotEmpty()) {
                        Text(fullName.first().toString().uppercase(), fontSize = 48.sp, fontWeight = FontWeight.ExtraBold, color = YellowDark)
                    } else {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = YellowDark)
                    }
                }

                // Camera icon overlay (outside the clip so it doesn't get cropped)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .size(36.dp)
                        .background(White, CircleShape)
                        .padding(2.dp)
                        .background(Yellow, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(Modifier.height(32.dp))

            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Full Name") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowDark)
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = YellowDark)
            )

            Spacer(Modifier.height(8.dp))
            Text(
                text = "End-to-End Encrypted Secure Storage",
                color = CorrectGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.Start)
            )

            Spacer(Modifier.height(32.dp))

            YellowButton(
                text = "Save Account",
                onClick = {
                    vm.saveProfile(fullName, email, imageUri?.toString() ?: "")
                    Toast.makeText(context, "Local data securely linked to account!", Toast.LENGTH_SHORT).show()
                }
            )

            Spacer(Modifier.weight(1f))

            TextButton(onClick = { showDeleteConfirm = true }) {
                Text("Remove Account & Data", color = WrongRed, fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Remove Account") },
            text = { Text("Are you sure you want to remove your account? This will permanently delete your profile and clear all local database progress.") },
            confirmButton = {
                Button(
                    onClick = {
                        vm.removeAccount()
                        showDeleteConfirm = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = WrongRed)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}
