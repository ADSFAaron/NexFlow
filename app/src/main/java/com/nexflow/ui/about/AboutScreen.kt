/*
 * Copyright 2026 NexFlow Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.nexflow.ui.about

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import coil3.compose.AsyncImagePainter
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import com.nexflow.R
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ListItemShapes
import com.nexflow.ui.common.SettingsDefaults
import com.nexflow.ui.common.SettingsGroup
import com.nexflow.ui.common.SettingsItem
import com.nexflow.ui.common.SettingsTopAppBar

/** Contributor avatars are keyed by GitHub user ID so they survive username changes. */
private const val DEVELOPER_AVATAR_URL = "https://avatars.githubusercontent.com/u/48749140"

// blob/master, not main: the default branch is master, and a main link 404s.
private const val TERMS_URL = "https://github.com/ADSFAaron/NexFlow/blob/master/TERMS.md"
private const val PRIVACY_URL = "https://github.com/ADSFAaron/NexFlow/blob/master/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    val appIcon = remember {
        context.packageManager.getApplicationIcon(context.packageName)
            .toBitmap(width = 256, height = 256)
            .asImageBitmap()
    }

    val version = remember {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        info.versionName ?: "1.0"
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = SettingsDefaults.pageColor,
        topBar = { SettingsTopAppBar(stringResource(R.string.about_title), scrollBehavior, onBack = onBack) },
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding(),
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {

            // App icon + name + version
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                ) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "NexFlow",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = version,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Action buttons — icon + label pairs, Material Expressive style
            item {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                        .padding(bottom = 16.dp),
                ) {
                    AboutActionButton(
                        icon = Icons.Outlined.Code,
                        label = "GitHub",
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/ADSFAaron/NexFlow"),
                                ),
                            )
                        },
                    )
                    AboutActionButton(
                        icon = Icons.Outlined.BugReport,
                        label = stringResource(R.string.about_report_issue),
                        onClick = {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("https://github.com/ADSFAaron/NexFlow/issues"),
                                ),
                            )
                        },
                    )
                }
            }

            item {
                SettingsGroup(stringResource(R.string.about_developer)) {
                    item { shapes ->
                        SettingsItem(
                            shapes = shapes,
                            title = "ADSFAaron",
                            summary = stringResource(R.string.about_dev_role),
                            leading = {
                                ContributorAvatar(
                                    avatarUrl = DEVELOPER_AVATAR_URL,
                                    initials = "A",
                                    size = 40.dp,
                                )
                            },
                        )
                    }
                }
            }

            item {
                SettingsGroup(stringResource(R.string.about_legal)) {
                    item { shapes ->
                        LegalLinkItem(shapes, stringResource(R.string.about_terms), TERMS_URL)
                    }
                    item { shapes ->
                        LegalLinkItem(shapes, stringResource(R.string.about_privacy), PRIVACY_URL)
                    }
                }
            }
        }
    }
}

/** A document on GitHub; the trailing mark says it opens outside the app. */
@Composable
private fun LegalLinkItem(shapes: ListItemShapes, title: String, url: String) {
    val context = LocalContext.current
    SettingsItem(
        shapes = shapes,
        title = title,
        trailing = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null) },
        onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) },
    )
}

@Composable
private fun AboutActionButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(72.dp),
    ) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Circular contributor avatar; falls back to [initials] while loading or on failure. */
@Composable
private fun ContributorAvatar(avatarUrl: String, initials: String, size: Dp) {
    SubcomposeAsyncImage(
        model = avatarUrl,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.size(size).clip(CircleShape),
    ) {
        val state by painter.state.collectAsState()
        if (state is AsyncImagePainter.State.Success) {
            SubcomposeAsyncImageContent()
        } else {
            InitialsAvatar(initials, size)
        }
    }
}

@Composable
private fun InitialsAvatar(initials: String, size: Dp) {
    Surface(
        modifier = Modifier.size(size),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}
