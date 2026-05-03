@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
package com.example.exerciseformanalyzer.ui.profile

import com.google.accompanist.permissions.ExperimentalPermissionsApi

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.exerciseformanalyzer.R
import com.example.exerciseformanalyzer.ui.MainViewModel
import com.example.exerciseformanalyzer.data.local.entity.UserEntity
import com.example.exerciseformanalyzer.model.WorkoutStats
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.launch
import com.google.accompanist.permissions.*
import android.os.Build
import android.Manifest

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel,
    mainViewModel: MainViewModel,
    onNavigateBack: () -> Unit,
    onLogout: () -> Unit = {}
) {
    val user by viewModel.observeCurrentUser().collectAsState(initial = null)
    val stats by viewModel.observePatientStats().collectAsState(initial = WorkoutStats())
    val isDarkMode by mainViewModel.isDarkMode.collectAsStateWithLifecycle()
    
    var isEditMode by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isUploading by remember { mutableStateOf(false) }

    // Android 13+ (API 33) için READ_MEDIA_IMAGES, daha eskiler için READ_EXTERNAL_STORAGE
    val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permission)

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                scope.launch {
                    isUploading = true
                    try {
                        val inputStream = context.contentResolver.openInputStream(it)
                        val originalBitmap = BitmapFactory.decodeStream(inputStream)
                        
                        // Square Crop & Resize
                        val size = Math.min(originalBitmap.width, originalBitmap.height)
                        val x = (originalBitmap.width - size) / 2
                        val y = (originalBitmap.height - size) / 2
                        val croppedBitmap = Bitmap.createBitmap(originalBitmap, x, y, size, size)
                        val scaledBitmap = Bitmap.createScaledBitmap(croppedBitmap, 512, 512, true)
                        
                        // Compress
                        val baos = ByteArrayOutputStream()
                        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                        val data = baos.toByteArray()
                        
                        viewModel.uploadProfileImage(data) { result ->
                            isUploading = false
                            result.onSuccess {
                                scope.launch { snackbarHostState.showSnackbar("Profile image updated successfully") }
                            }.onFailure {
                                scope.launch { snackbarHostState.showSnackbar("Upload failed: ${it.message}") }
                            }
                        }
                    } catch (e: Exception) {
                        isUploading = false
                        snackbarHostState.showSnackbar("Error processing image")
                    }
                }
            }
        }
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        stringResource(R.string.profile_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back_button))
                    }
                },
                actions = {
                    if (!isEditMode) {
                        IconButton(onClick = { isEditMode = true }) {
                            Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_profile), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingVals ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingVals)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
            ) {
                if (user == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    AnimatedContent(
                        targetState = isEditMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        label = "EditTransition"
                    ) { editMode ->
                        if (editMode) {
                            EditProfileContent(
                                user = user!!,
                                onSave = { updated ->
                                    viewModel.updateProfile(updated) { success ->
                                        if (success) isEditMode = false
                                    }
                                },
                                onCancel = { isEditMode = false }
                            )
                        } else {
                            ProfileDisplayContent(
                                user = user!!,
                                stats = stats,
                                isDarkMode = isDarkMode,
                                isUploading = isUploading,
                                onImageClick = {
                                    if (permissionState.status.isGranted) {
                                        pickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                    } else {
                                        permissionState.launchPermissionRequest()
                                    }
                                },
                                onDarkModeToggle = { mainViewModel.setDarkMode(it) },
                                onLogoutClick = { showLogoutDialog = true },
                                onChangePasswordClick = {
                                    viewModel.sendPasswordReset { success ->
                                        // Reset linki gönderildi uyarısı eklenebilir
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(stringResource(R.string.logout_confirm_title)) },
            text = { Text(stringResource(R.string.logout_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    viewModel.logout()
                    onLogout()
                }) {
                    Text(stringResource(R.string.yes), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
fun ProfileDisplayContent(
    user: UserEntity,
    stats: WorkoutStats,
    isDarkMode: Boolean,
    isUploading: Boolean,
    onImageClick: () -> Unit,
    onDarkModeToggle: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    onChangePasswordClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // 1. HEADER SECTION
        ProfileHeader(user, isUploading, onImageClick)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 2. STATS SECTION
        StatsSection(stats, user)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 3. PERSONAL INFO SECTION
        PersonalInfoSection(user)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // 4. SETTINGS & ACTIONS SECTION
        SettingsSection(
            isDarkMode = isDarkMode,
            onDarkModeToggle = onDarkModeToggle,
            onChangePasswordClick = onChangePasswordClick,
            onLogoutClick = onLogoutClick
        )
    }
}

@Composable
fun ProfileHeader(user: UserEntity, isUploading: Boolean = false, onImageClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(110.dp)
                .shadow(8.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .padding(4.dp)
                .then(
                    if (!isUploading && (user.role == "EXPERT" || user.role == "ADMIN")) {
                        Modifier.clickable { onImageClick() }
                    } else {
                        Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (user.profileImageUrl != null) {
                AsyncImage(
                    model = user.profileImageUrl,
                    contentDescription = "Profile Image",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                val initials = remember(user.fullName) {
                    user.fullName.split(" ")
                        .filter { it.isNotBlank() }
                        .take(2)
                        .map { it.first().uppercase() }
                        .joinToString("")
                }
                val backgroundColor = when (user.gender.uppercase()) {
                    "MALE" -> Color(0xFF2196F3)
                    "FEMALE" -> Color(0xFFE91E63)
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(backgroundColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            if (isUploading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(32.dp),
                        color = Color.White,
                        strokeWidth = 3.dp
                    )
                }
            } else {
                if (user.role == "EXPERT" || user.role == "ADMIN") {
                    // Overlay camera icon to indicate it's clickable
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.BottomEnd
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                            modifier = Modifier.padding(4.dp).size(28.dp),
                            shadowElevation = 2.dp
                        ) {
                            Icon(
                                Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = user.fullName,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground
        )
        
        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text(
                text = if (user.role == "EXPERT") stringResource(R.string.role_expert) else "Fitness Tracking User",
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun StatsSection(stats: WorkoutStats, user: UserEntity) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val completed = stats.completionStats["COMPLETED"] ?: 0
        val active = stats.completionStats["PENDING"] ?: 0
        
        StatsCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.completed_tasks),
            value = completed.toString(),
            icon = Icons.Default.CheckCircle,
            containerColor = Color(0xFFE8F5E9),
            contentColor = Color(0xFF2E7D32)
        )
        
        StatsCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.active_tasks),
            value = active.toString(),
            icon = Icons.Default.DirectionsRun,
            containerColor = Color(0xFFE3F2FD),
            contentColor = Color(0xFF1565C0)
        )
        
        StatsCard(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.streak_label),
            value = "🔥 ${user.streak}",
            icon = Icons.Default.Whatshot,
            containerColor = Color(0xFFFFF3E0),
            contentColor = Color(0xFFE65100)
        )
    }
}

@Composable
fun StatsCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color
) {
    Card(
        modifier = modifier.height(100.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = contentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PersonalInfoSection(user: UserEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                stringResource(R.string.personal_info),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            InfoRow(Icons.Default.Cake, stringResource(R.string.age_label), "${user.age ?: "-"} year")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.MonitorWeight, stringResource(R.string.weight_label), "${user.weightKg ?: "-"} ${stringResource(R.string.kg_unit)}")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.Timer, stringResource(R.string.rest_time), "${user.defaultRestSeconds} sec")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.Height, "Height", "${user.heightCm ?: "-"} cm")
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(Icons.Default.Flag, stringResource(R.string.goal_label), user.goal?.replace("_", " ")?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.none_label))
            
            if (user.weightKg != null && user.heightCm != null && user.heightCm!! > 0) {
                Spacer(modifier = Modifier.height(16.dp))
                BMISection(user.weightKg, user.heightCm!!.toInt())
            }
        }
    }
}

@Composable
fun BMISection(weightKg: Float?, heightCm: Int?) {
    if (weightKg == null || heightCm == null || heightCm == 0) return

    val heightM = heightCm / 100f
    val bmi = weightKg / (heightM * heightM)
    
    val (category, color) = when {
        bmi < 18.5 -> "Underweight" to Color(0xFF03A9F4)
        bmi < 25.0 -> "Normal" to Color(0xFF4CAF50)
        bmi < 30.0 -> "Overweight" to Color(0xFFFFC107)
        else -> "Obese" to Color(0xFFF44336)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("BMI Index", style = MaterialTheme.typography.labelMedium, color = color)
                Text(String.format("%.1f", bmi), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), color = color)
            }
            Surface(color = color, shape = RoundedCornerShape(8.dp)) {
                Text(
                    category, 
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), 
                    color = Color.White
                )
            }
        }
    }
}

@Composable
fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
    }
}

