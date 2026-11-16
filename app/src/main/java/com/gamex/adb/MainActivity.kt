package com.gamex.adb

import android.app.Activity
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.content.Context
import android.app.AlertDialog
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*

class MainActivity : Activity() {
    private var discovery: NsdManager.DiscoveryListener? = null
    private var connectDiscovery: NsdManager.DiscoveryListener? = null
    private var nsd: NsdManager? = null
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
        root.addView(TextView(this).apply { text = "TV: Kablosuz hata ayıklama → Eşleme koduyla cihaz eşleştir. Ardından Wi-Fi ağında TV eşleme adresini bul düğmesine bas." })
        fun button(label: String, action: () -> Unit) {
            root.addView(Button(this).apply { text = label; setOnClickListener { action() } })
        }
        button("Termux komut izni iste") {
            requestPermissions(arrayOf("com.termux.permission.RUN_COMMAND"), 101)
        }
        button("Wi-Fi ağında TV eşleme adresini bul") { discoverTv() }
        button("TV otomatik bağlantıyı başlat") { discoverConnect() }
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
        button("Root durumunu kontrol et") {
            onDevice("shell id; adb -s DEVICE shell command -v su; adb -s DEVICE shell getprop ro.debuggable")
        }
        button("Bootloader durumunu kontrol et (salt okunur)") {
            onDevice("shell getprop ro.boot.flash.locked; adb -s DEVICE shell getprop ro.boot.vbmeta.device_state; adb -s DEVICE shell getprop ro.oem_unlock_supported; adb -s DEVICE shell getprop ro.boot.verifiedbootstate")
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
        root.addView(TextView(this).apply { text = "APK ve uygulama yöneticisi"; textSize = 18f })
        val pkg = field("Paket adı: örn. com.example.app")
        root.addView(pkg)
        fun safePackage(): String? {
            val value = pkg.text.toString().trim()
            return value.takeIf { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z0-9_]+)+")) }
        }
        button("Uygulama ayrıntılarını göster") {
            val p = safePackage()
            if (p == null) status.text = "Geçerli paket adı gir"
            else onDevice("shell dumpsys package $p")
        }
        button("Uygulamayı devre dışı bırak") {
            val p = safePackage()
            if (p == null) status.text = "Geçerli paket adı gir"
            else if (p in setOf("android", "com.android.systemui", "com.android.settings", "com.android.shell")) {
                status.text = "Kritik sistem paketi korunuyor."
            } else AlertDialog.Builder(this)
                .setTitle("Uygulamayı devre dışı bırak?")
                .setMessage("$p devre dışı kalacak. TV'nin açılış ekranı veya kumanda işlevleri etkilenebilir. Önce paket işlevini doğrula.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Onayla") { _, _ -> onDevice("shell pm disable-user --user 0 $p") }
                .show()
        }
        button("Uygulamayı yeniden etkinleştir") {
            val p = safePackage()
            if (p == null) status.text = "Geçerli paket adı gir"
            else onDevice("shell pm enable --user 0 $p")
        }
        val apk = field("TV üzerindeki APK yolu: /sdcard/Download/app.apk")
        root.addView(apk)
        button("TV'deki APK dosyasını yükle") {
            val p = apk.text.toString().trim()
            if (!p.matches(Regex("/sdcard/[a-zA-Z0-9_./-]+\\.apk")) || p.contains("..")) {
                status.text = "Yalnızca /sdcard/ altındaki .apk dosyaları desteklenir."
            } else AlertDialog.Builder(this)
                .setTitle("APK kurulumunu onayla")
                .setMessage("Bu işlem TV üzerinde $p dosyasını yüklemeyi dener. Kaynağına güvendiğin APK'ları kullan.")
                .setNegativeButton("Vazgeç", null)
                .setPositiveButton("Yükle") { _, _ -> onDevice("shell pm install -r $p") }
                .show()
        }
        root.addView(TextView(this).apply {
            text = "AI sunucu bağlantısı (HTTPS, sunucu kurulumu gerekir)"
            textSize = 18f
        })
        val backend = field("https://sunucunuz.example/api/assistant")
        val prompt = field("AI'ya sor: TV sistemini nasıl analiz ederim?")
        root.addView(backend)
        root.addView(prompt)
        button("AI sunucusuna sor") {
            val endpoint = backend.text.toString().trim()
            val question = prompt.text.toString().trim()
            if (!endpoint.startsWith("https://") || question.isBlank()) {
                status.text = "HTTPS adresi ve soru gerekli"
            } else {
                status.text = "AI sunucusuna bağlanılıyor..."
                thread {
                    try {
                        val connection = URL(endpoint).openConnection() as HttpsURLConnection
                        connection.requestMethod = "POST"
                        connection.connectTimeout = 10000
                        connection.readTimeout = 20000
                        connection.doOutput = true
                        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                        val payload = JSONObject().put("message", question).toString()
                        connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                        val result = if (connection.responseCode in 200..299) {
                            connection.inputStream.bufferedReader().use { it.readText() }
                        } else "HTTP hata: ${connection.responseCode}"
                        connection.disconnect()
                        runOnUiThread { status.text = result.take(2000) }
                    } catch (e: Exception) {
                        runOnUiThread { status.text = "AI bağlantı hatası: ${e.message}" }
                    }
                }
            }
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

    private fun discoverConnect() {
        status.text = "Aynı Wi-Fi ağındaki ADB bağlantı servisi aranıyor..."
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = manager
        connectDiscovery?.let { try { manager.stopServiceDiscovery(it) } catch (_: Exception) {} }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_adb-tls-connect")) return
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        runOnUiThread { status.text = "Bağlantı adresi çözümlenemedi ($errorCode)" }
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val address = info.host?.hostAddress ?: return
                        if (!validHost(address) || !validPort(info.port.toString())) return
                        runOnUiThread {
                            host.setText(address)
                            connectPort.setText(info.port.toString())
                            status.text = "ADB cihazı bulundu: $address:${info.port}. Bağlantı deneniyor..."
                            runTermux("adb connect $address:${info.port}")
                            connectDiscovery?.let { try { manager.stopServiceDiscovery(it) } catch (_: Exception) {} }
                            connectDiscovery = null
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                runOnUiThread { status.text = "ADB bağlantı araması başarısız ($errorCode)" }
                try { manager.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
        }
        connectDiscovery = listener
        try { manager.discoverServices("_adb-tls-connect._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (e: Exception) { status.text = "ADB arama hatası: ${e.message}" }
    }

    private fun discoverTv() {
        status.text = "TV'de eşleme kodu ekranını açık tut. Ağda ADB eşleme servisi aranıyor..."
        val manager = getSystemService(Context.NSD_SERVICE) as NsdManager
        nsd = manager
        discovery?.let { try { manager.stopServiceDiscovery(it) } catch (_: Exception) {} }
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) {}
            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains("_adb-tls-pairing")) return
                manager.resolveService(service, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        runOnUiThread { status.text = "TV bulundu ancak adres çözümlenemedi ($errorCode). Yeniden dene." }
                    }
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val address = info.host?.hostAddress ?: return
                        val port = info.port
                        if (!validHost(address) || !validPort(port.toString())) return
                        runOnUiThread {
                            host.setText(address)
                            pairingPort.setText(port.toString())
                            status.text = "TV eşleme servisi bulundu: $address:$port. Şimdi 6 haneli kodu gir."
                            val input = EditText(this@MainActivity).apply {
                                inputType = android.text.InputType.TYPE_CLASS_NUMBER
                                filters = arrayOf(android.text.InputFilter.LengthFilter(6))
                                hint = "TV'deki 6 haneli kod"
                            }
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("TV ile eşleştir")
                                .setMessage("TV ekranındaki geçici eşleme kodunu gir.")
                                .setView(input)
                                .setNegativeButton("İptal", null)
                                .setPositiveButton("Eşleştir") { _, _ ->
                                    val value = input.text.toString().trim()
                                    if (!value.matches(Regex("[0-9]{6}"))) status.text = "6 haneli kod gerekli."
                                    else {
                                        code.setText(value)
                                        runTermux("adb pair $address:$port $value")
                                    }
                                }.show()
                            discovery?.let { try { manager.stopServiceDiscovery(it) } catch (_: Exception) {} }
                            discovery = null
                        }
                    }
                })
            }
            override fun onServiceLost(service: NsdServiceInfo) {}
            override fun onDiscoveryStopped(type: String) {}
            override fun onStartDiscoveryFailed(type: String, errorCode: Int) {
                runOnUiThread { status.text = "Ağ taraması başlatılamadı ($errorCode). IP ve portu elle girebilirsin." }
                try { manager.stopServiceDiscovery(this) } catch (_: Exception) {}
            }
            override fun onStopDiscoveryFailed(type: String, errorCode: Int) {}
        }
        discovery = listener
        try { manager.discoverServices("_adb-tls-pairing._tcp.", NsdManager.PROTOCOL_DNS_SD, listener) }
        catch (e: Exception) { status.text = "Ağ tarama hatası: ${e.message}" }
    }

    override fun onDestroy() {
        discovery?.let { try { nsd?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        connectDiscovery?.let { try { nsd?.stopServiceDiscovery(it) } catch (_: Exception) {} }
        super.onDestroy()
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
