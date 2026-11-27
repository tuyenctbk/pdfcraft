package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.scale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.components.BuyMeACoffeeDialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MainViewModel
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppNavigationRail
import com.example.ui.components.AppTopBar
import com.example.ui.components.InfoPrivacyDialog
import com.example.ui.components.PdfMetadataDialog
import com.example.ui.components.PdfViewerDialog
import com.example.ui.components.PoliteAdBanner
import com.example.ui.components.PoliteAdDialog
import com.example.ui.components.SettingsDialog
import com.example.data.local.PdfOperationType
import com.example.ui.screens.CompressScreen
import com.example.ui.screens.EncryptScreen
import com.example.ui.screens.HubScreen
import com.example.ui.screens.ImageToPdfScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.MergeScreen
import com.example.ui.screens.MetadataScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PageNumberScreen
import com.example.ui.screens.RotateScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.screens.SignScreen
import com.example.ui.screens.SplitScreen
import com.example.ui.screens.ToolsScreen
import com.example.ui.screens.WatermarkScreen
import com.example.ui.screens.TextToPdfScreen
import com.example.ui.screens.PdfToTextScreen
import com.example.ui.screens.PdfToImagesScreen
import com.example.ui.screens.HtmlToPdfScreen
import com.example.ui.screens.DocumentInfoScreen
import com.example.ui.theme.PDFCraftTheme
import com.example.ui.components.SuggestRateDialog
import com.example.ui.components.SuggestShareDialog
import com.example.ui.components.SuggestUpdateDialog
import androidx.compose.ui.res.stringResource
import com.example.data.remote.FirebaseConfigManager

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import com.example.ui.theme.ThemeMode

