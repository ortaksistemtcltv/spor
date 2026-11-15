package com.ortaksistem.seyirbox.catalog

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.lagradost.cloudstream3.CommonActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

internal object BrowserPlayback {
    private val opening = Mutex()

    suspend fun open(title: String, page: String, waitSeconds: Int = 0): Boolean = withContext(Dispatchers.Main) {
        if (!isHttpsUrl(page) || !opening.tryLock()) return@withContext false
        try {
            val activity = CommonActivity.activity ?: return@withContext false
            if (activity.isFinishing || activity.isDestroyed) return@withContext false

            if (waitSeconds > 0) {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setMessage("Tarayıcı açılıyor · ${waitSeconds} sn")
                    .setNegativeButton("İptal", null)
                    .show()
                try {
                    for (remaining in waitSeconds downTo 1) {
                        if (!dialog.isShowing) return@withContext false
                        dialog.setMessage("Tarayıcı açılıyor · $remaining sn")
                        delay(1_000)
                    }
                    if (!dialog.isShowing || dialog.window?.decorView?.hasWindowFocus() != true) {
                        return@withContext false
                    }
                } finally {
                    dialog.dismiss()
                }
            }

            if (activity.isFinishing || activity.isDestroyed) return@withContext false
            val uri = Uri.parse(page)
            val intent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
            try {
                activity.startActivity(intent)
                true
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(activity, "Bu cihazda web tarayıcısı bulunamadı.", Toast.LENGTH_LONG).show()
                false
            }
        } finally {
            opening.unlock()
        }
    }
}
