package com.example.agriscout.ui.screens.field

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ShoppingBasket
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.example.agriscout.camera.ImageFileProvider
import com.example.agriscout.data.local.FarmVisitEntity
import com.example.agriscout.data.local.HarvestListingEntity
import com.example.agriscout.data.local.HarvestRequestEntity
import com.example.agriscout.data.local.InventoryRequestEntity
import com.example.agriscout.data.local.ProductRequestEntity
import com.example.agriscout.data.local.SensorReadingEntity
import com.example.agriscout.data.local.SyncStatus
import com.example.agriscout.data.remote.FarmCameraCapture
import com.example.agriscout.ui.components.ActionRow
import com.example.agriscout.ui.components.CameraXPhotoCapture
import com.example.agriscout.ui.components.CapturedPhotoPreview
import com.example.agriscout.ui.components.visitOutputFile
import com.example.agriscout.ui.components.AgriScoutSpacing
import com.example.agriscout.ui.components.AppTextField
import com.example.agriscout.ui.components.DetailRow
import com.example.agriscout.ui.components.EmptyState
import com.example.agriscout.ui.components.InlineStatusMessage
import com.example.agriscout.ui.components.LoadingStatus
import com.example.agriscout.ui.components.PrimaryAction
import com.example.agriscout.ui.components.SectionCard
import com.example.agriscout.ui.components.StatusChip
import com.example.agriscout.ui.viewmodel.AgriScoutViewModel
import com.example.agriscout.recommendation.Recommendation
import com.example.agriscout.recommendation.RecommendationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SensorDashboardScreen(
    farmId: String,
    viewModel: AgriScoutViewModel,
    onBack: () -> Unit,
    onRecommendations: (String) -> Unit
) {
    val farms by viewModel.farms.collectAsState()
    val state by viewModel.sensorDashboard.collectAsState()
    val farm = farms.firstOrNull { it.id == farmId }
    var previewCapture by remember { mutableStateOf<FarmCameraCapture?>(null) }

    LaunchedEffect(farmId) {
        if (farmId.isNotBlank()) viewModel.openSensorDashboard(farmId)
    }

    previewCapture?.let { capture ->
        CaptureImageDialog(
            imageUrl = capture.imageUrl,
            title = formatTime(capture.capturedAt),
            subtitle = capture.deviceId.ifBlank { "Farm camera" },
            onDismiss = { previewCapture = null }
        )
    }

    FieldScreenScaffold("Farm IoT", onBack) {
        if (farm == null) {
            item { EmptyState("This farm could not be found.", title = "Farm not found") }
        } else {
            item {
                SectionCard(
                    title = farm.farmName,
                    subtitle = "Live sensors & camera · ${farm.cropType}",
                    icon = Icons.Filled.Science
                ) {
                    StatusChip(
                        label = state.dataSourceLabel,
                        leadingIcon = if (state.isLiveData) Icons.Filled.CheckCircle else Icons.Filled.Science
                    )
                    if (state.isStale && state.latestReading != null) {
                        StatusChip("Stale reading", leadingIcon = Icons.Filled.Warning)
                    }
                    DetailRow(
                        "Linked devices",
                        buildString {
                            append(farm.assignedDeviceId ?: "No sensor")
                            append(" + ")
                            append(farm.assignedCameraDeviceId ?: "No camera")
                        }
                    )
                    state.latestReading?.let { reading ->
                        DetailRow("Last sensor update", formatTime(reading.recordedAt))
                    }
                    state.message?.let { InlineStatusMessage(it, isError = true) }
                    if (state.refreshing && state.latestReading == null) {
                        LoadingStatus("Refreshing farm IoT data...")
                    }
                    PrimaryAction(
                        text = if (state.refreshing) "Refreshing..." else "Refresh sensors & camera",
                        onClick = { viewModel.refreshSensorNow(farm.id) },
                        enabled = !state.refreshing
                    )
                    OutlinedButton(
                        onClick = { onRecommendations(farm.id) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Open recommendations") }
                }
            }
            item {
                SectionCard(
                    "Device status",
                    subtitle = "Sensor and camera modules for this farm.",
                    icon = Icons.Filled.CloudSync
                ) {
                    DetailRow(
                        "Sensor",
                        state.sensorModule?.let { "${it.status} · ${it.deviceId}" }
                            ?: (farm.assignedDeviceId?.let { "Linked ($it), awaiting status" } ?: "Not linked")
                    )
                    DetailRow(
                        "Sensor last seen",
                        state.sensorModule?.lastSeen?.let { formatTime(it) }
                            ?: state.latestReading?.recordedAt?.let { formatTime(it) }
                            ?: "—"
                    )
                    DetailRow(
                        "Camera",
                        state.cameraModule?.let { "${it.status} · ${it.deviceId}" }
                            ?: (farm.assignedCameraDeviceId?.let { "Linked ($it), awaiting status" } ?: "Not linked")
                    )
                    DetailRow(
                        "Camera last capture",
                        state.cameraModule?.lastCaptureAt?.let { formatTime(it) }
                            ?: state.captures.firstOrNull()?.capturedAt?.let { formatTime(it) }
                            ?: "—"
                    )
                }
            }
            item {
                val reading = state.latestReading
                if (reading == null) {
                    EmptyState(
                        message = "No sensor readings yet. Wait for the linked ESP32 sensor to ingest, then refresh.",
                        title = "Waiting for sensor data",
                        icon = Icons.Filled.Science
                    )
                } else {
                    SensorReadingCard(reading, isStale = state.isStale)
                }
            }
            item {
                SectionCard(
                    "Camera gallery",
                    subtitle = "Tap any image to enlarge. Cloud captures only — LAN live stream stays on-device.",
                    icon = Icons.Filled.CameraAlt
                ) {
                    if (state.captures.isEmpty()) {
                        Text(
                            "No camera captures synced yet for this farm.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium),
                            contentPadding = PaddingValues(vertical = AgriScoutSpacing.small)
                        ) {
                            items(state.captures.take(12), key = { it.id }) { capture ->
                                CameraCaptureTile(
                                    capture = capture,
                                    onOpen = { previewCapture = capture }
                                )
                            }
                        }
                        state.captures.firstOrNull()?.let { latest ->
                            DetailRow("Latest capture", formatTime(latest.capturedAt))
                            DetailRow(
                                "Camera device",
                                latest.deviceId.ifBlank { farm.assignedCameraDeviceId ?: "—" }
                            )
                            latest.resolution?.takeIf { it.isNotBlank() }?.let {
                                DetailRow("Resolution", it)
                            }
                        }
                    }
                }
            }
            item {
                SectionCard(
                    "Recent sensor trend",
                    subtitle = "Latest readings for this farm.",
                    icon = Icons.Filled.WaterDrop
                ) {
                    if (state.recentReadings.isEmpty()) {
                        Text(
                            "Trend data will appear after a few refresh cycles.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        state.recentReadings.take(8).forEach { reading ->
                            DetailRow(
                                label = formatTime(reading.recordedAt),
                                value = "${reading.soilMoisturePercent.toInt()}% moisture · ${reading.temperatureCelsius.toInt()}°C · ${reading.status}"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraCaptureTile(
    capture: FarmCameraCapture,
    onOpen: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clickable(onClick = onOpen),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!capture.imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = capture.imageUrl,
                contentDescription = "Farm camera capture",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("No image", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            formatTime(capture.capturedAt),
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            capture.deviceId.ifBlank { "camera" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CaptureImageDialog(
    imageUrl: String?,
    title: String,
    subtitle: String,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(AgriScoutSpacing.large),
                verticalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Enlarged farm camera capture",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(320.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Fit
                    )
                } else {
                    Text("Image URL is unavailable.", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun FarmVisitLogScreen(
    farmId: String?,
    viewModel: AgriScoutViewModel,
    onBack: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val farms by viewModel.farms.collectAsState()
    val visits by viewModel.farmVisits.collectAsState()
    val form by viewModel.farmVisitForm.collectAsState()
    val selectedFarmId = form.farmId.ifBlank { farmId.orEmpty() }
    val selectedFarm = farms.firstOrNull { it.id == selectedFarmId }
    val visibleVisits = if (selectedFarmId.isBlank()) visits else visits.filter { it.farmId == selectedFarmId }
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
            viewModel.updateFarmVisitForm { copy(message = "Camera ready. Capture a visit photo.") }
        } else {
            showCamera = false
            viewModel.updateFarmVisitForm {
                copy(message = "Camera permission denied. Enable camera access to attach a visit photo.")
            }
        }
    }
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (permissions.values.any { it }) {
            hasLocationPermission = true
            viewModel.captureFarmVisitLocation()
        } else {
            hasLocationPermission = false
            val activity = context.findActivity()
            val permanentlyDenied = activity != null &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION) &&
                !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_COARSE_LOCATION)
            viewModel.onFarmVisitLocationPermissionDenied(permanentlyDenied = permanentlyDenied)
        }
    }
    fun captureVisitLocation(fromImageCapture: Boolean) {
        hasLocationPermission =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (hasLocationPermission) {
            viewModel.captureFarmVisitLocation()
        } else {
            viewModel.updateFarmVisitForm {
                copy(
                    message = if (fromImageCapture) {
                        "Photo captured. Allow location permission to attach GPS coordinates."
                    } else {
                        "Allow location permission to attach GPS coordinates to this visit."
                    }
                )
            }
            locationPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    LaunchedEffect(farmId) {
        if (!farmId.isNullOrBlank() && form.farmId != farmId) {
            viewModel.prepareFarmVisit(farmId)
        }
    }

    FieldScreenScaffold("Farm Visit Log", onBack) {
        item {
            SectionCard("New visit", subtitle = selectedFarm?.farmName ?: "Select a farm before saving.", icon = Icons.AutoMirrored.Filled.EventNote) {
                if (farms.isNotEmpty()) {
                    Text("Farm", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                        farms.take(4).forEach { farm ->
                            FilterChip(
                                selected = selectedFarmId == farm.id,
                                onClick = { viewModel.prepareFarmVisit(farm.id) },
                                label = { Text(farm.farmName) }
                            )
                        }
                    }
                }
                Text("Crop condition", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                    listOf("Good", "Watch", "Poor", "Critical").forEach { condition ->
                        FilterChip(
                            selected = form.cropCondition == condition,
                            onClick = { viewModel.updateFarmVisitForm { copy(cropCondition = condition) } },
                            label = { Text(condition) }
                        )
                    }
                }
                AppTextField(
                    form.cropConditionDetail,
                    "Crop condition detail",
                    { viewModel.updateFarmVisitForm { copy(cropConditionDetail = it) } },
                    singleLine = false
                )
                Text("Growth stage", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                    listOf("Germination", "Vegetative", "Flowering", "Harvest-ready", "Unknown").forEach { stage ->
                        FilterChip(
                            selected = form.growthStage == stage,
                            onClick = { viewModel.updateFarmVisitForm { copy(growthStage = stage) } },
                            label = { Text(stage) }
                        )
                    }
                }
                AppTextField(
                    form.pestObservations,
                    "Pest observations",
                    { viewModel.updateFarmVisitForm { copy(pestObservations = it) } },
                    singleLine = false
                )
                AppTextField(form.notes, "Visit notes", { viewModel.updateFarmVisitForm { copy(notes = it) } }, singleLine = false)
                AppTextField(
                    form.recommendedActions,
                    "Recommended actions",
                    { viewModel.updateFarmVisitForm { copy(recommendedActions = it) } },
                    singleLine = false
                )
                AppTextField(
                    form.followUpNotes,
                    "Follow-up notes",
                    { viewModel.updateFarmVisitForm { copy(followUpNotes = it) } },
                    singleLine = false
                )
                CapturedPhotoPreview(
                    localUri = form.photoLocalUri,
                    remoteUrl = form.remotePhotoUrl,
                    contentDescription = "Captured visit photo"
                )
                Button(
                    onClick = {
                        if (hasCameraPermission) {
                            showCamera = true
                        } else {
                            cameraPermission.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = if (showCamera) "Retake Visit Photo" else "Capture Visit Photo",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                if (showCamera && hasCameraPermission) {
                    CameraXPhotoCapture(
                        createOutputFile = ImageFileProvider.visitOutputFile(),
                        captureButtonText = "Save Visit Photo",
                        onImageCaptured = { imageUri ->
                            viewModel.updateFarmVisitForm {
                                copy(
                                    photoLocalUri = imageUri,
                                    remotePhotoUrl = null,
                                    message = "Visit photo captured. Capturing GPS coordinates..."
                                )
                            }
                            showCamera = false
                            captureVisitLocation(fromImageCapture = true)
                        },
                        onError = { error ->
                            viewModel.updateFarmVisitForm { copy(message = error) }
                        }
                    )
                }
                DetailRow(
                    "Visit GPS",
                    if (form.latitude != null && form.longitude != null) {
                        "${form.latitude}, ${form.longitude}"
                    } else {
                        "Not captured"
                    }
                )
                DetailRow(
                    "GPS accuracy",
                    form.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured"
                )
                DetailRow("GPS source", form.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
                OutlinedButton(
                    onClick = { captureVisitLocation(fromImageCapture = false) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !form.locationLoading
                ) {
                    Text(if (form.locationLoading) "Capturing visit GPS..." else "Capture Visit GPS")
                }
                form.message?.let { InlineStatusMessage(it, isError = true) }
                PrimaryAction(
                    text = if (form.saving) "Saving Visit..." else "Save Visit",
                    onClick = viewModel::submitFarmVisit,
                    loading = form.saving
                )
            }
        }
        item {
            SectionCard("Visit history", subtitle = "Offline-first notes that sync to Firebase.", icon = Icons.AutoMirrored.Filled.EventNote) {
                if (visibleVisits.isEmpty()) {
                    Text("No visits recorded yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(visibleVisits) { visit ->
            FarmVisitCard(visit, farms.firstOrNull { it.id == visit.farmId }?.farmName)
        }
    }
}

@Composable
fun InventoryRequestScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val farms by viewModel.farms.collectAsState()
    val form by viewModel.inventoryRequestForm.collectAsState()
    val requests by viewModel.inventoryRequests.collectAsState()
    val catalog by viewModel.inventoryCatalog.collectAsState()
    val stock = viewModel.stockPreview(form.itemType, form.inventoryItemId)

    LaunchedEffect(Unit) {
        viewModel.refreshInventoryCatalog()
    }

    FieldScreenScaffold("Inventory Requests", onBack) {
        item {
            SectionCard(
                "New request",
                subtitle = if (stock.isLiveData) {
                    "Live stock from the administrator ERP catalog."
                } else {
                    "Sync when online to load live stock from the ERP catalog."
                },
                icon = Icons.Filled.Inventory
            ) {
                if (catalog.refreshing) {
                    LoadingStatus("Refreshing live inventory...")
                }
                catalog.refreshError?.let { InlineStatusMessage(it, isError = true) }
                if (farms.isNotEmpty()) {
                    Text("Farm", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                        farms.take(3).forEach { farm ->
                            FilterChip(
                                selected = form.farmId == farm.id,
                                onClick = { viewModel.updateInventoryRequestForm { copy(farmId = farm.id) } },
                                label = { Text(farm.farmName) }
                            )
                        }
                    }
                }
                Text("Item type", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                    listOf("Fertilizers", "Chemicals", "Seeds", "Equipment").forEach { type ->
                        FilterChip(
                            selected = form.itemType == type,
                            onClick = { viewModel.updateInventoryRequestForm { copy(itemType = type) } },
                            label = { Text(type) }
                        )
                    }
                }
                if (stock.items.isNotEmpty()) {
                    Text("Select item", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
                        stock.items.forEach { item ->
                            FilterChip(
                                selected = form.inventoryItemId == item.id,
                                onClick = {
                                    viewModel.updateInventoryRequestForm { copy(inventoryItemId = item.id) }
                                },
                                label = { Text("${item.name} (${item.quantity} ${item.unit})") }
                            )
                        }
                    }
                }
                DetailRow(
                    "Available stock",
                    if (stock.isLiveData) {
                        "${stock.availableStock} ${stock.unit}"
                    } else {
                        "Unavailable offline"
                    }
                )
                stock.selectedItem?.let { item ->
                    DetailRow("Selected item", item.name)
                    if (item.quantity <= item.reorderLevel) {
                        InlineStatusMessage("This item is at or below the reorder level.", isError = true)
                    }
                }
                stock.alternativeItem?.let { DetailRow("Suggested alternative", it) }
                AppTextField(form.quantity, "Quantity", { viewModel.updateInventoryRequestForm { copy(quantity = it) } })
                AppTextField(form.reason, "Reason", { viewModel.updateInventoryRequestForm { copy(reason = it) } }, singleLine = false)
                form.message?.let { InlineStatusMessage(it, isError = true) }
                ActionRow {
                    OutlinedButton(onClick = viewModel::refreshInventoryCatalog, enabled = !catalog.refreshing) {
                        Text(if (catalog.refreshing) "Refreshing..." else "Refresh stock")
                    }
                    PrimaryAction(
                        text = if (form.submitting) "Submitting..." else "Submit Request",
                        onClick = viewModel::submitInventoryRequest,
                        loading = form.submitting
                    )
                }
            }
        }
        item {
            SectionCard("Request history", subtitle = "Local-first status tracking for field officers.", icon = Icons.Filled.Inventory) {
                if (requests.isEmpty()) {
                    Text("No inventory requests yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        items(requests) { request ->
            InventoryRequestCard(request)
        }
    }
}

@Composable
fun SupplierRequestsScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val requests by viewModel.productRequests.collectAsState()
    val refreshing by viewModel.marketplaceFollowUpRefreshing.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refreshMarketplaceFollowUps()
    }

    FieldScreenScaffold("Supplier Requests", onBack) {
        item {
            SectionCard(
                "My supplier requests",
                subtitle = "Follow-up on recommendation-driven procurement. Browsing the full catalog stays on the admin/supplier web portals.",
                icon = Icons.Filled.Store
            ) {
                Text(
                    "Create new requests from farm Recommendations when a matched supplier offer appears.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionRow {
                    OutlinedButton(
                        onClick = viewModel::refreshMarketplaceFollowUps,
                        enabled = !refreshing
                    ) {
                        Text(if (refreshing) "Refreshing..." else "Refresh status")
                    }
                }
            }
        }
        if (requests.isEmpty()) {
            item {
                EmptyState(
                    message = "No supplier product requests yet. Open Recommendations on a farm to request a matched offer.",
                    title = "No supplier requests",
                    icon = Icons.Filled.Store
                )
            }
        } else {
            items(requests) { request ->
                SupplierProductRequestCard(
                    request = request,
                    onCancel = { viewModel.cancelSupplierProductRequest(request.id) }
                )
            }
        }
    }
}

@Composable
fun HarvestFollowUpScreen(viewModel: AgriScoutViewModel, onBack: (() -> Unit)? = null) {
    val listings by viewModel.officerHarvestListings.collectAsState()
    val harvestRequests by viewModel.officerHarvestRequests.collectAsState()
    val refreshing by viewModel.marketplaceFollowUpRefreshing.collectAsState()
    val requestsByListing = remember(harvestRequests) {
        harvestRequests.groupBy { it.harvestListingId }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshMarketplaceFollowUps()
    }

    FieldScreenScaffold("Harvest Listings", onBack) {
        item {
            SectionCard(
                "My harvest listings",
                subtitle = "Publish from HARVEST recommendations, then respond to buyer interest here.",
                icon = Icons.Filled.ShoppingBasket
            ) {
                Text(
                    "Listings stay non-binding until you and the buyer agree offline. Admin verification is required before buyers see officer-published listings.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                ActionRow {
                    OutlinedButton(
                        onClick = viewModel::refreshMarketplaceFollowUps,
                        enabled = !refreshing
                    ) {
                        Text(if (refreshing) "Refreshing..." else "Refresh listings")
                    }
                }
            }
        }
        if (listings.isEmpty()) {
            item {
                EmptyState(
                    message = "No harvest listings yet. Open Recommendations and publish a HARVEST prediction.",
                    title = "No harvest listings",
                    icon = Icons.Filled.ShoppingBasket
                )
            }
        } else {
            items(listings) { listing ->
                HarvestListingFollowUpCard(
                    listing = listing,
                    requests = requestsByListing[listing.id].orEmpty() +
                        requestsByListing[listing.remoteId].orEmpty()
                            .filterNot { request ->
                                requestsByListing[listing.id].orEmpty().any { it.id == request.id }
                            },
                    onRespond = { requestId, status, note ->
                        viewModel.respondToHarvestRequest(requestId, status, note)
                    }
                )
            }
        }
        val orphanRequests = harvestRequests.filter { request ->
            listings.none { listing ->
                listing.id == request.harvestListingId || listing.remoteId == request.harvestListingId
            }
        }
        if (orphanRequests.isNotEmpty()) {
            item {
                SectionCard(
                    "Other buyer interest",
                    subtitle = "Requests linked to listings not cached on this device.",
                    icon = Icons.Filled.ShoppingBasket
                ) {}
            }
            items(orphanRequests) { request ->
                HarvestBuyerInterestCard(
                    request = request,
                    onRespond = { status, note ->
                        viewModel.respondToHarvestRequest(request.id, status, note)
                    }
                )
            }
        }
    }
}

@Composable
private fun SupplierProductRequestCard(
    request: ProductRequestEntity,
    onCancel: () -> Unit
) {
    val canCancel = request.status.equals("created", ignoreCase = true)
    SectionCard(
        request.productName,
        subtitle = "${request.supplierName} · ${request.status}",
        icon = Icons.Filled.Store
    ) {
        StatusChip(request.status, leadingIcon = Icons.Filled.CheckCircle)
        StatusChip("Sync: ${request.syncStatus}", leadingIcon = syncIcon(request.syncStatus))
        DetailRow("Quantity", "${request.quantity} ${request.unit}")
        request.productCategory.takeIf { it.isNotBlank() }?.let { DetailRow("Category", it) }
        request.agriculturalNeed?.takeIf { it.isNotBlank() }?.let { DetailRow("Need", it) }
        request.supplierNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Supplier note", it) }
        request.adminNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Admin note", it) }
        Text(
            "Updated ${formatTime(request.updatedAt)}",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (canCancel) {
            TextButton(onClick = onCancel) { Text("Cancel request") }
        }
    }
}

@Composable
private fun HarvestListingFollowUpCard(
    listing: HarvestListingEntity,
    requests: List<HarvestRequestEntity>,
    onRespond: (requestId: String, status: String, note: String?) -> Unit
) {
    val verification = if (listing.verified) "Verified for buyers" else "Awaiting admin verification"
    SectionCard(
        "${listing.cropType} · ${listing.farmName.ifBlank { "Farm" }}",
        subtitle = "${listing.status} · $verification",
        icon = Icons.Filled.ShoppingBasket
    ) {
        StatusChip(listing.status, leadingIcon = Icons.Filled.CheckCircle)
        StatusChip(
            if (listing.verified) "Verified" else "Unverified",
            leadingIcon = Icons.Filled.Assessment
        )
        StatusChip("Sync: ${listing.syncStatus}", leadingIcon = syncIcon(listing.syncStatus))
        listing.harvestPeriodLabel.takeIf { it.isNotBlank() }?.let {
            DetailRow("Window", it)
        }
        listing.estimatedQuantityMax?.let { max ->
            DetailRow(
                "Estimated yield",
                "${"%.1f".format(max)} ${listing.quantityUnit}"
            )
        }
        listing.adminNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Admin note", it) }
        if (requests.isEmpty()) {
            Text(
                "No buyer interest yet.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text("Buyer interest", style = MaterialTheme.typography.titleSmall)
            requests.forEach { request ->
                HarvestBuyerInterestCard(
                    request = request,
                    onRespond = { status, note -> onRespond(request.id, status, note) }
                )
            }
        }
    }
}

@Composable
private fun HarvestBuyerInterestCard(
    request: HarvestRequestEntity,
    onRespond: (status: String, note: String?) -> Unit
) {
    var note by remember(request.id) { mutableStateOf(request.officerNote.orEmpty()) }
    val canRespond = request.status.lowercase(Locale.getDefault()) in setOf(
        "interested",
        "requested",
        "under_review",
        "negotiated"
    )
    SectionCard(
        request.buyerName.ifBlank { "Buyer" },
        subtitle = "${request.status} · qty ${request.requestedQuantity} ${request.quantityUnit}",
        icon = Icons.Filled.ShoppingBasket
    ) {
        StatusChip(request.status, leadingIcon = Icons.Filled.CheckCircle)
        StatusChip("Sync: ${request.syncStatus}", leadingIcon = syncIcon(request.syncStatus))
        request.message.takeIf { it.isNotBlank() }?.let { DetailRow("Message", it) }
        request.buyerNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Buyer note", it) }
        request.adminNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Admin note", it) }
        request.officerNote?.takeIf { it.isNotBlank() }?.let { DetailRow("Your note", it) }
        if (canRespond) {
            AppTextField(
                value = note,
                label = "Officer note (optional)",
                onValueChange = { note = it },
                singleLine = false
            )
            ActionRow {
                OutlinedButton(
                    onClick = { onRespond("rejected", note.takeIf { it.isNotBlank() }) }
                ) {
                    Text("Decline")
                }
                OutlinedButton(
                    onClick = { onRespond("under_review", note.takeIf { it.isNotBlank() }) }
                ) {
                    Text("Under review")
                }
                PrimaryAction(
                    text = "Accept",
                    onClick = { onRespond("accepted", note.takeIf { it.isNotBlank() }) }
                )
            }
        }
    }
}

@Composable
fun RecommendationsScreen(
    farmId: String,
    viewModel: AgriScoutViewModel,
    onBack: () -> Unit,
    onOpenSensors: (String) -> Unit
) {
    val farms by viewModel.farms.collectAsState()
    val state by viewModel.recommendationState.collectAsState()
    val farm = farms.firstOrNull { it.id == farmId }

    LaunchedEffect(farmId) {
        if (farmId.isNotBlank()) viewModel.openRecommendations(farmId)
    }

    FieldScreenScaffold("Recommendations", onBack) {
        if (farm == null) {
            item { EmptyState("This farm could not be found.", title = "Farm not found") }
        } else {
            item {
                SectionCard(farm.farmName, subtitle = "Cultivation plan for ${farm.cropType}", icon = Icons.Filled.Psychology) {
                    DetailRow("Crop stage", state.lifecycleEstimate.stage.label)
                    DetailRow("Lifecycle note", state.lifecycleEstimate.summary)
                    Text(
                        "This week's tasks come from the crop calendar (planting date + stage), adjusted by sensor readings, weather, and inventory stock. Synced to the admin dashboard.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(onClick = { onOpenSensors(farm.id) }) { Text("View sensors & camera") }
                }
            }
            if (state.recommendations.isEmpty()) {
                item {
                    EmptyState(
                        message = "No recommendations generated yet. Refresh sensor data or update farm planting date.",
                        title = "No recommendations",
                        icon = Icons.Filled.Psychology
                    )
                }
            } else {
                items(state.recommendations) { recommendation ->
                    RecommendationCard(
                        recommendation = recommendation,
                        supplierOffers = recommendation.productCategory
                            ?.let { state.supplierOffersByCategory[it] }
                            .orEmpty(),
                        productRequests = state.productRequests.filter { request ->
                            request.productCategory.equals(
                                recommendation.productCategory,
                                ignoreCase = true
                            )
                        },
                        harvestListings = if (recommendation.type == RecommendationType.HARVEST) {
                            state.harvestListings
                        } else {
                            emptyList()
                        },
                        harvestRequests = if (recommendation.type == RecommendationType.HARVEST) {
                            state.harvestRequests
                        } else {
                            emptyList()
                        },
                        onRequestProduct = { product ->
                            viewModel.requestSupplierProduct(
                                farmId = farmId,
                                recommendation = recommendation,
                                product = product
                            )
                        },
                        onPublishHarvestListing = {
                            viewModel.publishHarvestListing(
                                farmId = farmId,
                                recommendation = recommendation
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SensorReadingCard(reading: SensorReadingEntity, isStale: Boolean = false) {
    val sourceLabel = if (reading.source.equals("device", ignoreCase = true)) "Live device" else "Simulated"
    SectionCard(
        title = if (isStale) "Sensor readings (stale)" else "Sensor readings",
        subtitle = "Updated ${formatTime(reading.recordedAt)} · $sourceLabel",
        icon = statusIcon(reading.status)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.small)) {
            StatusChip(reading.status, leadingIcon = statusIcon(reading.status))
            StatusChip(sourceLabel, leadingIcon = Icons.Filled.Science)
        }
        if (isStale) {
            Text(
                "This reading is older than one minute. The sensor module may be offline, or sync has not pulled the latest ingest yet.",
                color = MaterialTheme.colorScheme.error
            )
        }
        reading.deviceId?.takeIf { it.isNotBlank() }?.let { DetailRow("Device", it) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)
        ) {
            SensorMetricTile(
                label = "Soil moisture",
                value = "${reading.soilMoisturePercent.toInt()}%",
                icon = Icons.Filled.WaterDrop,
                modifier = Modifier.weight(1f)
            )
            SensorMetricTile(
                label = "Temperature",
                value = "${reading.temperatureCelsius.toInt()}°C",
                icon = Icons.Filled.Thermostat,
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium)
        ) {
            SensorMetricTile(
                label = "Humidity",
                value = "${reading.humidityPercent.toInt()}%",
                icon = Icons.Filled.WaterDrop,
                modifier = Modifier.weight(1f)
            )
            SensorMetricTile(
                label = "Light",
                value = "${reading.lightIntensityLux.toInt()} lx",
                icon = Icons.Filled.LightMode,
                modifier = Modifier.weight(1f)
            )
        }
        SensorMetricTile(
            label = "Water level",
            value = "${reading.waterLevelPercent.toInt()}%",
            icon = Icons.Filled.WaterDrop,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun FarmVisitCard(visit: FarmVisitEntity, farmName: String?) {
    SectionCard(farmName ?: "Farm visit", subtitle = "Condition: ${visit.cropCondition}", icon = Icons.AutoMirrored.Filled.EventNote) {
        StatusChip("Sync: ${visit.syncStatus}", leadingIcon = syncIcon(visit.syncStatus))
        if (visit.photoLocalUri != null || visit.remotePhotoUrl != null) {
            CapturedPhotoPreview(
                localUri = visit.photoLocalUri,
                remoteUrl = visit.remotePhotoUrl,
                contentDescription = "Visit photo"
            )
        }
        DetailRow("Notes", visit.notes)
        if (visit.growthStage.isNotBlank()) DetailRow("Growth stage", visit.growthStage)
        if (visit.cropConditionDetail.isNotBlank()) DetailRow("Condition detail", visit.cropConditionDetail)
        if (visit.pestObservations.isNotBlank()) DetailRow("Pests", visit.pestObservations)
        if (visit.recommendedActions.isNotBlank()) DetailRow("Recommended actions", visit.recommendedActions)
        if (visit.followUpNotes.isNotBlank()) DetailRow("Follow-up", visit.followUpNotes)
        DetailRow("GPS", if (visit.latitude != null && visit.longitude != null) "${visit.latitude}, ${visit.longitude}" else "Not captured")
        DetailRow("GPS accuracy", visit.gpsAccuracyMeters?.let { "±${it.toInt()} m" } ?: "Not captured")
        DetailRow("GPS source", visit.gpsSource?.ifBlank { "Not captured" } ?: "Not captured")
        Text("Created ${formatTime(visit.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SensorMetricTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(AgriScoutSpacing.medium),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun SensorMetric(label: String, value: String, icon: ImageVector) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AgriScoutSpacing.medium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        Text(label, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun InventoryRequestCard(request: InventoryRequestEntity) {
    SectionCard(request.itemType, subtitle = "Status: ${request.status}", icon = Icons.Filled.Inventory) {
        StatusChip(request.status, leadingIcon = Icons.Filled.CheckCircle)
        StatusChip("Sync: ${request.syncStatus}", leadingIcon = syncIcon(request.syncStatus))
        DetailRow("Quantity", request.quantity)
        DetailRow("Reason", request.reason)
        DetailRow("Stock at request", "${request.availableStock} units")
        request.alternativeItem?.let { DetailRow("Alternative", it) }
        request.itemName?.let { DetailRow("Requested item", it) }
        request.approvalNote?.let { DetailRow("Administrator note", it) }
        request.reviewedAt?.let { DetailRow("Reviewed", formatTime(it)) }
        request.issuedQuantity?.let { DetailRow("Issued quantity", it.toString()) }
        Text(
            "Approval decisions are managed by administrators in the ERP dashboard.",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("Created ${formatTime(request.createdAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun syncIcon(status: String): ImageVector {
    return when (status) {
        SyncStatus.SYNCED -> Icons.Filled.CheckCircle
        SyncStatus.FAILED -> Icons.Filled.CloudOff
        else -> Icons.Filled.CloudSync
    }
}

@Composable
private fun RecommendationCard(
    recommendation: Recommendation,
    supplierOffers: List<com.example.agriscout.marketplace.SupplierProductOffer> = emptyList(),
    productRequests: List<com.example.agriscout.data.local.ProductRequestEntity> = emptyList(),
    harvestListings: List<com.example.agriscout.data.local.HarvestListingEntity> = emptyList(),
    harvestRequests: List<com.example.agriscout.data.local.HarvestRequestEntity> = emptyList(),
    onRequestProduct: (com.example.agriscout.data.local.SupplierProductEntity) -> Unit = {},
    onPublishHarvestListing: () -> Unit = {}
) {
    val subtitle = buildString {
        append(recommendation.type.name.replace('_', ' '))
        recommendation.activityStatus?.let { append(" · ${it.replace('_', ' ')}") }
        recommendation.stage?.let { append(" · ${it.replace('_', ' ')}") }
        recommendation.source.takeIf { it.isNotBlank() }?.let { append(" · $it") }
    }
    SectionCard(recommendation.title, subtitle = subtitle, icon = statusIcon(recommendation.priority)) {
        StatusChip(recommendation.priority, leadingIcon = statusIcon(recommendation.priority))
        recommendation.confidence?.let { certainty ->
            StatusChip("$certainty% certainty", leadingIcon = Icons.Filled.Assessment)
        }
        recommendation.activityStatus?.let { status ->
            StatusChip(status.replaceFirstChar { it.titlecase() }, leadingIcon = statusIcon(recommendation.priority))
        }
        recommendation.source.takeIf { it.isNotBlank() }?.let { source ->
            StatusChip(
                when (source.lowercase(Locale.getDefault())) {
                    "rules" -> "Rules"
                    "heuristic" -> "Heuristic"
                    "calendar_trigger" -> "Calendar trigger"
                    "model" -> "Model"
                    else -> source.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                },
                leadingIcon = Icons.Filled.Psychology
            )
        }
        Text(recommendation.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        recommendation.issueSignal?.takeIf { it.isNotBlank() }?.let { DetailRow("Issue / signal", it) }
        recommendation.agriculturalNeed?.takeIf { it.isNotBlank() }?.let { DetailRow("Need", it) }
        recommendation.recommendedAction?.takeIf { it.isNotBlank() }?.let { DetailRow("Action", it) }
        recommendation.productCategory?.takeIf { it.isNotBlank() }?.let { DetailRow("Product category", it) }
        recommendation.rationale?.takeIf { it.isNotBlank() }?.let { DetailRow("Why", it) }
        recommendation.dayOfSeason?.let { DetailRow("Day of season", it.toString()) }
        recommendation.suggestedQuantity?.let { quantity ->
            val unit = recommendation.quantityUnit.orEmpty()
            DetailRow(
                if (recommendation.type == RecommendationType.HARVEST) "Estimated yield (upper)" else "Suggested quantity",
                "${"%.1f".format(quantity)}${if (unit.isNotBlank()) " $unit" else ""}"
            )
        }
        recommendation.suggestedItemName?.let { item ->
            DetailRow("In-stock option", item)
        }
        recommendation.alternativeItemName?.let { item ->
            DetailRow("Alternative in stock", item)
        }
        if (supplierOffers.isNotEmpty()) {
            Text("Approved supplier options", style = MaterialTheme.typography.titleSmall)
            supplierOffers.take(3).forEach { offer ->
                DetailRow(
                    offer.product.name,
                    "${offer.product.supplierName} · ${offer.product.availabilityStatus}" +
                        (offer.product.packSize.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "")
                )
                Text(
                    offer.matchReason,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                TextButton(onClick = { onRequestProduct(offer.product) }) {
                    Text("Request from supplier")
                }
            }
        }
        productRequests.take(3).forEach { request ->
            DetailRow(
                "Request · ${request.productName}",
                "${request.status} · qty ${request.quantity} ${request.unit}"
            )
        }
        if (recommendation.type == RecommendationType.HARVEST) {
            Text("Harvest marketplace", style = MaterialTheme.typography.titleSmall)
            Text(
                "Publish this prediction as a non-binding harvest listing for commercial buyers. " +
                    "Admin verification is required before buyers can see it.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall
            )
            TextButton(onClick = onPublishHarvestListing) {
                Text("Publish harvest listing")
            }
            harvestListings.take(3).forEach { listing ->
                val verification =
                    if (listing.verified) "verified" else "awaiting verification"
                DetailRow(
                    "Listing · ${listing.cropType}",
                    "${listing.status} · $verification · ${listing.syncStatus}"
                )
            }
            harvestRequests.take(3).forEach { request ->
                DetailRow(
                    "Buyer · ${request.buyerName.ifBlank { request.buyerId }}",
                    "${request.status} · qty ${request.requestedQuantity} ${request.quantityUnit}"
                )
            }
        }
        Text(
            "Decision support estimate — confirm in the field before acting.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

private fun statusIcon(status: String): ImageVector {
    val normalized = status.lowercase(Locale.getDefault())
    return if ("critical" in normalized || "high" in normalized || "warning" in normalized) {
        Icons.Filled.Warning
    } else {
        Icons.Filled.CheckCircle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldScreenScaffold(
    title: String,
    onBack: (() -> Unit)? = null,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    Scaffold(
        topBar = {
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

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
