package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.MsaToken
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
            val msaToken = MsaToken(msAccessToken, System.currentTimeMillis() + 3600000, "")
            val authManager = JavaAuthManager.create(httpClient)
            authManager.loginWithMsa(msaToken)

            val mcProfile = authManager.getMinecraftProfile().getUpToDate()
            val mcToken = authManager.getMinecraftAccessToken().getUpToDate()

            val profile = GameProfile(
                parseUUID(mcProfile.id),
                mcProfile.name
            )

            JavaCredentials(
                gameProfile = profile,
                accessToken = mcToken.accessToken
            )
        }
    }

    private fun parseUUID(id: String): UUID {
        return try {
            UUID.fromString(id)
        } catch (e: Exception) {
            val sb = StringBuilder(id)
            sb.insert(8, "-")
            sb.insert(13, "-")
            sb.insert(18, "-")
            sb.insert(23, "-")
            UUID.fromString(sb.toString())
        }
    }
}
