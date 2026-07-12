package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.model.MsaToken
import net.raphimc.minecraftauth.msa.service.impl.ExternalMsaAuthService
import org.geysermc.mcprotocollib.auth.GameProfile
import java.util.UUID

data class JavaCredentials(
    val gameProfile: GameProfile,
    val accessToken: String
)

class MinecraftAuthBridge {

    suspend fun getCredentials(msAccessToken: String): Result<JavaCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val httpClient = MinecraftAuth.createHttpClient()
            val authManager = JavaAuthManager.create(httpClient)
                .login(ExternalMsaAuthService::new, MsaToken(msAccessToken))

            val mcProfile = authManager.getMinecraftProfile().getUpToDate()
            val mcToken = authManager.getMinecraftToken().getUpToDate()

            val profile = GameProfile(
                UUID.fromString(formatUUID(mcProfile.id)),
                mcProfile.name
            )

            JavaCredentials(
                gameProfile = profile,
                accessToken = mcToken.token
            )
        }
    }

    private fun formatUUID(raw: String): String {
        if (raw.contains("-")) return raw
        return "${raw.substring(0, 8)}-${raw.substring(8, 12)}-${raw.substring(12, 16)}-${raw.substring(16, 20)}-${raw.substring(20)}"
    }
}
