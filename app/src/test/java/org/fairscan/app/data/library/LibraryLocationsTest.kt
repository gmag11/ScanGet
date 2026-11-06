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
package org.fairscan.app.data.library

import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.DocumentMetadataV3
import org.fairscan.app.data.PageV2
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LibraryLocationsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private class FakeSettings(var stored: StoredLibraryLocation? = null) : LibraryLocationSettings {
        override suspend fun load() = stored
        override suspend fun save(location: StoredLibraryLocation?) { stored = location }
    }

    // A platform made of directories: the "tree URI" of a folder is its path in the temp folder, and
    // what the app is allowed to do with it is set by the test
    private inner class FakePlatform : LibraryPlatform {
        val safPermissions = mutableSetOf<String>()
        val unreachable = mutableSetOf<String>()
        val released = mutableListOf<String>()
        override fun hasSafPermission(uri: String) = uri in safPermissions
        override fun isSafFolderReachable(uri: String) = uri !in unreachable && File(uri).isDirectory
        override fun safStore(uri: String) = FileLibraryStore(File(uri))
        override fun safFolderName(uri: String) = File(uri).name
        override fun releaseSafPermission(uri: String) { safPermissions -= uri; released += uri }
    }

    private val platform = FakePlatform()
    private val settings = FakeSettings()

    private fun locations(legacy: List<File> = emptyList()) =
        LibraryLocations(settings, platform, LibraryMover(), legacy)

    private fun dir(name: String) = File(folder.root, name).apply { mkdirs() }

    // A folder the user picked: the app holds its permission
    private fun picked(name: String): File = dir(name).also { platform.safPermissions += it.path }

    private fun saf(dir: File, name: String? = null) = LibraryLocation.Saf(dir.path, name)

    private fun addDocument(store: LibraryStore, id: String, updatedAt: Long = 10) {
        store.writeSource(id, "100", byteArrayOf(1))
        store.writeMetadata(DocumentMetadataV3(id = id, name = "Doc $id", createdAt = 1,
            updatedAt = updatedAt, pages = listOf(PageV2("100"))))
    }

    @Test
    fun `nothing is set up at first`() = runTest {
        val l = locations()
        assertThat(l.status.value).isNull() // not read yet

        l.load()

        assertThat(l.status.value).isEqualTo(LibraryStatus.NotSetUp)
        assertThat(l.currentStore()).isNull()
    }

    @Test
    fun `choosing a folder through the picker saves its uri and name`() = runTest {
        val dir = picked("Scans")
        val l = locations(); l.load()

        val change = l.change(saf(dir), move = false)

        assertThat(change.notMoved).isEqualTo(0)
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(saf(dir, "Scans")))
        assertThat(l.currentStore()).isNotNull()
        assertThat(settings.stored).isEqualTo(StoredLibraryLocation(dir.path, "Scans"))
    }

    @Test
    fun `the saved location is used at the next start`() = runTest {
        val dir = picked("Scans")
        settings.stored = StoredLibraryLocation(dir.path, "Scans")

        val l = locations(); l.load()

        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(saf(dir, "Scans")))
        assertThat(l.currentStore()).isNotNull()
    }

    @Test
    fun `inspecting a folder reads it and writes nothing`() = runTest {
        val existing = picked("Scans")
        addDocument(FileLibraryStore(existing), "d1")
        File(existing, "photo.jpg").writeText("x")
        val missing = File(folder.root, "Missing")
        val before = existing.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()
        val l = locations(); l.load()

        assertThat(l.inspect(saf(existing))).isEqualTo(FolderInspection(fairScanDocuments = 1, otherItems = 1))
        assertThat(l.inspect(saf(missing))).isEqualTo(FolderInspection(0, 0))

        assertThat(missing).doesNotExist() // not created by looking at it
        assertThat(existing.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()).isEqualTo(before)
    }

    @Test
    fun `a folder that disappears makes the library unavailable and it comes back with it`() = runTest {
        val dir = picked("Scans")
        settings.stored = StoredLibraryLocation(dir.path, "Scans")
        val l = locations(); l.load()
        val location = saf(dir, "Scans")
        assertThat(l.currentStore()).isNotNull()

        dir.deleteRecursively()
        l.refresh()
        assertThat(l.currentStore()).isNull()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Unavailable(location))

        dir("Scans")
        l.refresh()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(location))
        assertThat(l.currentStore()).isNotNull()
    }

    @Test
    fun `losing the permission of a picked folder makes it unavailable`() = runTest {
        val dir = picked("Scans")
        settings.stored = StoredLibraryLocation(dir.path, "Scans")
        val l = locations(); l.load()
        assertThat(l.currentStore()).isNotNull()

        platform.safPermissions -= dir.path

        assertThat(l.currentStore()).isNull()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Unavailable(saf(dir, "Scans")))
    }

    @Test
    fun `a picked folder that cannot be reached is unavailable at load`() = runTest {
        val dir = picked("Scans")
        platform.unreachable += dir.path
        settings.stored = StoredLibraryLocation(dir.path, "Scans")

        val l = locations(); l.load()

        assertThat(l.status.value).isInstanceOf(LibraryStatus.Unavailable::class.java)
    }

    @Test
    fun `changing the folder can move the documents`() = runTest {
        val oldDir = picked("Old"); val newDir = picked("New")
        addDocument(FileLibraryStore(oldDir), "d1"); addDocument(FileLibraryStore(oldDir), "d2")
        File(oldDir, "photo.jpg").writeText("mine")
        settings.stored = StoredLibraryLocation(oldDir.path, "Old")
        val l = locations(); l.load()
        val progress = mutableListOf<MoveProgress>()

        val change = l.change(saf(newDir), move = true) { progress += it }

        assertThat(change.notMoved).isEqualTo(0)
        assertThat(FileLibraryStore(newDir).list().map { it.info.id }).containsExactlyInAnyOrder("d1", "d2")
        assertThat(FileLibraryStore(oldDir).list()).isEmpty()
        assertThat(File(oldDir, "photo.jpg").readText()).isEqualTo("mine")
        assertThat(progress.last()).isEqualTo(MoveProgress(2, 2))
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(saf(newDir, "New")))
        assertThat(settings.stored!!.uri).isEqualTo(newDir.path)
    }

    @Test
    fun `changing the folder without moving leaves the documents where they are`() = runTest {
        val oldDir = picked("Old"); val newDir = picked("New")
        addDocument(FileLibraryStore(oldDir), "d1")
        settings.stored = StoredLibraryLocation(oldDir.path, "Old")
        val l = locations(); l.load()

        l.change(saf(newDir), move = false)

        assertThat(FileLibraryStore(oldDir).list().map { it.info.id }).containsExactly("d1")
        assertThat(FileLibraryStore(newDir).list()).isEmpty()
        assertThat(l.currentStore()!!.list()).isEmpty()
    }

    @Test
    fun `documents already in the new folder are in the library`() = runTest {
        val newDir = picked("New")
        addDocument(FileLibraryStore(newDir), "old-install")
        val l = locations(); l.load()

        l.change(saf(newDir), move = false)

        assertThat(l.currentStore()!!.list().map { it.info.id }).containsExactly("old-install")
    }

    @Test
    fun `the previous picked folder is released when the library goes elsewhere`() = runTest {
        val first = picked("Picked"); val other = picked("Other")
        settings.stored = StoredLibraryLocation(first.path, "Picked")
        val l = locations(); l.load()

        l.change(saf(other), move = false)

        assertThat(platform.released).containsExactly(first.path)
    }

    @Test
    fun `a rejected candidate is released but the folder in use is not`() = runTest {
        val inUse = picked("Picked"); val candidate = picked("Candidate")
        settings.stored = StoredLibraryLocation(inUse.path, "Picked")
        val l = locations(); l.load()

        l.discard(saf(candidate))
        l.discard(saf(inUse, "Picked"))

        assertThat(platform.released).containsExactly(candidate.path)
    }

    @Test
    fun `documents left in app storage by development builds are moved into the library`() = runTest {
        val legacy = dir("legacy-library")
        addDocument(FileLibraryStore(legacy), "dev1")
        val target = picked("Scans")
        val l = locations(listOf(legacy)); l.load()

        l.change(saf(target), move = false)

        assertThat(l.currentStore()!!.list().map { it.info.id }).containsExactly("dev1")
        assertThat(legacy).doesNotExist()
    }
}
