package com.example.agriscout.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.example.agriscout.data.local.DiseaseCatalogEntity
import com.example.agriscout.camera.ImageFileProvider
import com.example.agriscout.ui.components.CameraXPhotoCapture
import com.example.agriscout.ui.components.CapturedPhotoPreview
import com.example.agriscout.ui.components.farmOutputFile
import com.example.agriscout.ui.components.reportOutputFile
import com.example.agriscout.data.local.FarmEntity
import com.example.agriscout.data.local.FieldReportEntity
import com.example.agriscout.data.local.WeatherSnapshotEntity
import com.example.agriscout.data.local.WeatherWarningEntity
import com.example.agriscout.ui.components.ActionRow
import com.example.agriscout.ui.components.AgriScoutSpacing
import com.example.agriscout.ui.components.AppTextField
import com.example.agriscout.ui.components.DetailRow
import com.example.agriscout.ui.components.EmptyState
import com.example.agriscout.ui.components.InlineStatusMessage
import com.example.agriscout.ui.components.LoadingStatus
import com.example.agriscout.ui.components.MetricCard
import com.example.agriscout.ui.components.PrimaryAction
import com.example.agriscout.ui.components.SecondaryAction
import com.example.agriscout.ui.components.SectionCard
import com.example.agriscout.ui.components.StatusChip
import com.example.agriscout.ui.navigation.Routes
import com.example.agriscout.ui.viewmodel.AgriScoutViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.large),
            modifier = Modifier.padding(AgriScoutSpacing.xLarge)
        ) {
            Surface(
                modifier = Modifier.size(92.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Agriculture, contentDescription = null, modifier = Modifier.size(48.dp))
                }
            }
            Text("AgriScout", style = MaterialTheme.typography.displaySmall)
            Text(
                "Offline-ready crop scouting for field officers",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CircularProgressIndicator()
        }
    }
}

@Composable
fun AuthScreen(
    title: String,
    actionText: String,
    alternateText: String,
    viewModel: AgriScoutViewModel,
    onAction: () -> Unit,
    onAlternate: () -> Unit
) {
    val form by viewModel.authForm.collectAsState()
    val profile by viewModel.officerProfile.collectAsState()
    ScreenScaffold(title, topBar = false) {
        item {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.widthIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.large),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier.size(72.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(38.dp))
                        }
                    }
                    Text(title, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Sign in to manage farms, reports, sync, and reference guidance.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SectionCard("Secure officer access", subtitle = "Your data stays available offline and syncs when connected.") {
                if (!profile.isFirebaseConfigured) {
                    InlineStatusMessage(
                        "Firebase is not configured. Add app/google-services.json and enable Email/Password sign-in.",
                        isError = true
                    )
                }
                AppTextField(form.email, "Email", { viewModel.updateAuth(email = it) }, leadingIcon = Icons.Filled.Email)
                AppTextField(form.password, "Password", { viewModel.updateAuth(password = it) }, password = true, leadingIcon = Icons.Filled.Security)
                form.message?.let { InlineStatusMessage(it, isError = true) }
                PrimaryAction(
                    text = if (form.loading) "$actionText..." else actionText,
                    onClick = onAction,
                    loading = form.loading
                )
                TextButton(onClick = onAlternate) { Text(alternateText) }
                    }
                }
            }
        }
    }
}