import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    companion object {
        private const val TAG = "PDFCraft_Logcat"
    }

    private val incomingIntentState = mutableStateOf<android.content.Intent?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingIntentState.value = intent
    }

    override fun onResume() {
        super.onResume()
        com.example.ui.billing.BillingRepository.getInstance(applicationContext).registerActivity(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val fadeOut = android.animation.ObjectAnimator.ofFloat(splashScreenView, android.view.View.ALPHA, 1f, 0f)
            val scaleX = android.animation.ObjectAnimator.ofFloat(splashScreenView, android.view.View.SCALE_X, 1f, 1.04f)
            val scaleY = android.animation.ObjectAnimator.ofFloat(splashScreenView, android.view.View.SCALE_Y, 1f, 1.04f)
            android.animation.AnimatorSet().apply {
                playTogether(fadeOut, scaleX, scaleY)
                interpolator = android.view.animation.DecelerateInterpolator()
                duration = 260L
                addListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        splashScreenViewProvider.remove()
                    }
                })
                start()
            }
        }
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity.onCreate() initiated")

        try {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)
            Log.d(TAG, "PDFBoxResourceLoader initialized successfully")
        } catch (t: Throwable) {
            Log.e(TAG, "PDFBoxResourceLoader init error: ${t.localizedMessage}", t)
        }

        enableEdgeToEdge()
        com.example.ui.billing.BillingRepository.getInstance(applicationContext).registerActivity(this)

        incomingIntentState.value = intent

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val incomingIntent by remember { incomingIntentState }

            LaunchedEffect(incomingIntent) {
                val currentIntent = incomingIntent ?: return@LaunchedEffect
                val action = currentIntent.action
                val mimeType = currentIntent.type ?: ""

                try {
                    when (action) {
                        android.content.Intent.ACTION_VIEW -> {
                            val uri = currentIntent.data
                            if (uri != null) {
                                // Bypass onboarding when a user opens an external PDF directly
                                viewModel.completeOnboarding()
                                val localFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    com.example.data.pdf.PdfEngine.getLocalFileFromUri(applicationContext, uri)
                                }
                                if (localFile.exists()) {
                                    viewModel.openInViewer(localFile.absolutePath)
                                }
                            }
                        }
                        android.content.Intent.ACTION_SEND -> {
                            @Suppress("DEPRECATION")
                            val streamUri = currentIntent.getParcelableExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                                ?: currentIntent.data
                            if (streamUri != null) {
                                viewModel.completeOnboarding()
                                if (mimeType.startsWith("image/")) {
                                    viewModel.clearImagesToPdf()
                                    viewModel.addImagesToPdf(listOf(streamUri))
                                    viewModel.setActiveTool(PdfOperationType.IMAGE_TO_PDF)
                                } else {
                                    val localFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.example.data.pdf.PdfEngine.getLocalFileFromUri(applicationContext, streamUri)
                                    }
                                    if (localFile.exists()) {
                                        viewModel.openInViewer(localFile.absolutePath)
                                    }
                                }
                            }
                        }
                        android.content.Intent.ACTION_SEND_MULTIPLE -> {
                            @Suppress("DEPRECATION")
                            val uris = currentIntent.getParcelableArrayListExtra<android.net.Uri>(android.content.Intent.EXTRA_STREAM)
                            if (!uris.isNullOrEmpty()) {
                                viewModel.completeOnboarding()
                                if (mimeType.startsWith("image/")) {
                                    viewModel.clearImagesToPdf()
                                    viewModel.addImagesToPdf(uris)
                                    viewModel.setActiveTool(PdfOperationType.IMAGE_TO_PDF)
                                } else {
                                    viewModel.clearMergeGrid()
                                    viewModel.addPdfsToMerge(uris)
                                    viewModel.setActiveTool(PdfOperationType.MERGE)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error handling incoming external intent", e)
                    viewModel.showUserMessage(getString(R.string.picker_error_reading_file))
                } finally {
                    incomingIntentState.value = null
                }
            }

            val isExternalLaunchIntent = remember {
                val act = intent?.action
                act == android.content.Intent.ACTION_VIEW ||
                    act == android.content.Intent.ACTION_SEND ||
                    act == android.content.Intent.ACTION_SEND_MULTIPLE
            }
            var showAnimatedSplash by remember { mutableStateOf(!isExternalLaunchIntent) }

            PDFCraftTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PDFCraftApp(viewModel = viewModel)
                        if (showAnimatedSplash) {
                            com.example.ui.components.AnimatedSplashOverlay(
                                onFinished = { showAnimatedSplash = false }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PDFCraftApp(
    viewModel: MainViewModel = viewModel()
) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsStateWithLifecycle()
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val activeViewerFile by viewModel.activeViewerFile.collectAsStateWithLifecycle()
    val lastCompletedOutputFile by viewModel.lastCompletedOutputFile.collectAsStateWithLifecycle()
    val lastCompletedOperationSummary by viewModel.lastCompletedOperationSummary.collectAsStateWithLifecycle()
    val selectedMetadata by viewModel.selectedMetadata.collectAsStateWithLifecycle()
    val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsStateWithLifecycle()
    val showPoliteAdDialog by viewModel.showPoliteAdDialog.collectAsStateWithLifecycle()
    val showDonationDialog by viewModel.showDonationDialog.collectAsStateWithLifecycle()
    val showRateDialog by viewModel.showRateDialog.collectAsStateWithLifecycle()
    val showShareDialog by viewModel.showShareDialog.collectAsStateWithLifecycle()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsStateWithLifecycle()
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()
    val processingMessage by viewModel.processingMessage.collectAsStateWithLifecycle()
    val activeTool by viewModel.activeTool.collectAsStateWithLifecycle()

    var showInfoDialog by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.checkForUpdateSuggestion()
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    if (!isOnboardingCompleted && activeViewerFile == null && activeTool == null) {
        OnboardingScreen(
            onFinishOnboarding = { viewModel.completeOnboarding() }
        )
        return
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

    BackHandler(enabled = activeTool != null) {
        viewModel.setActiveTool(null)
    }

    val isDarkTheme = com.example.ui.theme.LocalIsDarkTheme.current

    @Composable
    fun MainContentArea(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = activeTool,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInVertically(tween(260, easing = FastOutSlowInEasing)) { it / 14 } +
                            fadeIn(tween(220)) +
                            scaleIn(tween(260, easing = FastOutSlowInEasing), initialScale = 0.97f)) togetherWith
                            fadeOut(tween(150))
                    } else {
                        fadeIn(tween(200)) togetherWith
                            (slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it / 16 } + fadeOut(tween(160)))
                    }
                },
                label = "activeToolTransition"
            ) { tool ->
                if (tool != null) {
                    when (tool) {
                        PdfOperationType.MERGE -> MergeScreen(viewModel = viewModel)
                        PdfOperationType.SPLIT -> SplitScreen(viewModel = viewModel)
                        PdfOperationType.COMPRESS -> CompressScreen(viewModel = viewModel)
                        PdfOperationType.METADATA -> MetadataScreen(viewModel = viewModel)
                        PdfOperationType.ENCRYPT -> EncryptScreen(viewModel = viewModel)
                        PdfOperationType.WATERMARK -> WatermarkScreen(viewModel = viewModel)
                        PdfOperationType.SIGN -> SignScreen(viewModel = viewModel)
                        PdfOperationType.ROTATE -> RotateScreen(viewModel = viewModel, onNavigateBack = { viewModel.setActiveTool(null) })
                        PdfOperationType.IMAGE_TO_PDF -> ImageToPdfScreen(viewModel = viewModel)
                        PdfOperationType.PAGE_NUMBER -> PageNumberScreen(viewModel = viewModel)
                        PdfOperationType.TEXT_TO_PDF -> TextToPdfScreen(viewModel = viewModel)
                        PdfOperationType.PDF_TO_TEXT -> PdfToTextScreen(viewModel = viewModel)
                        PdfOperationType.PDF_TO_IMAGES -> PdfToImagesScreen(viewModel = viewModel)
                        PdfOperationType.HTML_TO_PDF -> HtmlToPdfScreen(viewModel = viewModel)
                        PdfOperationType.DOCUMENT_INFO -> DocumentInfoScreen(viewModel = viewModel, onNavigateBack = { viewModel.setActiveTool(null) })
                        else -> {}
                    }
                } else {
                    AnimatedContent(
                        targetState = currentTab,
                        transitionSpec = {
                            val dir = if (targetState > initialState) 1 else -1
                            (slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { it / 12 * dir } + fadeIn(tween(200))) togetherWith
                                (slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -it / 14 * dir } + fadeOut(tween(160)))
                        },
                        label = "tabSlideTransition"
                    ) { tab ->
                        when (tab) {
                            0 -> HubScreen(viewModel = viewModel)
                            1 -> ToolsScreen(viewModel = viewModel)
                            2 -> LibraryScreen(viewModel = viewModel)
                            3 -> SettingsScreen(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        Row(modifier = Modifier.fillMaxSize()) {
            AppNavigationRail(
                selectedTab = currentTab,
                onTabSelected = { viewModel.selectTab(it) }
            )
            Scaffold(
                modifier = Modifier.weight(1f),
                topBar = {
                    if (activeTool == null) {
                        AppTopBar(
                            onOpenInfoDialog = { showInfoDialog = true },
                            onOpenSettingsDialog = { viewModel.selectTab(3) },
                            themeMode = themeMode,
                            onToggleTheme = {
                                val nextMode = if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                                viewModel.setThemeMode(nextMode)
                            }
                        )
                    }
                },
                bottomBar = {
                    PoliteAdBanner()
                },
                snackbarHost = { SnackbarHost(snackbarHostState) }
            ) { innerPadding ->
                MainContentArea(modifier = Modifier.padding(innerPadding))
            }
        }
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (activeTool == null) {
                    AppTopBar(
                        onOpenInfoDialog = { showInfoDialog = true },
                        onOpenSettingsDialog = { viewModel.selectTab(3) },
                        themeMode = themeMode,
                        onToggleTheme = {
                            val nextMode = if (isDarkTheme) ThemeMode.LIGHT else ThemeMode.DARK
                            viewModel.setThemeMode(nextMode)
                        }
                    )
                }
            },
            bottomBar = {
                Column {
                    PoliteAdBanner()
                    if (activeTool == null) {
                        AppBottomNav(
                            selectedTab = currentTab,
                            onTabSelected = { viewModel.selectTab(it) }
                        )
                    }
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            MainContentArea(modifier = Modifier.padding(innerPadding))
        }
    }

    // Dialogs
    if (showDonationDialog) {
        BuyMeACoffeeDialog(
            onDismiss = { viewModel.dismissDonationDialog() },
            onShowMessage = { msg -> viewModel.showUserMessage(msg) }
        )
    }

    if (showInfoDialog) {
        InfoPrivacyDialog(
            onDismiss = { showInfoDialog = false },
            onReplayOnboarding = { viewModel.resetOnboardingForTesting() }
        )
    }

    if (showPoliteAdDialog) {
        PoliteAdDialog(
            onDismiss = { viewModel.dismissPoliteAdDialog() }
        )
    }

    if (showRateDialog) {
        SuggestRateDialog(
            onDismiss = { neverAskAgain -> viewModel.dismissRateDialog(neverAskAgain) },
            onSubmitRating = { rating, ctx -> viewModel.submitRateApp(rating, ctx) }
        )
    }

    if (showShareDialog) {
        SuggestShareDialog(
            onDismiss = { neverAskAgain -> viewModel.dismissShareDialog(neverAskAgain) },
            onShareApp = { ctx -> viewModel.performShareApp(ctx) }
        )
    }

    if (showUpdateDialog) {
        SuggestUpdateDialog(
            versionName = FirebaseConfigManager.latestVersionName.value,
            releaseNotes = FirebaseConfigManager.updateReleaseNotes.value,
            onUpdateNow = { ctx -> viewModel.openPlayStoreForUpdate(ctx) },
            onLater = { viewModel.dismissUpdateDialog(skipThisVersion = false) },
            onSkipVersion = { viewModel.dismissUpdateDialog(skipThisVersion = true) }
        )
    }

    activeViewerFile?.let { file ->
        PdfViewerDialog(
            file = file,
            onDismiss = { viewModel.closeViewer() },
            onSendToTool = { tool -> viewModel.sendViewerFileToTool(file, tool) }
        )
    }

    lastCompletedOutputFile?.let { completedFile ->
        if (activeViewerFile == null) {
            val context = androidx.compose.ui.platform.LocalContext.current
            val checkScale = remember(completedFile.absolutePath) { Animatable(0.45f) }
            LaunchedEffect(completedFile.absolutePath) {
                checkScale.animateTo(
                    targetValue = 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
            }

            Dialog(
                onDismissRequest = { viewModel.dismissCompletedOutputFileDialog() }
            ) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(max = 380.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(22.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                            modifier = Modifier
                                .size(60.dp)
                                .scale(checkScale.value)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = completedFile.name,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = lastCompletedOperationSummary.ifBlank {
                                stringResource(
                                    R.string.completed_saved_to_downloads,
                                    com.example.data.pdf.PdfEngine.formatFileSize(completedFile.length())
                                )
                            },
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        Button(
                            onClick = {
                                val path = completedFile.absolutePath
                                viewModel.dismissCompletedOutputFileDialog()
                                viewModel.openInViewer(path)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(
                                text = stringResource(R.string.action_preview),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = {
                                    viewModel.sharePdfFile(context, completedFile)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = stringResource(R.string.action_share),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val path = completedFile.absolutePath
                                    viewModel.dismissCompletedOutputFileDialog()
                                    viewModel.openDocumentInfoScreen(path)
                                },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(46.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.size(6.dp))
                                Text(
                                    text = stringResource(R.string.tool_doc_info_title),
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        TextButton(
                            onClick = { viewModel.dismissCompletedOutputFileDialog() }
                        ) {
                            Text(
                                text = stringResource(R.string.action_close),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedMetadata != null || isLoadingMetadata) {
        PdfMetadataDialog(
            metadata = selectedMetadata,
            isLoading = isLoadingMetadata,
            onDismiss = { viewModel.clearSelectedMetadata() },
            onOpenInViewer = { filePath ->
                viewModel.openInViewer(filePath)
            }
        )
    }

    // Global Operation Progress Indicator Overlay
    if (isProcessing) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                modifier = Modifier.padding(24.dp).widthIn(max = 320.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                    modifier = Modifier.padding(24.dp)
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = stringResource(R.string.op_processing_pdf),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = processingMessage.ifBlank { stringResource(R.string.op_executing_local) },
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}


