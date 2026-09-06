package com.jeerovan.comfer

import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AppReorderTest {
    @get:Rule val composeRule = createComposeRule()
    private val application = ApplicationProvider.getApplicationContext<ComferApp>()
    private val store = ViewModelStore()
    private lateinit var viewModel: AppInfoViewModel
    private var originalOrder: List<String>? = null
    private var originalQuickOrder: List<String>? = null
    private var originalFolders: Map<String, FolderData> = emptyMap()
    private lateinit var listState: LazyListState
    private lateinit var scrollScope: CoroutineScope

    @Before
    fun setUp() {
        runBlocking {
            application.initializeApplicationData()
            StartupCoordinator.awaitReady()
            originalOrder = AppInfoManager.getAppPackageNames(application, LIST)
            originalQuickOrder = AppInfoManager.getAppPackageNames(application, AppInfoManager.QUICK_APPS_LIST_NAME)
            originalFolders = AppInfoManager.getFolders(application)
        }
        composeRule.runOnIdle {
            viewModel = ViewModelProvider(store,
                ViewModelProvider.AndroidViewModelFactory.getInstance(application),
            )[AppInfoViewModel::class.java]
        }
        composeRule.waitUntil(60_000) {
            viewModel.uiState.value.primaryApps.size >= 3 &&
                PerformanceTrace.appRefreshStats().active == 0
        }
        composeRule.setContent {
            val state by viewModel.uiState.collectAsState()
            listState = rememberLazyListState()
            scrollScope = rememberCoroutineScope()
            MaterialTheme {
                AppListColumn(
                    title = "Test apps", apps = state.primaryApps, canReOrder = true,
                    listState = listState, modifier = Modifier.width(120.dp),
                    listName = LIST, viewModel = viewModel, selectedList = null,
                    selectedPackageNames = emptySet(), iconSize = 48.dp, iconShape = CircleShape,
                    onItemSelect = { _, _ -> }, onAddFolderClick = {}, folders = 10,
                )
            }
        }
    }

    @After
    fun restore() {
        composeRule.runOnIdle { store.clear() }
        runBlocking {
            originalOrder?.let { AppInfoManager.saveAppPackageNames(application, LIST, it) }
            originalQuickOrder?.let {
                AppInfoManager.saveAppPackageNames(application, AppInfoManager.QUICK_APPS_LIST_NAME, it)
            }
            AppInfoManager.saveFolders(application, originalFolders)
        }
    }

    @Test
    fun firstItemMoveIsVisibleBeforeCallbackReturnsAndRapidMovesPersistInOrder() {
        val original = viewModel.uiState.value.primaryApps.map { it.packageName }
        val expected = original.toMutableList().apply { add(1, removeAt(0)) }
        composeRule.runOnIdle {
            viewModel.moveAppInList(LIST, 0, 1)
            assertEquals(expected, viewModel.uiState.value.primaryApps.map { it.packageName })
            viewModel.moveAppInList(LIST, 1, 2)
            expected.add(2, expected.removeAt(1))
            assertEquals(expected, viewModel.uiState.value.primaryApps.map { it.packageName })
        }
        awaitSaved(expected)
    }

    @Test
    fun longPressDragFirstItemToSecondAndBack() {
        val original = viewModel.uiState.value.primaryApps.map { it.packageName }
        drag(original[0], original[1])
        val swapped = original.toMutableList().apply { add(1, removeAt(0)) }
        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.primaryApps.map { it.packageName } == swapped
        }
        awaitSaved(swapped)
        drag(original[0], original[1])
        composeRule.waitUntil(5_000) {
            viewModel.uiState.value.primaryApps.map { it.packageName } == original
        }
        awaitSaved(original)
    }

    private fun drag(from: String, to: String) {
        val source = composeRule.onNodeWithTag("manage-app:$LIST:$from")
        val sourceBounds = source.fetchSemanticsNode().boundsInRoot
        val targetBounds = composeRule.onNodeWithTag("manage-app:$LIST:$to")
            .fetchSemanticsNode().boundsInRoot
        val delta = targetBounds.center - sourceBounds.center
        source.performTouchInput {
            down(center)
            advanceEventTime(600)
            repeat(20) { step -> moveTo(center + delta * ((step + 1) / 20f), delayMillis = 20) }
            up()
        }
        composeRule.waitForIdle()
    }

    @Test
    fun movedAppsAreRevealedAtTopOfScrolledDestination() {
        prepareQuickList(0)
        val packages = viewModel.uiState.value.primaryApps.take(2).map { it.packageName }.toSet()
        runBlocking {
            viewModel.performMoveAppsToList(LIST, AppInfoManager.QUICK_APPS_LIST_NAME, packages)
        }
        scrollAwayFromTop()
        runBlocking {
            viewModel.performMoveAppsToList(AppInfoManager.QUICK_APPS_LIST_NAME, LIST, packages)
        }
        assertNewItemsRevealed()
        assertEquals(packages, viewModel.uiState.value.primaryApps.take(2).map { it.packageName }.toSet())
    }

    @Test
    fun newFolderIsRevealedAtTopOfScrolledDestination() {
        scrollAwayFromTop()
        composeRule.runOnIdle { viewModel.createNewFolder("Scroll regression") }
        composeRule.waitUntil(10_000) {
            viewModel.uiState.value.primaryApps.firstOrNull()?.label == "Scroll regression"
        }
        assertNewItemsRevealed()
        awaitSaved(viewModel.uiState.value.primaryApps.map { it.packageName })
    }

    @Test
    fun nineSelectedAppsLeaveAllListsAndStoredPositionsUnchanged() {
        prepareQuickList(0)
        assertRejectedMovePreservesLists(9)
    }

    @Test
    fun sevenQuickAppsPlusTwoSelectedAppsRejectsEntireMove() {
        prepareQuickList(7)
        assertRejectedMovePreservesLists(2)
    }

    @Test
    fun fillingQuickListToExactlyEightSucceeds() {
        prepareQuickList(7)
        val before = viewModel.uiState.value.primaryApps
        val selected = setOf(before.first().packageName)
        val result = runBlocking { viewModel.performMoveAppsToList(LIST, QUICK, selected) }
        assertEquals(AppMoveResult.MOVED, result)
        assertEquals(8, viewModel.uiState.value.quickApps.size)
        assertEquals(before.drop(1), viewModel.uiState.value.primaryApps)
        awaitSaved(before.drop(1).map { it.packageName })
    }

    @Test
    fun concurrentTransfersCannotOverfillQuickList() {
        prepareQuickList(7)
        val selected = viewModel.uiState.value.primaryApps.take(2).map { it.packageName }
        val before = viewModel.uiState.value.primaryApps
        val results = runBlocking {
            selected.map { pkg -> async { viewModel.performMoveAppsToList(LIST, QUICK, setOf(pkg)) } }.awaitAll()
        }
        assertEquals(1, results.count { it == AppMoveResult.MOVED })
        assertEquals(1, results.count { it == AppMoveResult.QUICK_FULL })
        val quick = viewModel.uiState.value.quickApps.map { it.packageName }
        assertEquals(8, quick.size)
        val expected = before.filter { it.packageName !in quick }
        assertEquals(expected, viewModel.uiState.value.primaryApps)
        awaitSaved(expected.map { it.packageName })
    }

    @Test
    fun ghostsCannotMoveDirectlyToQuick() {
        prepareQuickList(0)
        val selected = setOf(viewModel.uiState.value.primaryApps.first().packageName)
        runBlocking { viewModel.performMoveAppsToList(LIST, AppInfoManager.REST_APPS_LIST_NAME, selected) }
        val before = viewModel.uiState.value
        val result = runBlocking {
            viewModel.performMoveAppsToList(AppInfoManager.REST_APPS_LIST_NAME, QUICK, selected)
        }
        assertEquals(AppMoveResult.REJECTED, result)
        assertEquals(before.quickApps, viewModel.uiState.value.quickApps)
        assertEquals(before.primaryApps, viewModel.uiState.value.primaryApps)
        assertEquals(before.restApps, viewModel.uiState.value.restApps)
    }

    private fun prepareQuickList(count: Int) = runBlocking {
        val existing = viewModel.uiState.value.quickApps.map { it.packageName }.toSet()
        viewModel.performMoveAppsToList(QUICK, LIST, existing)
        if (count > 0) {
            val selected = viewModel.uiState.value.primaryApps.take(count).map { it.packageName }.toSet()
            assertEquals(count, selected.size)
            assertEquals(AppMoveResult.MOVED, viewModel.performMoveAppsToList(LIST, QUICK, selected))
        }
        assertEquals(count, viewModel.uiState.value.quickApps.size)
    }

    private fun assertRejectedMovePreservesLists(selectionCount: Int) {
        val before = viewModel.uiState.value
        val selected = before.primaryApps.take(selectionCount).map { it.packageName }.toSet()
        assertEquals(selectionCount, selected.size)
        val storedPrimary = runBlocking { AppInfoManager.getAppPackageNames(application, LIST) }
        val storedQuick = runBlocking { AppInfoManager.getAppPackageNames(application, QUICK) }
        val result = runBlocking { viewModel.performMoveAppsToList(LIST, QUICK, selected) }
        assertEquals(AppMoveResult.QUICK_FULL, result)
        assertEquals(before.quickApps, viewModel.uiState.value.quickApps)
        assertEquals(before.primaryApps, viewModel.uiState.value.primaryApps)
        assertEquals(before.restApps, viewModel.uiState.value.restApps)
        assertEquals(storedPrimary, runBlocking { AppInfoManager.getAppPackageNames(application, LIST) })
        assertEquals(storedQuick, runBlocking { AppInfoManager.getAppPackageNames(application, QUICK) })
    }

    private fun scrollAwayFromTop() {
        composeRule.runOnIdle { scrollScope.launch { listState.scrollToItem(5) } }
        composeRule.waitUntil(5_000) { listState.firstVisibleItemIndex >= 5 }
    }

    private fun assertNewItemsRevealed() {
        composeRule.waitUntil(5_000) {
            listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
        }
    }

    private fun awaitSaved(expected: List<String>) {
        composeRule.waitUntil(10_000) {
            runBlocking { AppInfoManager.getAppPackageNames(application, LIST) } == expected
        }
    }

    private companion object {
        const val LIST = AppInfoManager.PRIMARY_APPS_LIST_NAME
        const val QUICK = AppInfoManager.QUICK_APPS_LIST_NAME
    }
}
