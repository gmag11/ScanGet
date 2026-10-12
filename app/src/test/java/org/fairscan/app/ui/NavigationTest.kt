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
import org.fairscan.app.ui.Screen.Main.EditImage
import org.fairscan.app.ui.Screen.Main.Library
import org.fairscan.app.ui.Screen.Main.LibrarySetup
import org.fairscan.app.ui.Screen.Main.Overview
import org.fairscan.app.ui.Screen.Overlay.Settings
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
    private val atLibrary = NavigationState.initial(Library)

    @Test
    fun the_setup_screen_comes_first_when_no_folder_is_set_up() {
        assertThat(initialNavigationState(LibraryStatus.NotSetUp).current).isEqualTo(LibrarySetup)
    }

    @Test
    fun the_setup_screen_comes_first_when_the_folder_is_not_accessible_any_more() {
        assertThat(initialNavigationState(LibraryStatus.Unavailable(folder)).current).isEqualTo(LibrarySetup)
    }

    @Test
    fun a_usable_folder_gives_the_library_as_first_screen() {
        assertThat(initialNavigationState(LibraryStatus.Ready(folder))).isEqualTo(atLibrary)
        assertThat(atLibrary.current).isEqualTo(Library)
    }

    @Test
    fun without_a_library_the_first_screen_is_the_camera() {
        // A scan requested by another app
        assertThat(initialNavigationState(library = null).current).isEqualTo(Camera)
    }

    @Test
    fun back_does_not_leave_the_setup_screen() {
        val setup = initialNavigationState(LibraryStatus.NotSetUp)

        assertThat(setup.navigateBack()).isEqualTo(setup)
        assertThat(setup.navigateTo(About).navigateBack()).isEqualTo(setup)
        assertThat(setup.navigateTo(Library).current).isEqualTo(Library) // once set up, it moves on
    }

    @Test
    fun back_from_the_library_is_left_to_the_system() {
        assertThat(atLibrary.navigateBack()).isEqualTo(atLibrary)
        assertThat(atLibrary.navigateBack(hasPages = true)).isEqualTo(atLibrary)
    }

    @Test
    fun back_from_the_overview_goes_to_the_library() {
        val atOverview = atLibrary.navigateTo(Overview)

        assertThat(atOverview.navigateBack().current).isEqualTo(Library)
        assertThat(atOverview.navigateBack(hasPages = true).current).isEqualTo(Library)
    }

    @Test
    fun back_from_the_editor_goes_to_the_overview() {
        val atEditor = atLibrary.navigateTo(Document(2))

        assertThat(atEditor.navigateBack().current).isEqualTo(Overview)
        assertThat(atEditor.navigateBack(hasPages = true).current).isEqualTo(Overview)
    }

    @Test
    fun back_from_the_camera_goes_to_the_overview_if_the_document_has_pages_else_to_the_library() {
        val atCamera = atLibrary.navigateTo(Camera)

        assertThat(atCamera.navigateBack(hasPages = true).current).isEqualTo(Overview)
        assertThat(atCamera.navigateBack(hasPages = false).current).isEqualTo(Library)
    }

    @Test
    fun back_from_the_export_goes_to_the_overview() {
        val atExport = atLibrary.navigateTo(Export)

        assertThat(atExport.navigateBack().current).isEqualTo(Overview)
    }

    @Test
    fun back_from_the_crop_goes_to_the_editor_then_the_overview_then_the_library() {
        val atCrop = atLibrary.navigateTo(EditImage)

        assertThat(atCrop.navigateBack().current).isEqualTo(Document())
        assertThat(atCrop.navigateBack().navigateBack().current).isEqualTo(Overview)
        assertThat(atCrop.navigateBack().navigateBack().navigateBack().current).isEqualTo(Library)
    }

    @Test
    fun overlays_return_to_the_screen_they_were_opened_from() {
        val atCamera = atLibrary.navigateTo(Camera)
        val atSettings = atCamera.navigateTo(Settings)

        assertThat(atSettings.navigateBack()).isEqualTo(atCamera)
        assertThat(atLibrary.navigateTo(About).navigateBack()).isEqualTo(atLibrary)
    }

    @Test
    fun a_scan_requested_by_another_app_keeps_every_screen_going_back_to_the_camera() {
        val external = NavigationState.initial()

        assertThat(external.navigateTo(Document()).navigateBack().current).isEqualTo(Camera)
        assertThat(external.navigateTo(Export).navigateBack().current).isEqualTo(Camera)
        assertThat(external.navigateBack(hasPages = true)).isEqualTo(external) // the app handles it
    }
}
