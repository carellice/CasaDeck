package dev.casadeck.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.casadeck.CasaViewModel
import dev.casadeck.data.FileEntry

private fun parentOf(p: String) = p.substringBeforeLast('/').ifEmpty { "/" }

@Composable
fun FilesScreen(vm: CasaViewModel) {
    var path by rememberSaveable { mutableStateOf("/DATA") }
    var forward by remember { mutableStateOf(true) }
    var reload by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf<FileEntry?>(null) }
    val upload by vm.upload.collectAsState()
    val cs = MaterialTheme.colorScheme
    BackHandler(path != "/") { forward = false; path = parentOf(path) }
    // document pickers (not GetContent): their grants can be persisted, so the background upload can still read the files
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) vm.uploadFiles(path, uris)
    }
    val pickDir = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) vm.uploadFolder(path, tree)
    }
    var uploadMenu by remember { mutableStateOf(false) }
    // progress lives in a notification while the app is closed, so ask once before the first upload
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun openMenu() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) askNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        uploadMenu = true
    }
    // refresh the listing when a queued upload finishes
    var wasUploading by remember { mutableStateOf(false) }
    LaunchedEffect(upload != null) {
        if (upload == null && wasUploading) reload++
        wasUploading = upload != null
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("File", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = 20.dp, top = 24.dp, end = 20.dp))
        val crumbs = listOf("/") + path.split('/').filter { it.isNotEmpty() }
        val crumbState = rememberLazyListState()
        LaunchedEffect(crumbs.size) { crumbState.animateScrollToItem(crumbs.lastIndex) }
        LazyRow(
            state = crumbState,
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(crumbs, key = { i, c -> "$i/$c" }) { i, c ->
                val last = i == crumbs.lastIndex
                Row(Modifier.animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    if (i > 0) Icon(Icons.Rounded.ChevronRight, null, Modifier.size(16.dp), tint = cs.onSurfaceVariant)
                    Text(
                        if (i == 0) "Root" else c,
                        Modifier.clip(CircleShape)
                            .background(if (last) cs.primary.copy(alpha = 0.16f) else Color.Transparent)
                            .clickable(!last) {
                                forward = false
                                path = if (i == 0) "/" else "/" + crumbs.subList(1, i + 1).joinToString("/")
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        color = if (last) cs.primary else cs.onSurfaceVariant,
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
        AnimatedContent(
            path,
            transitionSpec = {
                val d = if (forward) 1 else -1
                (slideInHorizontally { it / 3 * d } + fadeIn()) togetherWith (slideOutHorizontally { -it / 3 * d } + fadeOut())
            },
            label = "folder",
        ) { p ->
            FolderList(vm, p, reload, { menu = it }) { f ->
                if (f.isDir) { forward = true; path = f.path } else menu = f
            }
        }
    }
        // upload progress banner
        AnimatedVisibility(
            upload != null, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 96.dp),
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
        ) {
            val (name, p) = upload ?: ("" to 0f)
            val anim by androidx.compose.animation.core.animateFloatAsState(p, label = "up")
            Surface(shape = MaterialTheme.shapes.large, color = cs.surfaceContainerHigh, shadowElevation = 8.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CloudUpload, null, tint = cs.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                        Text("${(anim * 100).toInt()}%", color = cs.primary, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator({ anim }, Modifier.fillMaxWidth().height(6.dp).clip(CircleShape))
                }
            }
        }
        AnimatedVisibility(
            upload == null, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 24.dp, bottom = 100.dp),
            enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
        ) {
            Box {
                ExtendedFloatingActionButton(
                    onClick = ::openMenu, icon = { Icon(Icons.Rounded.Upload, null) }, text = { Text("Carica") },
                    shape = MaterialTheme.shapes.large,
                )
                DropdownMenu(uploadMenu, { uploadMenu = false }, shape = MaterialTheme.shapes.medium) {
                    DropdownMenuItem(
                        { Text("File") }, { uploadMenu = false; pick.launch(arrayOf("*/*")) },
                        leadingIcon = { Icon(Icons.Rounded.InsertDriveFile, null) },
                    )
                    DropdownMenuItem(
                        { Text("Cartella intera") }, { uploadMenu = false; pickDir.launch(null) },
                        leadingIcon = { Icon(Icons.Rounded.DriveFolderUpload, null) },
                    )
                }
            }
        }
    }

    menu?.let { f -> FileSheet(f, { menu = null }) { vm.download(f); menu = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FileSheet(f: FileEntry, onDismiss: () -> Unit, onDownload: () -> Unit) {
    val (icon, color) = iconFor(f)
    ModalBottomSheet(onDismiss) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(color, size = 52.dp) { Icon(icon, null, tint = color) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(f.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(if (f.isDir) f.path else formatBytes(f.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(onDownload, Modifier.fillMaxWidth().height(54.dp), shape = MaterialTheme.shapes.medium) {
                Icon(if (f.isDir) Icons.Rounded.FolderZip else Icons.Rounded.Download, null)
                Spacer(Modifier.width(8.dp))
                Text(if (f.isDir) "Scarica come ZIP" else "Scarica")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderList(vm: CasaViewModel, path: String, reload: Int, onLong: (FileEntry) -> Unit, onClick: (FileEntry) -> Unit) {
    var entries by remember(path) { mutableStateOf<List<FileEntry>?>(null) }
    LaunchedEffect(path, reload) { entries = vm.files(path) ?: emptyList() }
    val list = entries
    val cs = MaterialTheme.colorScheme
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 130.dp),
    ) {
        when {
            list == null -> items(8) { Shimmer(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 8.dp, vertical = 4.dp)) }
            list.isEmpty() -> item {
                Column(Modifier.fillMaxWidth().padding(top = 80.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.FolderOff, null, Modifier.size(56.dp), tint = cs.outline)
                    Spacer(Modifier.height(8.dp))
                    Text("Cartella vuota", color = cs.onSurfaceVariant)
                }
            }
            else -> itemsIndexed(list, key = { _, f -> f.path }) { i, f ->
                val (icon, color) = iconFor(f)
                Row(
                    Modifier.staggerIn(i).fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .combinedClickable(onLongClick = { onLong(f) }) { onClick(f) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconBadge(color) { Icon(icon, null, tint = color) }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(if (f.isDir) "Cartella" else formatBytes(f.size), f.date.take(10).ifEmpty { null }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                        )
                    }
                    Icon(if (f.isDir) Icons.Rounded.ChevronRight else Icons.Rounded.MoreVert, null, tint = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun iconFor(f: FileEntry): Pair<ImageVector, Color> {
    if (f.isDir) return Icons.Rounded.Folder to MaterialTheme.colorScheme.primary
    return when (f.name.substringAfterLast('.', "").lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "svg" -> Icons.Rounded.Image to Color(0xFFFF5FA2)
        "mp4", "mkv", "avi", "mov", "webm" -> Icons.Rounded.Movie to Color(0xFFB36BFF)
        "mp3", "flac", "wav", "ogg", "m4a" -> Icons.Rounded.MusicNote to Teal
        "zip", "rar", "7z", "tar", "gz" -> Icons.Rounded.Inventory2 to Amber
        "pdf", "doc", "docx", "txt", "md", "odt" -> Icons.Rounded.Description to Color(0xFF2EA8FF)
        else -> Icons.Rounded.InsertDriveFile to MaterialTheme.colorScheme.outline
    }
}
