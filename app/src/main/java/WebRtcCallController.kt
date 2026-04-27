import android.content.Context
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoCapturer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.util.concurrent.atomic.AtomicBoolean

class WebRtcCallController(
    private val appContext: Context,
    private val signalingRepository: CallSignalingRepository
) {

    private val _uiState = MutableStateFlow(WebRtcUiState())
    val uiState: StateFlow<WebRtcUiState> = _uiState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private val eglBase: EglBase = EglBase.create()
    private var factoryInitialized = false
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var audioSource: AudioSource? = null
    private var audioTrack: AudioTrack? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var remoteVideoTrack: VideoTrack? = null
    private var videoCapturer: VideoCapturer? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var localRenderer: SurfaceViewRenderer? = null
    private var remoteRenderer: SurfaceViewRenderer? = null
    private var sessionJob: Job? = null
    private var candidatesJob: Job? = null
    private var roomId: String? = null
    private var userId: String? = null
    private var callScope: CoroutineScope? = null
    private var isCaller = false
    private var enableVideo = false
    private var currentOffer: String? = null
    private var currentAnswer: String? = null
    private val remoteDescriptionSet = AtomicBoolean(false)
    private val pendingCandidates = mutableListOf<IceCandidate>()
    private val processedCandidateIds = mutableSetOf<String>()

    fun bindRenderers(local: SurfaceViewRenderer?, remote: SurfaceViewRenderer?) {
        localRenderer = local?.also {
            it.init(eglBase.eglBaseContext, null)
            it.setMirror(true)
            it.setEnableHardwareScaler(true)
        }
        remoteRenderer = remote?.also {
            it.init(eglBase.eglBaseContext, null)
            it.setMirror(false)
            it.setEnableHardwareScaler(true)
        }
        videoTrack?.addSink(localRenderer)
        remoteVideoTrack?.addSink(remoteRenderer)
    }

    fun connect(
        scope: CoroutineScope,
        room: Room,
        user: User,
        isCaller: Boolean,
        enableVideo: Boolean
    ) {
        val sameSession = roomId == room.id && userId == user.email && this.isCaller == isCaller && this.enableVideo == enableVideo
        if (sameSession && peerConnection != null) return

        releaseConnection()

        roomId = room.id
        userId = user.email
        callScope = scope
        this.isCaller = isCaller
        this.enableVideo = enableVideo
        currentOffer = null
        currentAnswer = null
        remoteDescriptionSet.set(false)
        pendingCandidates.clear()
        processedCandidateIds.clear()

        ensurePeerConnectionFactory()
        createPeerConnection()
        createLocalTracks(enableVideo)
        addLocalTracks()

        sessionJob = scope.launch {
            signalingRepository.observeSession(room.id).collectLatest { session ->
                if (session == null) return@collectLatest
                if (this@WebRtcCallController.isCaller) {
                    handleCallerSession(session)
                } else {
                    handleCalleeSession(scope, session, room.id, user.email)
                }
            }
        }

        candidatesJob = scope.launch {
            signalingRepository.observeRemoteCandidates(room.id, isCaller).collectLatest { candidates ->
                candidates.forEach { payload ->
                    if (!processedCandidateIds.add(payload.id)) return@forEach
                    val candidate = IceCandidate(payload.sdpMid, payload.sdpMLineIndex, payload.sdpCandidate)
                    addRemoteIceCandidate(candidate)
                }
            }
        }

        scope.launch {
            signalingRepository.ensureSession(room.id, room.activeCallHost.ifBlank { user.email }, room.activeCallType.ifBlank { if (enableVideo) "video" else "audio" })
            if (isCaller) {
                createOffer(scope, room.id, user.email, room.activeCallType.ifBlank { if (enableVideo) "video" else "audio" })
            }
        }
    }

    fun toggleMute() {
        val next = !_uiState.value.isMicEnabled
        audioTrack?.setEnabled(next)
        _uiState.value = _uiState.value.copy(isMicEnabled = next)
    }

    fun toggleCamera() {
        if (!enableVideo) return
        val next = !_uiState.value.isCameraEnabled
        videoTrack?.setEnabled(next)
        _uiState.value = _uiState.value.copy(isCameraEnabled = next)
    }

    fun release() {
        releaseConnection()
        localRenderer?.release()
        remoteRenderer?.release()
        localRenderer = null
        remoteRenderer = null
        eglBase.release()
    }

    private suspend fun handleCallerSession(session: WebRtcSession) {
        val answer = session.answerSdp
        if (answer.isBlank() || answer == currentAnswer) return
        currentAnswer = answer
        setRemoteDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(session.answerType), answer))
    }

    private suspend fun handleCalleeSession(
        scope: CoroutineScope,
        session: WebRtcSession,
        roomId: String,
        userId: String
    ) {
        val offer = session.offerSdp
        if (offer.isBlank()) return
        if (offer != currentOffer) {
            currentOffer = offer
            setRemoteDescription(SessionDescription(SessionDescription.Type.fromCanonicalForm(session.offerType), offer))
            createAnswer(scope, roomId, userId)
        }
    }

    private fun ensurePeerConnectionFactory() {
        if (factoryInitialized) return
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(appContext)
                .createInitializationOptions()
        )
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true))
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglBase.eglBaseContext))
            .createPeerConnectionFactory()
        factoryInitialized = true
    }

    private fun createPeerConnection() {
        val rtcConfig = PeerConnection.RTCConfiguration(
            listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
        ).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(
            rtcConfig,
            object : PeerConnection.Observer {
                override fun onSignalingChange(state: PeerConnection.SignalingState?) = Unit
                override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                    _uiState.value = _uiState.value.copy(
                        connectionLabel = when (state) {
                            PeerConnection.IceConnectionState.CONNECTED,
                            PeerConnection.IceConnectionState.COMPLETED -> "connected"
                            PeerConnection.IceConnectionState.CHECKING -> "connecting"
                            PeerConnection.IceConnectionState.DISCONNECTED -> "reconnecting"
                            PeerConnection.IceConnectionState.FAILED -> "failed"
                            PeerConnection.IceConnectionState.CLOSED -> "ended"
                            else -> _uiState.value.connectionLabel
                        }
                    )
                }
                override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
                override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) = Unit
                override fun onIceCandidate(candidate: IceCandidate?) {
                    val iceCandidate = candidate ?: return
                    _uiState.value = _uiState.value.copy(connectionLabel = "connecting")
                    onLocalIceCandidate(iceCandidate)
                }
                override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) = Unit
                override fun onAddStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onRemoveStream(stream: org.webrtc.MediaStream?) = Unit
                override fun onDataChannel(dataChannel: org.webrtc.DataChannel?) = Unit
                override fun onRenegotiationNeeded() = Unit
                override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out org.webrtc.MediaStream>?) = Unit
                override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {
                    val track = transceiver?.receiver?.track() as? VideoTrack ?: return
                    remoteVideoTrack = track
                    mainHandler.post {
                        remoteRenderer?.let(track::addSink)
                        _uiState.value = _uiState.value.copy(hasRemoteVideo = true)
                    }
                }
            }
        )
    }

    private fun createLocalTracks(enableVideo: Boolean) {
        val factory = peerConnectionFactory ?: return

        audioSource = factory.createAudioSource(MediaConstraints())
        audioTrack = factory.createAudioTrack("audio_track", audioSource).also {
            it.setEnabled(true)
        }

        if (enableVideo) {
            val capturer = createVideoCapturer() ?: return
            videoCapturer = capturer
            surfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", eglBase.eglBaseContext)
            videoSource = factory.createVideoSource(capturer.isScreencast)
            capturer.initialize(surfaceTextureHelper, appContext, videoSource?.capturerObserver)
            capturer.startCapture(1280, 720, 30)
            videoTrack = factory.createVideoTrack("video_track", videoSource).also {
                it.setEnabled(true)
                localRenderer?.let(it::addSink)
            }
        }

        _uiState.value = _uiState.value.copy(
            isMicEnabled = true,
            isCameraEnabled = enableVideo,
            connectionLabel = "connecting"
        )
    }

    private fun addLocalTracks() {
        peerConnection?.addTrack(audioTrack)
        videoTrack?.let { peerConnection?.addTrack(it) }
    }

    private fun createOffer(scope: CoroutineScope, roomId: String, userId: String, callType: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", enableVideo.toString()))
        }
        peerConnection?.createOffer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                val sdp = description ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                scope.launch {
                    signalingRepository.saveOffer(roomId, userId, callType, sdp.description, sdp.type.canonicalForm())
                }
            }
        }, constraints)
    }

    private fun createAnswer(scope: CoroutineScope, roomId: String, userId: String) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", enableVideo.toString()))
        }
        peerConnection?.createAnswer(object : SimpleSdpObserver() {
            override fun onCreateSuccess(description: SessionDescription?) {
                val sdp = description ?: return
                peerConnection?.setLocalDescription(SimpleSdpObserver(), sdp)
                scope.launch {
                    signalingRepository.saveAnswer(roomId, sdp.description, sdp.type.canonicalForm())
                }
            }
        }, constraints)
    }

    private fun setRemoteDescription(description: SessionDescription) {
        peerConnection?.setRemoteDescription(object : SimpleSdpObserver() {
            override fun onSetSuccess() {
                remoteDescriptionSet.set(true)
                flushPendingCandidates()
            }
        }, description)
    }

    private fun addRemoteIceCandidate(candidate: IceCandidate) {
        if (remoteDescriptionSet.get()) {
            peerConnection?.addIceCandidate(candidate)
        } else {
            pendingCandidates += candidate
        }
    }

    private fun flushPendingCandidates() {
        pendingCandidates.forEach { peerConnection?.addIceCandidate(it) }
        pendingCandidates.clear()
    }

    private fun onLocalIceCandidate(candidate: IceCandidate) {
        val currentRoomId = roomId ?: return
        val currentUserId = userId ?: return
        callScope?.launch {
            signalingRepository.addIceCandidate(currentRoomId, isCaller, currentUserId, candidate)
        }
    }

    private fun createVideoCapturer(): VideoCapturer? {
        val enumerator = Camera2Enumerator(appContext)
        enumerator.deviceNames.firstOrNull(enumerator::isFrontFacing)?.let { device ->
            enumerator.createCapturer(device, null)?.let { return it }
        }
        enumerator.deviceNames.firstOrNull()?.let { device ->
            enumerator.createCapturer(device, null)?.let { return it }
        }
        return null
    }

    private fun releaseConnection() {
        sessionJob?.cancel()
        candidatesJob?.cancel()
        sessionJob = null
        candidatesJob = null
        callScope = null
        remoteVideoTrack?.let { track -> remoteRenderer?.let(track::removeSink) }
        videoTrack?.let { track -> localRenderer?.let(track::removeSink) }
        remoteVideoTrack = null
        peerConnection?.dispose()
        peerConnection = null
        try {
            videoCapturer?.stopCapture()
        } catch (_: Exception) {
        }
        videoCapturer?.dispose()
        videoCapturer = null
        surfaceTextureHelper?.dispose()
        surfaceTextureHelper = null
        videoTrack?.dispose()
        videoTrack = null
        videoSource?.dispose()
        videoSource = null
        audioTrack?.dispose()
        audioTrack = null
        audioSource?.dispose()
        audioSource = null
        _uiState.value = WebRtcUiState()
    }

    open class SimpleSdpObserver : SdpObserver {
        override fun onCreateSuccess(description: SessionDescription?) = Unit
        override fun onSetSuccess() = Unit
        override fun onCreateFailure(error: String?) = Unit
        override fun onSetFailure(error: String?) = Unit
    }
}

data class WebRtcUiState(
    val isMicEnabled: Boolean = true,
    val isCameraEnabled: Boolean = true,
    val hasRemoteVideo: Boolean = false,
    val connectionLabel: String = "connecting"
)
