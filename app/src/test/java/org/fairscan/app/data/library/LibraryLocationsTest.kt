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

    // A platform made of directories: folders reached "as paths" and "granted" ones are both
    // directories of the temp folder; what the app is allowed to do is set by the test
    private inner class FakePlatform : LibraryPlatform {
        var allFilesAccess = true
        val safPermissions = mutableSetOf<String>()
        val unreachable = mutableSetOf<String>()
        val released = mutableListOf<String>()
        override fun hasAllFilesAccess() = allFilesAccess
        override fun isFolderUsable(path: String) = File(path).isDirectory
        override fun defaultFolderPath() = File(folder.root, "FairScan").path
        override fun existingDirectStore(path: String) =
            File(path).takeIf { it.isDirectory }?.let { FileLibraryStore(it) }
        override fun directStore(path: String): LibraryStore { File(path).mkdirs(); return FileLibraryStore(File(path)) }
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
    fun `the default location is FairScan at the root of the storage`() {
        assertThat(locations().defaultLocation()).isEqualTo(LibraryLocation.Direct(File(folder.root, "FairScan").path))
        assertThat(locations().defaultLocation().displayName).isEqualTo("FairScan")
    }

    @Test
    fun `choosing a location with all files access creates the folder and saves it`() = runTest {
        val l = locations(); l.load()
        val target = l.defaultLocation()
        assertThat(File(target.path)).doesNotExist()

        val change = l.change(target, move = false)

        assertThat(change.notMoved).isEqualTo(0)
        assertThat(File(target.path)).isDirectory()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(target))
        assertThat(l.currentStore()).isNotNull()
        assertThat(settings.stored).isEqualTo(StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, target.path))
    }

    @Test
    fun `choosing a folder through the picker saves its uri and name`() = runTest {
        val picked = dir("Scans")
        platform.safPermissions += picked.path
        val l = locations(); l.load()

        l.change(LibraryLocation.Saf(picked.path, null), move = false)

        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(LibraryLocation.Saf(picked.path, "Scans")))
        assertThat(settings.stored).isEqualTo(
            StoredLibraryLocation(StoredLibraryLocation.Kind.SAF, picked.path, "Scans"))
    }

    @Test
    fun `the saved location is used at the next start`() = runTest {
        val target = dir("FairScan")
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, target.path)

        val l = locations(); l.load()

        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(LibraryLocation.Direct(target.path)))
        assertThat(l.currentStore()).isNotNull()
    }

    @Test
    fun `inspecting a folder reads it and writes nothing`() = runTest {
        val existing = dir("Scans")
        addDocument(FileLibraryStore(existing), "d1")
        File(existing, "photo.jpg").writeText("x")
        val missing = File(folder.root, "Missing").path
        platform.safPermissions += existing.path
        val before = existing.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()
        val l = locations(); l.load()

        assertThat(l.inspect(LibraryLocation.Saf(existing.path, null)))
            .isEqualTo(FolderInspection(fairScanDocuments = 1, otherItems = 1))
        assertThat(l.inspect(LibraryLocation.Direct(existing.path)))
            .isEqualTo(FolderInspection(fairScanDocuments = 1, otherItems = 1))
        assertThat(l.inspect(LibraryLocation.Direct(missing))).isEqualTo(FolderInspection(0, 0))

        assertThat(File(missing)).doesNotExist() // not created by looking at it
        assertThat(existing.walkTopDown().map { it.relativeTo(folder.root).path }.sorted().toList()).isEqualTo(before)
    }

    @Test
    fun `a folder that disappears makes the library unavailable and it comes back with it`() = runTest {
        val target = dir("FairScan")
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, target.path)
        val l = locations(); l.load()
        val location = LibraryLocation.Direct(target.path)
        assertThat(l.currentStore()).isNotNull()

        target.deleteRecursively()
        assertThat(l.currentStore()).isNull()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Unavailable(location))

        dir("FairScan")
        l.refresh()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(location))
        assertThat(l.currentStore()).isNotNull()
    }

    @Test
    fun `revoking all files access makes a direct location unavailable`() = runTest {
        val target = dir("FairScan")
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, target.path)
        val l = locations(); l.load()

        platform.allFilesAccess = false
        assertThat(l.currentStore()).isNull()
        l.refresh()
        assertThat(l.status.value).isInstanceOf(LibraryStatus.Unavailable::class.java)

        platform.allFilesAccess = true
        l.refresh()
        assertThat(l.status.value).isInstanceOf(LibraryStatus.Ready::class.java)
    }

    @Test
    fun `losing the permission of a picked folder makes it unavailable`() = runTest {
        val picked = dir("Scans")
        platform.safPermissions += picked.path
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.SAF, picked.path, "Scans")
        val l = locations(); l.load()
        assertThat(l.currentStore()).isNotNull()

        platform.safPermissions -= picked.path

        assertThat(l.currentStore()).isNull()
        assertThat(l.status.value).isEqualTo(LibraryStatus.Unavailable(LibraryLocation.Saf(picked.path, "Scans")))
    }

    @Test
    fun `a picked folder that cannot be reached is unavailable at load`() = runTest {
        val picked = dir("Scans")
        platform.safPermissions += picked.path
        platform.unreachable += picked.path
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.SAF, picked.path, "Scans")

        val l = locations(); l.load()

        assertThat(l.status.value).isInstanceOf(LibraryStatus.Unavailable::class.java)
    }

    @Test
    fun `changing the folder can move the documents`() = runTest {
        val oldDir = dir("Old"); val newDir = dir("New")
        addDocument(FileLibraryStore(oldDir), "d1"); addDocument(FileLibraryStore(oldDir), "d2")
        File(oldDir, "photo.jpg").writeText("mine")
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, oldDir.path)
        val l = locations(); l.load()
        val progress = mutableListOf<MoveProgress>()

        val change = l.change(LibraryLocation.Direct(newDir.path), move = true) { progress += it }

        assertThat(change.notMoved).isEqualTo(0)
        assertThat(FileLibraryStore(newDir).list().map { it.info.id }).containsExactlyInAnyOrder("d1", "d2")
        assertThat(FileLibraryStore(oldDir).list()).isEmpty()
        assertThat(File(oldDir, "photo.jpg").readText()).isEqualTo("mine")
        assertThat(progress.last()).isEqualTo(MoveProgress(2, 2))
        assertThat(l.status.value).isEqualTo(LibraryStatus.Ready(LibraryLocation.Direct(newDir.path)))
        assertThat(settings.stored!!.value).isEqualTo(newDir.path)
    }

    @Test
    fun `changing the folder without moving leaves the documents where they are`() = runTest {
        val oldDir = dir("Old"); val newDir = dir("New")
        addDocument(FileLibraryStore(oldDir), "d1")
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.DIRECT, oldDir.path)
        val l = locations(); l.load()

        l.change(LibraryLocation.Direct(newDir.path), move = false)

        assertThat(FileLibraryStore(oldDir).list().map { it.info.id }).containsExactly("d1")
        assertThat(FileLibraryStore(newDir).list()).isEmpty()
        assertThat(l.currentStore()!!.list()).isEmpty()
    }

    @Test
    fun `documents already in the new folder are in the library`() = runTest {
        val newDir = dir("New")
        addDocument(FileLibraryStore(newDir), "old-install")
        val l = locations(); l.load()

        l.change(LibraryLocation.Direct(newDir.path), move = false)

        assertThat(l.currentStore()!!.list().map { it.info.id }).containsExactly("old-install")
    }

    @Test
    fun `the previous picked folder is released when the library goes elsewhere`() = runTest {
        val picked = dir("Picked"); val other = dir("Other")
        platform.safPermissions += picked.path
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.SAF, picked.path, "Picked")
        val l = locations(); l.load()

        l.change(LibraryLocation.Direct(other.path), move = false)

        assertThat(platform.released).containsExactly(picked.path)
    }

    @Test
    fun `a rejected candidate is released but the folder in use is not`() = runTest {
        val picked = dir("Picked"); val candidate = dir("Candidate")
        platform.safPermissions += picked.path; platform.safPermissions += candidate.path
        settings.stored = StoredLibraryLocation(StoredLibraryLocation.Kind.SAF, picked.path, "Picked")
        val l = locations(); l.load()

        l.discard(LibraryLocation.Saf(candidate.path, null))
        l.discard(LibraryLocation.Saf(picked.path, "Picked"))
        l.discard(LibraryLocation.Direct("/anywhere"))

        assertThat(platform.released).containsExactly(candidate.path)
    }

    @Test
    fun `documents left in app storage by development builds are moved into the library`() = runTest {
        val legacy = dir("legacy-library")
        addDocument(FileLibraryStore(legacy), "dev1")
        val l = locations(listOf(legacy)); l.load()

        l.change(l.defaultLocation(), move = false)

        assertThat(l.currentStore()!!.list().map { it.info.id }).containsExactly("dev1")
        assertThat(legacy).doesNotExist()
    }
}
