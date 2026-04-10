# VoxelMind Mod — CurseForge Fabric Mod

## Projekt
Fabric Mod fuer MC 1.21.4. Reiner REST API Client — ruft Brain Server Endpoints auf.
Kein Agent-Code, kein LLM, kein Mineflayer, kein Memory im Mod.

## Tech Stack
- **Fabric Loader:** 0.16.9
- **Fabric API:** 0.113.0+1.21.4
- **MC Version:** 1.21.4
- **Java:** 21
- **Build:** Gradle + Fabric Loom 1.9
- **REST:** java.net.http.HttpClient (zero external deps)
- **Tunnel:** java.net.http.WebSocket → Relay Server (relay.voxel-mind.com) — ersetzt UPnP + playit.gg
- **Mixin:** IntegratedServer.openToLan (fixer LAN Port)

## Architektur
```
com.voxelmind.mod/
├── VoxelMindMod.java          ← onInitialize (Config laden)
├── VoxelMindClient.java       ← onInitializeClient (Keybind, Commands, Events)
├── config/ModConfig.java      ← Persistent JSON Config (.minecraft/config/voxelmind.json) — autoTunnel, relayUrl
├── api/BrainApiClient.java    ← REST Client (alle Brain Endpoints, async)
├── api/dto/                   ← Response DTOs
├── auth/AuthManager.java      ← Token State, Refresh, OAuth
├── auth/OAuthCallbackServer   ← Localhost HTTP Server fuer OAuth Callback
├── lan/LanManager.java        ← LAN State, Auto-Open, Server-Adresse, Tunnel-Cleanup
├── tunnel/TunnelClient.java   ← WSS Client → Relay (Supabase JWT Auth)
├── tunnel/TunnelBridge.java   ← TCP Bridge (Relay Port ↔ lokaler MC Port)
├── tunnel/TunnelStatus.java   ← Status-Enum (DISCONNECTED/CONNECTING/CONNECTED)
├── tunnel/TunnelManager.java  ← Lifecycle, autoTunnel-Logik
├── gui/                       ← Vanilla MC Screens (VoxelMind, BotConfig, Login) — Tunnel-Status statt "port reachable"
├── command/VmCommand.java     ← /vm command tree (client-side) — Tunnel statt UPnP/playit.gg
├── event/WorldEventHandler    ← Join/Leave Events (auto-LAN, cleanup)
└── mixin/IntegratedServerMixin ← Fixed LAN Port
```

## Brain API Endpoints (Consumer)
| Method | Endpoint | Wofuer |
|--------|----------|--------|
| GET | /bots | Bot-Liste |
| POST | /bots | Bot erstellen |
| DELETE | /bots/:id | Bot loeschen |
| PATCH | /bots/:id | Bot updaten |
| POST | /bots/:id/spawn | Bot spawnen { host, port } |
| POST | /bots/:id/despawn | Bot despawnen |
| GET | /bots/:id/state | Bot Live-State |
| GET | /agent-status | Agent connected? |
| GET | /health | Brain erreichbar? |

## Build
```bash
./gradlew build
```
Output: `build/libs/voxelmind-mod-*.jar`

## Testing
Dev Token manuell in `.minecraft/config/voxelmind.json` eintragen.
OAuth Web-Route existiert noch nicht — kommt als separater Task in voxelmind-web.

## Regeln
- NIEMALS Agent-Code oder Mineflayer in den Mod
- NIEMALS Supabase direkt (nur Brain API)
- NIEMALS MC 26.1 targeten (Mineflayer unterstuetzt es nicht)
- NIEMALS externe Prozesse starten (CurseForge Policy)
- Alle API Calls async (nicht auf MC Main Thread)
- Deutsch fuer Kommunikation, Englisch fuer Code
