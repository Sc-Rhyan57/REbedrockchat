package gg.sevenmc.voice.mc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.raphimc.minecraftauth.MinecraftAuth
import net.raphimc.minecraftauth.java.JavaAuthManager
import net.raphimc.minecraftauth.msa.MsaToken
import net.raphimc.minecraftauth.xbox.XboxAuthManager
import org.geysermc.mcprotocollib.auth.GameProfile

data class JavaCredentials(
    val gameProfile: GameProfile,
    val accessToken: String
)

class MinecraftAuthBridge {

    suspend fun getCredentials(msAccessToken: String): Result<JavaCredentials> = withContext(Dispatchers.IO) {
        runCatching {
            val httpClient = MinecraftAuth.createHttpClient()
            val xboxAuthManager = XboxAuthManager.create(httpClient)
            val xboxToken = xboxAuthManager.loginWithMsa(MsaToken(msAccessToken, 0L, "")).get()

            val authManager = JavaAuthManager.create(httpClient)
            authManager.loginWithXbox(xboxToken)

            val mcProfile = authManager.getMinecraftProfile().getUpToDate()
            val mcToken = authManager.getMinecraftToken().getUpToDate()

            val profile = GameProfile(
                mcProfile.id,
                mcProfile.name
            )

            JavaCredentials(
                gameProfile = profile,
                accessToken = mcToken.token
            )
        }
    }
}
