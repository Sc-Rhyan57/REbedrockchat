# SevenVoice

**by rhyan57** • [dsc.gg/sevenmc7](https://dsc.gg/sevenmc7)

Aplicativo Android que conecta jogadores de Minecraft Bedrock ao mod **Simple Voice Chat** (Java Edition) rodando no servidor SevenMC — sem precisar de PC, apenas pelo celular.

---

## Como funciona

1. O app faz login com sua conta Xbox (Microsoft) via Device Code Flow.
2. Você adiciona o servidor (host + porta UDP do voice chat).
3. Ao conectar, o app abre um serviço em segundo plano que envia/recebe áudio via UDP na porta do Simple Voice Chat (padrão: `24454`).
4. Um overlay flutuante estilo Discord aparece por cima de qualquer app — clique nele para expandir o painel com controles de mudo, ensurdecer e desconectar. Clique fora para recolher em bolinha.

---

## Estrutura do projeto

```
SevenVoice/
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/gg/sevenmc/voice/
│       │   ├── auth/
│       │   │   └── XboxAuthManager.kt       ← Login Xbox via Device Code
│       │   ├── network/
│       │   │   ├── VoiceChatConnection.kt   ← UDP + AES com o servidor SVC
│       │   │   ├── AudioCapture.kt          ← Captura do microfone
│       │   │   └── AudioPlayback.kt         ← Reprodução de áudio
│       │   ├── overlay/
│       │   │   └── OverlayService.kt        ← Overlay flutuante (Discord-style)
│       │   ├── service/
│       │   │   └── VoiceChatService.kt      ← Foreground service principal
│       │   ├── ui/
│       │   │   ├── MainActivity.kt
│       │   │   ├── LoginActivity.kt
│       │   │   ├── SettingsActivity.kt
│       │   │   ├── ServerEditorActivity.kt
│       │   │   └── ServerAdapter.kt
│       │   └── util/
│       │       └── ServerPreferences.kt     ← Persistência de servidores e config
│       └── res/
│           ├── layout/                      ← Todos os layouts XML
│           ├── drawable/                    ← Ícones e backgrounds
│           └── values/                      ← Cores, strings, temas
├── build.gradle.kts
└── settings.gradle.kts
```

---

## Requisitos no servidor

- Plugin **Simple Voice Chat** instalado (Bukkit/Fabric/NeoForge).
- Porta UDP `24454` aberta no firewall e roteador.
- O secret pode ser configurado em `config/voicechat/voicechat-server.properties`.

---

## Como compilar

```bash
# Na raiz do projeto
./gradlew assembleDebug
# APK gerado em: app/build/outputs/apk/debug/app-debug.apk
```

Ou abrir no **Android Studio** → Build → Generate APK.

**Min SDK:** Android 8.0 (API 26)  
**Target SDK:** Android 14 (API 34)

---

## Permissões necessárias

| Permissão | Motivo |
|---|---|
| `RECORD_AUDIO` | Captura do microfone |
| `SYSTEM_ALERT_WINDOW` | Overlay flutuante |
| `FOREGROUND_SERVICE` | Serviço de voz em background |
| `INTERNET` | Conexão UDP com o servidor |

---

*by rhyan57 • [dsc.gg/sevenmc7](https://dsc.gg/sevenmc7)*
