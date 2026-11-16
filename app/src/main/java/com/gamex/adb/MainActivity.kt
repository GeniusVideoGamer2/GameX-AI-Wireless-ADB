package com.gamex.adb

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private lateinit var host: EditText
    private lateinit var pairingPort: EditText
    private lateinit var code: EditText
    private lateinit var connectPort: EditText
    private lateinit var status: TextView

    private fun field(hintText: String, initial: String = "") = EditText(this).apply {
        setSingleLine(true)
        hint = hintText
        setText(initial)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 32, 32, 32)
        }
        setContentView(ScrollView(this).apply { addView(root) })
        root.addView(TextView(this).apply { text = "GameX Wireless ADB"; textSize = 25f })
        root.addView(TextView(this).apply {
            text = "Sunny TV: Geliştirici seçenekleri → Kablosuz hata ayıklama → Eşleme kodu ile eşleştir"
        })
        host = field("TV IP adresi", "192.168.1.20")
        pairingPort = field("Eşleme portu")
        code = field("6 haneli eşleme kodu")
        connectPort = field("Bağlantı portu", "35779")
        listOf(host, pairingPort, code, connectPort).forEach(root::addView)
        status = TextView(this).apply { text = "Termux + android-tools gerekir." }
        root.addView(status)
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Termux komut izni iste") {
            requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"), 101)
        }
        button("Eşleştir") {
            val h = host.text.toString().trim()
            val p = pairingPort.text.toString().trim()
            val c = code.text.toString().trim()
            if (!validHost(h) || !validPort(p) || !c.matches(Regex("[0-9]{6}"))) {
                status.text = "IP, eşleme portu veya kod hatalı"
            } else runTermux("adb pair $h:$p $c")
        }
        button("Bağlan") {
            val h = host.text.toString().trim()
            val p = connectPort.text.toString().trim()
            if (!validHost(h) || !validPort(p)) status.text = "IP veya port hatalı"
            else runTermux("adb connect $h:$p")
        }
        button("Sistem bilgilerini oku") {
            val h = host.text.toString().trim()
            val p = connectPort.text.toString().trim()
            if (!validHost(h) || !validPort(p)) {
                status.text = "Önce geçerli bağlantı adresi gir"
            } else {
                val device = "$h:$p"
                runTermux("adb -s $device shell getprop ro.product.model; adb -s $device shell getprop ro.build.version.release; adb -s $device shell getprop ro.product.cpu.abi; adb -s $device shell getprop ro.treble.enabled")
            }
        }
        button("Yüklü uygulamaları listele") {
            onDevice("shell pm list packages -f")
        }
        button("ROM uyumluluğunu kontrol et") {
            onDevice("shell getprop ro.treble.enabled; adb -s DEVICE shell getprop ro.boot.verifiedbootstate; adb -s DEVICE shell getprop ro.boot.dynamic_partitions; adb -s DEVICE shell getprop ro.product.cpu.abi")
        }
        button("TV ana ekranına dön") {
            onDevice("shell input keyevent KEYCODE_HOME")
        }
        button("TV geri tuşu") {
            onDevice("shell input keyevent KEYCODE_BACK")
        }
        button("TV ses artır") {
            onDevice("shell input keyevent KEYCODE_VOLUME_UP")
        }
        button("TV ses azalt") {
            onDevice("shell input keyevent KEYCODE_VOLUME_DOWN")
        }
        root.addView(TextView(this).apply {
            text = "AI komut yardımcısı (çevrimdışı; GPT-6 değil)"
            textSize = 18f
        })
        val request = field("Örnek: Android sürümü / uygulamaları listele / ana ekran")
        root.addView(request)
        button("Komut öner ve çalıştır") {
            val command = when {
                request.text.contains("sürüm", true) -> "shell getprop ro.build.version.release"
                request.text.contains("uygulama", true) -> "shell pm list packages"
                request.text.contains("işlemci", true) -> "shell getprop ro.product.cpu.abi"
                request.text.contains("ana ekran", true) -> "shell input keyevent KEYCODE_HOME"
                else -> null
            }
            if (command == null) status.text = "Bu istek için güvenli komut tanımlı değil."
            else onDevice(command)
        }
    }

    private fun onDevice(action: String) {
        val h = host.text.toString().trim()
        val p = connectPort.text.toString().trim()
        if (!validHost(h) || !validPort(p)) {
            status.text = "Geçerli IP ve bağlantı portu gir"
            return
        }
        val device = "$h:$p"
        runTermux("adb -s $device " + action.replace("DEVICE", device))
    }

    private fun validHost(h: String): Boolean =
        h.matches(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) &&
            h.split('.').all { (it.toIntOrNull() ?: -1) in 0..255 }

    private fun validPort(p: String): Boolean = (p.toIntOrNull() ?: 0) in 1..65535

    private fun runTermux(command: String) {
        try {
            if (checkSelfPermission("com.termux.permission.RUN_COMMAND") != PackageManager.PERMISSION_GRANTED) {
                status.text = "Önce Termux komut iznini ver"
                return
            }
            val intent = Intent("com.termux.RUN_COMMAND").apply {
                component = ComponentName("com.termux", "com.termux.app.RunCommandService")
                putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
                putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-lc", command))
                putExtra("com.termux.RUN_COMMAND_WORKDIR", "/data/data/com.termux/files/home")
                putExtra("com.termux.RUN_COMMAND_BACKGROUND", false)
            }
            startService(intent)
            status.text = "Komut Termux'a gönderildi. Çıktıyı Termux'ta kontrol et."
        } catch (e: Exception) {
            status.text = "Termux başlatılamadı: ${e.message}"
        }
    }
}
