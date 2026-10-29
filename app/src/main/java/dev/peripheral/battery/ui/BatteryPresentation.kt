package dev.peripheral.battery.ui

import dev.peripheral.battery.domain.*

data class ReadingPresentation(val value: String, val description: String, val progress: Float?)
fun DeviceReading.present(): ReadingPresentation {
    val status = when (availability) {
        Availability.PC_OFFLINE -> "PC offline"
        Availability.DEVICE_DISCONNECTED -> "Device disconnected"
        Availability.STALE -> "Stale reading"
        Availability.AVAILABLE -> null
    }
    val value = when {
        batteryPercent == null -> "Unknown"
        precision == Precision.APPROXIMATE -> "~$batteryPercent%"
        precision == Precision.ESTIMATE -> "Battery estimate: $batteryPercent%"
        else -> "$batteryPercent%"
    }
    val wording = when (precision) {
        Precision.PRECISE -> "$source · Precise reading"
        Precision.APPROXIMATE -> "Approximate reading"
        Precision.ESTIMATE -> "Estimate · source under validation"
    }
    val unavailable = availability == Availability.PC_OFFLINE || availability == Availability.DEVICE_DISCONNECTED
    return ReadingPresentation(if (unavailable) "Unknown" else value,
        listOfNotNull(status, wording, caution?.takeUnless { it == wording }).joinToString(" · "),
        if (unavailable) null else batteryPercent?.div(100f))
}
