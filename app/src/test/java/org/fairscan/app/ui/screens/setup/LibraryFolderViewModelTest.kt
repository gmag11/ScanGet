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
package org.fairscan.app.ui.screens.setup

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.PageV2
import org.fairscan.app.data.library.FileLibraryStore
import org.fairscan.app.data.library.FolderInspection
import org.fairscan.app.data.library.LibraryLocation
import org.fairscan.app.data.library.LibraryLocationSettings
import org.fairscan.app.data.library.LibraryLocations
import org.fairscan.app.data.library.LibraryMover
import org.fairscan.app.data.library.LibraryPlatform
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.data.library.LibraryStore
import org.fairscan.app.data.library.StoredLibraryLocation
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFolderViewModelTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeSettings(var stored: StoredLibraryLocation? = null) : LibraryLocationSettings {
        override suspend fun load() = stored
        override suspend fun save(location: StoredLibraryLocation?) { stored = location }
    }

    private inner class FakePlatform : LibraryPlatform {
        var failToOpen = false
        val safPermissions = mutableSetOf<String>()
        val released = mutableListOf<String>()
        override fun hasSafPermission(uri: String) = uri in safPermissions
        override fun isSafFolderReachable(uri: String) = File(uri).isDirectory
        override fun safStore(uri: String): LibraryStore {
            check(!failToOpen) { "the folder cannot be opened" }
            return FileLibraryStore(File(uri))
        }
        override fun safFolderName(uri: String) = File(uri).name
        override fun releaseSafPermission(uri: String) { safPermissions -= uri; released += uri }
    }

    private val platform = FakePlatform()
    private val settings = FakeSettings()
    private lateinit var locations: LibraryLocations

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun viewModel(): LibraryFolderViewModel {
        locations = LibraryLocations(settings, platform, LibraryMover(), emptyList())
        locations.load()
        return LibraryFolderViewModel(locations, { _, _, _ -> }, Dispatchers.Unconfined)
    }

    private fun dir(name: String) = File(folder.root, name).apply { mkdirs() }

    private fun addDocument(dir: File, id: String) {
        val store = FileLibraryStore(dir)
        store.writeSource(id, "100", byteArrayOf(1))
        store.writeMetadata(DocumentMetadataV3(id = id, name = "Doc $id", createdAt = 1, updatedAt = 10,
            pages = listOf(PageV2("100"))))
    }

    private fun events(vm: LibraryFolderViewModel): MutableList<LibraryFolderEvent> {
        val received = mutableListOf<LibraryFolderEvent>()
        CoroutineScope(Dispatchers.Unconfined).launch { vm.events.collect { received += it } }
        return received
    }

    @Test
    fun `the setup starts with nothing chosen and no folder proposed`() = runTest {
        val vm = viewModel()

        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp)
        assertThat(vm.uiState.value.confirmation).isNull()
        assertThat(vm.uiState.value.hasError).isFalse()
    }

    @Test
    fun `choosing a folder opens the picker and writes nothing`() = runTest {
        val vm = viewModel()
        val received = events(vm)

        vm.onChooseFolder()

        assertThat(received).containsExactly(LibraryFolderEvent.PickFolder)
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp)
        assertThat(settings.stored).isNull()
        assertThat(folder.root.list()).isEmpty()
    }

    @Test
    fun `a picked empty folder is used without asking`() = runTest {
        val picked = dir("Scans")
        platform.safPermissions += picked.path
        val vm = viewModel()

        vm.onFolderPicked(picked.path)

        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.Ready(LibraryLocation.Saf(picked.path, "Scans")))
        assertThat(vm.uiState.value.confirmation).isNull()
        assertThat(platform.released).isEmpty()
    }

    @Test
    fun `a folder with a previous installation is confirmed before anything is written`() = runTest {
        val picked = dir("FairScan")
        (1..3).forEach { addDocument(picked, "d$it") }
        platform.safPermissions += picked.path
        val vm = viewModel()
        val before = picked.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()

        vm.onFolderPicked(picked.path)

        val confirmation = vm.uiState.value.confirmation!!
        assertThat(confirmation.inspection).isEqualTo(FolderInspection(fairScanDocuments = 3, otherItems = 0))
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp) // not used yet
        assertThat(settings.stored).isNull()
        assertThat(picked.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()).isEqualTo(before)

        vm.onConfirmFolder()

        assertThat(vm.uiState.value.confirmation).isNull()
        assertThat(vm.uiState.value.status).isInstanceOf(LibraryStatus.Ready::class.java)
        assertThat(locations.currentStore()!!.list()).hasSize(3) // the documents are in the library
    }

    @Test
    fun `a folder with other content is confirmed too and the content is left alone`() = runTest {
        val picked = dir("Download")
        File(picked, "photo.jpg").writeText("x")
        File(picked, "docs").mkdirs()
        platform.safPermissions += picked.path
        val vm = viewModel()

        vm.onFolderPicked(picked.path)
        assertThat(vm.uiState.value.confirmation!!.inspection)
            .isEqualTo(FolderInspection(fairScanDocuments = 0, otherItems = 2))
        vm.onConfirmFolder()

        assertThat(File(picked, "photo.jpg").readText()).isEqualTo("x")
        assertThat(picked.list()!!.toSet()).containsExactlyInAnyOrder("photo.jpg", "docs")
        assertThat(locations.currentStore()!!.list()).isEmpty()
    }

    @Test
    fun `choosing another folder gives up the first and opens the picker again`() = runTest {
        val first = dir("First")
        File(first, "photo.jpg").writeText("x")
        platform.safPermissions += first.path
        val vm = viewModel()
        val received = events(vm)
        vm.onFolderPicked(first.path)

        vm.onChooseAnother()

        assertThat(vm.uiState.value.confirmation).isNull()
        assertThat(platform.released).containsExactly(first.path)
        assertThat(received).containsExactly(LibraryFolderEvent.PickFolder)
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp)
        assertThat(settings.stored).isNull()
    }

    @Test
    fun `when the library has documents elsewhere the user is offered to move them`() = runTest {
        val old = dir("Old"); val new = dir("New")
        addDocument(old, "d1"); addDocument(old, "d2")
        settings.stored = StoredLibraryLocation(old.path, "Old")
        platform.safPermissions += old.path
        platform.safPermissions += new.path
        val vm = viewModel()

        vm.onFolderPicked(new.path)

        val offer = vm.uiState.value.moveOffer!!
        assertThat(offer.documentCount).isEqualTo(2)
        assertThat(offer.fromName).isEqualTo("Old")
        assertThat(offer.candidate).isEqualTo(LibraryLocation.Saf(new.path, "New")) // named for the dialog
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.Ready(LibraryLocation.Saf(old.path, "Old")))

        vm.onMoveAnswer(true)

        assertThat(FileLibraryStore(new).list().map { it.info.id }).containsExactlyInAnyOrder("d1", "d2")
        assertThat(FileLibraryStore(old).list()).isEmpty()
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.Ready(LibraryLocation.Saf(new.path, "New")))
        assertThat(vm.uiState.value.notMoved).isEqualTo(0)
        assertThat(vm.uiState.value.isWorking).isFalse()
    }

    @Test
    fun `not moving leaves the documents where they are`() = runTest {
        val old = dir("Old"); val new = dir("New")
        addDocument(old, "d1")
        settings.stored = StoredLibraryLocation(old.path, "Old")
        platform.safPermissions += old.path
        platform.safPermissions += new.path
        val vm = viewModel()
        vm.onFolderPicked(new.path)

        vm.onMoveAnswer(false)

        assertThat(FileLibraryStore(old).list().map { it.info.id }).containsExactly("d1")
        assertThat(FileLibraryStore(new).list()).isEmpty()
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.Ready(LibraryLocation.Saf(new.path, "New")))
    }

    @Test
    fun `no move is offered when the current library is empty`() = runTest {
        val old = dir("Old"); val new = dir("New")
        settings.stored = StoredLibraryLocation(old.path, "Old")
        platform.safPermissions += old.path
        platform.safPermissions += new.path
        val vm = viewModel()

        vm.onFolderPicked(new.path)

        assertThat(vm.uiState.value.moveOffer).isNull()
        assertThat(vm.uiState.value.status).isInstanceOf(LibraryStatus.Ready::class.java)
    }

    @Test
    fun `a folder that cannot be used shows an error and is given up`() = runTest {
        val picked = dir("Scans")
        platform.safPermissions += picked.path
        platform.failToOpen = true
        val vm = viewModel()

        vm.onFolderPicked(picked.path)

        assertThat(vm.uiState.value.hasError).isTrue()
        assertThat(vm.uiState.value.isWorking).isFalse()
        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp)
        assertThat(settings.stored).isNull()
        assertThat(platform.released).containsExactly(picked.path)
    }

    @Test
    fun `a library saved as a path by an earlier build asks for the folder again`() = runTest {
        // The settings do not return a location that was saved as a path, so nothing is set up
        val vm = viewModel()
        val picked = dir("FairScan")
        (1..2).forEach { addDocument(picked, "d$it") }
        platform.safPermissions += picked.path

        assertThat(vm.uiState.value.status).isEqualTo(LibraryStatus.NotSetUp)
        vm.onFolderPicked(picked.path)
        vm.onConfirmFolder()

        assertThat(locations.currentStore()!!.list()).hasSize(2)
    }
}
