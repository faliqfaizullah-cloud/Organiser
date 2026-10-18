package com.organiser.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class Item(
    val id: String,
    val name: String,
    val file: String,
    val price: Double? = null,
    val url: String = "",
    val note: String = "",
    val purchased: Boolean = false,
    val created: Long = System.currentTimeMillis()
)

data class Board(
    val id: String,
    val name: String,
    val items: List<Item>,
    val created: Long = System.currentTimeMillis()
)

data class Profile(
    val name: String = "",
    val avatar: String? = null,
    val remind: Boolean = true,
    val hour: Int = 9,
    val minute: Int = 0,
    val currency: String = "$"
)

fun money(cur: String, v: Double): String =
    cur + String.format(Locale.getDefault(), if (v % 1.0 == 0.0) "%,.0f" else "%,.2f", v)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("organiser", Context.MODE_PRIVATE)
    fun read() = Profile(
        sp.getString("name", "") ?: "",
        sp.getString("avatar", null),
        sp.getBoolean("remind", true),
        sp.getInt("hour", 9),
        sp.getInt("minute", 0),
        sp.getString("currency", "$") ?: "$"
    )

    fun write(p: Profile) {
        sp.edit().putString("name", p.name).putString("avatar", p.avatar)
            .putBoolean("remind", p.remind).putInt("hour", p.hour).putInt("minute", p.minute)
            .putString("currency", p.currency).apply()
    }
}

/** JSON-file storage for boards + item metadata. Cut-out PNGs live in filesDir. */
class Store(ctx: Context) {
    private val f = File(ctx.filesDir, "data.json")

    fun load(): List<Board> {
        if (!f.exists()) return emptyList()
        return try {
            val a = JSONArray(f.readText())
            (0 until a.length()).map { i ->
                val b = a.getJSONObject(i)
                val ia = b.getJSONArray("items")
                Board(
                    b.getString("id"), b.getString("name"),
                    (0 until ia.length()).map { j ->
                        val o = ia.getJSONObject(j)
                        Item(
                            o.getString("id"), o.optString("name"), o.getString("file"),
                            if (o.has("price")) o.getDouble("price") else null,
                            o.optString("url"), o.optString("note"),
                            o.optBoolean("purchased"), o.optLong("created", 0L)
                        )
                    },
                    b.optLong("created", 0L)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(boards: List<Board>) {
        val a = JSONArray()
        boards.forEach { b ->
            val items = JSONArray()
            b.items.forEach {
                val o = JSONObject().put("id", it.id).put("name", it.name).put("file", it.file)
                    .put("url", it.url).put("note", it.note).put("purchased", it.purchased)
                    .put("created", it.created)
                if (it.price != null) o.put("price", it.price)
                items.put(o)
            }
            a.put(JSONObject().put("id", b.id).put("name", b.name).put("items", items).put("created", b.created))
        }
        f.writeText(a.toString())
    }
}

fun loadBitmap(ctx: Context, uri: Uri): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { dec, info, _ ->
        dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val s = maxOf(info.size.width, info.size.height) / 1024
        if (s > 1) dec.setTargetSampleSize(s)
    }

fun squareThumb(b: Bitmap, size: Int = 256): Bitmap {
    val s = minOf(b.width, b.height)
    val sq = Bitmap.createBitmap(b, (b.width - s) / 2, (b.height - s) / 2, s, s)
    return Bitmap.createScaledBitmap(sq, size, size, true)
}

/** Saves a cut-out as PNG (max 800px) in filesDir and returns its file name. */
fun saveItemBitmap(ctx: Context, bmp: Bitmap): String {
    val name = "${UUID.randomUUID()}.png"
    val scaled = if (maxOf(bmp.width, bmp.height) > 800) {
        val s = 800f / maxOf(bmp.width, bmp.height)
        Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
    } else bmp
    File(ctx.filesDir, name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return name
}

/** Removes the background so only the item remains (transparent PNG), cropped to the item. */
suspend fun cutOut(src: Bitmap): Bitmap = suspendCoroutine { cont ->
    val seg = SubjectSegmentation.getClient(
        SubjectSegmenterOptions.Builder().enableForegroundBitmap().build()
    )
    seg.process(InputImage.fromBitmap(src, 0))
        .addOnSuccessListener { r ->
            val fg = r.foregroundBitmap
            cont.resume(if (fg != null) trim(fg) else src)
        }
        .addOnFailureListener { cont.resume(src) }
}

private fun trim(b: Bitmap): Bitmap {
    val w = b.width; val h = b.height
    val px = IntArray(w * h)
    b.getPixels(px, 0, w, 0, 0, w, h)
    var l = w; var t = h; var r = -1; var bt = -1
    for (y in 0 until h) for (x in 0 until w) {
        if ((px[y * w + x] ushr 24) > 16) {
            if (x < l) l = x; if (x > r) r = x
            if (y < t) t = y; if (y > bt) bt = y
        }
    }
    if (r < 0) return b
    return Bitmap.createBitmap(b, l, t, r - l + 1, bt - t + 1)
}

object Backup {
    fun export(ctx: Context): File {
        val out = File(ctx.cacheDir, "Organiser-backup.zip")
        ZipOutputStream(out.outputStream()).use { z ->
            ctx.filesDir.listFiles()?.filter { it.name == "data.json" || it.name.endsWith(".png") }?.forEach {
                z.putNextEntry(ZipEntry(it.name)); it.inputStream().use { i -> i.copyTo(z) }; z.closeEntry()
            }
        }
        return out
    }

    fun import(ctx: Context, uri: Uri) {
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    val n = e.name
                    val ok = !n.contains('/') && !n.contains("..") && (n == "data.json" || n.endsWith(".png"))
                    if (ok) File(ctx.filesDir, n).outputStream().use { z.copyTo(it) }
                    e = z.nextEntry
                }
            }
        }
    }
}