@Composable
fun SettingsSection(
    isDarkMode: Boolean,
    onDarkModeToggle: (Boolean) -> Unit,
    onChangePasswordClick: () -> Unit,
    onLogoutClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            ActionItem(
                icon = Icons.Default.DarkMode,
                title = stringResource(R.string.dark_mode),
                trailing = {
                    Switch(checked = isDarkMode, onCheckedChange = onDarkModeToggle)
                }
            )
            ActionItem(
                icon = Icons.Default.Lock,
                title = stringResource(R.string.change_password),
                onClick = onChangePasswordClick
            )
            ActionItem(
                icon = Icons.Default.Logout,
                title = stringResource(R.string.logout),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onLogoutClick
            )
        }
    }
}

@Composable
fun ActionItem(
    icon: ImageVector,
    title: String,
    titleColor: Color = MaterialTheme.colorScheme.onSurface,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp), tint = if (titleColor == MaterialTheme.colorScheme.error) titleColor else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = titleColor
        )
        trailing?.invoke() ?: Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun EditProfileContent(
    user: UserEntity,
    onSave: (UserEntity) -> Unit,
    onCancel: () -> Unit
) {
    var firstName by remember { mutableStateOf(user.firstName ?: "") }
    var lastName by remember { mutableStateOf(user.lastName ?: "") }
    var ageStr by remember { mutableStateOf(user.age?.toString() ?: "") }
    var weightStr by remember { mutableStateOf(user.weightKg?.toString() ?: "") }
    var heightStr by remember { mutableStateOf(user.heightCm?.toInt()?.toString() ?: "") }
    var diseaseInfo by remember { mutableStateOf(user.diseaseInfo ?: "") }
    var activityLevel by remember { mutableStateOf(user.activityLevel ?: "medium") }
    var selectedDiseases by remember { 
        mutableStateOf(
            user.diseasesJson?.removeSurrounding("[", "]")?.split(",")
                ?.map { it.trim().removeSurrounding("\"") }?.filter { it.isNotEmpty() }?.toSet() ?: emptySet()
        )
    }
    var otherDisease by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf(user.gender ?: "MALE") }
    
    var hasHernia by remember { mutableStateOf(user.hasHernia) }
    var hasMeniscus by remember { mutableStateOf(user.hasMeniscus) }
    var goal by remember { mutableStateOf(user.goal ?: "general_health") }
    var restSecondsStr by remember { mutableStateOf(user.defaultRestSeconds.toString()) }

    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            stringResource(R.string.edit_profile),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = firstName,
                onValueChange = { firstName = it },
                label = { Text("Ad") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = lastName,
                onValueChange = { lastName = it },
                label = { Text("Soyad") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = ageStr,
                onValueChange = { ageStr = it },
                label = { Text(stringResource(R.string.age_label)) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            OutlinedTextField(
                value = weightStr,
                onValueChange = { weightStr = it },
                label = { Text("${stringResource(R.string.weight_label)} (${stringResource(R.string.kg_unit)})") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = heightStr,
                onValueChange = { heightStr = it },
                label = { Text("Height (cm)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
            OutlinedTextField(
                value = restSecondsStr,
                onValueChange = { restSecondsStr = it },
                label = { Text("Rest (sec)") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        BMISection(weightStr.toFloatOrNull(), heightStr.toIntOrNull())

        Spacer(modifier = Modifier.height(20.dp))
        Text("Gender", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = gender == "MALE", onClick = { gender = "MALE" })
            Text("Male", modifier = Modifier.clickable { gender = "MALE" })
            Spacer(modifier = Modifier.width(16.dp))
            RadioButton(selected = gender == "FEMALE", onClick = { gender = "FEMALE" })
            Text("Female", modifier = Modifier.clickable { gender = "FEMALE" })
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text(stringResource(R.string.goal_label), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        val goals = listOf("lose_weight", "gain_muscle", "rehab", "general_health")
        FlowRow(modifier = Modifier.padding(vertical = 8.dp)) {
            goals.forEach { g ->
                FilterChip(
                    selected = goal == g,
                    onClick = { goal = g },
                    label = { Text(g.replace("_", " ").replaceFirstChar { it.uppercase() }) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        ActivitySelector(activityLevel) { activityLevel = it }

        Spacer(modifier = Modifier.height(20.dp))
        DiseaseSelector(
            selectedDiseases = selectedDiseases,
            onDiseasesChanged = { selectedDiseases = it },
            otherDisease = otherDisease,
            onOtherDiseaseChanged = { otherDisease = it }
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text("Physical Conditions", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hasHernia, onCheckedChange = { hasHernia = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Hernia history?")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = hasMeniscus, onCheckedChange = { hasMeniscus = it })
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Meniscus history?")
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = {
                val finalDiseaseInfo = if (selectedDiseases.contains("Other")) {
                    (selectedDiseases - "Other").joinToString(", ") + if (otherDisease.isNotEmpty()) ": $otherDisease" else ""
                } else {
                    selectedDiseases.joinToString(", ")
                }

                val updated = user.copy(
                    firstName = firstName,
                    lastName = lastName,
                    fullName = "$firstName $lastName".trim(),
                    age = ageStr.toIntOrNull(),
                    weightKg = weightStr.toFloatOrNull(),
                    heightCm = heightStr.toFloatOrNull(),
                    diseaseInfo = finalDiseaseInfo,
                    diseasesJson = selectedDiseases.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" },
                    activityLevel = activityLevel,
                    hasHernia = hasHernia,
                    hasMeniscus = hasMeniscus,
                    goal = goal,
                    gender = gender,
                    defaultRestSeconds = restSecondsStr.toIntOrNull() ?: 90
                )
                onSave(updated)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(stringResource(R.string.save), style = MaterialTheme.typography.titleMedium)
        }
        
        TextButton(
            onClick = onCancel,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        ) {
            Text(stringResource(R.string.cancel), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun DiseaseSelector(
    selectedDiseases: Set<String>,
    onDiseasesChanged: (Set<String>) -> Unit,
    otherDisease: String,
    onOtherDiseaseChanged: (String) -> Unit
) {
    val commonDiseases = listOf("Diabetes", "Hypertension", "Knee Problems", "Back Pain")
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Known Diseases", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        
        FlowRow(modifier = Modifier.fillMaxWidth()) {
            commonDiseases.forEach { disease ->
                val isSelected = selectedDiseases.contains(disease)
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (isSelected) onDiseasesChanged(selectedDiseases - disease)
                        else onDiseasesChanged(selectedDiseases + disease)
                    },
                    label = { Text(disease) },
                    modifier = Modifier.padding(end = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )
            }
            val isOtherSelected = selectedDiseases.contains("Other")
            FilterChip(
                selected = isOtherSelected,
                onClick = {
                    if (isOtherSelected) onDiseasesChanged(selectedDiseases - "Other")
                    else onDiseasesChanged(selectedDiseases + "Other")
                },
                label = { Text("Other") },
                shape = RoundedCornerShape(12.dp)
            )
        }
        
        if (selectedDiseases.contains("Other")) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = otherDisease,
                onValueChange = onOtherDiseaseChanged,
                label = { Text("Describe other conditions") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )
        }
    }
}

@Composable
fun ActivitySelector(
    selectedLevel: String,
    onLevelSelected: (String) -> Unit
) {
    val levels = listOf(
        "low" to ("Low Activity" to "Mostly sitting during the day, no regular exercise"),
        "medium" to ("Moderate Activity" to "Regular movement, light exercise 1-3 days a week"),
        "high" to ("High Activity" to "Active lifestyle, intense exercise 4-7 days a week")
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Physical Activity Level", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
        Spacer(modifier = Modifier.height(8.dp))
        
        levels.forEach { (key, info) ->
            val (label, desc) = info
            val isSelected = selectedLevel == key
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onLevelSelected(key) },
                shape = RoundedCornerShape(16.dp),
                border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = isSelected, onClick = { onLevelSelected(key) })
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold))
                        Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