@Composable
fun OfficerAccessGateScreen(viewModel: AgriScoutViewModel, onLogout: () -> Unit) {
    val access by viewModel.officerAccess.collectAsState()
    val profile by viewModel.officerProfile.collectAsState()
    ScreenScaffold("Officer Access") {
        item {
            SectionCard(
                title = access.title.ifBlank { "Officer access check" },
                subtitle = "Account status controls farm operations and cloud sync.",
                icon = Icons.Filled.Person
            ) {
                DetailRow("Signed in as", profile.email ?: "Unknown officer")
                DetailRow("Role", access.role ?: "field_officer")
                DetailRow("Status", access.status ?: "unverified")
                InlineStatusMessage(
                    message = access.message.ifBlank { "Checking officer approval status..." },
                    isError = access.status != "active"
                )
                access.error?.takeIf { it.isNotBlank() && it != access.message }?.let {
                    InlineStatusMessage(it, isError = true)
                }
                PrimaryAction(
                    text = if (access.loading) "Checking status..." else "Refresh access status",
                    onClick = { viewModel.refreshOfficerAccess() },
                    loading = access.loading
                )
                SecondaryAction(text = "Logout", onClick = onLogout)
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: AgriScoutViewModel, navigate: (String) -> Unit) {
    val dashboard by viewModel.dashboard.collectAsState()
    val warnings by viewModel.warnings.collectAsState()
    val weatherSnapshot by viewModel.weatherSnapshot.collectAsState()
    val farms by viewModel.farms.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val inventoryRequests by viewModel.inventoryRequests.collectAsState()
    val officerAccess by viewModel.officerAccess.collectAsState()
    val syncOverview by viewModel.syncOverview.collectAsState()
    val dashboardWarnings = remember(warnings) { warnings.prioritizedWarnings().take(3) }
    val highRiskReports = remember(reports) {
        reports.filter { report ->
            severityRank(report.severity) >= 3 || (report.detectionConfidence ?: 0) >= 75
        }.take(3)
    }
    val upcomingCropTasks = remember(farms) { farms.cropScheduleItems().take(3) }
    ScreenScaffold("AgriScout") {
        item {
            SectionCard(
                title = "Field operations center",
                subtitle = "Smart Agriculture ERP field officer workspace.",
                icon = Icons.Filled.Agriculture
            ) {
                Text(
                    "Register farms, capture observations, review simulated IoT status, request resources, and use rule-based recommendations.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (officerAccess.loaded) {
                    StatusChip(
                        label = "Access: ${officerAccess.status ?: "unknown"}",
                        leadingIcon = Icons.Filled.Person
                    )
                }
            }
        }
        item {
            BoxWithConstraints {
                if (maxWidth < 520.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)) {
                        MetricCard(
                            "Farms registered",
                            dashboard.farmCount.toString(),
                            Icons.Filled.Agriculture,
                            Modifier.fillMaxWidth(),
                            "${dashboard.pendingFarmCount} pending · ${dashboard.failedFarmCount} failed"
                        )
                        MetricCard(
                            "Field reports",
                            dashboard.reportCount.toString(),
                            Icons.Filled.Assessment,
                            Modifier.fillMaxWidth(),
                            "${dashboard.pendingReportCount} pending · ${dashboard.failedReportCount} failed"
                        )
                        MetricCard(
                            "Sync queue",
                            syncOverview.pendingTotal.toString(),
                            Icons.Filled.CloudSync,
                            Modifier.fillMaxWidth(),
                            "${syncOverview.failedTotal} failed · ${syncOverview.syncedTotal} synced"
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)) {
                        MetricCard(
                            "Farms registered",
                            dashboard.farmCount.toString(),
                            Icons.Filled.Agriculture,
                            Modifier.weight(1f),
                            "${dashboard.pendingFarmCount} pending · ${dashboard.failedFarmCount} failed"
                        )
                        MetricCard(
                            "Field reports",
                            dashboard.reportCount.toString(),
                            Icons.Filled.Assessment,
                            Modifier.weight(1f),
                            "${dashboard.pendingReportCount} pending · ${dashboard.failedReportCount} failed"
                        )
                        MetricCard(
                            "Sync queue",
                            syncOverview.pendingTotal.toString(),
                            Icons.Filled.CloudSync,
                            Modifier.weight(1f),
                            "${syncOverview.failedTotal} failed · ${syncOverview.syncedTotal} synced"
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Priority actions", subtitle = "Common field tasks are one tap away.", icon = Icons.Filled.CheckCircle) {
                ActionRow {
                    Button(onClick = { navigate(Routes.FARMS) }, modifier = Modifier.weight(1f)) { Text("Manage farms") }
                    OutlinedButton(onClick = { navigate(Routes.REPORTS) }, modifier = Modifier.weight(1f)) { Text("Reports") }
                }
                ActionRow {
                    OutlinedButton(onClick = { navigate(Routes.INVENTORY) }, modifier = Modifier.weight(1f)) { Text("Inventory") }
                    OutlinedButton(onClick = { navigate(Routes.CATALOG) }, modifier = Modifier.weight(1f)) { Text("Catalog") }
                }
                ActionRow {
                    OutlinedButton(onClick = { navigate(Routes.MAP) }, modifier = Modifier.weight(1f)) { Text("Field map") }
                    OutlinedButton(onClick = { navigate(Routes.SYNC) }, modifier = Modifier.weight(1f)) { Text("Sync") }
                }
            }
        }
        item {
            SectionCard("ERP readiness summary", subtitle = "Quick gaps before admin dashboard and real IoT are added.", icon = Icons.Filled.Assessment) {
                DetailRow("Farms with device IDs", "${farms.count { !it.assignedDeviceId.isNullOrBlank() }} of ${farms.size}")
                DetailRow("Pending inventory approvals", inventoryRequests.count { it.status.equals("Pending", ignoreCase = true) }.toString())
                DetailRow("Approved or issued requests", inventoryRequests.count { it.status.equals("Approved", ignoreCase = true) || it.status.equals("Issued", ignoreCase = true) }.toString())
                DetailRow("High-risk scouting reports", highRiskReports.size.toString())
            }
        }
        item {
            SectionCard("Crop schedule preview", subtitle = "Planting-date reminders for the next field actions.", icon = Icons.AutoMirrored.Filled.EventNote) {
                if (upcomingCropTasks.isEmpty()) {
                    EmptyState(
                        title = "No crop schedule yet",
                        message = "Add planting dates to farms to show activity reminders and harvest estimates here.",
                        icon = Icons.AutoMirrored.Filled.EventNote
                    )
                } else {
                    upcomingCropTasks.forEach { task ->
                        DetailRow(task.farmName, task.summary)
                    }
                }
            }
        }
        item {
            SectionCard("Crop health priorities", subtitle = "Detected high-risk scouting reports with suggested actions.", icon = Icons.Filled.Warning) {
                if (highRiskReports.isEmpty()) {
                    EmptyState(
                        title = "No high-risk reports",
                        message = "Analyze report symptoms to surface treatment and prevention priorities here.",
                        icon = Icons.Filled.CheckCircle
                    )
                } else {
                    highRiskReports.forEach { report ->
                        ReportRiskItem(report)
                    }
                }
            }
        }
        item {
            SectionCard("Recent high-severity warnings", subtitle = "Cached alerts remain visible offline.", icon = Icons.Filled.NotificationsActive) {
                WeatherSnapshotSummary(weatherSnapshot)
                if (dashboardWarnings.isEmpty()) {
                    EmptyState(
                        title = "No urgent alerts",
                        message = "No active cached warnings. Refresh when online to get the latest weather and outbreak alerts.",
                        icon = Icons.Filled.NotificationsActive,
                        actionText = "View warnings",
                        onAction = { navigate(Routes.WARNINGS) }
                    )
                } else {
                    dashboardWarnings.forEach { warning ->
                        WarningListItem(warning)
                    }
                }
                TextButton(onClick = { navigate(Routes.WARNINGS) }) { Text("View all warnings") }
            }
        }
    }
}

@Composable
fun FarmListScreen(
    viewModel: AgriScoutViewModel,
    onAdd: () -> Unit,
    onEdit: (FarmEntity) -> Unit,
    onOpen: (FarmEntity) -> Unit,
    onOpenIoT: (FarmEntity) -> Unit = {},
    onBack: (() -> Unit)? = null
) {
    val farms by viewModel.farms.collectAsState()
    val operations by viewModel.operations.collectAsState()
    val profile by viewModel.officerProfile.collectAsState()
    var farmPendingDelete by remember { mutableStateOf<FarmEntity?>(null) }

    farmPendingDelete?.let { farm ->
        DeleteConfirmationDialog(
            title = "Delete farm?",
            body = "Delete ${farm.farmName} and all of its field reports locally and from cloud if already synced?",
            confirmText = if (operations.deletingFarmId == farm.id) "Deleting..." else "Delete",
            enabled = operations.deletingFarmId == null,
            onConfirm = {
                viewModel.deleteFarm(farm)
                farmPendingDelete = null
            },
            onDismiss = { farmPendingDelete = null }
        )
    }

    ScreenScaffold("Farms", onBack) {
        item {
            SectionCard(
                "Farms",
                subtitle = "Open a farm for reports, or jump straight into sensors and camera captures.",
                icon = Icons.Filled.Agriculture
            ) {
                PrimaryAction("Add Farm", onClick = onAdd)
            }
        }
        if (farms.isEmpty()) {
            item {
                EmptyState(
                    "No farms yet. Add the first farm to start field reporting.",
                    title = "Start with a farm",
                    icon = Icons.Filled.Agriculture,
                    actionText = "Add farm",
                    onAction = onAdd
                )
            }
        }
        items(farms) { farm ->
            val canModify = farm.ownerUserId == null || farm.ownerUserId == profile.userId
            FarmCard(
                farm = farm,
                onOpen = { onOpen(farm) },
                onOpenIoT = { onOpenIoT(farm) },
                onEdit = { onEdit(farm) },
                onDelete = { farmPendingDelete = farm },
                deleting = operations.deletingFarmId == farm.id,
                deleteEnabled = operations.deletingFarmId == null && canModify,
                editEnabled = canModify
            )
        }
    }
}

@Composable
fun FarmFormScreen(viewModel: AgriScoutViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val form by viewModel.farmForm.collectAsState()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showCamera by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            showCamera = true
            viewModel.updateFarmForm { copy(message = "Camera ready. Capture a farm photo.") }
        } else {
            showCamera = false
            viewModel.updateFarmForm {
                copy(message = "Camera permission denied. Enable camera access to attach a farm photo.")
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            viewModel.captureFarmLocation()
        } else {
            viewModel.updateFarmForm { copy(message = "Location permission denied. Farm can be saved with text location only.") }
        }
    }
    ScreenScaffold(if (form.id == null) "Add Farm" else "Edit Farm", onBack) {
        item {
            SectionCard(
                "Farm details",
                subtitle = "Saved farms sync to the cloud so the administrator can see them. Admins can also assign farms to you.",
                icon = Icons.Filled.Agriculture
            ) {
                AppTextField(form.farmName, "Farm name", { viewModel.updateFarmForm { copy(farmName = it) } }, leadingIcon = Icons.Filled.Grass)
                AppTextField(form.farmerName, "Farmer/owner name", { viewModel.updateFarmForm { copy(farmerName = it) } }, leadingIcon = Icons.Filled.Groups)
                AppTextField(form.cropType, "Crop type", { viewModel.updateFarmForm { copy(cropType = it) } }, leadingIcon = Icons.Filled.Grass)
                AppTextField(form.locationText, "District/location", { viewModel.updateFarmForm { copy(locationText = it) } }, leadingIcon = Icons.Filled.LocationOn)
                DetailRow("Farm pin", if (form.latitude != null && form.longitude != null) "${formatCoordinate(form.latitude)}, ${formatCoordinate(form.longitude)}" else "Not captured")
                DetailRow("GPS accuracy", form.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured")
                DetailRow("GPS source", form.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
                OutlinedButton(
                    onClick = {
                        val hasLocationPermission =
                            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasLocationPermission) {
                            viewModel.captureFarmLocation()
                        } else {
                            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !form.locationLoading
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (form.locationLoading) "Capturing farm pin..." else "Capture Farm GPS Pin")
                }
                AppTextField(form.landSize, "Land size", { viewModel.updateFarmForm { copy(landSize = it) } }, supportingText = "Example: 3 acres or 1.5 ha")
                AppTextField(
                    form.plantingDateText,
                    "Planting date",
                    { viewModel.updateFarmForm { copy(plantingDateText = it) } },
                    supportingText = "Use yyyy-MM-dd, for example 2026-06-24"
                )
                AppTextField(
                    form.assignedDeviceId,
                    "Sensor ESP32 device ID",
                    { viewModel.updateFarmForm { copy(assignedDeviceId = it) } },
                    leadingIcon = Icons.Filled.Sensors,
                    supportingText = "Must match firmware DEVICE_ID. Example: ESP32-FARM-001"
                )
                AppTextField(
                    form.assignedCameraDeviceId,
                    "Camera ESP32 device ID",
                    { viewModel.updateFarmForm { copy(assignedCameraDeviceId = it) } },
                    leadingIcon = Icons.Filled.PhotoCamera,
                    supportingText = "Optional separate camera node. Example: ESP32-CAM-001"
                )
                AppTextField(form.notes, "Notes", { viewModel.updateFarmForm { copy(notes = it) } }, singleLine = false)
                form.message?.let { InlineStatusMessage(it, isError = true) }
                PrimaryAction(
                    text = if (form.saving) "Saving Farm..." else "Save Farm",
                    onClick = { viewModel.saveFarm(onSaved) },
                    loading = form.saving
                )
            }
        }
        item {
            SectionCard(
                "Farm photo",
                subtitle = "Optional registration photo uploaded to Firebase Storage during sync.",
                icon = Icons.Filled.PhotoCamera
            ) {
                CapturedPhotoPreview(
                    localUri = form.photoLocalUri,
                    remoteUrl = form.remotePhotoUrl,
                    contentDescription = "Captured farm photo"
                )
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            showCamera = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (showCamera) "Retake Farm Photo" else "Capture Farm Photo")
                }
                if (showCamera && hasCameraPermission) {
                    CameraXPhotoCapture(
                        createOutputFile = ImageFileProvider.farmOutputFile(),
                        captureButtonText = "Save Farm Photo",
                        onImageCaptured = { imageUri ->
                            viewModel.updateFarmForm {
                                copy(
                                    photoLocalUri = imageUri,
                                    remotePhotoUrl = null,
                                    message = "Farm photo captured. It will upload on sync."
                                )
                            }
                            showCamera = false
                        },
                        onError = { error ->
                            viewModel.updateFarmForm { copy(message = error) }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun FarmDetailScreen(
    farmId: String,
    viewModel: AgriScoutViewModel,
    onBack: () -> Unit,
    onAddReport: (FarmEntity) -> Unit,
    onOpenReport: (FieldReportEntity) -> Unit,
    onEditReport: (FieldReportEntity) -> Unit,
    onDeleteReport: (FieldReportEntity) -> Unit,
    onOpenSensors: (FarmEntity) -> Unit,
    onOpenVisits: (FarmEntity) -> Unit,
    onOpenInventory: (FarmEntity) -> Unit,
    onOpenRecommendations: (FarmEntity) -> Unit
) {
    val farms by viewModel.farms.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val operations by viewModel.operations.collectAsState()
    val farm = farms.firstOrNull { it.id == farmId }
    val farmReports = reports.filter { it.farmId == farmId }
    var reportPendingDelete by remember { mutableStateOf<FieldReportEntity?>(null) }

    reportPendingDelete?.let { report ->
        DeleteConfirmationDialog(
            title = "Delete report?",
            body = "Delete this ${report.cropType} field report locally and from cloud if already synced?",
            confirmText = if (operations.deletingReportId == report.id) "Deleting..." else "Delete",
            enabled = operations.deletingReportId == null,
            onConfirm = {
                onDeleteReport(report)
                reportPendingDelete = null
            },
            onDismiss = { reportPendingDelete = null }
        )
    }

    ScreenScaffold("Farm Detail", onBack) {
        if (farm == null) {
            item { EmptyState("This farm may have been deleted or is still syncing from another device.", title = "Farm not found") }
        } else {
            item {
                SectionCard(
                    farm.farmName,
                    subtitle = "${farm.cropType} in ${farm.locationText}",
                    icon = Icons.Filled.Agriculture
                ) {
                    SyncStatusChip(farm.syncStatus)
                    DetailRow("Owner", farm.farmerName)
                    DetailRow("Crop", farm.cropType)
                    DetailRow("Location", farm.locationText)
                    DetailRow(
                        "Farm pin",
                        if (farm.latitude != null && farm.longitude != null) "${formatCoordinate(farm.latitude)}, ${formatCoordinate(farm.longitude)}" else "Not captured"
                    )
                    DetailRow("GPS accuracy", farm.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured")
                    DetailRow("GPS source", farm.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
                    DetailRow("Land", farm.landSize.ifBlank { "Not specified" })
                    DetailRow("Planting date", farm.plantingDate?.let { formatDate(it) } ?: "Not set")
                    DetailRow("Sensor device", farm.assignedDeviceId ?: "Not assigned")
                    DetailRow("Camera device", farm.assignedCameraDeviceId ?: "Not assigned")
                    if (farm.photoLocalUri != null || farm.remotePhotoUrl != null) {
                        CapturedPhotoPreview(
                            localUri = farm.photoLocalUri,
                            remoteUrl = farm.remotePhotoUrl,
                            contentDescription = "Farm photo"
                        )
                    } else {
                        DetailRow("Farm photo", "Not attached")
                    }
                    if (farm.notes.isNotBlank()) {
                        Text(farm.notes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    PrimaryAction("Add Report", onClick = { onAddReport(farm) })
                    PrimaryAction(
                        text = "View sensors & camera",
                        onClick = { onOpenSensors(farm) }
                    )
                    ActionRow {
                        OutlinedButton(onClick = { onOpenVisits(farm) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Filled.EventNote, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Visits")
                        }
                        OutlinedButton(onClick = { onOpenRecommendations(farm) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("AI Rules")
                        }
                    }
                    OutlinedButton(onClick = { onOpenInventory(farm) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Inventory, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Request Inventory")
                    }
                }
            }
            if (farmReports.isEmpty()) {
                item {
                    EmptyState(
                        "No field reports linked to this farm yet. Add a report after scouting this crop.",
                        title = "No reports for this farm",
                        icon = Icons.Filled.Assessment,
                        actionText = "Add report",
                        onAction = { onAddReport(farm) }
                    )
                }
            }
            items(farmReports) { report ->
                ReportSummaryCard(
                    report = report,
                    onOpen = { onOpenReport(report) },
                    onEdit = { onEditReport(report) },
                    onDelete = { reportPendingDelete = report },
                    deleting = operations.deletingReportId == report.id
                )
            }
        }
    }
}

@Composable
fun ReportListScreen(
    viewModel: AgriScoutViewModel,
    onAdd: () -> Unit,
    onEdit: (FieldReportEntity) -> Unit,
    onOpen: (FieldReportEntity) -> Unit,
    onBack: (() -> Unit)? = null
) {
    val reports by viewModel.reports.collectAsState()
    val operations by viewModel.operations.collectAsState()
    var reportPendingDelete by remember { mutableStateOf<FieldReportEntity?>(null) }

    reportPendingDelete?.let { report ->
        DeleteConfirmationDialog(
            title = "Delete report?",
            body = "Delete this ${report.cropType} field report locally and from cloud if already synced?",
            confirmText = if (operations.deletingReportId == report.id) "Deleting..." else "Delete",
            enabled = operations.deletingReportId == null,
            onConfirm = {
                viewModel.deleteReport(report)
                reportPendingDelete = null
            },
            onDismiss = { reportPendingDelete = null }
        )
    }

    ScreenScaffold("Field Reports", onBack) {
        item {
            SectionCard("Field reports", subtitle = "Capture crop observations, evidence, and location for sync.", icon = Icons.Filled.Assessment) {
                PrimaryAction("Add Field Report", onClick = onAdd)
            }
        }
        if (reports.isEmpty()) {
            item {
                EmptyState(
                    "No reports yet. Reports work offline and sync later.",
                    title = "No field reports",
                    icon = Icons.Filled.Assessment,
                    actionText = "Add report",
                    onAction = onAdd
                )
            }
        }
        items(reports) { report ->
            ReportSummaryCard(
                report = report,
                onOpen = { onOpen(report) },
                onEdit = { onEdit(report) },
                onDelete = { reportPendingDelete = report },
                deleting = operations.deletingReportId == report.id
            )
        }
    }
}

@Composable
fun ReportFormScreen(viewModel: AgriScoutViewModel, onSaved: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val form by viewModel.reportForm.collectAsState()
    val farms by viewModel.farms.collectAsState()
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }
    var showCamera by remember { mutableStateOf(false) }
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCameraPermission = granted
        if (granted) {
            showCamera = true
            viewModel.updateReportForm { copy(message = "Camera ready. Capture a report image.") }
        } else {
            showCamera = false
            viewModel.updateReportForm {
                copy(message = "Camera permission denied. Enable camera access in app settings to capture evidence.")
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            hasLocationPermission = true
            viewModel.captureCurrentLocation()
        } else {
            hasLocationPermission = false
            val activity = context.findActivity()
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            viewModel.onLocationPermissionDenied(permanentlyDenied = permanentlyDenied)
        }
    }
    fun captureLocationWithPermission(fromImageCapture: Boolean) {
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (hasLocationPermission) {
            viewModel.captureCurrentLocation()
        } else {
            viewModel.updateReportForm {
                copy(
                    message = if (fromImageCapture) {
                        "Image captured. Allow location permission to attach GPS coordinates, or use Get Current Location after enabling it."
                    } else {
                        "Allow location permission to attach GPS coordinates to this report."
                    }
                )
            }
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    ScreenScaffold(if (form.id == null) "Add Report" else "Edit Report", onBack) {
        item {
            SectionCard("Select farm", subtitle = "Reports need a farm so crop and location context stay linked.", icon = Icons.Filled.Agriculture) {
                if (farms.isEmpty()) {
                    EmptyState(
                        title = "Farm required",
                        message = "Add a farm before creating reports so each observation has a field location and crop context.",
                        icon = Icons.Filled.Agriculture
                    )
                } else {
                    farms.forEach { farm ->
                        FilterChip(
                            selected = form.farmId == farm.id,
                            onClick = { viewModel.updateReportForm { copy(farmId = farm.id, cropType = farm.cropType) } },
                            label = { Text(farm.farmName) },
                            leadingIcon = if (form.farmId == farm.id) {
                                { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            } else {
                                { Icon(Icons.Filled.Agriculture, contentDescription = null, modifier = Modifier.size(18.dp)) }
                            }
                        )
                    }
                }
            }
        }
        item {
            SectionCard("Observation", subtitle = "Capture structured field findings for later analysis and sync.", icon = Icons.Filled.Grass) {
                AppTextField(form.cropType, "Crop type", { viewModel.updateReportForm { copy(cropType = it) } }, leadingIcon = Icons.Filled.Grass)
                AppTextField(form.symptoms, "Disease/pest symptoms", { viewModel.updateReportForm { copy(symptoms = it) } }, singleLine = false)
                SeveritySelector(selected = form.severity, onSelect = { viewModel.updateReportForm { copy(severity = it) } })
                Text("Growth stage", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                    listOf("Germination", "Vegetative", "Flowering", "Harvest-ready", "Unknown").forEach { stage ->
                        FilterChip(
                            selected = form.growthStage == stage,
                            onClick = { viewModel.updateReportForm { copy(growthStage = stage) } },
                            label = { Text(stage) }
                        )
                    }
                }
                AppTextField(
                    form.cropConditionDetail,
                    "Crop condition detail",
                    { viewModel.updateReportForm { copy(cropConditionDetail = it) } },
                    singleLine = false,
                    supportingText = "Optional detail such as leaf color, canopy density, or stress signs"
                )
                AppTextField(
                    form.pestObservations,
                    "Pest observations",
                    { viewModel.updateReportForm { copy(pestObservations = it) } },
                    singleLine = false,
                    supportingText = "Optional pest type, severity, and affected area"
                )
                AppTextField(form.estimatedYield, "Estimated yield", { viewModel.updateReportForm { copy(estimatedYield = it) } }, supportingText = "Optional estimate for officer notes")
                AppTextField(form.notes, "Observation notes", { viewModel.updateReportForm { copy(notes = it) } }, singleLine = false)
                AppTextField(
                    form.recommendedActions,
                    "Recommended actions",
                    { viewModel.updateReportForm { copy(recommendedActions = it) } },
                    singleLine = false,
                    supportingText = "Actions for the farmer or follow-up visit"
                )
                AppTextField(
                    form.followUpNotes,
                    "Follow-up notes",
                    { viewModel.updateReportForm { copy(followUpNotes = it) } },
                    singleLine = false,
                    supportingText = "Optional reminders for the next visit"
                )
                OutlinedButton(
                    onClick = { viewModel.analyzeReportSymptoms() },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    enabled = !form.analyzing
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (form.analyzing) "Analyzing symptoms..." else "Analyze Symptoms")
                }
                DetectionResultSummary(
                    issueType = form.issueType,
                    detectedIssue = form.detectedIssue,
                    confidence = form.detectionConfidence,
                    recommendation = form.recommendation,
                    preventiveMeasures = form.preventiveMeasures,
                    explanation = form.detectionExplanation,
                    analysisSource = form.detectionSource,
                    detectionUpdatedAt = form.detectionUpdatedAt
                )
            }
        }
        item {
            SectionCard("Evidence and location", subtitle = "Capture image evidence with GPS coordinates when available.", icon = Icons.Filled.PhotoCamera) {
                form.imageLocalUri?.let { uriValue ->
                    CapturedPhotoPreview(
                        localUri = uriValue,
                        remoteUrl = null,
                        contentDescription = "Captured crop evidence"
                    )
                }
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            showCamera = true
                            viewModel.updateReportForm { copy(message = "Camera ready. Capture a report image.") }
                        } else {
                            val activity = context.findActivity()
                            if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.CAMERA)) {
                                viewModel.updateReportForm {
                                    copy(message = "Camera access is needed to capture crop evidence. Please allow it in the prompt.")
                                }
                            }
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(if (showCamera) "Retake GPS Evidence Image" else "Open Camera for GPS Evidence")
                }
                if (showCamera && hasCameraPermission) {
                    CameraXPhotoCapture(
                        createOutputFile = ImageFileProvider.reportOutputFile(),
                        captureButtonText = "Capture Image and GPS",
                        onImageCaptured = { imageUri ->
                            viewModel.updateReportForm { copy(imageLocalUri = imageUri, message = "Image captured. Capturing GPS coordinates...") }
                            showCamera = false
                            captureLocationWithPermission(fromImageCapture = true)
                        },
                        onError = { errorMessage ->
                            viewModel.updateReportForm { copy(message = errorMessage) }
                        }
                    )
                }
                DetailRow("Latitude", formatCoordinate(form.latitude))
                DetailRow("Longitude", formatCoordinate(form.longitude))
                DetailRow(
                    "GPS accuracy",
                    form.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured"
                )
                DetailRow(
                    "GPS captured",
                    form.gpsCapturedAt?.let { formatDate(it) } ?: "Not captured"
                )
                DetailRow("GPS source", form.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
                OutlinedButton(
                    onClick = {
                        captureLocationWithPermission(fromImageCapture = false)
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium,
                    enabled = !form.locationLoading
                ) {
                    if (form.locationLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Refreshing location...")
                    } else {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(if (form.latitude == null || form.longitude == null) "Get Current Location" else "Refresh Current Location")
                    }
                }
                if (form.locationDenied) {
                    Text(
                        text = "Tip: You can still save this report manually and update location later.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                form.message?.let {
                    InlineStatusMessage(
                        message = it,
                        isError = it.contains("required", ignoreCase = true) ||
                            it.contains("denied", ignoreCase = true) ||
                            it.contains("unable", ignoreCase = true) ||
                            it.contains("failed", ignoreCase = true)
                    )
                }
                PrimaryAction(
                    text = if (form.saving) "Saving Report..." else "Save Report",
                    onClick = { viewModel.saveReport(onSaved) },
                    enabled = farms.isNotEmpty(),
                    loading = form.saving
                )
            }
        }
    }
}

@Composable
fun ReportDetailScreen(reportId: String, viewModel: AgriScoutViewModel, onBack: () -> Unit, onEdit: (FieldReportEntity) -> Unit) {
    val context = LocalContext.current
    val reports by viewModel.reports.collectAsState()
    val operations by viewModel.operations.collectAsState()
    val report = reports.firstOrNull { it.id == reportId }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    if (showDeleteConfirmation && report != null) {
        DeleteConfirmationDialog(
            title = "Delete report?",
            body = "Delete this ${report.cropType} field report locally and from cloud if already synced?",
            confirmText = if (operations.deletingReportId == report.id) "Deleting..." else "Delete",
            enabled = operations.deletingReportId == null,
            onConfirm = {
                viewModel.deleteReport(report, onDeleted = onBack)
                showDeleteConfirmation = false
            },
            onDismiss = { showDeleteConfirmation = false }
        )
    }

    ScreenScaffold("Report Detail", onBack) {
        if (report == null) {
            item { EmptyState("This report may have been deleted or is still syncing from another device.", title = "Report not found") }
        } else {
            item {
                SectionCard(
                    report.cropType,
                    subtitle = "Updated ${formatDate(report.updatedAt)}",
                    icon = Icons.Filled.Assessment
                ) {
                    val imageModel = report.imageLocalUri?.toUri() ?: report.remoteImageUrl
                    imageModel?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = "Report image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(MaterialTheme.shapes.large),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                        SeverityChip(report.severity)
                        SyncStatusChip(report.syncStatus)
                    }
                    DetectionResultSummary(
                        issueType = report.issueType,
                        detectedIssue = report.detectedIssue,
                        confidence = report.detectionConfidence,
                        recommendation = report.recommendation,
                        preventiveMeasures = report.preventiveMeasures,
                        explanation = report.detectionExplanation,
                        analysisSource = report.detectionSource,
                        detectionUpdatedAt = report.detectionUpdatedAt
                    )
                    DetailRow("Estimated yield", report.estimatedYield.ifBlank { "Not specified" })
                    DetailRow("Growth stage", report.growthStage.ifBlank { "Not specified" })
                    DetailRow("Crop condition detail", report.cropConditionDetail.ifBlank { "Not specified" })
                    DetailRow("Pest observations", report.pestObservations.ifBlank { "Not recorded" })
                    DetailRow("Recommended actions", report.recommendedActions.ifBlank { "Not recorded" })
                    DetailRow("Follow-up notes", report.followUpNotes.ifBlank { "Not recorded" })
                    DetailRow(
                        "Coordinates",
                        if (report.latitude != null && report.longitude != null) {
                            "${formatCoordinate(report.latitude)}, ${formatCoordinate(report.longitude)}"
                        } else {
                            "Not captured"
                        }
                    )
                    DetailRow(
                        "GPS accuracy",
                        report.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured"
                    )
                    DetailRow(
                        "GPS captured",
                        report.gpsCapturedAt?.let { formatDate(it) } ?: "Not captured"
                    )
                    DetailRow("GPS source", report.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
                    SectionCard("Symptoms", subtitle = report.symptoms.ifBlank { "No symptoms recorded" }, icon = Icons.Filled.Warning) {}
                    if (report.notes.isNotBlank()) {
                        SectionCard("Notes", subtitle = report.notes, icon = Icons.Filled.Edit) {}
                    }
                    if (report.latitude != null && report.longitude != null) {
                        AgriScoutMapView(
                            markers = listOf(MapMarker("Report: ${report.cropType}", report.latitude, report.longitude)),
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                        OutlinedButton(
                            onClick = {
                                val uri = Uri.parse("geo:${report.latitude},${report.longitude}?q=${report.latitude},${report.longitude}(Agri Scout Report)")
                                context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Open in Maps")
                        }
                    }
                    ActionRow {
                        OutlinedButton(onClick = { onEdit(report) }, modifier = Modifier.weight(1f)) { Text("Edit") }
                        TextButton(
                            onClick = { showDeleteConfirmation = true },
                            modifier = Modifier.weight(1f),
                            enabled = operations.deletingReportId == null
                        ) { Text(if (operations.deletingReportId == report.id) "Deleting..." else "Delete Report") }
                    }
                }
            }
        }
    }
}

@Composable
fun DiseaseCatalogScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val catalog by viewModel.catalog.collectAsState()
    val referenceData by viewModel.referenceData.collectAsState()
    val filteredCatalog = remember(catalog, referenceData.catalogSearchQuery, referenceData.catalogFilter) {
        catalog.filterCatalog(
            query = referenceData.catalogSearchQuery,
            filter = referenceData.catalogFilter
        )
    }
    ScreenScaffold("Disease Catalog", onBack) {
        item {
            SectionCard("Find guidance", subtitle = "Search cached crop disease and treatment guidance.", icon = Icons.AutoMirrored.Filled.MenuBook) {
                AppTextField(
                    value = referenceData.catalogSearchQuery,
                    label = "Search disease, crop, symptoms, treatment",
                    onValueChange = { viewModel.updateCatalogSearch(it) },
                    leadingIcon = Icons.Filled.Search
                )
                AppTextField(
                    value = referenceData.catalogFilter,
                    label = "Filter by crop or severity",
                    onValueChange = { viewModel.updateCatalogFilter(it) },
                    leadingIcon = Icons.Filled.Grass
                )
                PrimaryAction(
                    text = if (referenceData.refreshing) "Refreshing Catalog..." else "Refresh Catalog",
                    onClick = { viewModel.refreshReferenceData() },
                    enabled = !referenceData.refreshing
                )
                RefreshStatus(referenceData.refreshing, referenceData.refreshError, referenceData.lastRefreshAt)
            }
        }
        if (catalog.isEmpty()) {
            item {
                EmptyState(
                    title = "Catalog not cached",
                    message = "No disease catalog is cached yet. Connect to the internet and refresh once, then entries will remain available offline."
                )
            }
        } else if (filteredCatalog.isEmpty()) {
            item {
                EmptyState(
                    title = "No matching guidance",
                    message = "No catalog entries match your search. Try a crop name, disease name, symptom, or clear the filter."
                )
            }
        }
        items(filteredCatalog) { disease -> DiseaseCatalogCard(disease) }
    }
}

@Composable
fun WeatherWarningsScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val warnings by viewModel.warnings.collectAsState()
    val weatherSnapshot by viewModel.weatherSnapshot.collectAsState()
    val referenceData by viewModel.referenceData.collectAsState()
    val prioritizedWarnings = remember(warnings) { warnings.prioritizedWarnings() }
    ScreenScaffold("Weather Warnings", onBack) {
        item {
            SectionCard("Cached alerts", subtitle = "Warnings remain visible after refresh, even when offline.", icon = Icons.Filled.NotificationsActive) {
                WeatherSnapshotSummary(weatherSnapshot)
                PrimaryAction(
                    text = if (referenceData.weatherRefreshing) "Refreshing Weather..." else "Refresh Live Weather",
                    onClick = { viewModel.refreshWeather() },
                    enabled = !referenceData.weatherRefreshing
                )
                SecondaryAction("Refresh Firestore Alerts", onClick = { viewModel.refreshReferenceData() }, enabled = !referenceData.refreshing)
                referenceData.weatherError?.let { InlineStatusMessage(it, isError = true) }
                RefreshStatus(referenceData.refreshing, referenceData.refreshError, referenceData.lastRefreshAt)
            }
        }
        if (prioritizedWarnings.isEmpty()) {
            item {
                EmptyState(
                    title = "No cached warnings",
                    message = "No weather warnings are cached yet. Refresh when online to download alerts for offline use."
                )
            }
        }
        items(prioritizedWarnings) { warning -> WarningListItem(warning) }
    }
}

@Composable
fun MapScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val farms by viewModel.farms.collectAsState()
    val reports by viewModel.reports.collectAsState()
    val markers = remember(farms, reports) {
        farms.mapNotNull { farm ->
            val lat = farm.latitude
            val lng = farm.longitude
            if (lat != null && lng != null && isValidMapCoordinate(lat, lng)) {
                MapMarker("Farm: ${farm.farmName}", lat, lng)
            } else {
                null
            }
        } + reports.mapNotNull { report ->
            val lat = report.latitude
            val lng = report.longitude
            if (lat != null && lng != null && isValidMapCoordinate(lat, lng)) {
                MapMarker("${report.cropType}: ${report.detectedIssue ?: report.severity}", lat, lng)
            } else {
                null
            }
        }
    }
    val farmsMissingGps = remember(farms) {
        farms.count { it.latitude == null || it.longitude == null || !isValidMapCoordinate(it.latitude!!, it.longitude!!) }
    }
    ScreenScaffold("Field Map", onBack) {
        item {
            SectionCard("Farm and report locations", subtitle = "Captured GPS pins from farm records and scouting reports.", icon = Icons.Filled.Map) {
                if (markers.isEmpty()) {
                    EmptyState(
                        title = "No GPS pins yet",
                        message = "Open a farm and tap Capture GPS, or attach location on a field report, then reopen this map.",
                        icon = Icons.Filled.LocationOn
                    )
                } else {
                    AgriScoutMapView(markers = markers, modifier = Modifier.fillMaxWidth().height(420.dp))
                    DetailRow("Pins shown", markers.size.toString())
                    if (farmsMissingGps > 0) {
                        InlineStatusMessage(
                            "$farmsMissingGps farm(s) have no usable GPS pin yet.",
                            isError = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SyncStatusScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val syncOverview by viewModel.syncOverview.collectAsState()
    val officerAccess by viewModel.officerAccess.collectAsState()
    ScreenScaffold("Sync Status", onBack) {
        item {
            SectionCard(
                "Current state",
                subtitle = syncOverview.statusLabel,
                icon = Icons.Filled.CloudSync
            ) {
                DetailRow(
                    "Connectivity",
                    when {
                        !syncOverview.isOnline || syncOverview.waitingForConnectivity -> "Waiting for network"
                        else -> "Online"
                    }
                )
                DetailRow(
                    "Access",
                    if (!officerAccess.canSync || syncOverview.blockedByAccess) {
                        "Blocked until officer account is active"
                    } else {
                        "Allowed"
                    }
                )
                DetailRow(
                    "Activity",
                    when {
                        syncOverview.syncing -> "Retrying now"
                        syncOverview.failedTotal > 0 -> "Failed items will retry on next sync"
                        syncOverview.pendingTotal > 0 -> "Pending uploads queued"
                        else -> "Nothing waiting"
                    }
                )
            }
        }
        item {
            BoxWithConstraints {
                if (maxWidth < 520.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)) {
                        MetricCard("Pending", syncOverview.pendingTotal.toString(), Icons.Filled.CloudSync, Modifier.fillMaxWidth(), "Waiting to upload")
                        MetricCard("Synced", syncOverview.syncedTotal.toString(), Icons.Filled.CloudDone, Modifier.fillMaxWidth(), "Already in cloud")
                        MetricCard("Failed", syncOverview.failedTotal.toString(), Icons.Filled.CloudOff, Modifier.fillMaxWidth(), "Retry with Sync Now")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)) {
                        MetricCard("Pending", syncOverview.pendingTotal.toString(), Icons.Filled.CloudSync, Modifier.weight(1f), "Waiting to upload")
                        MetricCard("Synced", syncOverview.syncedTotal.toString(), Icons.Filled.CloudDone, Modifier.weight(1f), "Already in cloud")
                        MetricCard("Failed", syncOverview.failedTotal.toString(), Icons.Filled.CloudOff, Modifier.weight(1f), "Retry with Sync Now")
                    }
                }
            }
        }
        item {
            SectionCard(
                "Sync legend",
                subtitle = "Room stays the source of truth. Offline and failed items remain local and retry when connectivity returns.",
                icon = Icons.Filled.CloudSync
            ) {
                DetailRow("Pending", "Saved offline and waiting for a successful cloud upload")
                DetailRow("Synced", "Confirmed in Firebase")
                DetailRow("Failed", "Last upload attempt failed for a non-network reason; Sync Now retries these records")
                DetailRow("Retrying", "Sync is actively running against pending/failed records")
                DetailRow("Waiting for connectivity", "Network unavailable; records stay local and are not marked failed")
                DetailRow("Blocked by access", "Officer account is pending or inactive, so cloud sync is gated")
                Text(
                    "Conflict policy: ${syncOverview.conflictPolicySummary}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!officerAccess.canSync) {
                    InlineStatusMessage(
                        officerAccess.message.ifBlank {
                            "Cloud sync unlocks after an administrator activates your officer account."
                        },
                        isError = true
                    )
                }
            }
        }
        item {
            SectionCard("Offline queue", subtitle = "Counts by record type for the signed-in officer.", icon = Icons.Filled.CloudSync) {
                Text("Pending", style = MaterialTheme.typography.titleMedium)
                DetailRow("Farms", syncOverview.pendingFarms.toString())
                DetailRow("Reports", syncOverview.pendingReports.toString())
                DetailRow("Visits", syncOverview.pendingVisits.toString())
                DetailRow("Inventory requests", syncOverview.pendingInventoryRequests.toString())
                DetailRow("Sensor readings", syncOverview.pendingSensorReadings.toString())
                Text("Synced", style = MaterialTheme.typography.titleMedium)
                DetailRow("Farms", syncOverview.syncedFarms.toString())
                DetailRow("Reports", syncOverview.syncedReports.toString())
                DetailRow("Visits", syncOverview.syncedVisits.toString())
                DetailRow("Inventory requests", syncOverview.syncedInventoryRequests.toString())
                DetailRow("Sensor readings", syncOverview.syncedSensorReadings.toString())
                Text("Failed", style = MaterialTheme.typography.titleMedium)
                DetailRow("Farms", syncOverview.failedFarms.toString())
                DetailRow("Reports", syncOverview.failedReports.toString())
                DetailRow("Visits", syncOverview.failedVisits.toString())
                DetailRow("Inventory requests", syncOverview.failedInventoryRequests.toString())
                DetailRow("Sensor readings", syncOverview.failedSensorReadings.toString())
                Text("One-time sync runs after saves, and periodic sync resumes in the background when network is available.")
                syncOverview.lastResultSummary?.let { Text("Last sync: $it", color = MaterialTheme.colorScheme.primary) }
                if (syncOverview.lastResultErrors.isNotEmpty()) {
                    Text("Last sync errors:", color = MaterialTheme.colorScheme.error)
                    syncOverview.lastResultErrors.take(5).forEach { error ->
                        Text("- $error", color = MaterialTheme.colorScheme.error)
                    }
                }
                PrimaryAction(
                    text = when {
                        syncOverview.syncing -> "Retrying..."
                        !officerAccess.canSync -> "Sync unavailable"
                        !syncOverview.isOnline || syncOverview.waitingForConnectivity -> "Waiting for connectivity"
                        syncOverview.failedTotal > 0 -> "Retry Failed / Sync Now"
                        else -> "Sync Now"
                    },
                    onClick = { viewModel.syncNow() },
                    loading = syncOverview.syncing
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null, onLogout: () -> Unit) {
    val context = LocalContext.current
    val profile by viewModel.officerProfile.collectAsState()
    val access by viewModel.officerAccess.collectAsState()
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.registerForPushAlerts()
        }
    }

    if (showLogoutConfirmation) {
        DeleteConfirmationDialog(
            title = "Log out?",
            body = "You can log back in later. Make sure pending farms and reports have synced before switching officers.",
            confirmText = "Logout",
            enabled = true,
            onConfirm = {
                showLogoutConfirmation = false
                onLogout()
            },
            onDismiss = { showLogoutConfirmation = false }
        )
    }

    ScreenScaffold("Settings", onBack) {
        item {
            SectionCard("Officer profile", subtitle = "Account and cloud sync configuration.", icon = Icons.Filled.Person) {
                if (!profile.isFirebaseConfigured) {
                    InlineStatusMessage(
                        "Firebase is not configured. Authentication and cloud sync are unavailable until app/google-services.json is valid.",
                        isError = true
                    )
                }
                DetailRow("Email", profile.email ?: "Not signed in")
                DetailRow("User ID", profile.userId ?: "Not signed in")
                DetailRow("Access status", access.status ?: "unverified")
                DetailRow("Role", access.role ?: "field_officer")
                if (!access.canOperate && access.message.isNotBlank()) {
                    InlineStatusMessage(access.message, isError = true)
                }
                Text("Firebase Authentication separates each officer's farms and reports.")
                SecondaryAction(
                    text = if (access.loading) "Checking access..." else "Refresh access status",
                    onClick = { viewModel.refreshOfficerAccess() },
                    enabled = profile.isLoggedIn && !access.loading
                )
                SecondaryAction(
                    text = "Enable Push Alerts",
                    onClick = {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.registerForPushAlerts()
                        }
                    },
                    enabled = profile.isLoggedIn
                )
                PrimaryAction("Logout", onClick = { showLogoutConfirmation = true })
            }
        }
    }
}

@Composable
private fun ReportSummaryCard(
    report: FieldReportEntity,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleting: Boolean
) {
    SectionCard(report.cropType, subtitle = report.symptoms.ifBlank { "No symptoms recorded" }, icon = Icons.Filled.Assessment) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            SeverityChip(report.severity)
            SyncStatusChip(report.syncStatus)
            if (report.imageLocalUri != null || report.remoteImageUrl != null) {
                StatusChip("Evidence", leadingIcon = Icons.Filled.Image)
            }
            if (report.latitude != null && report.longitude != null) {
                StatusChip("GPS", leadingIcon = Icons.Filled.LocationOn)
            }
        }
        DetailRow("Updated", formatDate(report.updatedAt))
        ActionRow {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) { Text("Edit") }
            TextButton(onClick = onDelete, modifier = Modifier.weight(1f), enabled = !deleting) {
                Text(if (deleting) "Deleting..." else "Delete")
            }
        }
    }
}

@Composable
private fun ReportRiskItem(report: FieldReportEntity) {
    SectionCard(
        title = report.detectedIssue ?: report.cropType,
        subtitle = report.recommendation?.lineSequence()?.firstOrNull().orEmpty().ifBlank { report.symptoms },
        icon = Icons.Filled.Warning
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            SeverityChip(report.severity)
            report.detectionConfidence?.let { StatusChip("$it% confidence", leadingIcon = Icons.Filled.Assessment) }
        }
        report.preventiveMeasures?.takeIf { it.isNotBlank() }?.let {
            DetailRow("Prevention", it)
        }
    }
}

private data class MapMarker(
    val title: String,
    val latitude: Double,
    val longitude: Double
)

private fun isValidMapCoordinate(latitude: Double, longitude: Double): Boolean =
    latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        !(latitude == 0.0 && longitude == 0.0)

private fun GoogleMap.moveCameraToMarkers(markers: List<MapMarker>, mapWidthPx: Int, mapHeightPx: Int) {
    if (markers.isEmpty()) return
    if (markers.size == 1) {
        val only = markers.first()
        moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(only.latitude, only.longitude), 14f))
        return
    }
    val bounds = LatLngBounds.builder().apply {
        markers.forEach { include(LatLng(it.latitude, it.longitude)) }
    }.build()
    val padding = (minOf(mapWidthPx, mapHeightPx) * 0.18f).toInt().coerceIn(64, 160)
    runCatching {
        moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, mapWidthPx, mapHeightPx, padding))
    }.onFailure {
        val first = markers.first()
        moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(first.latitude, first.longitude), 12f))
    }
}

@Composable
private fun AgriScoutMapView(markers: List<MapMarker>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val mapView = remember { MapView(context) }
    val markerSnapshot = remember(markers) { markers.toList() }

    DisposableEffect(lifecycleOwner, mapView) {
        // MapView must receive the full lifecycle, including when Compose mounts
        // while the host Activity is already RESUMED (otherwise tiles stay blank).
        mapView.onCreate(Bundle())
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        when {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) -> {
                mapView.onStart()
                mapView.onResume()
            }
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> {
                mapView.onStart()
            }
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching {
                mapView.onPause()
                mapView.onStop()
                mapView.onDestroy()
            }
        }
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier.clip(MaterialTheme.shapes.large),
        update = { view ->
            view.getMapAsync { googleMap ->
                googleMap.uiSettings.isZoomControlsEnabled = true
                googleMap.uiSettings.isMapToolbarEnabled = true
                googleMap.uiSettings.isCompassEnabled = true
                googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL
                googleMap.clear()
                markerSnapshot.forEach { marker ->
                    googleMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(marker.latitude, marker.longitude))
                            .title(marker.title)
                    )
                }
                val applyCamera = {
                    googleMap.moveCameraToMarkers(
                        markers = markerSnapshot,
                        mapWidthPx = view.width.coerceAtLeast(1),
                        mapHeightPx = view.height.coerceAtLeast(1)
                    )
                }
                if (view.width > 0 && view.height > 0) {
                    applyCamera()
                } else {
                    view.post { applyCamera() }
                }
                googleMap.setOnMapLoadedCallback { applyCamera() }
            }
        }
    )
}

@Composable
private fun DeleteConfirmationDialog(
    title: String,
    body: String,
    confirmText: String,
    enabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = enabled) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    topBar: Boolean = true,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
            if (topBar) {
                CenterAlignedTopAppBar(
                    title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Navigate back")
                            }
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 920.dp),
                contentPadding = PaddingValues(
                    start = AgriScoutSpacing.large,
                    top = AgriScoutSpacing.large,
                    end = AgriScoutSpacing.large,
                    bottom = AgriScoutSpacing.xxLarge
                ),
                verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium),
                content = content
            )
        }
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun RefreshStatus(refreshing: Boolean, error: String?, lastRefreshAt: Long?) {
    if (refreshing) {
        LoadingStatus("Fetching from Firestore. Cached content remains visible.")
    }
    if (error != null) {
        InlineStatusMessage(error, isError = true)
    } else if (lastRefreshAt != null) {
        Text("Last refreshed: ${formatDate(lastRefreshAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FarmCard(
    farm: FarmEntity,
    onOpen: () -> Unit,
    onOpenIoT: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    deleting: Boolean,
    deleteEnabled: Boolean,
    editEnabled: Boolean
) {
    SectionCard(farm.farmName, subtitle = "${farm.cropType} - ${farm.locationText}", icon = Icons.Filled.Agriculture) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            SyncStatusChip(farm.syncStatus)
            StatusChip(farm.landSize.ifBlank { "Land not set" }, leadingIcon = Icons.Filled.Grass)
            if (!farm.assignedDeviceId.isNullOrBlank() || !farm.assignedCameraDeviceId.isNullOrBlank()) {
                StatusChip("IoT linked", leadingIcon = Icons.Filled.Sensors)
            }
        }
        DetailRow("Owner", farm.farmerName)
        DetailRow("Planting date", farm.plantingDate?.let { formatDate(it) } ?: "Not set")
        ActionRow {
            Button(onClick = onOpen, modifier = Modifier.weight(1f)) { Text("Open") }
            OutlinedButton(onClick = onOpenIoT, modifier = Modifier.weight(1f)) { Text("IoT") }
        }
        ActionRow {
            OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), enabled = editEnabled) { Text("Edit") }
            TextButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                enabled = deleteEnabled
            ) { Text(if (deleting) "Deleting..." else "Delete") }
        }
    }
}

@Composable
private fun SeveritySelector(selected: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
        Text("Severity", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            listOf("Low", "Medium", "High", "Critical").forEach { severity ->
                FilterChip(
                    selected = selected.equals(severity, ignoreCase = true),
                    onClick = { onSelect(severity) },
                    label = { Text(severity) },
                    leadingIcon = if (selected.equals(severity, ignoreCase = true)) {
                        { Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun DetectionResultSummary(
    issueType: String?,
    detectedIssue: String?,
    confidence: Int?,
    recommendation: String?,
    preventiveMeasures: String?,
    explanation: String? = null,
    analysisSource: String? = null,
    detectionUpdatedAt: Long? = null
) {
    if (detectedIssue.isNullOrBlank()) return
    val sourceLabel = when (analysisSource?.lowercase()) {
        "model" -> "Image model"
        "fused" -> "Rules + image"
        "heuristic" -> "Heuristic"
        else -> "Rule-based"
    }
    SectionCard("$sourceLabel analysis", subtitle = detectedIssue, icon = Icons.Filled.Search) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            StatusChip(issueType ?: "UNKNOWN", leadingIcon = Icons.Filled.Assessment)
            StatusChip("${confidence ?: 0}% certainty", leadingIcon = Icons.Filled.CheckCircle)
            StatusChip(sourceLabel, leadingIcon = Icons.Filled.Psychology)
        }
        if (!explanation.isNullOrBlank()) {
            DetailRow("Why", explanation)
        }
        if (!recommendation.isNullOrBlank()) {
            DetailRow("Recommendation", recommendation)
        }
        if (!preventiveMeasures.isNullOrBlank()) {
            DetailRow("Preventive measures", preventiveMeasures)
        }
        detectionUpdatedAt?.let {
            Text(
                "Updated ${formatDate(it)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            "Decision support only — confirm locally before applying chemical treatment. Model/rule matches are estimates, not guarantees.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeverityChip(severity: String) {
    val rank = severityRank(severity)
    val colorScheme = MaterialTheme.colorScheme
    val container = when (rank) {
        4 -> colorScheme.errorContainer
        3 -> colorScheme.tertiaryContainer
        2 -> colorScheme.secondaryContainer
        else -> colorScheme.surfaceContainerHigh
    }
    val content = when (rank) {
        4 -> colorScheme.onErrorContainer
        3 -> colorScheme.onTertiaryContainer
        2 -> colorScheme.onSecondaryContainer
        else -> colorScheme.onSurfaceVariant
    }
    StatusChip(
        label = severity.ifBlank { "Not specified" },
        leadingIcon = if (rank >= 3) Icons.Filled.Warning else Icons.Filled.CheckCircle,
        containerColor = container,
        contentColor = content
    )
}

@Composable
private fun SyncStatusChip(status: String) {
    val normalized = status.lowercase(Locale.getDefault())
    val label = when {
        "synced" in normalized -> "Synced"
        "failed" in normalized -> "Failed"
        "pending" in normalized -> "Pending"
        else -> status.ifBlank { "Pending" }
    }
    val icon = when {
        "synced" in normalized -> Icons.Filled.CloudDone
        "failed" in normalized -> Icons.Filled.CloudOff
        else -> Icons.Filled.CloudSync
    }
    StatusChip(label, leadingIcon = icon)
}

@Composable
private fun WarningListItem(warning: WeatherWarningEntity) {
    SectionCard(warning.title, subtitle = warning.affectedArea, icon = Icons.Filled.NotificationsActive) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            SeverityChip(warning.severity)
            StatusChip("Valid until ${formatDate(warning.validUntil)}", leadingIcon = Icons.Filled.Thermostat)
        }
        Text(warning.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherSnapshotSummary(snapshot: WeatherSnapshotEntity?) {
    if (snapshot == null) {
        Text("No live weather snapshot cached yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    SectionCard("Current weather: ${snapshot.locationLabel}", subtitle = snapshot.riskSummary, icon = Icons.Filled.Thermostat) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            StatusChip("${snapshot.temperatureCelsius.toInt()} C", leadingIcon = Icons.Filled.Thermostat)
            StatusChip("${snapshot.humidityPercent}% humidity", leadingIcon = Icons.Filled.Grass)
        }
        DetailRow("Condition", snapshot.condition)
        DetailRow("Wind", "${String.format(Locale.US, "%.1f", snapshot.windSpeedMetersPerSecond)} m/s")
        Text("Fetched ${formatDate(snapshot.fetchedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DiseaseCatalogCard(disease: DiseaseCatalogEntity) {
    SectionCard(disease.diseaseName, subtitle = disease.cropAffected, icon = Icons.AutoMirrored.Filled.MenuBook) {
        SeverityChip(disease.severityGuidance)
        DetailRow("Symptoms", disease.symptoms)
        DetailRow("Treatment", disease.treatment)
        DetailRow("Prevention", disease.prevention)
        Text("Updated ${formatDate(disease.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun List<DiseaseCatalogEntity>.filterCatalog(query: String, filter: String): List<DiseaseCatalogEntity> {
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    val normalizedFilter = filter.trim().lowercase(Locale.getDefault())
    return filter { disease ->
        val searchableText = listOf(
            disease.diseaseName,
            disease.cropAffected,
            disease.symptoms,
            disease.treatment,
            disease.prevention,
            disease.severityGuidance
        ).joinToString(" ").lowercase(Locale.getDefault())
        (normalizedQuery.isBlank() || searchableText.contains(normalizedQuery)) &&
            (normalizedFilter.isBlank() || searchableText.contains(normalizedFilter))
    }
}

private fun List<WeatherWarningEntity>.prioritizedWarnings(): List<WeatherWarningEntity> {
    val now = System.currentTimeMillis()
    val recentThreshold = now - RECENT_WARNING_WINDOW_MS
    return sortedWith(
        compareByDescending<WeatherWarningEntity> { it.validUntil == 0L || it.validUntil >= now }
            .thenByDescending { severityRank(it.severity) }
            .thenByDescending { it.updatedAt >= recentThreshold }
            .thenByDescending { it.updatedAt }
    )
}

private data class CropSchedulePreview(
    val farmName: String,
    val summary: String
)

private fun List<FarmEntity>.cropScheduleItems(now: Long = System.currentTimeMillis()): List<CropSchedulePreview> {
    return mapNotNull { farm ->
        val plantingDate = farm.plantingDate ?: return@mapNotNull null
        if (plantingDate <= 0L || plantingDate > now) return@mapNotNull null
        val ageDays = TimeUnit.MILLISECONDS.toDays(now - plantingDate)
        val nextAction = when {
            ageDays < 14 -> "Seedling check due soon"
            ageDays < 45 -> "Fertilizer and weed inspection window"
            ageDays < 80 -> "Pest scouting and irrigation review"
            ageDays < 110 -> "Flowering/yield assessment"
            else -> "Harvest readiness review"
        }
        CropSchedulePreview(
            farmName = farm.farmName,
            summary = "${farm.cropType}: day $ageDays - $nextAction"
        )
    }.sortedBy { it.summary }
}

private fun severityRank(severity: String): Int = when (severity.trim().lowercase(Locale.getDefault())) {
    "critical", "extreme" -> 4
    "high", "severe" -> 3
    "medium", "moderate" -> 2
    "low", "minor" -> 1
    else -> 0
}

private const val RECENT_WARNING_WINDOW_MS = 7L * 24 * 60 * 60 * 1000

private fun formatDate(value: Long): String {
    if (value <= 0L) return "Not set"
    return SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(value))
}

private fun formatCoordinate(value: Double?): String {
    if (value == null) return "Not captured"
    return String.format(Locale.getDefault(), "%.6f", value)
}
