package dev.peripheral.battery.data

data class LanPreviewSettings(val host: String, val port: Int = 8765) {
    init {
        val parts = host.split('.')
        require(parts.size == 4 && parts.all { it.toIntOrNull() in 0..255 && it.toInt().toString() == it }) { "Use canonical private IPv4" }
        val b = parts.map { it.toInt() }
        require(b[0] == 10 || b[0] == 172 && b[1] in 16..31 || b[0] == 192 && b[1] == 168) { "Use private IPv4" }
        require(port in 1024..65535)
    }
    val url: String get() = "http://$host:$port/v1/battery-snapshot"
}
