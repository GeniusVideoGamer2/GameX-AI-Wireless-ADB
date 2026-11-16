package com.gamex.adb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast

class AdbResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = intent.getBundleExtra("result")
        val stdout = result?.getString("stdout").orEmpty()
        val stderr = result?.getString("stderr").orEmpty()
        val error = result?.getString("errmsg").orEmpty()
        val exit = result?.getInt("exitCode", -1) ?: -1
        val output = listOf(stdout, stderr, error).filter { it.isNotBlank() }.joinToString("\n")
        val display = if (output.isNotBlank()) output.take(1600) else "ADB sonucu alınamadı (çıkış kodu: $exit)"
        context.getSharedPreferences("adb", Context.MODE_PRIVATE).edit().putString("last_result", output.take(150000)).apply()
        context.sendBroadcast(Intent("com.gamex.adb.RESULT_UPDATED").setPackage(context.packageName))
        Toast.makeText(context, if (exit == 0) "ADB komutu tamamlandı" else "ADB hata: $exit", Toast.LENGTH_SHORT).show()
    }
}
