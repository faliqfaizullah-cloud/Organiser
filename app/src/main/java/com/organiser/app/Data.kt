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
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

data class Item(val id: String, val name: String, val file: String)
data class Board(val id: String, val name: String, val items: List<Item>)

/** Tiny JSON-file storage: boards + item metadata. Cut-out PNGs live in filesDir. */
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
                        Item(o.getString("id"), o.getString("name"), o.getString("file"))
                    }
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    fun save(boards: List<Board>) {
        val a = JSONArray()
        boards.forEach { b ->
            val items = JSONArray()
            b.items.forEach {
                items.put(JSONObject().put("id", it.id).put("name", it.name).put("file", it.file))
            }
            a.put(JSONObject().put("id", b.id).put("name", b.name).put("items", items))
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

/** Removes the background so only the item remains (transparent PNG), then crops to the item. */
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
