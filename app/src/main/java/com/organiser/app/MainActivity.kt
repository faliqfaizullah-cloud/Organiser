package com.organiser.app

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

private val Bg = Color(0xFF121010)
private val Card = Color(0xFF1E1B1B)
private val Accent = Color(0xFFFF9A4D)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Accent, background = Bg, surface = Card)) {
                OrganiserApp()
            }
        }
    }
}

@Composable
fun OrganiserApp() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var boards by remember { mutableStateOf(store.load()) }
    var openId by remember { mutableStateOf<String?>(null) }
    fun update(n: List<Board>) { boards = n; store.save(n) }
    val open = boards.find { it.id == openId }
    BackHandler(open != null) { openId = null }

    Surface(color = Bg, modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        if (open == null) {
            HomeScreen(
                boards,
                onOpen = { openId = it.id },
                onCreate = { name -> update(boards + Board(UUID.randomUUID().toString(), name, emptyList())) },
                onDelete = { b ->
                    b.items.forEach { File(ctx.filesDir, it.file).delete() }
                    update(boards.filter { it.id != b.id })
                }
            )
        } else {
            BoardScreen(open, onBack = { openId = null }, onChange = { nb ->
                update(boards.map { if (it.id == nb.id) nb else it })
            })
        }
    }
}

@Composable
fun CutoutImage(file: String, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, file) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(ctx.filesDir, file).path)?.asImageBitmap()
        }
    }
    bmp?.let { Image(it, null, modifier, contentScale = ContentScale.Fit) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    boards: List<Board>,
    onOpen: (Board) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (Board) -> Unit
) {
    var showNew by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<Board?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Organiser", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
        Text("Your boards", color = Color(0xFFBBBBBB), fontSize = 14.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 12.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(boards, key = { it.id }) { b ->
                Column(Modifier.combinedClickable(onClick = { onOpen(b) }, onLongClick = { toDelete = b })) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.85f).background(Card, RoundedCornerShape(24.dp))) {
                        Column(Modifier.fillMaxSize().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in 0..1) Row(
                                Modifier.weight(1f).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (c in 0..1) {
                                    val item = b.items.getOrNull(row * 2 + c)
                                    Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                        if (item != null) CutoutImage(item.file, Modifier.fillMaxSize().padding(2.dp))
                                    }
                                }
                            }
                        }
                    }
                    Text(b.name, color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                }
            }
            item {
                Column(Modifier.combinedClickable(onClick = { showNew = true })) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.85f).drawBehind {
                            drawRoundRect(
                                Color(0xFF777777), size = Size(size.width, size.height),
                                cornerRadius = CornerRadius(24.dp.toPx()),
                                style = Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(16f, 12f)))
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, null, tint = Color(0xFF999999), modifier = Modifier.size(36.dp)) }
                    Text("New", color = Color(0xFFDDDDDD), fontSize = 13.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                }
            }
        }
    }

    if (showNew) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("New board") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (e.g. Fashion)") }) },
            confirmButton = {
                TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim()); showNew = false }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("Cancel") } }
        )
    }
    toDelete?.let { b ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete \"${b.name}\"?") },
            text = { Text("The board and all its items will be removed.") },
            confirmButton = { TextButton(onClick = { onDelete(b); toDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(board: Board, onBack: () -> Unit, onChange: (Board) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Bitmap?>(null) }
    var toDelete by remember { mutableStateOf<Item?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch {
                pending = withContext(Dispatchers.Default) {
                    val bmp = loadBitmap(ctx, uri)
                    try { cutOut(bmp) } catch (e: Exception) { bmp }
                }
                busy = false
            }
        }
    }

    Scaffold(
        containerColor = Bg,
        topBar = {
            TopAppBar(
                title = { Text(board.name, color = Color.White) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Bg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }, containerColor = Accent, contentColor = Color.Black) { Icon(Icons.Default.Add, "Add item") }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (board.items.isEmpty()) {
                Text("Tap + to add a photo.\nThe background is removed automatically.",
                    color = Color(0xFF999999), modifier = Modifier.align(Alignment.Center).padding(32.dp))
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp)
            ) {
                items(board.items, key = { it.id }) { item ->
                    Column(Modifier.combinedClickable(onClick = {}, onLongClick = { toDelete = item })) {
                        Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Card, RoundedCornerShape(24.dp)).padding(14.dp),
                            contentAlignment = Alignment.Center) {
                            CutoutImage(item.file, Modifier.fillMaxSize())
                        }
                        if (item.name.isNotBlank()) Text(item.name, color = Color(0xFFDDDDDD), fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp))
                    }
                }
            }
            if (busy) Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Cutting out item…", color = Color.White)
                }
            }
        }
    }

    pending?.let { bmp ->
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add item") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(bmp.asImageBitmap(), null, Modifier.height(180.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") })
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = "${UUID.randomUUID()}.png"
                    val scaled = if (maxOf(bmp.width, bmp.height) > 800) {
                        val s = 800f / maxOf(bmp.width, bmp.height)
                        Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
                    } else bmp
                    File(ctx.filesDir, file).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    onChange(board.copy(items = board.items + Item(UUID.randomUUID().toString(), name.trim(), file)))
                    pending = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete item?") },
            confirmButton = {
                TextButton(onClick = {
                    File(ctx.filesDir, item.file).delete()
                    onChange(board.copy(items = board.items.filter { it.id != item.id }))
                    toDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}
