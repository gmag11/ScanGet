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

import org.fairscan.app.data.library.LibraryStatus

sealed class Screen {
    sealed class Main : Screen() {
        object Camera : Main()
        object EditImage : Main()
        data class Document(val initialPage: Int = 0) : Main()
        object Export : Main()
        object ResumeScan : Main()
        /** Asks where the library is kept, until there is a folder to keep it in. */
        object LibrarySetup : Main()
    }
    sealed class Overlay : Screen() {
        object About : Overlay()
        object Libraries : Overlay()
        object Settings : Overlay()
        object OcrLanguages : Overlay()
    }
}

data class Navigation(
    val toCameraScreen: () -> Unit,
    val toEditImageScreen: () -> Unit,
    val toDocumentScreen: () -> Unit,
    val toExportScreen: () -> Unit,
    val toAboutScreen: () -> Unit,
    val toLibrariesScreen: () -> Unit,
    val toSettingsScreen: (() -> Unit)?,
    val toOcrLanguagesScreen: () -> Unit,
    val back: () -> Unit,
    val shouldDisplayBackButton: () -> Boolean,
)

@ConsistentCopyVisibility
data class NavigationState private constructor(val stack: List<Screen>, val root: Screen.Main) {

    companion object {
        fun initial(): NavigationState {
            val root = Screen.Main.Camera
            return NavigationState(listOf(root), root)
        }
    }

    val current: Screen get() = stack.last()

    fun navigateTo(destination: Screen): NavigationState {
        return if (destination is Screen.Overlay) {
            copy(stack = stack + destination)
        } else {
            copy(stack = listOf(destination))
        }
    }

    fun navigateBack(): NavigationState {
        return when (current) {
            root -> this // Back handled by system
            is Screen.Main.ResumeScan -> this // Back handled by system
            is Screen.Main.LibrarySetup -> this // Back handled by system
            is Screen.Main.Camera -> this // Back handled by system
            is Screen.Main.Document -> copy(stack = listOf(Screen.Main.Camera))
            is Screen.Main.EditImage -> copy(stack = listOf(Screen.Main.Document()))
            is Screen.Main.Export -> copy(stack = listOf(Screen.Main.Camera))
            is Screen.Overlay -> copy(stack = stack.dropLast(1))
        }
    }
}

/**
 * The first screen of the app. [library] is the state of the library location, or null when there is
 * no library (a scan requested by another app): then the library is not involved at all.
 * Without a usable library folder the setup screen comes first; the working document, if any, is
 * kept for when the folder is chosen.
 */
fun initialNavigationState(hasPages: Boolean, library: LibraryStatus?): NavigationState =
    when {
        library is LibraryStatus.NotSetUp || library is LibraryStatus.Unavailable ->
            NavigationState.initial().navigateTo(Screen.Main.LibrarySetup)
        hasPages -> NavigationState.initial().navigateTo(Screen.Main.ResumeScan)
        else -> NavigationState.initial()
    }
