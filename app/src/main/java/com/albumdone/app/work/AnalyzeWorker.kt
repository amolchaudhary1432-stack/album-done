package com.albumdone.app.work

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.albumdone.app.AlbumDoneApp
import com.albumdone.app.R
import com.albumdone.app.analysis.FaceEmbedder
import com.albumdone.app.analysis.PhotoAnalyzer
import com.albumdone.app.data.AppDatabase
import com.albumdone.app.data.FaceEntity
import com.albumdone.app.data.PhotoEntity
import com.albumdone.app.data.ProjectStatus
import com.albumdone.app.people.PeopleRepository
import com.albumdone.app.plan.Box
import com.albumdone.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Analyses every not-yet-done photo of a project in the background.
 *
 * - Runs as a foreground job with a progress notification, so it continues with the screen off.
 * - Saves each photo's result as soon as it is done, so a stopped run resumes where it left off.
 * - Uses one or two lanes depending on the phone, so cheap phones are not overloaded.
 * - When every photo is done, groups the faces into people and finds the couple.
 */
class AnalyzeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    private val dao = AppDatabase.get(context).dao()

    override suspend fun doWork(): Result {
        val projectId = inputData.getLong(KEY_PROJECT_ID, -1L)
        val project = dao.project(projectId) ?: return Result.failure()

        val total = dao.countAll(projectId)
        val alreadyDone = dao.countProcessed(projectId)
        trySetForeground(projectId, project.name, alreadyDone, total)

        val pending = dao.pendingPhotos(projectId)
        val doneCount = AtomicInteger(alreadyDone)
        val runStart = System.currentTimeMillis()
        val baseActive = project.activeMillis
        val progressLock = Mutex()
        var lastUpdate = 0L

        val thumbDir = File(applicationContext.filesDir, "thumbs/$projectId")
        val queue = Channel<PhotoEntity>(capacity = Channel.UNLIMITED)
        pending.forEach { queue.trySend(it) }
        queue.close()

        coroutineScope {
            repeat(laneCount()) {
                launch(Dispatchers.IO) {
                    PhotoAnalyzer(applicationContext).use { analyzer ->
                        for (photo in queue) {
                            if (isStopped) break
                            val (updated, faces) = analyzeOne(analyzer, photo, thumbDir)
                            dao.savePhotoResult(updated, faces)
                            val done = doneCount.incrementAndGet()
                            progressLock.withLock {
                                val now = System.currentTimeMillis()
                                if (now - lastUpdate > 1500 || done == total) {
                                    lastUpdate = now
                                    dao.setActiveMillis(projectId, baseActive + (now - runStart))
                                    trySetForeground(projectId, project.name, done, total)
                                }
                            }
                        }
                    }
                }
            }
        }

        dao.setActiveMillis(projectId, baseActive + (System.currentTimeMillis() - runStart))
        if (isStopped) return Result.retry()

        // Everyone in the photos: group faces into people, then find the couple.
        dao.setStatus(projectId, ProjectStatus.PEOPLE)
        trySetForeground(projectId, project.name, total, total, "Finding the people in your photos…")
        PeopleRepository(dao).build(projectId)
        dao.setActiveMillis(projectId, baseActive + (System.currentTimeMillis() - runStart))
        if (isStopped) return Result.retry()

        dao.setStatus(projectId, ProjectStatus.DONE)
        notifyFinished(projectId, project.name, total)
        return Result.success()
    }

    private fun analyzeOne(analyzer: PhotoAnalyzer, photo: PhotoEntity, thumbDir: File): Pair<PhotoEntity, List<FaceEntity>> {
        val thumbFile = File(thumbDir, "${photo.id}.jpg")
        return try {
            val a = analyzer.analyze(Uri.parse(photo.docUri), thumbFile)
            val updated = photo.copy(
                processed = true,
                failed = false,
                takenAt = a.takenAt,
                width = a.width,
                height = a.height,
                sharpness = a.quality.sharpness,
                peakSharpness = a.peakSharpness,
                brightness = a.quality.brightness,
                darkFraction = a.quality.darkFraction,
                brightFraction = a.quality.brightFraction,
                hash = a.hash,
                tilt = a.tilt,
                faceCount = a.faces.size,
                phones = Box.encodeList(a.phones),
                foregroundBacks = a.foregroundBacks,
                thumbPath = thumbFile.absolutePath,
            )
            val faces = a.faces.map { f ->
                val r = f.reading
                FaceEntity(
                    projectId = photo.projectId,
                    photoId = photo.id,
                    left = r.box.left, top = r.box.top, right = r.box.right, bottom = r.box.bottom,
                    eyes = r.eyes.name,
                    expression = r.expression.name,
                    smile = r.smile,
                    looking = r.lookingAtCamera,
                    directionX = r.directionX,
                    yaw = r.yaw,
                    sharpness = f.sharpness,
                    brightness = f.brightness,
                    backlit = f.backlit,
                    occlusion = f.occlusion,
                    cutOff = r.cutOff,
                    quality = f.quality,
                    embedding = f.embedding?.let { FaceEmbedder.toBytes(it) },
                )
            }
            updated to faces
        } catch (e: OutOfMemoryError) {
            // Skip this one photo instead of crashing the whole run.
            System.gc()
            photo.copy(processed = true, failed = true) to emptyList()
        } catch (e: Exception) {
            photo.copy(processed = true, failed = true) to emptyList()
        }
    }

    private fun laneCount(): Int {
        val am = applicationContext.getSystemService(ActivityManager::class.java)
        val cores = Runtime.getRuntime().availableProcessors()
        return if (am.isLowRamDevice || cores < 6) 1 else 2
    }

    private suspend fun trySetForeground(projectId: Long, name: String, done: Int, total: Int, text: String? = null) {
        try {
            setForeground(foregroundInfo(projectId, name, done, total, text))
        } catch (_: Exception) {
            // Android may refuse a foreground job if the app was not open when it restarted.
            // The work still continues; it just may pause while the screen is off.
        }
    }

    private fun foregroundInfo(projectId: Long, name: String, done: Int, total: Int, text: String?): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, AlbumDoneApp.CHANNEL_ANALYSIS)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Checking photos: $name")
            .setContentText(text ?: "$done of $total done. You can lock your phone.")
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent(projectId))
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_PROGRESS, notification)
        }
    }

    private suspend fun notifyFinished(projectId: Long, name: String, total: Int) = withContext(Dispatchers.Main) {
        val notification: Notification = NotificationCompat.Builder(applicationContext, AlbumDoneApp.CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("Your photos are ready")
            .setContentText("$total photos checked for $name. Tap to tag your family and make your shortlist.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(projectId))
            .build()
        try {
            applicationContext.getSystemService(NotificationManager::class.java)
                .notify(NOTIFICATION_DONE + projectId.toInt(), notification)
        } catch (_: SecurityException) {
            // Notifications not allowed; the app shows the result when opened.
        }
    }

    private fun openAppIntent(projectId: Long): PendingIntent {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_PROJECT_ID, projectId)
        return PendingIntent.getActivity(
            applicationContext,
            projectId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        const val KEY_PROJECT_ID = "projectId"
        private const val NOTIFICATION_PROGRESS = 1001
        private const val NOTIFICATION_DONE = 2000

        fun workName(projectId: Long) = "analyze-$projectId"

        fun start(context: Context, projectId: Long) {
            val request = OneTimeWorkRequestBuilder<AnalyzeWorker>()
                .setInputData(workDataOf(KEY_PROJECT_ID to projectId))
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(projectId), ExistingWorkPolicy.KEEP, request)
        }
    }
}
