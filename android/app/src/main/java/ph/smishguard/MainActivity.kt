package ph.smishguard

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ph.smishguard.data.Preferences
import ph.smishguard.model.Detection
import ph.smishguard.privacy.Brand
import ph.smishguard.privacy.City
import ph.smishguard.privacy.ReportPayload
import ph.smishguard.sms.PermissionState

class MainActivity : ComponentActivity() {
    private val vm: CheckViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        consumeShare(intent)
        setContent {
            val dark = isSystemInDarkTheme()
            val colors = if (dark) darkColorScheme(primary = Color(0xFF9BD4BE), secondary = Color(0xFFBACBBF),
                    surfaceContainer = Color(0xFF202B26), secondaryContainer = Color(0xFF344B40))
                else lightColorScheme(primary = Color(0xFF236B5E), secondary = Color(0xFF4E6358),
                    background = Color(0xFFF7F9F5), surface = Color(0xFFF7F9F5),
                    surfaceContainer = Color(0xFFEBF0E9), secondaryContainer = Color(0xFFD9EBDD))
            MaterialTheme(colorScheme = colors) {
                // No Compose saveable state, including text field state, enters activity bundles.
                CompositionLocalProvider(LocalSaveableStateRegistry provides null) {
                    SmishGuardUi(application as SmishGuardApp, vm)
                }
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); consumeShare(intent) }
    private fun consumeShare(incoming: Intent) {
        if (incoming.action == Intent.ACTION_SEND && incoming.type == "text/plain") {
            val text = incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            incoming.removeExtra(Intent.EXTRA_TEXT)
            incoming.clipData = null
            incoming.replaceExtras(null as Bundle?)
            // Keep routing identity stable; discard the payload rather than replace the
            // launch intent. Android lifecycle observers match action/type/categories.
            incoming.setDataAndType(null, "text/plain")
            if (text != null) vm.share(text)
        }
    }
    override fun onStop() { vm.endSession(); super.onStop() }
    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.clear() }
}

