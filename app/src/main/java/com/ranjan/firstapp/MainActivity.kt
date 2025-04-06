package com.ranjan.firstapp

import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.bumptech.glide.Glide
import com.ranjan.firstapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val backThread =CoroutineScope(Dispatchers.IO)
    var downloadedBytes=0L
    var totalBytes=0L
    var mergingProgress=0
    var suffix="Video"
    private val handler = Handler(Looper.getMainLooper())
    private val variants = listOf(
        RequestVariant(
            data = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "ANDROID")
                        put("clientVersion", "19.07.35")
                        put("androidSdkVersion", 30)
                        put("hl", "en")
                        put("timeZone", "UTC")
                    })
                })
                put("playbackContext", JSONObject().apply {
                    put("contentPlaybackContext", JSONObject().apply {
                        put("html5Preference", "HTML5_PREF_WANTS")
                        put("signatureTimestamp", 19369)
                    })
                })
            },
            headers =mapOf(
                "Content-Type" to "application/json",
                "X-YouTube-Client-Name" to "3",  // 3 = ANDROID
                "X-YouTube-Client-Version" to "19.07.35",
                "Origin" to "https://www.youtube.com",
                "User-Agent" to "com.google.android.youtube/19.07.35 (Linux; U; Android 11) gzip"
            ),
            query =mapOf("key" to "AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"),
        ),
        RequestVariant(
            data = JSONObject().apply {
                put("context", JSONObject(mapOf(
                    "client" to mapOf(
                        "clientName" to "IOS",
                        "clientVersion" to "20.10.4",
                        "deviceMake" to "Apple",
                        "deviceModel" to "iPhone16,2",
                        "userAgent" to "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                        "osName" to "iPhone",
                        "osVersion" to "18.3.2.22D82",
                        "hl" to "en",
                        "timeZone" to "UTC",
                        "utcOffsetMinutes" to 0
                    )
                )))
                put("playbackContext", JSONObject(mapOf(
                    "contentPlaybackContext" to mapOf(
                        "html5Preference" to "HTML5_PREF_WANTS",
                        "signatureTimestamp" to 20167
                    )
                )))
                put("contentCheckOk", true)
                put("racyCheckOk", true)
            },
            headers = mapOf(
                "X-YouTube-Client-Name" to "5",
                "X-YouTube-Client-Version" to "20.10.4",
                "userAgent" to "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                "content-type" to "application/json",
                "Origin" to "https://www.youtube.com",
                "X-Goog-Visitor-Id" to "CgtKZmx2SXMyQmh2RSjalYu_BjIKCgJJThIEGgAgQw%3D%3D"
            ),
            query =mapOf("key" to "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8")
        )
    )
    private val updateTask = object : Runnable {
        override fun run() {
            val partdownloaded="${convertBytes(downloadedBytes)}/${convertBytes(totalBytes)}"
            if (totalBytes!=0L){
                val percent=(downloadedBytes*100/totalBytes).toInt()
                var progress="$partdownloaded  $percent% $suffix"
                if (mergingProgress!=0){
                    progress="$partdownloaded  $percent% Merging $mergingProgress%"
                }
                if (mergingProgress==100){
                    progress="$partdownloaded  $percent% $suffix"
                }
                binding.apply {
                    partDownloaded.text=progress
                    progressBar.progress=percent
                }
                handler.postDelayed(this, 1000)
            }
        }

    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.apply {
            db.setOnClickListener {
                val url=urlinputed.text.toString()
                val videoId=extractVideoId(url)
                urlinputed.text.clear()
                backThread.launch {
                    val response=retriveStreamingData(videoId.toString())
                    if (response==null){
                        println("streaming not found")
                    }else{
                        val streamingData=response.getJSONObject("streamingData")
                        val adaptiveFormats=streamingData.getJSONArray("adaptiveFormats")
                        val map= HashMap<String,JSONObject>()
                        val dataList = mutableListOf<String>()
                        val title=txt2filename(response.getJSONObject("videoDetails").getString("title"))
                        withContext(Dispatchers.Main){
                            videoTitle.text=title
                            val movieFolder= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                            val musicFolder= Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                            val videoFile=File(filesDir,"video.mp4")
                            val audioFile= File(filesDir,"audio.mp4")
                            val finalVideoFile= File(movieFolder,"$title($videoId).mp4")
                            val finalAudioFile= File(musicFolder,"$title($videoId).mp3")
                            val thumbnailurl="https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                            Glide.with(this@MainActivity)
                                .load(thumbnailurl)
                                .into(thumbnail)
                            val dialogView = layoutInflater.inflate(R.layout.custom_dialog_layout, null)
                            val listView = dialogView.findViewById<ListView>(R.id.listView)
                            for (index in 0..<adaptiveFormats.length()) {
                                println(adaptiveFormats.getJSONObject(index))
                                val format_item=adaptiveFormats.getJSONObject(index)
                                if ("audio/mp4" in format_item.getString("mimeType").lowercase()){
                                    val format_detail="(${convertBytes(format_item.getString("contentLength").toLong())}) Audio bitrate(${convertBytes(format_item.getInt("bitrate").toLong())}/s)"
                                    dataList.add(format_detail)
                                    map.put(format_detail,format_item)
                                }
                                if ("video/mp4" in format_item.getString("mimeType")){
                                    val format_detail="(${format_item.getString("qualityLabel")}) ${convertBytes(format_item.getString("contentLength").toLong())}"
                                    dataList.add(format_detail)
                                    map.put(format_detail,format_item)
                                }
                            }
                            val adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_list_item_1, dataList)
                            listView.adapter=adapter
                            var alertDialog: AlertDialog? = null
                            listView.setOnItemClickListener { parent, view, position, id ->
                                alertDialog?.dismiss()
                                val selectItem=dataList[position]
                                val seletemJson=map.get(selectItem)
                                handler.post(updateTask)
                                if (seletemJson!!.getString("mimeType").contains("audio")){
                                   backThread.launch {
                                       println("downloading audio")
                                       downloadedBytes=0
                                       suffix="Audio"
                                       totalBytes=seletemJson.getString("contentLength").toLong()
                                       downloadas9mb(seletemJson.getString("url"), FileOutputStream(finalAudioFile))
                                       MediaScannerConnection.scanFile(this@MainActivity, arrayOf(finalAudioFile.absoluteFile.toString()),null
                                       ) { path, uri -> println("scanned") }
                                       this@MainActivity?.sendBroadcast(
                                           Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                               Uri.parse(finalAudioFile.absoluteFile.toString()))
                                       )
                                       downloadedBytes=0
                                       totalBytes=0
                                   }
                                }else{
                                    downloadedBytes=0
                                    totalBytes=seletemJson!!.getString("contentLength").toLong()
                                    for (index in 0..<adaptiveFormats.length()) {
                                        val item=adaptiveFormats.getJSONObject(index)
                                        if (item.getInt("itag")==140){
                                            suffix="Video"
                                            backThread.launch {
                                                println("downloading video")
                                                downloadas9mb(seletemJson!!.getString("url"), FileOutputStream(videoFile))
                                                downloadedBytes=0
                                                totalBytes=item.getString("contentLength").toLong()
                                                println("downloading audio")
                                                downloadas9mb(item.getString("url"), FileOutputStream(audioFile))
                                                downloadedBytes=0
                                                totalBytes=item.getString("contentLength").toLong()
                                                suffix="Audio"
                                                downloadas9mb(item.getString("url"), FileOutputStream(audioFile))
                                                muxUsingFfmpeggpt(
                                                    videoFile.absoluteFile.toString(),
                                                    audioFile.absoluteFile.toString(),
                                                    finalVideoFile.absoluteFile.toString()
                                                )
                                                suffix="Video"
                                                MediaScannerConnection.scanFile(this@MainActivity, arrayOf(finalVideoFile.absoluteFile.toString()),null
                                                ) { path, uri -> println("scanned") }
                                                this@MainActivity?.sendBroadcast(
                                                    Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                                                        Uri.parse(finalVideoFile.absoluteFile.toString()))
                                                )
                                            }
                                            break
                                        }
                                    }
                                }


                            }
                            val title=response.getJSONObject("videoDetails").getString("title")
                            val builder = AlertDialog.Builder(this@MainActivity)
                            builder.setView(dialogView as View)
                                .setTitle(title)
                                .setNegativeButton("Cancel") { dialog, _ ->
                                    dialog.dismiss()
                                    Toast.makeText(this@MainActivity,"Paste Url Again and Select Format",
                                        Toast.LENGTH_SHORT).show()
                                }
                            alertDialog = builder.create()
                            alertDialog.show()
                        }
                    }
                }

            }
        }



    }
    fun txt2filename(txt: String): String {
        val specialCharacters = listOf(
            "@", "#", "$", "*", "&", "<", ">", "/", "\\b", "|", "?", "CON", "PRN", "AUX", "NUL",
            "COM0", "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9", "LPT0",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9", ":", "\"", "'"
        )

        var normalString = txt
        for (sc in specialCharacters) {
            normalString = normalString.replace(sc, "")
        }

        return normalString
    }

    private fun convertBytes(sizeInBytes: Long): String {
        val kilobyte = 1024L
        val megabyte = kilobyte * 1024
        val gigabyte = megabyte * 1024

        return when {
            sizeInBytes >= gigabyte -> String.format("%.2f GB", sizeInBytes.toDouble() / gigabyte)
            sizeInBytes >= megabyte -> String.format("%.2f MB", sizeInBytes.toDouble() / megabyte)
            sizeInBytes >= kilobyte -> String.format("%.2f KB", sizeInBytes.toDouble() / kilobyte)
            else -> "$sizeInBytes Bytes"
        }
    }
    private fun getDuration(videoFilePath: String): String {
        val cmd = mutableListOf(
            videoFilePath
        )
        val commandString = cmd.joinToString(" ")
        val session = FFprobeKit.execute(commandString)
        val output: String = session.output
        val durationRegex = Regex("Duration: (\\d\\d):(\\d\\d):(\\d\\d\\.\\d\\d),")
        val durationMatchResult = durationRegex.find(output)

        return durationMatchResult?.value ?: ""

    }
     fun muxUsingFfmpeggpt(
        video: String,
        audio: String,
        final: String,
    ) {
        val cmd = mutableListOf(
            "-i", "\"$video\"",
            "-i", "\"$audio\"",
            "-c:v", "copy",
            "-y",
            "\"$final\""
        )
        val vfile=File(video)
        val afile=File(audio)
        val commandString = cmd.joinToString(" ")
        val duration = getDuration(video).replace("Duration:", "").replace(",", "")
        val session = FFmpegKit.executeAsync(commandString,
            { session ->
                val state = session.state
                val returnCode = session.returnCode
                if (returnCode?.value == 0) {
                    vfile.delete()
                    afile.delete()
                } else {
                    println("FFmpeg process exited with state $state and return code ${returnCode?.value}.${session.failStackTrace}")
                }
            },
            { log ->

                val timeRegex = Regex("time=(\\d\\d:\\d\\d:\\d\\d\\.\\d\\d)")
                val matchResult = timeRegex.find(log.message)
                val progress = matchResult?.groups?.get(1)?.value

                if (progress != null) {
                    mergingProgress = calculatePercentage(progress, duration)
                }
            },
            { _ -> }
        )
    }
    fun parseTimeToSeconds(time: String): Double {
        val parts = time.split(":").map { it.toDouble() }
        // Convert "HH:MM:SS.SS" to total seconds
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }
    fun calculatePercentage(numerator: String, denominator: String): Int {
        val numSeconds = parseTimeToSeconds(numerator)
        val denomSeconds = parseTimeToSeconds(denominator)

        if (denomSeconds == 0.0) return 0  // Avoid division by zero

        // Calculate and round the percentage to an integer
        return ((numSeconds / denomSeconds) * 100).toInt()
    }
    fun retriveStreamingData(videoId:String): JSONObject? {
        val requestVariant=variants[1]
        val keY=requestVariant.query["key"].toString()
        val url = "https://www.youtube.com/youtubei/v1/player?${encodeParams(mapOf("videoId" to videoId,"contentCheckOk" to true, "racyCheckOk" to true))}"
        val requestBody =requestVariant.data.toString()
        val request = Request.Builder()
            .url(url)
            .apply {
                requestVariant.headers.forEach { (key, value) ->
                    addHeader(key, value)
                }
            }
            .post(requestBody.toRequestBody())
            .build()
        val client = OkHttpClient()
        val response = client.newCall(request).execute()
        if (response.code==200){
            val responseString= response.body?.string()
            val responseJson= responseString?.let { JSONObject(it) }
            return responseJson
        }
        return null

    }
    fun encodeParams(params: Map<String, Any>): String {
        return params.entries.joinToString("&") { "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value.toString(), "UTF-8")}" }
    }
    fun extractVideoId(ytUrl: String): String? {
        val regex = """^.*(?:(?:youtu\.be\/|v\/|vi\/|u\/\w\/|embed\/|shorts\/|live\/)|(?:(?:watch)?\?v(?:i)?=|\&v(?:i)?=))([^#\&\?]*).*""".toRegex()
        val matchResult = regex.find(ytUrl)
        if (matchResult != null) {
            return matchResult.groupValues[1]
        }
        return null
    }
    fun downloadas9mb(url: String, fos: FileOutputStream) {
        println(url)
        val client = OkHttpClient()
        val enbyte= minOf(downloadedBytes+9437184,totalBytes)
        /*to use endbyte put range=$downloadedBytes-$endBytes and uncomment downloadasmb(url,fos),after resonse block*/
        val request = Request.Builder()
            .url(url)
            .addHeader("Range", "bytes=0-")
            .build()
        val response=client.newCall(request).execute()
        println(response.code)
        response.body?.byteStream().use { inputStream->
            val buffer=ByteArray(1024)
            var bytesRead: Int
            if (inputStream != null) {
                while (inputStream.read(buffer).also { bytesRead=it }!=-1){
                    fos.write(buffer,0,bytesRead)
                    downloadedBytes+=bytesRead
                }
            }
        }
        /* if (downloadedBytes==totalBytes){
             println("\nDownload Finished")
             fos.close()
         }else{
             downloadas9mb(url,fos)
         }*/
    }
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(updateTask) // Clean up
    }
}