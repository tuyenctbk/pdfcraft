package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setOnExitAnimationListener { splashScreenViewProvider ->
            val splashScreenView = splashScreenViewProvider.view
            val fadeOut = android.animation.ObjectAnimator.ofFloat(splashScreenView, android.view.View.ALPHA, 1f, 0f)
            fadeOut.interpolator = android.view.animation.AccelerateInterpolator()
            fadeOut.duration = 350L
            fadeOut.addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    splashScreenViewProvider.remove()
                }
            })
            fadeOut.start()
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

        val incomingUri = intent?.data

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            LaunchedEffect(incomingUri) {
                incomingUri?.let { uri ->
                    try {
                        val localFile = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            com.example.data.pdf.PdfEngine.getLocalFileFromUri(applicationContext, uri)
                        }
                        if (localFile.exists()) {
                            viewModel.openInViewer(localFile.absolutePath)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling incoming URI", e)
                    }
                }
            }

            PDFCraftTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PDFCraftApp(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PDFCraftApp(
    viewModel: MainViewModel = viewModel()
) {
    val isOnboardingCompleted by viewModel.isOnboardingCompleted.collectAsState()
    val currentTab by viewModel.currentTab.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()
    val activeViewerFile by viewModel.activeViewerFile.collectAsState()
    val selectedMetadata by viewModel.selectedMetadata.collectAsState()
    val isLoadingMetadata by viewModel.isLoadingMetadata.collectAsState()
    val showPoliteAdDialog by viewModel.showPoliteAdDialog.collectAsState()
    val showRateDialog by viewModel.showRateDialog.collectAsState()
    val showShareDialog by viewModel.showShareDialog.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val isProcessing by viewModel.isProcessing.collectAsState()
    val processingMessage by viewModel.processingMessage.collectAsState()

    var showInfoDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

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

    if (!isOnboardingCompleted) {
        OnboardingScreen(
            onFinishOnboarding = { viewModel.completeOnboarding() }
        )
        return
    }

    val isWideScreen = LocalConfiguration.current.screenWidthDp >= 600

    val activeTool by viewModel.activeTool.collectAsState()

    BackHandler(enabled = activeTool != null) {
        viewModel.setActiveTool(null)
    }

    @Composable
    fun MainContentArea(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            AnimatedContent(
                targetState = activeTool,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
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
                        else -> {}
                    }
                } else {
                    Crossfade(
                        targetState = currentTab,
                        animationSpec = tween(200),
                        label = "tabCrossfade"
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
                            onOpenSettingsDialog = { showSettingsDialog = true },
                            themeMode = themeMode,
                            onToggleTheme = {
                                val nextMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
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
                        onOpenSettingsDialog = { showSettingsDialog = true },
                        themeMode = themeMode,
                        onToggleTheme = {
                            val nextMode = if (themeMode == ThemeMode.DARK) ThemeMode.LIGHT else ThemeMode.DARK
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
    if (showSettingsDialog) {
        SettingsDialog(
            viewModel = viewModel,
            onDismiss = { showSettingsDialog = false }
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
            onDismiss = { viewModel.closeViewer() }
        )
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


