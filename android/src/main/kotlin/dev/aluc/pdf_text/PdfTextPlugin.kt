package dev.aluc.pdf_text

import android.os.Handler
import android.os.Looper
import androidx.annotation.NonNull
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import java.io.File
import kotlin.concurrent.thread

class PdfTextPlugin : FlutterPlugin, MethodCallHandler {

  private lateinit var channel: MethodChannel

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel = MethodChannel(binding.binaryMessenger, "pdf_text")
    channel.setMethodCallHandler(this)                   // <- usa a MESMA instância
    PDFBoxResourceLoader.init(binding.applicationContext) // lib nativa
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    channel.setMethodCallHandler(null)                   // desliga
  }

  // ====================== chamadas vindas do Dart =========================
  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    thread(start = true) {
      when (call.method) {
        "initDoc" -> {
          val args = call.arguments as Map<*, *>
          initDoc(result, args["path"] as String, args["password"] as String)
        }
        "getDocPageText" -> {
          val args = call.arguments as Map<*, *>
          getDocPageText(result,
            args["path"] as String,
            args["number"] as Int,
            args["password"] as String)
        }
        "getDocText" -> {
          val args = call.arguments as Map<*, *>
          @Suppress("UNCHECKED_CAST")
          getDocText(
            result,
            args["path"] as String,
            args["missingPagesNumbers"] as List<Int>,
            args["password"] as String)
        }
        else -> Handler(Looper.getMainLooper()).post { result.notImplemented() }
      }
    }
  }

  // ====================== IMPLEMENTAÇÃO =========================

  private fun initDoc(result: Result, path: String, password: String) {
    getDoc(result, path, password)?.use { doc ->
      val info = doc.documentInformation
      val data = hashMapOf(
        "length" to doc.numberOfPages,
        "info" to hashMapOf(
          "author" to info.author,
          "creationDate" to info.creationDate?.time.toString(),
          "modificationDate" to info.modificationDate?.time.toString(),
          "creator" to info.creator,
          "producer" to info.producer,
          "keywords" to splitKeywords(info.keywords),
          "title" to info.title,
          "subject" to info.subject
        )
      )
      doc.close()
      Handler(Looper.getMainLooper()).post { result.success(data) }
    }
  }

  private fun splitKeywords(keywordsString: String?): List<String>? =
    keywordsString?.split(',')?.map { it.trim() }

  private fun getDocPageText(result: Result, path: String, pageNumber: Int, password: String) {
    getDoc(result, path, password)?.use { doc ->
      val stripper = PDFTextStripper()
      stripper.startPage = pageNumber
      stripper.endPage   = pageNumber
      val text = stripper.getText(doc)
      doc.close()
      Handler(Looper.getMainLooper()).post { result.success(text) }
    }
  }

  private fun getDocText(result: Result, path: String, missingPagesNumbers: List<Int>, password: String) {
    getDoc(result, path, password)?.use { doc ->
      val texts = arrayListOf<String>()
      val stripper = PDFTextStripper()
      for (p in missingPagesNumbers) {
        stripper.startPage = p
        stripper.endPage   = p
        texts.add(stripper.getText(doc))
      }
      doc.close()
      Handler(Looper.getMainLooper()).post { result.success(texts) }
    }
  }

  private fun getDoc(result: Result, path: String, password: String = ""): PDDocument? = try {
    PDDocument.load(File(path), password)
  } catch (e: Exception) {
    Handler(Looper.getMainLooper()).post {
      result.error("INVALID_PATH",
        "File path or password (in case of encrypted document) is invalid",
        null)
    }
    null
  }
}
