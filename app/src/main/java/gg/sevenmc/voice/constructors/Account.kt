package gg.sevenmc.voice.constructors

import gg.sevenmc.voice.auth.XboxDeviceInfo

data class Account(
    var remark: String,
    val deviceInfo: XboxDeviceInfo,
    val refreshToken: String
)
