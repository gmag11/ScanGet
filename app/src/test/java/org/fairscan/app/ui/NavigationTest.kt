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
package org.fairscan.app.ui

import org.assertj.core.api.Assertions.assertThat
import org.fairscan.app.data.library.LibraryLocation
import org.fairscan.app.data.library.LibraryStatus
import org.fairscan.app.ui.Screen.Main.Camera
import org.fairscan.app.ui.Screen.Main.Document
import org.fairscan.app.ui.Screen.Main.Export
import org.fairscan.app.ui.Screen.Main.LibrarySetup
import org.fairscan.app.ui.Screen.Main.ResumeScan
import org.fairscan.app.ui.Screen.Overlay.About
import org.fairscan.app.ui.Screen.Overlay.Libraries
import org.junit.Test

class NavigationTest {

    @Test
    fun empty_ScreenStack() {
        val empty = NavigationState.initial()
        assertThat(empty.current).isEqualTo(Camera)
        assertThat(empty.navigateBack()).isEqualTo(empty)
    }

    @Test
    fun navigate_between_fixed_screens() {
        val atCamera = NavigationState.initial()
        val atDocument = atCamera.navigateTo(Document())
        val atExport = atCamera.navigateTo(Export)

        assertThat(atCamera.current).isEqualTo(Camera)
        assertThat(atDocument.current).isEqualTo(Document())
        assertThat(atExport.current).isEqualTo(Export)

        assertThat(atCamera.navigateTo(Document())).isEqualTo(atDocument)
        assertThat(atDocument.navigateTo(Export)).isEqualTo(atExport)
        assertThat(atDocument.navigateTo(Camera)).isEqualTo(atCamera)

        assertThat(atCamera.navigateBack()).isEqualTo(atCamera)
        assertThat(atDocument.navigateBack()).isEqualTo(atCamera)
        assertThat(atExport.navigateBack()).isEqualTo(atCamera)
    }

    @Test
    fun navigate_to_secondary_screens() {
        val atHome = NavigationState.initial()
        val atCamera = atHome.navigateTo(Camera)

        val atAboutAfterHome = atHome.navigateTo(About)
        assertThat(atAboutAfterHome.current).isEqualTo(About)
        assertThat(atAboutAfterHome.navigateBack()).isEqualTo(atHome)

        val atAboutAfterCamera = atCamera.navigateTo(About)
        assertThat(atAboutAfterCamera.current).isEqualTo(About)
        assertThat(atAboutAfterCamera.navigateBack()).isEqualTo(atCamera)

        val atLibrariesAfterCameraAbout = atAboutAfterCamera.navigateTo(Libraries)
        assertThat(atLibrariesAfterCameraAbout.current).isEqualTo(Libraries)
        assertThat(atLibrariesAfterCameraAbout.navigateBack()).isEqualTo(atAboutAfterCamera)
    }

    @Test
    fun external_call() {
        val initial = NavigationState.initial()
        assertThat(initial.current).isEqualTo(Camera)
        assertThat(initial.navigateBack().current).isEqualTo(Camera)
    }

    private val folder = LibraryLocation.Direct("/storage/emulated/0/FairScan")

    @Test
    fun the_setup_screen_comes_first_when_no_folder_is_set_up() {
        assertThat(initialNavigationState(hasPages = false, LibraryStatus.NotSetUp).current)
            .isEqualTo(LibrarySetup)
        // A document in progress (from a previous version) waits for the folder
        assertThat(initialNavigationState(hasPages = true, LibraryStatus.NotSetUp).current)
            .isEqualTo(LibrarySetup)
    }

    @Test
    fun the_setup_screen_comes_first_when_the_folder_is_not_accessible_any_more() {
        assertThat(initialNavigationState(hasPages = false, LibraryStatus.Unavailable(folder)).current)
            .isEqualTo(LibrarySetup)
        assertThat(initialNavigationState(hasPages = true, LibraryStatus.Unavailable(folder)).current)
            .isEqualTo(LibrarySetup)
    }

    @Test
    fun a_usable_folder_gives_the_usual_first_screen() {
        assertThat(initialNavigationState(hasPages = false, LibraryStatus.Ready(folder)).current)
            .isEqualTo(Camera)
        assertThat(initialNavigationState(hasPages = true, LibraryStatus.Ready(folder)).current)
            .isEqualTo(ResumeScan)
    }

    @Test
    fun without_a_library_the_setup_never_shows() {
        // A scan requested by another app
        assertThat(initialNavigationState(hasPages = false, library = null).current).isEqualTo(Camera)
        assertThat(initialNavigationState(hasPages = true, library = null).current).isEqualTo(ResumeScan)
    }

    @Test
    fun back_does_not_leave_the_setup_screen() {
        val setup = initialNavigationState(hasPages = false, LibraryStatus.NotSetUp)

        assertThat(setup.navigateBack()).isEqualTo(setup)
        assertThat(setup.navigateTo(About).navigateBack()).isEqualTo(setup)
        assertThat(setup.navigateTo(Camera).current).isEqualTo(Camera) // once set up, it moves on
    }
}