@Composable
private fun SmishGuardUi(app: SmishGuardApp, vm: CheckViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val preferences by app.preferences.flow.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val scroll = rememberScrollState()
    LaunchedEffect(state.screen, preferences?.onboarded) { scroll.scrollTo(0) }
    var permissions by remember { mutableStateOf(PermissionState.read(app)) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) permissions = PermissionState.read(app)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(state.screen != Screen.HOME) { vm.endSession() }
    Scaffold(bottomBar = {
        if (preferences?.onboarded == true) NavigationBar {
            listOf(Screen.HOME to "Home", Screen.CHECK to "Check", Screen.SETTINGS to "Settings").forEach { (screen, label) ->
                NavigationBarItem(selected = state.screen == screen, onClick = { vm.navigate(screen) },
                    icon = { Text(when (screen) { Screen.HOME -> "⌂"; Screen.CHECK -> "✉"; else -> "⚙" }, Modifier.clearAndSetSemantics { }) }, label = { Text(label) })
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(scroll).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("SMISHGUARD", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold)
            when {
                preferences == null -> CircularProgressIndicator()
                preferences?.onboarded != true -> {
                    Heading("A moment to check.\nA little more clarity.")
                    Text("Check SMS messages for potential financial-institution impersonation in Filipino, English, and Taglish.")
                    InfoCard("Your message stays here", "Processing happens on this phone. Message text is not stored or sent. Leaving the app clears your checking session.")
                    InfoCard("A warning tool", "SmishGuard never blocks, deletes, sends, or changes SMS. It does not replace your messaging app. Checks can miss scams or flag legitimate messages.")
                    Text("This prototype has no trained model installed yet. It cannot currently assess whether a message is suspicious.")
                    Text("Incoming SMS scanning is optional and off by default. No permission is needed to paste or share text. RCS and other messaging services are not monitored.")
                    Button(onClick = { scope.launch { app.preferences.onboard() } }, Modifier.fillMaxWidth()) { Text("Continue without permissions") }
                }
                else -> when (state.screen) {
                    Screen.HOME -> {
                        Heading("Before you tap,\ncheck the message.")
                        Text("A private space to pause and review a financial SMS.", style = MaterialTheme.typography.bodyLarge)
                        InfoCard(state.modelStatus, if (state.modelStatus.startsWith("Model ready")) "Checks run entirely on this phone." else "No assessment is available until a compatible trained model is included.")
                        InfoCard(if (permissions.active(preferences!!.scanning)) "Incoming SMS · enabled" else "Incoming SMS · off",
                            if (!permissions.notifications) "Warnings cannot appear because notifications are disabled. Paste and share remain available." else "Optional scanning only applies to new SMS messages after permission is granted.")
                        Button(onClick = { vm.navigate(Screen.CHECK) }, Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Check a message") }
                        Text("Never share an OTP, PIN, or password. Verify requests using your bank’s official app or a number you already trust.")
                        Text("Research prototype · On-device processing", style = MaterialTheme.typography.labelMedium)
                    }
                    Screen.CHECK -> {
                        Heading("Check a message")
                        Text("Type, paste, or share an SMS here. Links are never opened or fetched.")
                        OutlinedTextField(value = state.text, onValueChange = vm::edit, modifier = Modifier.fillMaxWidth(),
                            label = { Text("SMS message") }, placeholder = { Text("Paste the message you want to check…") },
                            minLines = 7, maxLines = 12, enabled = !state.busy,
                            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                            supportingText = { Text("${state.text.length} / 10,000 characters · cleared after checking") })
                        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        Button(onClick = vm::scan, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
                            if (state.busy) { CircularProgressIndicator(Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)) }
                            Text(if (state.busy) "Checking on your phone…" else "Check message")
                        }
                        OutlinedButton(onClick = { vm.edit("") }, modifier = Modifier.fillMaxWidth()) { Text("Clear message") }
                        Text("${state.modelStatus}. A low score would not guarantee legitimacy.", style = MaterialTheme.typography.bodySmall)
                    }
                    Screen.RESULT -> {
                        Heading("Check result")
                        when (val result = state.result) {
                            is Detection.Success -> {
                                InfoCard(if (result.synthetic) "Synthetic interface fixture" else if (result.suspicious) "Potential financial impersonation" else "Not flagged by this model",
                                    if (result.synthetic) "This is not a real assessment." else if (result.suspicious) "This message may be suspicious. Pause before acting on requests for money or account access." else "The model did not flag this message. This does not establish that it is legitimate.")
                                if (result.probability && !result.synthetic) Text("Estimated suspicious-class probability: ${"%.1f".format(result.score * 100)}%. This is a model estimate, not certainty.")
                                else Text("No confidence percentage is available.")
                            }
                            Detection.Unavailable -> InfoCard("Model not installed", "No prediction was made. This prototype needs an evaluated model before it can assess messages.")
                            Detection.InvalidModel -> InfoCard("Unsupported or invalid model", "This model cannot be used. No prediction was made. Contact the research team for a compatible build.")
                            else -> InfoCard("Could not complete the check", "No prediction was made. Try checking the message again.")
                        }
                        Text("Verify suspicious requests through the institution’s official app, website entered independently, or a known phone number. Do not use contact details supplied by the suspicious message.")
                        Text("Message text has been cleared.", style = MaterialTheme.typography.labelMedium)
                        OutlinedButton(onClick = { vm.navigate(Screen.REPORT) }, Modifier.fillMaxWidth()) { Text("Review reporting options") }
                        Button(onClick = { vm.navigate(Screen.CHECK) }, Modifier.fillMaxWidth()) { Text("Check another message") }
                    }
                    Screen.REPORT -> {
                        Heading("Your choice to report")
                        Text("Only the metadata below would be sent. Reporting never includes your message or phone number.")
                        var brand by remember { mutableStateOf(Brand.UNSPECIFIED) }
                        Choice("Impersonated brand (optional)", Brand.entries, brand, { it.label }) { brand = it }
                        val result = state.result as? Detection.Success
                        val payload = if (result != null && state.checkedAt != null) ReportPayload.create(state.domain, result, state.checkedAt!!, preferences!!.city, brand) else null
                        if (payload != null) InfoCard("Exact metadata preview", payload.json())
                        else InfoCard("No report can be prepared", "A real model prediction and a manually selected city are required. There is no score to report when a model is missing.")
                        InfoCard("Reporting unavailable", "No reporting endpoint is configured in this build. Nothing will be sent or queued. Offline checks do not depend on this service.")
                        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Submit report · unavailable") }
                        Text("Future city dashboards will summarize voluntary reports; their counts cannot measure total citywide scam prevalence.")
                    }
                    Screen.SETTINGS -> SettingsContent(app, preferences!!, permissions, { permissions = PermissionState.read(app) }, { vm.endSession() })
                }
            }
        }
    }
}

@Composable private fun SettingsContent(app: SmishGuardApp, preferences: Preferences, permissions: PermissionState, refresh: () -> Unit, clear: () -> Unit) {
    val scope = rememberCoroutineScope()
    var consentDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val smsRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        scope.launch { app.preferences.scanning(granted) }
        notice = if (granted) "Incoming scanning enabled. It needs a trained model to assess messages." else "SMS permission was not granted. Use paste or share, or review Android app settings."
        refresh()
    }
    val notificationRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }
    Heading("Privacy & settings")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Scan incoming SMS", Modifier.weight(1f).padding(top = 12.dp))
        Switch(checked = preferences.scanning && permissions.active(true), onCheckedChange = {
            if (it) consentDialog = true else scope.launch { app.preferences.scanning(false) }
        }, enabled = permissions.telephony, modifier = Modifier.semantics { contentDescription = "Scan incoming SMS" })
    }
    Text("Telephony: ${if (permissions.telephony) "available" else "unavailable"}\nSMS permission: ${if (permissions.sms) "granted" else "not granted"}\nNotifications: ${if (permissions.notifications) "enabled" else "disabled"}")
    notice?.let { Text(it) }
    if (!permissions.notifications) OutlinedButton(onClick = {
        if (Build.VERSION.SDK_INT >= 33) notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        else app.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, app.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }) { Text("Enable warning notifications") }
    TextButton(onClick = { app.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }) { Text("Open Android permission settings") }
    Choice("Your city · selected manually", City.entries, preferences.city, { it.label }) { city -> scope.launch { app.preferences.city(city) } }
    InfoCard("Local processing", "Only onboarding, incoming-SMS consent, and your selected city are saved. No scan history or raw message records are kept. Reports are not queued. The app has no internet permission.")
    InfoCard("Know the limits", "Android treats SMS access as a restricted permission. Your installer and device may prevent it, even for a directly installed APK. Force-stopping the app prevents automatic scanning until you open it again. Battery policies and certain protected SMS may also limit delivery. Notifications must be enabled for warnings.")
    OutlinedButton(onClick = { clearDialog = true }, Modifier.fillMaxWidth()) { Text("Clear local preferences and session") }
    if (consentDialog) AlertDialog(onDismissRequest = { consentDialog = false }, title = { Text("Allow incoming SMS checks?") },
        text = { Text("SmishGuard will receive new SMS text to check locally, then discard it. It will not read your inbox or change messages. No model is included yet, so this build cannot assess new messages. Android may refuse this restricted permission. Notifications are a separate choice.") },
        confirmButton = { TextButton(onClick = {
            consentDialog = false
            if (permissions.sms) scope.launch { app.preferences.scanning(true) } else smsRequest.launch(Manifest.permission.RECEIVE_SMS)
        }) { Text("Agree and continue") } }, dismissButton = { TextButton(onClick = { consentDialog = false }) { Text("Keep off") } })
    if (clearDialog) AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text("Clear local records?") },
        text = { Text("This clears your city, consent preferences, and current checking session. Incoming scanning turns off. Android permissions remain under system settings.") },
        confirmButton = { TextButton(onClick = { clearDialog = false; scope.launch { app.preferences.clear(); clear() } }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { clearDialog = false }) { Text("Cancel") } })
}

@Composable private fun Heading(text: String) { Text(text, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold) }
@Composable private fun InfoCard(title: String, body: String) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
@Composable private fun <T> Choice(label: String, choices: List<T>, selected: T, display: (T) -> String, change: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, Modifier.fillMaxWidth()) { Text(display(selected)) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                choices.forEach { choice -> DropdownMenuItem(text = { Text(display(choice)) }, onClick = { expanded = false; change(choice) }) }
            }
        }
    }
}
