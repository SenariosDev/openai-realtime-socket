//package com.navbot.aihelper
//
//import android.Manifest
//import android.annotation.SuppressLint
//import android.content.pm.PackageManager
//import android.media.AudioFormat
//import android.media.AudioManager
//import android.media.AudioRecord
//import android.media.AudioTrack
//import android.media.MediaRecorder
//import android.os.Bundle
//import android.util.Base64
//import android.util.Log
//import android.widget.Button
//import androidx.appcompat.app.AppCompatActivity
//import androidx.core.app.ActivityCompat
//import be.tarsos.dsp.AudioDispatcher
//import be.tarsos.dsp.AudioEvent
//import be.tarsos.dsp.AudioProcessor
//import be.tarsos.dsp.io.android.AudioDispatcherFactory
//import com.blankj.utilcode.util.ToastUtils
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.launch
//import kotlinx.coroutines.withContext
//import okhttp3.OkHttpClient
//import okhttp3.Request
//import okhttp3.WebSocket
//import okhttp3.WebSocketListener
//import org.json.JSONObject
//import java.io.ByteArrayOutputStream
//import java.io.File
//import java.io.FileInputStream
//import java.nio.ByteBuffer
//import java.nio.ByteOrder
//import java.util.LinkedList
//import java.util.concurrent.Executors
//
//class NewActivity : AppCompatActivity() {
//
//    private val TAG = "AudioRMS"
//    private lateinit var webSocket: WebSocket
//    private var dispatcher: AudioDispatcher? = null
//    private val permissions = arrayOf(Manifest.permission.RECORD_AUDIO)
//    private var canRecord = false
//    private val bufferSize = 4096  // Buffer size for audio chunks
//    private val WSURL = "wss://api.openai.com/v1/realtime?model=gpt-4o-realtime-preview-2024-10-01"
//    private val Authorization = ""
//    private val CHUNK_SIZE = 100 // Chunk size in milliseconds for sending audio data
//    private val IS_DEBUG = false
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_new)
//        ActivityCompat.requestPermissions(this, permissions, 200)
//
//        findViewById<Button>(R.id.btn_send_whole_file)?.setOnClickListener {
//            sendTestAudioFile(0) // Sends the entire audio file
//        }
//        findViewById<Button>(R.id.btn_send_chunk_file)?.setOnClickListener {
//            sendTestAudioFile(1) // Sends the audio file in chunks
//        }
//        initTestFile()
//    }
//
//    // Permission request result handler
//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//        if (requestCode == 200 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//            ToastUtils.showShort("Permission granted")
//            initWebSocket() // Initialize WebSocket once permission is granted
//        } else {
//            ToastUtils.showShort("Permission denied")
//        }
//    }
//
//
//
//    // Initialize WebSocket connection to communicate with the OpenAI API
//    private fun initWebSocket() {
//        val client = OkHttpClient()
//        val request = Request.Builder()
//            .url(WSURL)
//            .addHeader("Authorization", Authorization)
//            .addHeader("OpenAI-Beta", "realtime=v1")
//            .build()
//
//        // Setting up WebSocket listener
//        webSocket = client.newWebSocket(request, object : WebSocketListener() {
//            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
//                Log.d(TAG, "WebSocket connection opened.")
//            }
//
//            override fun onMessage(webSocket: WebSocket, text: String) {
//                Log.d(TAG, "Message received: $text")
//                val jsonObject = JSONObject(text)
//
//                when (jsonObject.getString("type")) {
//
//                    "session.created" -> {
//                        Log.d(TAG, "Session created. Sending session update.")
//                        sendSessionUpdate()
//                    }
//                    "session.updated" -> {
//                        Log.d(TAG, "Session updated. Starting audio recording.")
//
//                        if (IS_DEBUG) {
//                            sendTestAudioFile(1) // Debug: Send audio file in chunks
//                        } else {
//                            canRecord = true
//                            startAudioRecording() // Start audio recording in real-time
//                        }
//                    }
//
//                    "response.audio.delta" -> {
//                        isModelSpeaking = true
//                        val audioBase64 = jsonObject.getString("delta")
//                        appendAndPlayAudio(audioBase64) // Process and play received audio
//                    }
//                    "response.audio.done" -> {
//                        isModelSpeaking = false
//                        Log.d(TAG, "Audio transmission complete.")
//                        stopAudioPlayback() // Stop audio playback
//                        handleServerResponse() // Allow further audio data to be sent
//                    }
//                }
//            }
//        })
//    }
//
//    // Sends session update with settings for voice model and turn detection
//    private fun sendSessionUpdate() {
//        val sessionConfig = """{
//        "type": "session.update",
//        "session": {
//            "instructions": "请用中文和我交流",
//            "turn_detection": {
//                "type": "server_vad",
//                "threshold": 0.5,
//                "prefix_padding_ms": 300,
//                "silence_duration_ms": 500
//            },
//            "voice": "alloy",
//            "temperature": 1,
//            "modalities": ["text", "audio"],
//            "input_audio_format": "pcm16",
//            "output_audio_format": "pcm16",
//            "input_audio_transcription": {
//                "model": "whisper-1"
//            },
//            "tool_choice": "auto"
//        }
//    }"""
//        Log.d(TAG, "Sending session update: $sessionConfig")
//        webSocket.send(sessionConfig)
//    }
//
//    // Sends a simple text message to the server
//    private fun sendTextMessage() {
//        val textMessage = """{
//            "type": "conversation.item.create",
//            "item": {
//                "type": "message",
//                "role": "user",
//                "content": [{"type": "input_text", "text": "Tell me a story."}]
//            }
//        }"""
//        Log.d(TAG, "Sending text message: $textMessage")
//        webSocket.send(textMessage)
//    }
//
//    private val audioDataBuffer = ByteArrayOutputStream()
//    private lateinit var audioTrack: AudioTrack
//
//
//    // Append and play the received base64 audio data
//    private fun appendAndPlayAudio(audioBase64: String) {
//        try {
//            val pcmData = Base64.decode(audioBase64, Base64.NO_WRAP)
//            if (pcmData.isNotEmpty()) {
//                audioDataBuffer.write(pcmData)
//                Log.d(TAG, "Audio data written to buffer, size: ${pcmData.size}")
//                playBufferedAudio()
//            } else {
//                Log.d(TAG, "Received empty audio data.")
//            }
//        } catch (e: Exception) {
//            Log.e(TAG, "Error decoding base64 audio: ${e.message}")
//        }
//    }
//    // Play buffered audio data using AudioTrack
//    private fun playBufferedAudio() {
//        if (!::audioTrack.isInitialized) {
//            initializeAudioTrack() // Initialize if not already done
//        }
//
//        val pcmData = audioDataBuffer.toByteArray()
//        if (pcmData.isNotEmpty()) {
//            audioTrack.write(pcmData, 0, pcmData.size)
//            audioTrack.play() // Ensure AudioTrack is playing only when there's data
//            audioDataBuffer.reset() // Clear buffer after playing
//            Log.d(TAG, "Playing audio, size: ${pcmData.size}")
//        } else {
//            Log.d(TAG, "No audio data in buffer to play.")
//        }
//    }
//
//    // Initialize AudioTrack for audio playback
//    private fun initializeAudioTrack() {
//        if (!::audioTrack.isInitialized) {
//            audioTrack = AudioTrack(
//                AudioManager.STREAM_MUSIC,
//                24000, // Sample rate
//                AudioFormat.CHANNEL_OUT_MONO,
//                AudioFormat.ENCODING_PCM_16BIT,
//                AudioTrack.getMinBufferSize(24000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT),
//                AudioTrack.MODE_STREAM
//            )
//            audioTrack.play() // Set AudioTrack to play mode
//            Log.d(TAG, "AudioTrack initialized.")
//        }
//    }
//
//    // Stop audio playback (optionally keep resources)
//    private fun stopAudioPlayback() {
////      if (::audioTrack.isInitialized && audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
////          audioTrack.pause()
////          audioTrack.flush()
////          Log.d(TAG, "Audio playback paused.")
////      }
//    }
//
//    // Converts a ByteArray to a Hex string representation (for debugging)
//    fun ByteArray.toHexString(): String {
//        return joinToString(separator = "") { byte -> "%02x".format(byte) }
//    }
//
//
//    private fun initTestFile() {
//        val targetPath: String = FileUtils.getFaceShapeModelPath(this)
//
//        CoroutineScope(Dispatchers.Main).launch {
//            if (!File(targetPath).exists()) {
//                withContext(Dispatchers.IO) {
//                    FileUtils.copyFileFromRawToOthers(
//                        applicationContext,
//                        R.raw.audio2,
//                        targetPath
//                    )
//                    Log.d(TAG, "audio test file copied successfully")
//                }
//            }
//        }
//    }
//
//    // Send audio file (whole or in chunks) to the WebSocket
//    private fun sendTestAudioFile(i: Int) {
//        val audioFilePath = FileUtils.getFaceShapeModelPath(this@NewActivity)
//        val audioFile = File(audioFilePath)
//
//        if (audioFile.exists()) {
//            try {
//                val fileInputStream = FileInputStream(audioFile)
//                val byteArray = fileInputStream.readBytes() // Read the entire file into ByteArray
//                fileInputStream.close()
//
//                // Skip the WAV header (44 bytes)
//                val pcmData = byteArray.copyOfRange(44, byteArray.size)
//
//                // Send the audio data depending on the mode (whole or chunked)
//                when (i) {
//                    0 -> { // Send whole file
//                        val base64Audio = Base64.encodeToString(pcmData, Base64.NO_WRAP)
//                        sendAudioData(base64Audio)
//                        Log.d(TAG, "Sent entire file")
//                    }
//
//                    1 -> { // Send in chunks
//                        val chunkSize = 1024 // Chunk size
//                        var offset = 0
//                        while (offset < pcmData.size) {
//                            // Determine the size of the current chunk
//                            val chunkData = pcmData.copyOfRange(
//                                offset,
//                                Math.min(offset + chunkSize, pcmData.size)
//                            )
//
//                            // Check if the chunk contains non-empty data
//                            if (chunkData.any { it.toInt() != 0 }) {
//                                // Convert ByteArray to Base64
//                                val base64Audio = Base64.encodeToString(chunkData, Base64.NO_WRAP)
//                                sendAudioData(base64Audio)
//                                Log.d(TAG, "Sent chunk of audio, size: ${chunkData.size}")
//                            } else {
//                                Log.d(TAG, "Skipped empty chunk of audio data.")
//                            }
//
//                            // Move to the next chunk
//                            offset += chunkSize
//                        }
//                    }
//                }
//
//            } catch (e: Exception) {
//                Log.e(TAG, "Error reading audio file: ${e.message}")
//            }
//        } else {
//            Log.e(TAG, "Audio file does not exist: $audioFilePath")
//        }
//    }
//
//    // Send audio data to the WebSocket without the 'chunk' parameter
//    private fun sendAudioData(base64Audio: String) {
//        val json = JSONObject().apply {
//            put("type", "input_audio_buffer.append")
//            put("audio", base64Audio)
//        }
//        Log.d(TAG, "Sending audio data: $json")
//        webSocket.send(json.toString())
//    }
//
//
//    private var audioRecord: AudioRecord? = null
//    private var isRecording = false
//    private var isModelSpeaking = false
//
//    private fun startAudioRecording() {
//        val sampleRate = 16000
//        val channelConfig = AudioFormat.CHANNEL_IN_MONO
//        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
//        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat) * 2
//
//        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
//            Log.w("AudioActivity", "Audio recording permission not granted")
//            return
//        }
//
//        Log.d("AudioActivity", "Starting audio recording")
//        audioRecord = AudioRecord(
//            MediaRecorder.AudioSource.VOICE_RECOGNITION,
//            sampleRate,
//            channelConfig,
//            audioFormat,
//            bufferSize
//        ).apply {
//            startRecording()
//        }
//        isRecording = true
//
//        Executors.newSingleThreadExecutor().execute {
//            val audioBuffer = ByteArray(bufferSize)
//            try {
//                while (isRecording) {
//                    val readBytes = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0
//                    if (readBytes > 0 && !isModelSpeaking) {
//                        val audioData = audioBuffer.copyOf(readBytes)
//                        val base64Audio = Base64.encodeToString(audioData, Base64.NO_WRAP)
//                        sendAudioData(base64Audio)
//                    }
//                }
//            } catch (e: Exception) {
//                Log.e("AudioActivity", "Error during audio recording: ${e.message}", e)
//            } finally {
//                audioRecord?.release()
//                Log.i("AudioActivity", "Audio recording stopped")
//            }
//        }
//    }
//
//
//    // 将 FloatArray 转换为 PCM16 格式的 ByteArray
//    private fun convertFloatArrayToPCM16(buffer: FloatArray): ByteArray {
//        val byteBuffer = ByteBuffer.allocate(buffer.size * 2) // 2 bytes per PCM16 sample
//        for (sample in buffer) {
//            // 正则化并转换为 PCM16
//            val pcm16 = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
//            byteBuffer.putShort(pcm16)
//        }
//        return byteBuffer.array()
//    }
//
//
//
//    // Calculate the Root Mean Square (RMS) of the audio signal to detect voice activity
//    private fun calculateRMS(buffer: FloatArray): Float {
//        var sum = 0.0
//        for (sample in buffer) {
//            sum += sample * sample
//        }
//        return Math.sqrt(sum / buffer.size).toFloat()
//    }
//
//
//
//
//    // Start the CircleWaveView animation when speaking is detected
//    private fun startCircleWaveAnimation() {
//        val waveView = findViewById<CircleWaveView>(R.id.circle_wave_view)
//
//        // Generate 4 random heights for the wave animation
//        val randomHeights = List(4) { 0.5f + kotlin.random.Random.nextFloat() * 1f }
//
//        // Update the CircleWaveView with the new heights
//        waveView.updateCircles(randomHeights)
//    }
//
//    // Reset the CircleWaveView animation when no speech is detected
//    private fun resetCircleWaveAnimation() {
//        val waveView = findViewById<CircleWaveView>(R.id.circle_wave_view)
//        waveView.resetCircles() // Reset to original state
//    }
//
//    // Handle server response, allowing new audio data to be sent
//    private var isWaitingForResponse = false  // Initialize state
//
//    private fun handleServerResponse() {
//        isWaitingForResponse = false // Reset state after receiving server response
//    }
//}