package com.organiser.app

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLEncoder
import java.util.Calendar
import java.util.UUID

val Bg = Color(0xFF111010)
val CardC = Color(0xFF1D1C1C)
val Tile = Color(0xFF282727)
val Pill = Color(0xFF2B2A2A)
val Muted = Color(0xFF9A9A9A)
val Accent = Color(0xFFFF9A4D)

// ---------------------------------------------------------------- helpers

fun openUrl(ctx: Context, raw: String) {
    val u = if (raw.startsWith("http")) raw else "https://$raw"
    try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
}

fun shareFile(ctx: Context, f: File, mime: String) {
    val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
fun Ico(@DrawableRes id: Int, tint: Color = Color.White, size: Dp = 22.dp, modifier: Modifier = Modifier) {
    Icon(painterResource(id), null, modifier.size(size), tint = tint)
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

@Composable
fun Avatar(p: Profile, size: Dp, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val av = p.avatar
    val bmp by produceState<ImageBitmap?>(null, av) {
        value = if (av == null) null else withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(File(ctx.filesDir, av).path)?.asImageBitmap()
        }
    }
    Box(modifier.size(size).clip(CircleShape).background(Accent), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Text(
            p.name.take(1).uppercase().ifBlank { "O" }, color = Color.Black,
            fontWeight = FontWeight.Bold, fontSize = (size.value * 0.42f).sp
        )
    }
}

// ---------------------------------------------------------------- chrome

@Composable
fun TopBar(p: Profile, onProfile: () -> Unit, onBell: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(p, 42.dp, Modifier.clickable(onClick = onProfile))
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pill).padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Organiser", color = Color(0xFFCCCCCC), fontStyle = FontStyle.Italic,
                fontFamily = FontFamily.Serif, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(20.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFFDDDDDD)), contentAlignment = Alignment.Center) {
                Ico(R.drawable.ic_bookmark, Color(0xFF222222), 12.dp)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(42.dp).clip(CircleShape).background(Pill).clickable(onClick = onBell), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_bell, Color(0xFFBBBBBB), 18.dp)
        }
    }
}

@Composable
fun FloatingNav(tab: Int, onTab: (Int) -> Unit, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Pill).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            NavBtn(R.drawable.ic_home, tab == 0) { onTab(0) }
            NavBtn(R.drawable.ic_globe, tab == 1) { onTab(1) }
            NavBtn(if (tab == 2) R.drawable.ic_bookmark else R.drawable.ic_bookmark_outline, tab == 2) { onTab(2) }
        }
        Box(Modifier.size(58.dp).clip(CircleShape).background(Pill).clickable(onClick = onSearch), contentAlignment = Alignment.Center) {
            Ico(R.drawable.ic_search, Muted, 22.dp)
        }
    }
}

@Composable
private fun NavBtn(@DrawableRes icon: Int, selected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Ico(icon, if (selected) Color.White else Muted, 22.dp)
    }
}

