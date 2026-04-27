import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.chatbot.Result
import org.webrtc.SurfaceViewRenderer

@OptIn(ExperimentalMaterial3Api::class)
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun CallScreen(
    roomId: String,
    roomName: String,
    requestedType: String,
    autoStart: Boolean,
    onBack: () -> Unit,
    callViewModel: CallViewModel = viewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember(roomId) {
        WebRtcCallController(
            appContext = context.applicationContext,
            signalingRepository = CallSignalingRepository(Injection.instance())
        )
    }

    val room by callViewModel.room.observeAsState()
    val currentUser by callViewModel.currentUser.observeAsState()
    val participants by callViewModel.participants.observeAsState(emptyList())
    val callState by callViewModel.callState.observeAsState(Result.Idle)
    val webRtcState by controller.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var permissionsGranted by remember { mutableStateOf(false) }
    var hasRequestedJoin by remember(roomId) { mutableStateOf(false) }
    var localRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var remoteRenderer by remember { mutableStateOf<SurfaceViewRenderer?>(null) }
    var hasBeenInCall by remember(roomId) { mutableStateOf(false) }

    val requiredPermissions = remember(requestedType) {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (requestedType == "video") add(Manifest.permission.CAMERA)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionsGranted = requiredPermissions.all { grants[it] == true || ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }

    LaunchedEffect(roomId) {
        callViewModel.initialize(roomId, roomName, requestedType, autoStart)
    }

    LaunchedEffect(requiredPermissions) {
        val alreadyGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        permissionsGranted = alreadyGranted
        if (!alreadyGranted) permissionLauncher.launch(requiredPermissions.toTypedArray())
    }

    LaunchedEffect(callState) {
        when (val state = callState) {
            is Result.Error -> {
                snackbarHostState.showSnackbar(state.exception.message ?: "Call action failed")
                callViewModel.clearState()
            }
            is Result.Success -> {
                if (state.data == "left") onBack()
                callViewModel.clearState()
            }
            else -> Unit
        }
    }

    LaunchedEffect(localRenderer, remoteRenderer) {
        controller.bindRenderers(localRenderer, remoteRenderer)
    }

    val self = participants.firstOrNull { it.userId == currentUser?.email }
    val callType = room?.activeCallType?.ifBlank { requestedType } ?: requestedType

    LaunchedEffect(permissionsGranted, currentUser?.email, room?.id, room?.activeCallHost, callType, autoStart) {
        if (!permissionsGranted || currentUser == null || room == null || hasRequestedJoin) return@LaunchedEffect
        if (autoStart || room?.activeCallType?.isNotBlank() == true) {
            hasRequestedJoin = true
            callViewModel.ensureJoined(callType)
        }
    }

    LaunchedEffect(self?.userId, room?.activeCallHost, room?.activeCallType, permissionsGranted) {
        val activeRoom = room ?: return@LaunchedEffect
        val user = currentUser ?: return@LaunchedEffect
        val selfParticipant = self ?: return@LaunchedEffect
        if (!permissionsGranted) return@LaunchedEffect

        hasBeenInCall = true
        controller.connect(
            scope = scope,
            room = activeRoom,
            user = user,
            isCaller = activeRoom.activeCallHost.ifBlank { user.email } == user.email,
            enableVideo = activeRoom.activeCallType.ifBlank { requestedType } == "video"
        )

        if (selfParticipant.connectionState != webRtcState.connectionLabel) {
            callViewModel.updateConnectionState(webRtcState.connectionLabel)
        }
    }

    LaunchedEffect(webRtcState.connectionLabel, self?.connectionState) {
        if (self != null && self.connectionState != webRtcState.connectionLabel) {
            callViewModel.updateConnectionState(webRtcState.connectionLabel)
        }
    }

    LaunchedEffect(hasBeenInCall, room?.activeCallType) {
        if (hasBeenInCall && room?.activeCallType?.isBlank() == true) {
            snackbarHostState.showSnackbar("Call ended")
            onBack()
        }
    }

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(room?.name ?: roomName, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${participants.size} connected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { callViewModel.leaveCall() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CallStage(
                participant = self,
                roomName = room?.name ?: roomName,
                callType = callType,
                connectionLabel = webRtcState.connectionLabel,
                remoteRenderer = remoteRenderer,
                localRenderer = localRenderer,
                onLocalRendererCreated = { localRenderer = it },
                onRemoteRendererCreated = { remoteRenderer = it }
            )

            CallStats(
                participants = participants.size,
                host = room?.activeCallHost.orEmpty(),
                type = callType
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(participants, key = { it.userId }) { participant ->
                    ParticipantCard(participant = participant)
                }
            }

            CallControls(
                isMuted = !webRtcState.isMicEnabled,
                isCameraEnabled = webRtcState.isCameraEnabled,
                isHandRaised = self?.isHandRaised ?: false,
                callType = callType,
                onToggleMute = {
                    controller.toggleMute()
                    callViewModel.toggleMute()
                },
                onToggleCamera = {
                    controller.toggleCamera()
                    callViewModel.toggleCamera()
                },
                onToggleHand = { callViewModel.toggleHand() },
                onLeave = { callViewModel.leaveCall() }
            )
        }
    }
}

@Composable
private fun CallStage(
    participant: CallParticipant?,
    roomName: String,
    callType: String,
    connectionLabel: String,
    remoteRenderer: SurfaceViewRenderer?,
    localRenderer: SurfaceViewRenderer?,
    onLocalRendererCreated: (SurfaceViewRenderer) -> Unit,
    onRemoteRendererCreated: (SurfaceViewRenderer) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.72f)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.72f)
                        )
                    )
                )
        ) {
            if (callType == "video") {
                if (remoteRenderer == null) {
                    FullSizeRenderer(onRendererCreated = onRemoteRendererCreated)
                } else {
                    AndroidView(
                        factory = { remoteRenderer },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                ) {
                    if (localRenderer == null) {
                        VideoRendererTile(onRendererCreated = onLocalRendererCreated)
                    } else {
                        AndroidView(
                            factory = { localRenderer },
                            modifier = Modifier
                                .size(width = 116.dp, height = 164.dp)
                                .background(Color.Black, RoundedCornerShape(18.dp))
                        )
                    }
                }
            } else {
                VideoPlaceholder(roomName = roomName, connectionLabel = connectionLabel)
            }

            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(18.dp),
                color = Color.Black.copy(alpha = 0.24f)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Live ${callType.replaceFirstChar(Char::titlecase)} Call", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(participant?.displayName ?: roomName, color = Color.White.copy(alpha = 0.86f))
                    Text(connectionLabel.replaceFirstChar(Char::titlecase), color = Color.White.copy(alpha = 0.72f), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun VideoPlaceholder(roomName: String, connectionLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.height(1.dp))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .background(Color.White.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(roomName.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Text(roomName, color = Color.White, fontWeight = FontWeight.SemiBold)
            Text(connectionLabel.replaceFirstChar(Char::titlecase), color = Color.White.copy(alpha = 0.72f))
        }
        Spacer(modifier = Modifier.height(1.dp))
    }
}

@Composable
private fun VideoRendererTile(onRendererCreated: (SurfaceViewRenderer) -> Unit) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).also(onRendererCreated)
        },
        modifier = Modifier
            .size(width = 116.dp, height = 164.dp)
            .background(Color.Black, RoundedCornerShape(18.dp))
    )
}

