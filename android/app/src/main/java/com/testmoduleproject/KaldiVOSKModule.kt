package com.testmoduleproject;

import android.media.AudioRecord
import android.util.Log
import com.beust.klaxon.Klaxon
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule.RCTDeviceEventEmitter
import java.io.File
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import org.kaldi.KaldiRecognizer
import org.kaldi.Model

class KaldiVOSKModule(private val reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    class KaldiPartialResult(val partial: String)
    class KaldiResultConf(val conf: Float, val end: Float, val start: Float, val word: String)
    class KaldiResult(val result: List<KaldiResultConf>, val text: String)
    class KaldiFinalResult(val text: String)

    private var model: Model? = null
    private var recognizer: KaldiRecognizer? = null

    private var isRecording: AtomicBoolean = AtomicBoolean(false)
    private var recorder: AudioRecord? = null
    private var transcriptionThread: Thread? = null

    private val RECORDER_CHANNELS: Int = android.media.AudioFormat.CHANNEL_IN_MONO
    private val RECORDER_AUDIO_ENCODING: Int = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val SAMPLE_RATE_IN_HZ = 16000
    private val NUM_BUFFER_ELEMENTS = 2048
    private val BYTES_PER_ELEMENT = 2

    private val RECORDING_STEP_WIDTH: Long = 50L

    override fun getName(): String {
        return "Kaldi"
    }

    private fun createModel(): Boolean {
       val modelsDir = File("${reactContext.filesDir.absolutePath}/models/kaldi")

        if (!modelsDir.exists()) {
            val event = Arguments.createMap()
            event.putString("message", "Model creation failed: ${modelsDir.absolutePath} does not exist.")
            sendEvent("onSpeechError", event)
            return false
        }

        model = Model(modelsDir.absolutePath)
        recognizer = KaldiRecognizer(model, 16000f)

        return true
    }

    private fun createRecorder() {
        recorder = AudioRecord(
                android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_IN_HZ,
                RECORDER_CHANNELS,
                RECORDER_AUDIO_ENCODING,
                NUM_BUFFER_ELEMENTS * BYTES_PER_ELEMENT
        )
    }

    @ReactMethod
    fun initialize(promise: Promise) {
        if (!createModel()) {
            promise.reject("error", "Model creation failed.")
            return
        }

        createRecorder()
        sendEvent("onSpeechReady", Arguments.createMap())
        promise.resolve("success")
    }

    private fun transcribe() {
        val audioData = ShortArray(NUM_BUFFER_ELEMENTS)

        while (isRecording.get()) {
            recorder?.read(audioData, 0, NUM_BUFFER_ELEMENTS)
            val isFinal = recognizer?.AcceptWaveform(audioData, audioData.size)!!
          
            if (isFinal) {
                val jsonResult = recognizer?.Result()!!
                val result = Klaxon().parse<KaldiResult>(jsonResult)
                val text = result?.text ?: ""

                val event = Arguments.createMap()
                val value = Arguments.createArray()
                value.pushString(text)
                event.putArray("value", value)
                sendEvent("onSpeechResults", event)
            } else {
                val jsonPartialResult = recognizer?.PartialResult()!!
                val partialResult = Klaxon().parse<KaldiPartialResult>(jsonPartialResult)
                val text = partialResult?.partial ?: ""

                val event = Arguments.createMap()
                val value = Arguments.createArray()
                value.pushString(text)
                event.putArray("value", value)
                sendEvent("onSpeechPartialResults", event)
            }
        }

        val jsonFinalResult = recognizer?.FinalResult()!!
        val finalResult = Klaxon().parse<KaldiFinalResult>(jsonFinalResult)

        val event = Arguments.createMap()
        val value = Arguments.createArray()
        value.pushString(finalResult?.text ?: "")
        event.putArray("value", value)
        sendEvent("onSpeechResults", event)

        recorder?.stop()

        sendEvent("onSpeechEnd", Arguments.createMap())
    }

    @ReactMethod
    fun startListening() {
        if (isRecording.compareAndSet(false, true)) {
            sendEvent("onSpeechStart", Arguments.createMap())
            recorder?.startRecording()

            if (transcriptionThread == null) {
                transcriptionThread = Thread({ transcribe() }, "Transcription Thread")
                transcriptionThread?.start()
            }
        }
    }

    @ReactMethod
    fun stopListening() {
        isRecording.set(false)

        Thread.sleep(RECORDING_STEP_WIDTH)

        // recording thread should be done by now

        transcriptionThread?.interrupt()
        transcriptionThread = null
    }

    private fun sendEvent(eventName: String, params: WritableMap?) {
        reactContext.getJSModule(RCTDeviceEventEmitter::class.java).emit(eventName, params)
    }

    @ReactMethod
    fun destroy() {
        if (model != null) {
            model?.delete()
            model = null
        }

        if (recognizer != null) {
            recognizer?.delete()
            recognizer = null
        }

        if (recorder != null) {
            recorder?.release()
            recorder = null
        }

        isRecording.set(false)
        transcriptionThread = null
    }
}
