package com.vartex.banglatype.ime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.inputmethodservice.InputMethodService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat

class BanglaVoiceIMEService : InputMethodService(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var continuousMode = true
    private var isMultiLineEditor = false
    private var currentLocale = "bn-BD"
    private var serverRetryCount = 0

    private lateinit var tvStatus: TextView
    private lateinit var btnMic: Button
    private lateinit var btnDialect: Button
    private lateinit var btnContinuous: Button
    private lateinit var btnEnter: Button

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateInputView(): View {
        val view = requireNotNull(layoutInflater.inflate(R.layout.keyboard_voice_ime, null))

        tvStatus = ViewCompat.requireViewById(view, R.id.tvStatus)
        btnMic = ViewCompat.requireViewById(view, R.id.btnMic)
        btnDialect = ViewCompat.requireViewById(view, R.id.btnDialect)
        btnContinuous = ViewCompat.requireViewById(view, R.id.btnContinuous)
        btnEnter = ViewCompat.requireViewById(view, R.id.btnEnter)

        btnContinuous.text = if (continuousMode) "টানা মোড: চালু" else "টানা মোড: বন্ধ"

        btnMic.setOnClickListener {
            if (isListening || continuousMode && btnMic.text.toString().contains("থামুন")) {
                stopListening()
            } else {
                startListening()
            }
        }

        btnDialect.setOnClickListener {
            currentLocale = if (currentLocale == "bn-BD") "bn-IN" else "bn-BD"
            btnDialect.text = currentLocale
            tvStatus.text = if (currentLocale == "bn-BD") "বাংলা (বাংলাদেশ) নির্বাচিত" else "বাংলা (ভারত) নির্বাচিত"
        }

        btnContinuous.setOnClickListener {
            continuousMode = !continuousMode
            btnContinuous.text = if (continuousMode) "টানা মোড: চালু" else "টানা মোড: বন্ধ"
        }

        ViewCompat.requireViewById<Button>(view, R.id.btnSwitchIme).setOnClickListener {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showInputMethodPicker()
        }

        ViewCompat.requireViewById<Button>(view, R.id.btnDari).setOnClickListener { commitText("। ") }
        ViewCompat.requireViewById<Button>(view, R.id.btnComma).setOnClickListener { commitText(", ") }
        ViewCompat.requireViewById<Button>(view, R.id.btnQuestion).setOnClickListener { commitText("? ") }
        ViewCompat.requireViewById<Button>(view, R.id.btnSpace).setOnClickListener { commitText(" ") }

        ViewCompat.requireViewById<Button>(view, R.id.btnBackspace).setOnClickListener {
            val ic = currentInputConnection ?: return@setOnClickListener
            val selected = ic.getSelectedText(0)
            if (selected.isNullOrEmpty()) {
                ic.deleteSurroundingText(1, 0)
            } else {
                ic.commitText("", 1)
            }
        }

        btnEnter.setOnClickListener {
            val ic = currentInputConnection ?: return@setOnClickListener
            if (isMultiLineEditor) {
                ic.commitText("\n", 1)
            } else {
                val handled = ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
                if (!handled) {
                    sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
                }
            }
        }

        return view
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        if (info != null) {
            super.onStartInputView(info, restarting)
        }
        val pkg = info?.packageName.orEmpty()
        val imeOpts = info?.imeOptions ?: 0
        val inType = info?.inputType ?: 0
        isMultiLineEditor = (imeOpts and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            (inType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0

        if (::btnEnter.isInitialized) {
            btnEnter.text = if (isMultiLineEditor) "এন্টার" else "পাঠান"
        }

        val appLabel = when {
            pkg.contains("docs") -> "Google Docs-এ সরাসরি টাইপ করুন"
            pkg.contains("notes") -> "Samsung Notes-এ সরাসরি টাইপ করুন"
            pkg.contains("word") || pkg.contains("office") -> "Word-এ সরাসরি টাইপ করুন"
            else -> "বাংলা টাইপ · মাইকে চাপ দিয়ে বলুন"
        }
        if (::tvStatus.isInitialized && !isListening) {
            tvStatus.text = appLabel
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        mainHandler.removeCallbacksAndMessages(null)
        isListening = false
        speechRecognizer?.cancel()
        updateMicUi(listening = false)
    }

    private fun ensureRecognizer(): SpeechRecognizer {
        val existing = speechRecognizer
        if (existing != null) return existing
        return SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(this@BanglaVoiceIMEService)
        }.also { speechRecognizer = it }
    }

    private fun resetRecognizer() {
        try {
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (_: Exception) {
        }
        speechRecognizer = null
    }

    private fun startListening() {
        mainHandler.removeCallbacksAndMessages(null)

        val hasMic = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            tvStatus.text = "মাইক্রোফোন পারমিশন দিন (বাংলা টাইপ অ্যাপ খুলুন)"
            updateMicUi(listening = false)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "ফোনে Google Speech Service পাওয়া যায়নি"
            updateMicUi(listening = false)
            return
        }

        val recognizer = ensureRecognizer()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, currentLocale)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, currentLocale)
            putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 15000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
        }

        isListening = true
        updateMicUi(listening = true)
        tvStatus.text = "শুনছি... বাংলায় কথা বলুন"
        recognizer.startListening(intent)
    }

    private fun stopListening() {
        mainHandler.removeCallbacksAndMessages(null)
        isListening = false
        serverRetryCount = 0
        speechRecognizer?.stopListening()
        updateMicUi(listening = false)
        tvStatus.text = "বন্ধ করা হয়েছে"
    }

    private fun updateMicUi(listening: Boolean) {
        if (!::btnMic.isInitialized) return
        if (listening) {
            btnMic.text = "থামুন (শুনছে...)"
            btnMic.setBackgroundColor(Color.parseColor("#DC2626"))
            btnMic.setTextColor(Color.parseColor("#FFFFFF"))
        } else {
            btnMic.text = "কথা বলুন (বাংলা টাইপ)"
            btnMic.setBackgroundColor(Color.parseColor("#C5A24D"))
            btnMic.setTextColor(Color.parseColor("#0A132B"))
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
    }

    private fun formatBanglaVoiceCommands(raw: String): String {
        return raw.trim()
            .replace(Regex("""\s*(দাঁড়ি|দাড়ি|দাড়ি)\s*"""), "। ")
            .replace(Regex("""\s*কমা\s*"""), ", ")
            .replace(Regex("""\s*প্রশ্নবোধক\s*"""), "? ")
            .replace(Regex("""\s*নতুন লাইন\s*"""), "\n")
            .replace(Regex("""\s+([।,?])""")) { it.groupValues[1] }
            .trim()
    }

    override fun onReadyForSpeech(params: Bundle?) {
        serverRetryCount = 0
        tvStatus.text = "এখন বলুন..."
    }

    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        tvStatus.text = "রূপান্তর হচ্ছে..."
    }

    override fun onPartialResults(partialResults: Bundle?) {
        val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val partial = matches?.firstOrNull() ?: return
        currentInputConnection?.setComposingText(partial, 1)
    }

    override fun onResults(results: Bundle?) {
        serverRetryCount = 0
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        val rawText = matches?.firstOrNull().orEmpty()

        if (rawText.isNotEmpty()) {
            val formatted = formatBanglaVoiceCommands(rawText)
            currentInputConnection?.commitText("$formatted ", 1)
            tvStatus.text = "টাইপ হয়েছে: $formatted"
        } else {
            currentInputConnection?.finishComposingText()
        }

        if (continuousMode && isListening) {
            updateMicUi(listening = true)
            mainHandler.postDelayed({ startListening() }, 200)
        } else {
            isListening = false
            updateMicUi(listening = false)
        }
    }

    override fun onError(error: Int) {
        currentInputConnection?.finishComposingText()

        if (error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
            error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY
        ) {
            resetRecognizer()
            if (isListening && serverRetryCount < 2) {
                serverRetryCount += 1
                tvStatus.text = "পুনরায় সংযুক্ত হচ্ছে..."
                mainHandler.postDelayed({ startListening() }, 550)
                return
            }
        }

        if (continuousMode && isListening &&
            (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)
        ) {
            tvStatus.text = "শুনছি... কথা বলুন"
            mainHandler.postDelayed({ startListening() }, 250)
            return
        }

        isListening = false
        updateMicUi(listening = false)
        tvStatus.text = when (error) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "কোনো কথা শোনা যায়নি, আবার চেষ্টা করুন"
            SpeechRecognizer.ERROR_AUDIO -> "অডিও ইনপুটে সমস্যা হয়েছে"
            SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ইন্টারনেট বা অফলাইন বাংলা প্যাক চেক করুন"
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "স্পিচ সার্ভিস বিচ্ছিন্ন হয়েছে (Google Speech ও Battery সেটিংস চেক করুন)"
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ফোনে বাংলা ভয়েস প্যাক ডাউনলোড করুন"
            else -> "ত্রুটি কোড: $error"
        }
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacksAndMessages(null)
        resetRecognizer()
    }
}
