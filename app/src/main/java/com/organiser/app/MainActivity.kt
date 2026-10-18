package com.organiser.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handle(intent)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = CardC)
            ) { OrganiserApp(incoming) { incoming = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    @Suppress("DEPRECATION")
    private fun handle(i: Intent?) {
        if (i?.action == Intent.ACTION_SEND && i.type?.startsWith("image/") == true) {
            incoming = i.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
        }
    }
}

@Composable
fun OrganiserApp(incoming: Uri?, onIncomingHandled: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { Store(ctx) }
    val prefs = remember { Prefs(ctx) }
    var boards by remember { mutableStateOf(store.load()) }
    var profile by remember { mutableStateOf(prefs.read()) }

    var tab by remember { mutableStateOf(2) }
    var sort by remember { mutableStateOf(0) }
    var openId by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var searching by remember { mutableStateOf(false) }
    var profileOpen by remember { mutableStateOf(false) }
    var sheetKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var showNew by remember { mutableStateOf(false) }
    var showReminder by remember { mutableStateOf(false) }
    var optBoard by remember { mutableStateOf<Board?>(null) }
    var renameBoard by remember { mutableStateOf<Board?>(null) }
    var deleteBoard by remember { mutableStateOf<Board?>(null) }
    var moveFrom by remember { mutableStateOf<Pair<String, Item>?>(null) }

    fun persist(n: List<Board>) { boards = n; store.save(n) }
    fun setProfile(n: Profile) { profile = n; prefs.write(n) }
    fun addBoard(name: String): Board {
        val b = Board(UUID.randomUUID().toString(), name, emptyList())
        persist(boards + b); return b
    }
    fun mapBoard(id: String, f: (Board) -> Board) = persist(boards.map { if (it.id == id) f(it) else it })

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        Reminder.ensureChannel(ctx)
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        Reminder.schedule(ctx)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { Backup.import(ctx, uri); boards = store.load() } catch (_: Exception) {}
        }
    }

    val open = boards.find { it.id == openId }
    BackHandler(profileOpen || searching || openId != null) {
        when { profileOpen -> profileOpen = false; searching -> searching = false; else -> openId = null }
    }

    Surface(color = Bg, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            when {
                profileOpen -> ProfileScreen(
                    profile, boards,
                    onChange = { setProfile(it) },
                    onAvatar = { uri ->
                        scope.launch {
                            val name = withContext(Dispatchers.Default) {
                                val bmp = squareThumb(loadBitmap(ctx, uri))
                                val n = "avatar_${System.currentTimeMillis()}.png"
                                File(ctx.filesDir, n).outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                n
                            }
                            profile.avatar?.let { File(ctx.filesDir, it).delete() }
                            setProfile(profile.copy(avatar = name))
                        }
                    },
                    onBack = { profileOpen = false },
                    onExport = {
                        scope.launch {
                            val f = withContext(Dispatchers.IO) { Backup.export(ctx) }
                            shareFile(ctx, f, "application/zip")
                        }
                    },
                    onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) }
                )
                searching -> SearchScreen(boards, profile.currency, { searching = false }) { bid, item -> sheetKey = bid to item.id }
                open != null -> BoardScreen(
                    open, profile.currency, pendingUri, { pendingUri = null },
                    onBack = { openId = null },
                    onRename = { renameBoard = open },
                    onAdd = { item -> mapBoard(open.id) { it.copy(items = it.items + item) } },
                    onItem = { item -> sheetKey = open.id to item.id }
                )
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        TopBar(profile, { profileOpen = true }, { showReminder = true })
                        Box(Modifier.weight(1f)) {
                            when (tab) {
                                0 -> HomeTab(boards, profile) { bid, item -> sheetKey = bid to item.id }
                                1 -> DiscoverTab(boards) { addBoard(it) }
                                else -> BoardsTab(boards, sort, { sort = it }, { openId = it.id }, { optBoard = it }, { showNew = true })
                            }
                        }
                    }
                    FloatingNav(tab, { tab = it }, { searching = true }, Modifier.align(Alignment.BottomCenter))
                }
            }

            // ---- item sheet
            val key = sheetKey
            val sheetItem = key?.let { k -> boards.find { it.id == k.first }?.items?.find { it.id == k.second } }
            if (key != null && sheetItem != null) {
                ItemSheet(
                    sheetItem, profile.currency,
                    onDismiss = { sheetKey = null },
                    onSave = { n -> mapBoard(key.first) { b -> b.copy(items = b.items.map { if (it.id == n.id) n else it }) } },
                    onDelete = {
                        File(ctx.filesDir, sheetItem.file).delete()
                        mapBoard(key.first) { b -> b.copy(items = b.items.filter { it.id != sheetItem.id }) }
                        sheetKey = null
                    },
                    onMove = { moveFrom = key.first to sheetItem }
                )
            }
        }
    }

    // ---- dialogs
    if (showNew) NameDialog("New board", "Name (e.g. Fashion)", "", "Create", { showNew = false }) { addBoard(it) }

    renameBoard?.let { b ->
        NameDialog("Rename board", "Name", b.name, "Save", { renameBoard = null }) { n -> mapBoard(b.id) { it.copy(name = n) } }
    }
    optBoard?.let { b ->
        AlertDialog(
            onDismissRequest = { optBoard = null },
            title = { Text(b.name) },
            text = {
                Column {
                    TextButton(onClick = { renameBoard = b; optBoard = null }) { Text("Rename") }
                    TextButton(onClick = { deleteBoard = b; optBoard = null }) { Text("Delete", color = Color(0xFFFF6B6B)) }
                }
            },
            confirmButton = { TextButton(onClick = { optBoard = null }) { Text("Close") } }
        )
    }
    deleteBoard?.let { b ->
        AlertDialog(
            onDismissRequest = { deleteBoard = null },
            title = { Text("Delete \"${b.name}\"?") },
            text = { Text("The board and all ${b.items.size} item(s) will be removed.") },
            confirmButton = {
                TextButton(onClick = {
                    b.items.forEach { File(ctx.filesDir, it.file).delete() }
                    persist(boards.filter { it.id != b.id }); deleteBoard = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteBoard = null }) { Text("Cancel") } }
        )
    }
    moveFrom?.let { (fromId, item) ->
        AlertDialog(
            onDismissRequest = { moveFrom = null },
            title = { Text("Move to…") },
            text = {
                Column {
                    boards.filter { it.id != fromId }.forEach { b ->
                        TextButton(onClick = {
                            persist(boards.map {
                                when (it.id) {
                                    fromId -> it.copy(items = it.items.filter { i -> i.id != item.id })
                                    b.id -> it.copy(items = it.items + item)
                                    else -> it
                                }
                            })
                            moveFrom = null; sheetKey = null
                        }) { Text(b.name) }
                    }
                    if (boards.size < 2) Text("Create another board first.")
                }
            },
            confirmButton = { TextButton(onClick = { moveFrom = null }) { Text("Cancel") } }
        )
    }
    if (showReminder) ReminderDialog(
        profile,
        onChange = { setProfile(it); Reminder.schedule(ctx) },
        onDismiss = { showReminder = false }
    )

    // ---- photo shared from another app
    if (incoming != null) {
        var newName by remember { mutableStateOf("") }
        fun pick(b: Board) { pendingUri = incoming; openId = b.id; profileOpen = false; searching = false; onIncomingHandled() }
        AlertDialog(
            onDismissRequest = onIncomingHandled,
            title = { Text("Add to which board?") },
            text = {
                Column {
                    boards.forEach { b -> TextButton(onClick = { pick(b) }) { Text(b.name) } }
                    OutlinedTextField(newName, { newName = it }, singleLine = true, label = { Text("or new board") })
                }
            },
            confirmButton = {
                TextButton(onClick = { if (newName.isNotBlank()) pick(addBoard(newName.trim())) }) { Text("Create & add") }
            },
            dismissButton = { TextButton(onClick = onIncomingHandled) { Text("Cancel") } }
        )
    }
}

@Composable
fun NameDialog(title: String, label: String, initial: String, ok: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(label) }) },
        confirmButton = { TextButton(onClick = { if (name.isNotBlank()) onOk(name.trim()); onDismiss() }) { Text(ok) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
