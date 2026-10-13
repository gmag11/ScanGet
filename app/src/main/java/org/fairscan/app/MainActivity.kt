/*
 * Copyright 2025-2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.provider.DocumentsContract
import android.os.Build
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES.Q
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.net.toFile
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.fairscan.app.data.FileLogger
import org.fairscan.app.data.ImageRepository
import org.fairscan.app.data.library.DEFAULT_LIBRARY_FOLDER_NAME
import org.fairscan.app.ui.Navigation
import org.fairscan.app.ui.Screen
import org.fairscan.app.ui.components.rememberCameraPermissionState
import org.fairscan.app.ui.screens.LibrariesScreen
import org.fairscan.app.ui.screens.about.AboutEvent
import org.fairscan.app.ui.screens.about.AboutScreen
import org.fairscan.app.ui.screens.about.AboutViewModel
import org.fairscan.app.ui.screens.about.createEmailWithImageIntent
import org.fairscan.app.ui.screens.camera.CameraEvent
import org.fairscan.app.ui.screens.camera.CameraScreen
import org.fairscan.app.ui.screens.camera.CameraViewModel
import org.fairscan.app.ui.screens.crop.CropScreen
import org.fairscan.app.ui.screens.document.AdjustmentActions
import org.fairscan.app.ui.screens.document.DocumentScreen
import org.fairscan.app.ui.screens.export.ExportActions
import org.fairscan.app.ui.screens.export.ExportEvent
import org.fairscan.app.ui.screens.export.ExportResult
import org.fairscan.app.ui.screens.export.ExportPageActions
import org.fairscan.app.ui.screens.export.ExportScreenWrapper
import org.fairscan.app.ui.screens.export.ExportViewModel
import org.fairscan.app.ui.screens.settings.OcrLanguagesScreen
import org.fairscan.app.ui.screens.setup.LibrarySetupScreen
import org.fairscan.app.ui.screens.setup.LibraryFolderViewModel
import org.fairscan.app.ui.screens.setup.LibraryFolderUiState
import org.fairscan.app.ui.screens.setup.LibraryFolderEvent
import org.fairscan.app.ui.screens.library.LibraryActions
import org.fairscan.app.ui.screens.library.LibraryScreen
import org.fairscan.app.ui.screens.library.LibraryViewModel
import org.fairscan.app.ui.screens.overview.DocumentOverviewScreen
import org.fairscan.app.ui.screens.setup.LibraryFolderDialogs
import org.fairscan.app.ui.screens.settings.LibraryFolderSetting
import org.fairscan.app.ui.screens.settings.SettingsScreen
import org.fairscan.app.ui.screens.settings.SettingsUiState
import org.fairscan.app.ui.screens.settings.SettingsViewModel
import org.fairscan.app.ui.theme.FairScanTheme
import org.opencv.android.OpenCVLoader
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var cameraViewModel: CameraViewModel
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initLibraries()

        val appContainer = (application as FairScanApp).appContainer
        val launchMode = resolveLaunchMode(intent)

        val sessionViewModel: SessionViewModel by viewModels {
            SessionViewModelFactory(
                application = application,
                launchMode = launchMode,
                appContainer = appContainer
            )
        }

        val imageRepository = sessionViewModel.imageRepository
        // The autosave waits for a pause in the edits: do not lose them if the app goes away
        lifecycle.addObserver(LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) sessionViewModel.saveToLibrary()
        })
        if (launchMode == LaunchMode.NORMAL) {
            // The library folder may have become (or stopped being) accessible while the app was away
            lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    lifecycleScope.launch(Dispatchers.IO) { appContainer.libraryLocations.refresh() }
                }
            })
        }
        val libraryViewModel: LibraryViewModel? = sessionViewModel.libraryRepository?.let { library ->
            viewModels<LibraryViewModel> {
                appContainer.viewModelFactory {
                    LibraryViewModel(library, it.logger, deleteDocument = { id -> viewModel.deleteDocument(id) })
                }
            }.value
        }
        val libraryFolderViewModel: LibraryFolderViewModel by viewModels {
            appContainer.viewModelFactory {
                LibraryFolderViewModel(
                    it.libraryLocations, BuildConfig.ALL_FILES_ACCESS_AVAILABLE, it.logger)
            }
        }
        viewModel = viewModels<MainViewModel> {
            appContainer.viewModelFactory {
                MainViewModel(
                    imageRepository, appContainer.logger, sessionViewModel.libraryRepository,
                    // A scan requested by another app has no library to set up
                    libraryStatus = if (launchMode == LaunchMode.NORMAL) appContainer.libraryLocations.status else null,
                )
            }
        }.value
        val exportViewModel: ExportViewModel by viewModels {
            appContainer.viewModelFactory {
                ExportViewModel(appContainer, imageRepository)
            }
        }
        val aboutViewModel: AboutViewModel by viewModels {
            appContainer.viewModelFactory {
                AboutViewModel(appContainer, imageRepository)
            }
        }
        cameraViewModel = viewModels<CameraViewModel> { appContainer.cameraViewModelFactory }.value

        val settingsViewModel: SettingsViewModel
            by viewModels { appContainer.settingsViewModelFactory }
        lifecycleScope.launch(Dispatchers.IO) {
            exportViewModel.cleanUpOldPreparedFiles(1000 * 3600)
        }
        enableEdgeToEdge()
        setContent {
            val logger = appContainer.logger
            val context = LocalContext.current
            val currentScreen by viewModel.currentScreen.collectAsStateWithLifecycle()
            val liveAnalysisState by cameraViewModel.liveAnalysisState.collectAsStateWithLifecycle()
            val importState by cameraViewModel.importState.collectAsStateWithLifecycle()
            val document by viewModel.documentUiModel.collectAsStateWithLifecycle()
            val documentUiState by viewModel.documentUiState.collectAsStateWithLifecycle()
            val adjustmentState by viewModel.adjustmentState.collectAsStateWithLifecycle()
            val cropInitialState by viewModel.cropInitState.collectAsStateWithLifecycle()
            val exportUiState by exportViewModel.uiState.collectAsStateWithLifecycle()
            val settingsUiState by settingsViewModel.uiState.collectAsStateWithLifecycle()
            val libraryFolderState by libraryFolderViewModel.uiState.collectAsStateWithLifecycle()
            val cameraPermission = rememberCameraPermissionState()
            CollectCameraEvents(cameraViewModel, viewModel)
            CollectExportEvents(context, exportViewModel)
            CollectAboutEvents(context, aboutViewModel, imageRepository)

            FairScanTheme {
                if (launchMode == LaunchMode.NORMAL) {
                    LibraryFolderHandling(libraryFolderViewModel, libraryFolderState)
                }
                val navigation = navigation(viewModel, launchMode)
                val onExportClick = if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
                    {
                        lifecycleScope.launch {
                            try {
                                val result = exportViewModel.generatePdfForExternalCall()
                                sendActivityResult(result)
                            } catch (e: Exception) {
                                val message = "Export failed"
                                showToast(message)
                                appContainer.logger.e("MainActivity", message, e)
                                return@launch
                            }
                            viewModel.startNewDocument()
                            finish()
                        }
                        Unit
                    }
                } else {
                    navigation.toExportScreen
                }

                when (currentScreen) {
                    null -> {
                        // waiting to load pages to get an initial screen
                    }
                    is Screen.Main.Library -> {
                        val library = checkNotNull(libraryViewModel) { "No library to show" }
                        val libraryState by library.uiState.collectAsStateWithLifecycle()
                        val isOpening by viewModel.isOpeningDocument.collectAsStateWithLifecycle()
                        val isQuickSharing by exportViewModel.isQuickSharing.collectAsStateWithLifecycle()
                        val documentShare by exportViewModel.documentShare.collectAsStateWithLifecycle()
                        var sharingId by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(isQuickSharing) { if (!isQuickSharing) sharingId = null }
                        LaunchedEffect(Unit) { library.refresh() }
                        LibraryScreen(
                            uiState = libraryState,
                            navigation = navigation,
                            actions = LibraryActions(
                                onOpen = { id -> viewModel.openDocument(id) { navigation.toOverviewScreen() } },
                                onScan = {
                                    viewModel.startNewDocument()
                                    navigation.toCameraScreen()
                                },
                                onImport = {
                                    viewModel.requestImportOnCameraOpen()
                                    viewModel.startNewDocument()
                                    navigation.toCameraScreen()
                                },
                                onAddPages = { id -> viewModel.openDocument(id) { navigation.toCameraScreen() } },
                                onShare = { id ->
                                    sharingId = id
                                    viewModel.openDocument(id) { exportViewModel.shareWholeDocument() }
                                },
                                onExport = { id ->
                                    viewModel.openDocument(id) {
                                        viewModel.requestExportAllPages()
                                        navigation.toExportScreen()
                                    }
                                },
                                onRename = library::rename,
                                onDelete = library::delete,
                                onSearch = library::openSearch,
                                onQueryChange = library::onQueryChange,
                                onCloseSearch = library::closeSearch,
                                onStartSelection = library::startSelection,
                                onToggleSelection = library::toggleSelection,
                                onSelectAll = library::selectAll,
                                onClearSelection = library::clearSelection,
                                onShareSelected = {
                                    exportViewModel.shareDocuments(
                                        libraryState.selectedIds.toList(),
                                        beforeExport = { sessionViewModel.libraryRepository?.flush() },
                                    )
                                },
                                onCancelShare = exportViewModel::cancelDocumentShare,
                                onDeleteSelected = library::deleteSelected,
                            ),
                            loadCover = library::loadCover,
                            busy = isOpening,
                            sharingId = sharingId,
                            shareProgress = documentShare,
                        )
                    }
                    is Screen.Main.Overview -> {
                        BackHandler { navigation.back() }
                        val documentName by viewModel.documentName.collectAsStateWithLifecycle()
                        DocumentOverviewScreen(
                            documentName = documentName,
                            document = document,
                            navigation = navigation,
                            onPageClick = { index -> viewModel.navigateTo(Screen.Main.Document(index)) },
                            onPageReorder = { id, index -> viewModel.movePage(id, index) },
                            onAddPage = navigation.toCameraScreen,
                            onExport = {
                                // From the pages of the document, all of it is exported (from the page
                                // editor, the page being edited)
                                viewModel.requestExportAllPages()
                                navigation.toExportScreen()
                            },
                            onRename = viewModel::renameDocument,
                            onDeleteDocument = viewModel::deleteCurrentDocument,
                            loadThumbnail = viewModel::gridThumbnail,
                        )
                    }
                    is Screen.Main.LibrarySetup -> {
                        LibrarySetupScreen(
                            uiState = libraryFolderState,
                            navigation = navigation,
                            onUseDefaultFolder = libraryFolderViewModel::onUseDefaultFolder,
                            onChooseFolder = libraryFolderViewModel::onChooseFolder,
                        )
                    }
                    is Screen.Main.Camera -> {
                        BackHandler { navigation.back() }
                        val pickMultiple = rememberLauncherForActivityResult(
                            ActivityResultContracts.GetMultipleContents()) {
                                uris -> cameraViewModel.importPhotos(uris)
                            }
                        // "Import" in the library opens the camera screen already asking for the photos
                        LaunchedEffect(Unit) {
                            if (viewModel.consumeImportOnCameraOpen()) {
                                cameraViewModel.onImportClicked()
                                pickMultiple.launch("image/*")
                            }
                        }
                        CameraScreen(
                            viewModel,
                            cameraViewModel,
                            navigation,
                            liveAnalysisState,
                            importState,
                            onImageAnalyzed = { image -> cameraViewModel.liveAnalysis(image) },
                            // With the library, the pages of the document come before exporting it
                            onFinalizePressed =
                                if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) onExportClick
                                else navigation.toOverviewScreen,
                            cameraPermission = cameraPermission,
                            onImportClicked = {
                                cameraViewModel.onImportClicked()
                                pickMultiple.launch("image/*")
                            }
                        )
                    }
                    is Screen.Main.EditImage -> {
                        CropScreen(
                            pageId = documentUiState.currentPage?.key?.pageId ?: "",
                            initState = cropInitialState,
                            navigation = navigation,
                            onUpdatePageQuad = { quad -> viewModel.setCurrentPageUserQuad(quad) },
                        )
                    }
                    is Screen.Main.Document -> {
                        DocumentScreen (
                            uiState = documentUiState,
                            navigation = navigation,
                            onExportClick = onExportClick,
                            onDeleteImage =  { viewModel.deleteCurrentPage() },
                            onRotateImage = { clockwise -> viewModel.rotateCurrentPage(clockwise) },
                            onColorModeSelected = { viewModel.setCurrentPageColorMode(it) },
                            onCropClick = { viewModel.onClickOnCropButton() },
                            onPageReorder = { id, newIndex -> viewModel.movePage(id, newIndex) },
                            onPageSelected = viewModel::onPageSelected,
                            adjustmentState = adjustmentState,
                            adjustmentActions = AdjustmentActions(
                                onStart = viewModel::startAdjusting,
                                onChange = viewModel::setAdjustment,
                                onReset = viewModel::resetAdjustments,
                                onApplyToAll = viewModel::applyAdjustmentsToAll,
                                onCancel = viewModel::cancelAdjusting,
                                onConfirm = viewModel::confirmAdjusting,
                            ),
                        )
                    }
                    is Screen.Main.Export -> {
                        LaunchedEffect(Unit) {
                            exportViewModel.setActivePage(documentUiState.currentPage?.key?.pageId)
                            // Exporting a document from the library exports all of it
                            if (viewModel.consumeExportAllPages()) exportViewModel.selectAll()
                        }
                        ExportScreenWrapper(
                            navigation = navigation,
                            uiState = exportUiState,
                            currentDocument = document,
                            pageActions = ExportPageActions(
                                onPageTap = exportViewModel::onPageTap,
                                onPageLongPress = exportViewModel::onPageLongPress,
                                onPageReorder = { id, newIndex -> viewModel.movePage(id, newIndex) },
                                onSelectAll = exportViewModel::selectAll,
                            ),
                            exportActions = ExportActions(
                                prepareExportIfNeeded = exportViewModel::prepareExportIfNeeded,
                                setFilename = exportViewModel::setFilename,
                                share = { exportViewModel.onShareClicked() },
                                shareSeparately = { exportViewModel.onShareSeparatelyClicked() },
                                save = { exportViewModel.onSaveClicked() },
                                open = { item -> openUri(item.uri, item.format.mimeType, logger) },
                                cancelPreparationJob = exportViewModel::cancelPreparationJob,
                            ),
                            onCloseScan = {
                                exportViewModel.resetFilename()
                                viewModel.startNewDocument()
                                viewModel.navigateTo(Screen.Main.Camera)
                            }
                        )
                    }
                    is Screen.Overlay.About -> {
                        LaunchedEffect(Unit) {
                            aboutViewModel.refreshLastCapturedImageState()
                        }
                        val aboutUiState by aboutViewModel.uiState.collectAsStateWithLifecycle()
                        AboutScreen(
                            aboutUiState = aboutUiState,
                            onBack = navigation.back,
                            onCopyLogs = { aboutViewModel.onCopyLogsClicked() },
                            onContactWithLastImageClicked =
                                { aboutViewModel.onContactWithLastImageClicked() },
                            onStartActivity = { intent ->
                                try {
                                    startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    showToast(getString(R.string.error_occurred))
                                }
                            },
                            onViewLibraries = navigation.toLibrariesScreen)
                    }
                    is Screen.Overlay.Libraries -> {
                        LibrariesScreen(onBack = navigation.back)
                    }
                    is Screen.Overlay.Settings -> {
                        SettingsScreenWrapper(settingsUiState, settingsViewModel, libraryFolderViewModel, libraryFolderState, navigation, logger)
                    }
                    is Screen.Overlay.OcrLanguages -> {
                        OcrLanguagesScreen(
                            uiState = settingsUiState,
                            onBack = navigation.back,
                            onLanguageClick = settingsViewModel::onLanguageClick,
                            onRemoveLanguage = settingsViewModel::onRemoveLanguage,
                            onCancelOcrDownload = settingsViewModel::cancelOcrDownload,
                        )
                    }
                }
            }
        }
    }

    /**
     * What the library folder flow needs from Android: the system folder picker, the system screen
     * where "All files access" is granted, and the dialogs. It reacts to the events of [viewModel].
     */
    @Composable
    private fun LibraryFolderHandling(viewModel: LibraryFolderViewModel, uiState: LibraryFolderUiState) {
        val logger = (application as FairScanApp).appContainer.logger
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.takePersistableUriPermission(uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    viewModel.onFolderPicked(uri.toString())
                } catch (e: Exception) {
                    logger.e("LibraryFolder", "Failed to get access to $uri", e)
                    showToast(getString(R.string.error_file_picker_result))
                }
            }
        }
        val allFilesAccessScreen = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()) { viewModel.onAllFilesAccessResult() }
        val writePermission = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()) { viewModel.onAllFilesAccessResult() }

        LaunchedEffect(Unit) {
            viewModel.events.collect { event ->
                when (event) {
                    LibraryFolderEvent.RequestAllFilesAccess -> {
                        try {
                            if (SDK_INT >= Build.VERSION_CODES.R) {
                                allFilesAccessScreen.launch(allFilesAccessIntent())
                            } else {
                                writePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            }
                        } catch (e: Exception) {
                            logger.e("LibraryFolder", "Cannot ask for all files access", e)
                            viewModel.onAllFilesAccessResult()
                        }
                    }
                    is LibraryFolderEvent.PickFolder -> {
                        try {
                            pickFolder.launch(pickerInitialUri(event.defaultFolderExists))
                        } catch (e: Exception) {
                            val message = getString(R.string.error_file_picker_launch)
                            logger.e("LibraryFolder", message, e)
                            showToast(message)
                        }
                    }
                }
            }
        }
        LibraryFolderDialogs(
            uiState,
            onConfirmFolder = viewModel::onConfirmFolder,
            onChooseAnother = viewModel::onChooseAnother,
            onMoveAnswer = viewModel::onMoveAnswer,
            onDismissNotMoved = viewModel::onDismissNotMoved,
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun allFilesAccessIntent(): Intent {
        val appIntent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.fromParts("package", packageName, null))
        // A few devices only have the screen listing every app
        return if (appIntent.resolveActivity(packageManager) != null) appIntent
        else Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /** The picker starts in the FairScan folder if it exists, so that it only has to be confirmed. */
    private fun pickerInitialUri(defaultFolderExists: Boolean): Uri =
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents",
            if (defaultFolderExists) "primary:$DEFAULT_LIBRARY_FOLDER_NAME" else "primary:")

    private fun resolveLaunchMode(intent: Intent?): LaunchMode {
        return when (intent?.action) {
            "org.fairscan.app.action.SCAN_TO_PDF" -> LaunchMode.EXTERNAL_SCAN_TO_PDF
            "android.intent.action.GET_CONTENT" -> LaunchMode.EXTERNAL_SCAN_TO_PDF
            else -> LaunchMode.NORMAL
        }
    }

    private fun showToast(text: String) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun SettingsScreenWrapper(
        settingsUiState: SettingsUiState,
        settingsViewModel: SettingsViewModel,
        libraryFolderViewModel: LibraryFolderViewModel,
        libraryFolderState: LibraryFolderUiState,
        nav: Navigation,
        logger: FileLogger,
    ) {
        val launcher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocumentTree()
        ) { uri ->
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                try {
                    contentResolver.takePersistableUriPermission(uri, flags)
                    settingsViewModel.setExportDirUri(uri.toString())
                } catch (e: Exception) {
                    logger.e("Settings", "Failed to set export dir to $uri", e)
                    showToast(this.getString(R.string.error_file_picker_result))
                }
            }
        }
        LaunchedEffect(Unit) {
            settingsViewModel.refreshExportDirName()
        }
        SettingsScreen(
            settingsUiState,
            onDefaultColorModeChanged = { mode -> settingsViewModel.setDefaultColorMode(mode) },
            onTorchAlwaysOnChanged = settingsViewModel::setTorchAlwaysOn,
            onOcrOnSaveChanged = settingsViewModel::setOcrOnSave,
            onChooseDirectoryClick = {
                try {
                    launcher.launch(null)
                } catch (e: Exception) {
                    val message = getString(R.string.error_file_picker_launch)
                    logger.e("Settings", message, e)
                    showToast(message)
                }
            },
            onResetExportDirClick = { settingsViewModel.setExportDirUri(null) },
            onExportFormatChanged = { format -> settingsViewModel.setExportFormat(format) },
            onExportQualityChanged = { quality -> settingsViewModel.setExportQuality(quality) },
            onDefaultFileNameStyleChanged = settingsViewModel::setDefaultFilenameStyle,
            navigation = nav,
            library = LibraryFolderSetting(
                status = libraryFolderState.status,
                canUseDefaultFolder = libraryFolderState.allFilesAccessOffered,
                defaultFolderName = libraryFolderState.defaultFolderName,
                onChooseFolderClick = libraryFolderViewModel::onChooseFolder,
                onUseDefaultFolderClick = libraryFolderViewModel::onUseDefaultFolder,
            ),
        )
    }

    @Composable
    private fun CollectAboutEvents(
        context: Context,
        aboutViewModel: AboutViewModel,
        imageRepository: ImageRepository,
    ) {
        val clipboard = LocalClipboard.current
        val msgCopiedLogs = stringResource(R.string.copied_logs)
        LaunchedEffect(Unit) {
            aboutViewModel.events.collect { event ->
                when (event) {
                    is AboutEvent.CopyLogs -> {
                        clipboard.setClipEntry(
                            ClipData.newPlainText("FairScan logs", event.logs).toClipEntry()
                        )
                        showToast(msgCopiedLogs)
                    }
                    is AboutEvent.PrepareEmailWithLastImage -> {
                        val file = imageRepository.lastAddedSourceFile()
                        if (file != null) {
                            startActivity(createEmailWithImageIntent(context, file))
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CollectExportEvents(
        context: Context,
        exportViewModel: ExportViewModel,
    ) {
        val storagePermissionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                exportViewModel.onSaveClicked()
            } else {
                showToast(this.getString(R.string.storage_permission_denied))
            }
        }
        LaunchedEffect(Unit) {
            exportViewModel.events.collect { event ->
                when (event) {
                    ExportEvent.RequestSave -> {
                        checkPermissionThen(storagePermissionLauncher) {
                            exportViewModel.onRequestSave(context)
                        }
                    }
                    is ExportEvent.Share -> {
                        share(event.result)
                    }
                }
            }
        }
    }

    @Composable
    private fun CollectCameraEvents(
        cameraViewModel: CameraViewModel,
        viewModel: MainViewModel,
    ) {
        LaunchedEffect(Unit) {
            cameraViewModel.events.collect { event ->
                when (event) {
                    is CameraEvent.ImageCaptured -> viewModel.handleImageCaptured(event.page)
                }
            }
        }
    }

    private fun share(result: ExportResult) {
        if (result.files.isEmpty()) return

        val uris = result.files.map(::uriForFile)
        val intent = Intent().apply {
            action = if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE
            type = result.format.mimeType
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

            if (uris.size == 1) {
                putExtra(Intent.EXTRA_STREAM, uris[0])
            } else {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            }
        }
        val chooser = Intent.createChooser(intent, getString(R.string.share_document))

        val resolveInfos = packageManager.queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY)
        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            for (uri in uris) {
                grantUriPermission(pkg, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }

        startActivity(chooser)
    }

    private fun sendActivityResult(result: ExportResult?) {
        val pdf = result as? ExportResult.Pdf ?: return

        val uri = uriForFile(pdf.file)
        val resultIntent = Intent().apply {
            data = uri
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        setResult(RESULT_OK, resultIntent)
    }

    private fun uriForFile(file: File): Uri {
        return org.fairscan.app.ui.uriForFile(this, file)
    }

    private fun checkPermissionThen(
        requestPermissionLauncher: ManagedActivityResultLauncher<String, Boolean>,
        action: () -> Unit
    ) {
        val permission = Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (SDK_INT < Q && checkSelfPermission(this, permission) != PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(permission)
        } else {
            action()
        }
    }

    private fun openUri(fileUri: Uri?, mimeType: String, logger: FileLogger) {
        if (fileUri == null) return
        val uriToOpen: Uri =
            if (fileUri.scheme == ContentResolver.SCHEME_CONTENT) {
                fileUri
            } else {
                uriForFile(fileUri.toFile())
            }
        try {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uriToOpen, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                clipData = ClipData.newUri(contentResolver, "file", uriToOpen)
            }
            val chooser = Intent.createChooser(openIntent, getString(R.string.open_file)).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(chooser)
        } catch (_: ActivityNotFoundException) {
            showToast(getString(R.string.error_no_app))
        } catch (e: Exception) {
            val errorMessage =
                "Failed to open URI, scheme=${uriToOpen.scheme}, authority=${uriToOpen.authority}"
            logger.e("OpenUri", errorMessage, e)
            showToast(getString(R.string.error_occurred))
        }
    }

    private fun initLibraries() {
        com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(applicationContext)

        if (!OpenCVLoader.initLocal()) {
            Log.e("OpenCV", "Initialization failed")
        } else {
            Log.d("OpenCV", "Initialization successful")
        }
    }

    private fun navigation(viewModel: MainViewModel, launchMode: LaunchMode): Navigation = Navigation(
        toCameraScreen = { viewModel.navigateTo(Screen.Main.Camera) },
        toEditImageScreen = { viewModel.navigateTo(Screen.Main.EditImage) },
        toDocumentScreen = { viewModel.navigateTo(Screen.Main.Document()) },
        toOverviewScreen = { viewModel.navigateTo(Screen.Main.Overview) },
        toExportScreen = { viewModel.navigateTo(Screen.Main.Export) },
        toAboutScreen = { viewModel.navigateTo(Screen.Overlay.About) },
        toLibrariesScreen = { viewModel.navigateTo(Screen.Overlay.Libraries) },
        toSettingsScreen = if (launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) null else {
            {
                viewModel.navigateTo(Screen.Overlay.Settings)
            }
        },
        toOcrLanguagesScreen = { viewModel.navigateTo(Screen.Overlay.OcrLanguages) },
        back = {
            val origin = viewModel.currentScreen.value
            viewModel.navigateBack()
            val destination = viewModel.currentScreen.value
            if (destination == origin && launchMode == LaunchMode.EXTERNAL_SCAN_TO_PDF) {
                setResult(RESULT_CANCELED)
                finish()
            }
        },
        shouldDisplayBackButton = {
            // The library is where the app is left from; everything else goes back somewhere
            val screen = viewModel.currentScreen.value
            screen !is Screen.Main.Library && screen !is Screen.Main.LibrarySetup
        }
    )

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (viewModel.currentScreen.value is Screen.Main.Camera) {
                cameraViewModel.onVolumeKeyPressed()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
