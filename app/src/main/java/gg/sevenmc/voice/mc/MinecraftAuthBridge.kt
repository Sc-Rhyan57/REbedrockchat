package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.model.MsaToken
import org.geysermc.mcprotocollib.auth.GameProfile

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
                mcProfile.id,
                mcProfile.name
            )

            JavaCredentials(
                gameProfile = profile,
                accessToken = mcToken.accessToken
            )
        }
    }
}
