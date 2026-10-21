/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package app.kaze.browser.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kaze.browser.data.Space
import app.kaze.browser.data.Tab
import app.kaze.browser.ui.components.ActionBar
import app.kaze.browser.ui.components.EssentialsGrid
import app.kaze.browser.ui.components.LocalApp
import app.kaze.browser.ui.components.SpaceBackground
import app.kaze.browser.ui.components.SpaceSwitcher
import app.kaze.browser.ui.components.TabRow
import app.kaze.browser.ui.components.tap
import app.kaze.browser.ui.theme.KazeType
import app.kaze.browser.ui.theme.LocalKaze
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Pantalla principal (la de Z-Sync): barra de acciones, Essentials, el espacio actual
 * con sus pestañas fijadas y normales, y el selector de espacios abajo.
 * Deslizar a los lados cambia de espacio.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpacesScreen(
    onOpenTab: (Tab) -> Unit,
    onNewTab: (Space) -> Unit,
    onSearch: () -> Unit,
    onHistory: () -> Unit,
    onSettings: () -> Unit,
    onTabMenu: (Tab) -> Unit,
    onEditSpace: (Space) -> Unit,
    onAddSpace: () -> Unit,
) {
    val app = LocalApp.current
    val store = app.store
    val spaces = store.visibleSpaces
    val startIndex = spaces.indexOfFirst { it.id == store.currentSpaceId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = startIndex) { spaces.size }

    // Pager → espacio actual
    LaunchedEffect(pager, spaces) {
        snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { page ->
            spaces.getOrNull(page)?.let { store.selectSpace(it.id) }
        }
    }
    // Espacio actual (cambiado desde otro lado) → pager
    LaunchedEffect(store.currentSpaceId, spaces.size) {
        val i = spaces.indexOfFirst { it.id == store.currentSpaceId }
        if (i >= 0 && i != pager.currentPage) pager.animateScrollToPage(i)
    }

    val bgColors = spaces.getOrNull(pager.currentPage)?.colors.orEmpty()

    Box(Modifier.fillMaxSize()) {
        SpaceBackground(bgColors)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            ActionBar(onHistory = onHistory, onSearch = onSearch, onSettings = onSettings)
            EssentialsGrid(
                tabs = store.essentials(),
                selectedId = store.selectedTabId,
                onOpen = onOpenTab,
                onMenu = onTabMenu,
            )
            Spacer(Modifier.height(10.dp))
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                beyondViewportPageCount = 0,
                key = { spaces.getOrNull(it)?.id ?: it },
            ) { page ->
                spaces.getOrNull(page)?.let { space ->
                    SpacePage(
                        space = space,
                        selectedId = store.selectedTabId,
                        onOpenTab = onOpenTab,
                        onNewTab = { onNewTab(space) },
                        onTabMenu = onTabMenu,
                        onEdit = { onEditSpace(space) },
                    )
                }
            }
            SpaceSwitcher(
                spaces = spaces,
                currentIndex = pager.currentPage,
                onSelect = { i -> store.selectSpace(spaces[i].id) },
                onAdd = onAddSpace,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun SpacePage(
    space: Space,
    selectedId: String?,
    onOpenTab: (Tab) -> Unit,
    onNewTab: () -> Unit,
    onTabMenu: (Tab) -> Unit,
    onEdit: () -> Unit,
) {
    val app = LocalApp.current
    val k = LocalKaze.current
    val pinned = app.store.pinned(space.id)
    val normal = app.store.normal(space.id)
    val list = rememberLazyListState()
    val showTopLine by remember { derivedStateOf { list.canScrollBackward } }

    Column(Modifier.fillMaxSize()) {
        // Cabecera: emoji + nombre; tocar para editar el espacio.
        Row(
            // Mismo margen que las filas de pestañas (12 + 8 dp) para que emoji y favicons queden alineados.
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 6.dp, bottom = 6.dp)
                .tap(onEdit),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                Text(space.emoji, fontSize = 20.sp)
            }
            Spacer(Modifier.width(12.dp))
            Text(
                space.name, style = KazeType.spaceTitle, color = k.ink, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.MoreHoriz, "Editar espacio", tint = k.ink35, modifier = Modifier.size(22.dp))
        }

        Box(Modifier.weight(1f)) {
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 16.dp),
            ) {
                items(pinned, key = { it.id }) { t ->
                    TabRow(
                        tab = t, selected = t.id == selectedId,
                        onOpen = { onOpenTab(t) }, onMenu = { onTabMenu(t) }, onClose = null,
                    )
                }
                if (pinned.isNotEmpty()) {
                    item(key = "divider") {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            HorizontalDivider(Modifier.weight(1f), thickness = 1.dp, color = k.ink.copy(alpha = 0.25f))
                            if (normal.isNotEmpty()) {
                                Text(
                                    "Limpiar", style = KazeType.small, color = k.ink55,
                                    modifier = Modifier.padding(start = 10.dp).tap {
                                        app.store.clearNormal(space.id).forEach(app.pool::close)
                                    },
                                )
                            }
                        }
                    }
                }
                item(key = "new") {
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).tap(onNewTab).padding(start = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Outlined.Add, null, tint = k.ink55, modifier = Modifier.size(22.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Text("Nueva pestaña", style = KazeType.tabTitle, color = k.ink55)
                    }
                }
                items(normal, key = { it.id }) { t ->
                    TabRow(
                        tab = t, selected = t.id == selectedId,
                        onOpen = { onOpenTab(t) }, onMenu = { onTabMenu(t) },
                        onClose = {
                            app.pool.close(t)
                            app.store.close(t)
                        },
                    )
                }
                if (pinned.isEmpty() && normal.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            "Este espacio está vacío.\nMantén presionada una pestaña para fijarla aquí o en Essentials.",
                            style = KazeType.small, color = k.ink55,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            androidx.compose.animation.AnimatedVisibility(showTopLine, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                HorizontalDivider(color = k.ink.copy(alpha = 0.18f))
            }
        }
    }
}
