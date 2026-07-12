package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.step.msa.MsaXBLToken
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
            
            val msaToken = MsaXBLToken(msAccessToken)
            val result = MinecraftAuth.JAVA_JWT_LOGIN.getFromInput(httpClient, msaToken)

            val mcProfile = result.getMinecraftProfile()
            val mcToken = result.getMinecraftToken()

            val profile = GameProfile(
                parseUUID(mcProfile.id.toString()),
                mcProfile.name
            )

            JavaCredentials(
                gameProfile = profile,
                accessToken = mcToken.token
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