// ---------------------------------------------------------------- boards tab (matches mockup)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BoardsTab(
    boards: List<Board>, sort: Int, onSort: (Int) -> Unit,
    onOpen: (Board) -> Unit, onOptions: (Board) -> Unit, onNew: () -> Unit
) {
    val sorted = when (sort) {
        1 -> boards.sortedByDescending { it.created }
        2 -> boards.sortedBy { it.name.lowercase() }
        3 -> boards.sortedByDescending { it.items.size }
        else -> boards
    }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Your boards", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Box {
                Row(Modifier.clickable { menu = true }.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Filter", color = Color.White, fontSize = 15.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(30.dp).clip(CircleShape).background(Pill), contentAlignment = Alignment.Center) {
                        Ico(R.drawable.ic_filter, Color.White, 16.dp)
                    }
                }
                DropdownMenu(menu, { menu = false }) {
                    listOf("Oldest first", "Newest first", "A – Z", "Most items").forEachIndexed { i, s ->
                        DropdownMenuItem(text = { Text(if (i == sort) "✓  $s" else s) }, onClick = { onSort(i); menu = false })
                    }
                }
            }
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            gridItems(sorted, key = { it.id }) { b ->
                Column(Modifier.combinedClickable(onClick = { onOpen(b) }, onLongClick = { onOptions(b) })) {
                    Box(Modifier.fillMaxWidth().aspectRatio(0.88f).clip(RoundedCornerShape(30.dp)).background(CardC)) {
                        Column(Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            for (row in 0..1) Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (c in 0..1) {
                                    val item = b.items.getOrNull(row * 2 + c)
                                    Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(18.dp)).background(Tile),
                                        contentAlignment = Alignment.Center) {
                                        if (item != null) CutoutImage(item.file, Modifier.fillMaxSize().padding(5.dp))
                                    }
                                }
                            }
                        }
                    }
                    Text(b.name, color = Color(0xFFDDDDDD), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                }
            }
            item {
                Column(Modifier.clickable(onClick = onNew)) {
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(0.88f).clip(RoundedCornerShape(30.dp)).background(CardC).drawBehind {
                            val inset = 18.dp.toPx()
                            drawRoundRect(
                                Color(0xFF8A8A8A), topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = Size(size.width - inset * 2, size.height - inset * 2),
                                cornerRadius = CornerRadius(20.dp.toPx()),
                                style = Stroke(1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Add, null, tint = Color(0xFFBBBBBB), modifier = Modifier.size(34.dp)) }
                    Text("New", color = Color(0xFFDDDDDD), fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- home tab

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
        Text(label, color = Muted, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
fun HomeTab(boards: List<Board>, p: Profile, onItem: (String, Item) -> Unit) {
    val all = boards.flatMap { b -> b.items.map { b.id to it } }
    val todo = all.filter { !it.second.purchased }
    val toBuy = todo.sumOf { it.second.price ?: 0.0 }
    val bought = all.filter { it.second.purchased }.sumOf { it.second.price ?: 0.0 }
    val hr = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when { hr < 12 -> "Good morning"; hr < 18 -> "Good afternoon"; else -> "Good evening" }
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(Modifier.padding(top = 14.dp)) {
                Text(greet + if (p.name.isBlank()) "" else ", ${p.name}", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Your wishlist at a glance", color = Muted, fontSize = 14.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Items", all.size.toString(), Modifier.weight(1f))
                StatCard("Boards", boards.size.toString(), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Still to buy", money(p.currency, toBuy), Modifier.weight(1f))
                StatCard("Bought", money(p.currency, bought), Modifier.weight(1f))
            }
        }
        item { Text("Up next", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp)) }
        if (todo.isEmpty()) {
            item { Text("Nothing here yet. Add items from the Boards tab.", color = Muted) }
        } else {
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(todo.sortedByDescending { it.second.created }.take(12)) { (bid, item) ->
                        Box(Modifier.width(130.dp)) { ItemCard(item, p.currency) { onItem(bid, item) } }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- discover tab

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoverTab(boards: List<Board>, onAddBoard: (String) -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val shops = listOf(
        "Google Shopping" to "https://www.google.com/search?tbm=shop&q=",
        "Amazon" to "https://www.amazon.com/s?k=",
        "Pinterest" to "https://www.pinterest.com/search/pins/?q=",
        "eBay" to "https://www.ebay.com/sch/i.html?_nkw="
    )
    val ideas = listOf("Fashion", "Gadgets", "Skincare", "Home", "Gifts", "Shoes", "Books", "Travel")
        .filter { n -> boards.none { it.name.equals(n, true) } }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Discover", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 14.dp))
        OutlinedTextField(q, { q = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search a product to find inspiration") })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            shops.forEach { (n, u) ->
                AssistChip(onClick = { if (q.isNotBlank()) openUrl(ctx, u + URLEncoder.encode(q.trim(), "UTF-8")) }, label = { Text(n) })
            }
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
            Text("Add from anywhere", color = Color.White, fontWeight = FontWeight.SemiBold)
            Text("Open any photo in your gallery or browser, tap Share, and pick Organiser. The background is removed and you choose the board.",
                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (ideas.isNotEmpty()) {
            Text("Board ideas", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ideas.forEach { AssistChip(onClick = { onAddBoard(it) }, label = { Text("+ $it") }) }
            }
        }
    }
}

// ---------------------------------------------------------------- items

@Composable
fun ItemCard(item: Item, cur: String, onClick: () -> Unit) {
    Column(Modifier.clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)).background(CardC).padding(14.dp),
            contentAlignment = Alignment.Center) {
            CutoutImage(item.file, Modifier.fillMaxSize().alpha(if (item.purchased) 0.4f else 1f))
            if (item.purchased) Icon(Icons.Default.Check, null, tint = Accent, modifier = Modifier.align(Alignment.TopEnd).size(20.dp))
        }
        if (item.name.isNotBlank()) Text(item.name, color = Color(0xFFDDDDDD), fontSize = 13.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 4.dp))
        if (item.price != null) Text(money(cur, item.price), color = Accent, fontSize = 12.sp, modifier = Modifier.padding(start = 4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoardScreen(
    board: Board, cur: String, initialUri: Uri?, onInitialConsumed: () -> Unit,
    onBack: () -> Unit, onRename: () -> Unit, onAdd: (Item) -> Unit, onItem: (Item) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var filter by remember { mutableStateOf(0) }
    var sort by remember { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }

    fun process(uri: Uri) {
        busy = true
        scope.launch {
            pending = withContext(Dispatchers.Default) {
                val bmp = loadBitmap(ctx, uri)
                try { cutOut(bmp) } catch (e: Exception) { bmp }
            }
            busy = false
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) process(uri) }
    LaunchedEffect(initialUri) { if (initialUri != null) { onInitialConsumed(); process(initialUri) } }

    val list = board.items
        .filter { when (filter) { 1 -> !it.purchased; 2 -> it.purchased; else -> true } }
        .let {
            when (sort) {
                1 -> it.sortedBy { i -> i.price ?: Double.MAX_VALUE }
                2 -> it.sortedByDescending { i -> i.price ?: -1.0 }
                3 -> it.sortedBy { i -> i.name.lowercase() }
                else -> it.sortedByDescending { i -> i.created }
            }
        }
    val toBuy = board.items.filter { !it.purchased }.sumOf { it.price ?: 0.0 }
    val bought = board.items.filter { it.purchased }.sumOf { it.price ?: 0.0 }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
                Text(board.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onRename) { Icon(Icons.Default.Edit, "Rename", tint = Muted) }
            }
            Text("To buy ${money(cur, toBuy)}  ·  Bought ${money(cur, bought)}", color = Muted, fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "To buy", "Bought").forEachIndexed { i, s ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(s) })
                }
                Spacer(Modifier.weight(1f))
                Box {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Pill).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Ico(R.drawable.ic_filter, Color.White, 16.dp)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        listOf("Newest", "Price: low to high", "Price: high to low", "A – Z").forEachIndexed { i, s ->
                            DropdownMenuItem(text = { Text(if (i == sort) "✓  $s" else s) }, onClick = { sort = i; menu = false })
                        }
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                if (list.isEmpty()) Text(
                    if (board.items.isEmpty()) "Tap + to add a photo.\nThe background is removed automatically." else "No items in this filter.",
                    color = Muted, textAlign = TextAlign.Center, modifier = Modifier.align(Alignment.Center).padding(32.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 100.dp)
                ) { gridItems(list, key = { it.id }) { ItemCard(it, cur) { onItem(it) } } }
            }
        }
        Box(
            Modifier.align(Alignment.BottomEnd).padding(24.dp).size(60.dp).clip(CircleShape).background(Accent)
                .clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.Add, "Add item", tint = Color.Black, modifier = Modifier.size(28.dp)) }

        if (busy) Box(Modifier.fillMaxSize().background(Color(0xAA000000)).clickable(enabled = false) {}, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = Accent)
                Spacer(Modifier.height(12.dp))
                Text("Cutting out item…", color = Color.White)
            }
        }
    }

    pending?.let { bmp ->
        var name by remember { mutableStateOf("") }
        var price by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Add item") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Image(bmp.asImageBitmap(), null, Modifier.height(170.dp).fillMaxWidth(), contentScale = ContentScale.Fit)
                    OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name (optional)") })
                    OutlinedTextField(price, { price = it }, singleLine = true, label = { Text("Price (optional)") },
                        prefix = { Text(cur) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val file = saveItemBitmap(ctx, bmp)
                    onAdd(Item(UUID.randomUUID().toString(), name.trim(), file, price.replace(',', '.').toDoubleOrNull()))
                    pending = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemSheet(item: Item, cur: String, onDismiss: () -> Unit, onSave: (Item) -> Unit, onDelete: () -> Unit, onMove: () -> Unit) {
    val ctx = LocalContext.current
    var name by remember(item.id) { mutableStateOf(item.name) }
    var price by remember(item.id) {
        mutableStateOf(item.price?.let { if (it % 1.0 == 0.0) it.toLong().toString() else it.toString() } ?: "")
    }
    var url by remember(item.id) { mutableStateOf(item.url) }
    var note by remember(item.id) { mutableStateOf(item.note) }
    var bought by remember(item.id) { mutableStateOf(item.purchased) }
    var confirm by remember(item.id) { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = CardC) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                CutoutImage(item.file, Modifier.fillMaxSize())
            }
            OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(price, { price = it }, singleLine = true, label = { Text("Price") }, prefix = { Text(cur) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(url, { url = it }, singleLine = true, label = { Text("Link to product") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note (size, colour, where to buy…)") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Purchased", color = Color.White, modifier = Modifier.weight(1f))
                Switch(bought, { bought = it })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (url.isNotBlank()) OutlinedButton(onClick = { openUrl(ctx, url.trim()) }, modifier = Modifier.weight(1f)) { Text("Open link") }
                OutlinedButton(onClick = { shareFile(ctx, File(ctx.filesDir, item.file), "image/png") }, modifier = Modifier.weight(1f)) { Text("Share") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onMove, modifier = Modifier.weight(1f)) { Text("Move") }
                OutlinedButton(onClick = { if (confirm) onDelete() else confirm = true }, modifier = Modifier.weight(1f)) {
                    Text(if (confirm) "Tap again" else "Delete", color = Color(0xFFFF6B6B))
                }
            }
            Button(
                onClick = {
                    onSave(item.copy(name = name.trim(), price = price.replace(',', '.').toDoubleOrNull(),
                        url = url.trim(), note = note.trim(), purchased = bought))
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }
        }
    }
}

// ---------------------------------------------------------------- search

@Composable
fun SearchScreen(boards: List<Board>, cur: String, onBack: () -> Unit, onItem: (String, Item) -> Unit) {
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val res = boards.flatMap { b -> b.items.map { b to it } }.filter {
        q.isNotBlank() && (it.second.name.contains(q, true) || it.second.note.contains(q, true) || it.first.name.contains(q, true))
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            OutlinedTextField(q, { q = it }, singleLine = true, placeholder = { Text("Search your wishlist") },
                modifier = Modifier.weight(1f).focusRequester(focus))
        }
        if (q.isNotBlank() && res.isEmpty()) Text("No results", color = Muted, modifier = Modifier.padding(24.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(16.dp)
        ) {
            gridItems(res, key = { it.second.id }) { (b, i) ->
                Column {
                    ItemCard(i, cur) { onItem(b.id, i) }
                    Text(b.name, color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------- profile & reminder

@Composable
fun ProfileScreen(
    p: Profile, boards: List<Board>, onChange: (Profile) -> Unit, onAvatar: (Uri) -> Unit,
    onBack: () -> Unit, onExport: () -> Unit, onImport: () -> Unit
) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) onAvatar(uri) }
    val all = boards.flatMap { it.items }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) }
            Text("Profile", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }
        Avatar(p, 96.dp, Modifier.align(Alignment.CenterHorizontally).clickable {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        })
        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            modifier = Modifier.align(Alignment.CenterHorizontally)) { Text("Change photo") }
        OutlinedTextField(p.name, { onChange(p.copy(name = it)) }, singleLine = true, label = { Text("Your name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(p.currency, { onChange(p.copy(currency = it.take(4))) }, singleLine = true,
            label = { Text("Currency symbol") }, modifier = Modifier.fillMaxWidth())
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CardC).padding(16.dp)) {
            Text("${boards.size} boards · ${all.size} items · ${all.count { it.purchased }} bought", color = Color.White)
            Text("Still to buy: ${money(p.currency, all.filter { !it.purchased }.sumOf { it.price ?: 0.0 })}", color = Muted, fontSize = 13.sp)
        }
        Text("Backup", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export") }
            OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("Import") }
        }
        Text("Everything is stored on this phone only. Export a backup zip to keep it safe or move to a new phone.", color = Muted, fontSize = 12.sp)
    }
}

@Composable
fun ReminderDialog(p: Profile, onChange: (Profile) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Morning reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Daily reminder", modifier = Modifier.weight(1f))
                    Switch(p.remind, { onChange(p.copy(remind = it)) })
                }
                OutlinedButton(
                    enabled = p.remind,
                    onClick = {
                        TimePickerDialog(ctx, { _, h, m -> onChange(p.copy(hour = h, minute = m)) },
                            p.hour, p.minute, DateFormat.is24HourFormat(ctx)).show()
                    }
                ) { Text("Time: %02d:%02d".format(p.hour, p.minute)) }
                OutlinedButton(onClick = { Reminder.notifyNow(ctx) }) { Text("Send test notification") }
                Text("Every morning you get a nudge with a random item from your wishlist.", color = Muted, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}
