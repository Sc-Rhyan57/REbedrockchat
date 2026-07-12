package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.model.MsaCredentials
import net.raphimc.minecraftauth.msa.service.impl.CredentialsMsaAuthService
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
            
            val authManager = JavaAuthManager.create(httpClient).login(
                ::CredentialsMsaAuthService,
                MsaCredentials(msAccessToken, "")
            )

            val mcProfile = authManager.minecraftProfile.upToDate
            val mcToken = authManager.minecraftToken.upToDate

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
