package com.nexlink.app.db

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Transcribes a recorded audio file (AMR/AAC) using the on-device speech recognizer.
 *
 * Flow: decode compressed audio → raw PCM temp file → feed PCM to SpeechRecognizer
 * via the hidden EXTRA_AUDIO_SOURCE bundle key (supported by Google's on-device model
 * on Android 10+ and the on-device model on Android 13+).
 */
object AudioTranscriber {

    /** Call on any thread. [onResult] is always called on the main thread. */
    fun transcribe(ctx: Context, audioUriStr: String, onResult: (String?) -> Unit) {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) { post(onResult, null); return }
        val audioUri = Uri.parse(audioUriStr)
        val pcmFile  = File(ctx.cacheDir, "tr_${System.currentTimeMillis()}.pcm")

        Thread {
            val decoded = runCatching { decodeToPcm(ctx, audioUri, pcmFile) }.getOrDefault(false)
            Handler(Looper.getMainLooper()).post {
                if (!decoded || pcmFile.length() == 0L) {
                    pcmFile.delete(); onResult(null); return@post
                }
                startRecognition(ctx, pcmFile, onResult)
            }
        }.start()
    }

    private fun startRecognition(ctx: Context, pcmFile: File, onResult: (String?) -> Unit) {
        val recognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                              SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx))
            SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx)
        else
            SpeechRecognizer.createSpeechRecognizer(ctx)

        val pfd = runCatching {
            ParcelFileDescriptor.open(pcmFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull()
        if (pfd == null) { pcmFile.delete(); onResult(null); recognizer.destroy(); return }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
            // Feed pre-decoded PCM directly — avoids the microphone entirely
            putExtra("android.speech.extra.AUDIO_SOURCE", pfd)
            putExtra("android.speech.extra.AUDIO_SOURCE_ENCODING", AudioFormat.ENCODING_PCM_16BIT)
            putExtra("android.speech.extra.AUDIO_SOURCE_CHANNEL_COUNT", 1)
            putExtra("android.speech.extra.AUDIO_SOURCE_SAMPLING_RATE", 8000)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(b: Bundle) {
                cleanup(); onResult(b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.takeIf { it.isNotBlank() })
            }
            override fun onError(e: Int) { cleanup(); onResult(null) }
            private fun cleanup() {
                runCatching { pfd.close() }; pcmFile.delete(); recognizer.destroy()
            }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(b: Bundle?) {}
            override fun onEvent(e: Int, b: Bundle?) {}
        })

        recognizer.startListening(intent)
    }

    /** Decodes any Android-supported audio format to raw 16-bit PCM. */
    private fun decodeToPcm(ctx: Context, uri: Uri, out: File): Boolean {
        val extractor = MediaExtractor()
        extractor.setDataSource(ctx, uri, null)

        val trackIdx = (0 until extractor.trackCount).firstOrNull { i ->
            (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")
        } ?: run { extractor.release(); return false }

        extractor.selectTrack(trackIdx)
        val fmt  = extractor.getTrackFormat(trackIdx)
        val mime = fmt.getString(MediaFormat.KEY_MIME) ?: run { extractor.release(); return false }

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(fmt, null, null, 0)
        codec.start()

        FileOutputStream(out).use { fos ->
            val info = MediaCodec.BufferInfo()
            var eos  = false
            while (!eos) {
                val inIdx = codec.dequeueInputBuffer(10_000L)
                if (inIdx >= 0) {
                    val buf = codec.getInputBuffer(inIdx)!!
                    val n   = extractor.readSampleData(buf, 0)
                    if (n < 0) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000L)
                if (outIdx >= 0) {
                    val buf   = codec.getOutputBuffer(outIdx)!!
                    val bytes = ByteArray(info.size)
                    buf.get(bytes)
                    fos.write(bytes)
                    codec.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                }
            }
        }

        codec.stop(); codec.release(); extractor.release()
        return true
    }

    private fun post(fn: (String?) -> Unit, v: String?) =
        Handler(Looper.getMainLooper()).post { fn(v) }
}