@Composable
private fun FullSizeRenderer(onRendererCreated: (SurfaceViewRenderer) -> Unit) {
    AndroidView(
        factory = { context ->
            SurfaceViewRenderer(context).also(onRendererCreated)
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun CallStats(participants: Int, host: String, type: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatTile(modifier = Modifier.weight(1f), title = "Type", value = type.replaceFirstChar(Char::titlecase))
        StatTile(modifier = Modifier.weight(1f), title = "Participants", value = participants.toString())
        StatTile(modifier = Modifier.weight(1f), title = "Host", value = host.substringBefore('@').ifBlank { "-" })
    }
}

@Composable
private fun StatTile(modifier: Modifier, title: String, value: String) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ParticipantCard(participant: CallParticipant) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(participant.displayName.take(1).uppercase(), fontWeight = FontWeight.Bold)
                }
                Column {
                    Text(
                        buildString {
                            append(participant.displayName)
                            if (participant.isHost) append(" • host")
                        },
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        participant.connectionState.replaceFirstChar(Char::titlecase),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (participant.isHandRaised) {
                    Icon(Icons.Default.PanTool, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                }
                Icon(
                    if (participant.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                    contentDescription = null,
                    tint = if (participant.isMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                Icon(
                    if (participant.isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff,
                    contentDescription = null,
                    tint = if (participant.isCameraEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CallControls(
    isMuted: Boolean,
    isCameraEnabled: Boolean,
    isHandRaised: Boolean,
    callType: String,
    onToggleMute: () -> Unit,
    onToggleCamera: () -> Unit,
    onToggleHand: () -> Unit,
    onLeave: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(onClick = onToggleMute) {
                Icon(if (isMuted) Icons.Default.MicOff else Icons.Default.Mic, contentDescription = "Toggle mic")
            }
            FilledIconButton(onClick = onToggleCamera, enabled = callType == "video") {
                Icon(if (isCameraEnabled) Icons.Default.Videocam else Icons.Default.VideocamOff, contentDescription = "Toggle camera")
            }
            FilledIconButton(onClick = onToggleHand) {
                Icon(if (isHandRaised) Icons.Default.Campaign else Icons.Default.PanTool, contentDescription = "Raise hand")
            }
            FilledIconButton(
                onClick = onLeave,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Icon(Icons.Default.CallEnd, contentDescription = "Leave call")
            }
        }
    }
}
