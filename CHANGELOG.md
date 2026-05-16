# Changelog

## 0.3.7 (2026-05-16)

### New

- **You decide whether your bot is allowed to hit people.** Two new toggles in the bot create/edit screen: "PVP (attack players)" — off by default, so your bot will refuse to attack any player on command — and "Attack owner", which is locked off until PVP is on and pops a confirmation dialog before turning on (so a misclick can't end with your bot beating you to death). Existing bots stay non-PVP until you flip the toggle.

---

## 0.3.6 (2026-05-15)

### Behind the scenes

- **Better forensics when a bot disconnects mid-session.** Before, when the local tunnel-bridge to your Minecraft world dropped, the mod just told the relay "channel closed" and you saw your bot vanish without any explanation. The mod now sends the underlying reason — server kick (`read_eof`), connection reset (`read_error: ...`), local write failure (`write_error: ...`), or explicit close — to diagnostics, so we can tell the difference between a server-side kick and a network glitch.

No gameplay change; this is pure observability for the brain side.

---

## 0.3.5 (2026-05-15)

### New

- **Naming a bot got a small nudge.** The name field in the bot create screen now shows a faint hint ("Cae, Ember, Sprout… — not your own name") so the first thing you see isn't an empty box. Helps people pick something other than the default and avoids the "name already taken" kick that happens when a bot shares the player's MC username.

### Behind the scenes

- **Better forensics on spawn failures.** When a bot fails to come online within 12 seconds of spawn, the mod now forwards the exact disconnect reason (server-required-Fabric, name collision, raw kick message) to our diagnostics endpoint. Means future "why didn't my bot join?" investigations are faster and more accurate. No change you can see in-game; affects how cleanly we can debug your reports.

---

## 0.3.4 (2026-05-12)

### Smarter bots (behind-the-scenes brain upgrades)

- **Your bot stops chasing every mob during follow.** Before: bot saw a zombie 12 blocks away while following you → dropped everything to fight, often dying. Now: while following / escorting you, bots ignore distant mobs and just keep moving with you. They only fight if a mob is actually attacking you or them, or if you ask. Alone or far from you, they still defend themselves as before. Creepers are still avoided in all cases.
- **Bots eat before they take damage.** Before: bots waited until hunger hit zero and HP started dropping. Now: at food ≤ 10 they eat proactively (during quiet moments — not while following you), at food ≤ 6 they eat immediately. They prefer cooked over raw food (2-3x more nutrition).

### Fix

- **Can't accidentally name your bot after yourself anymore.** Naming a bot the same as your Minecraft username made the server kick it on every spawn ("name already taken"). The mod now blocks that at create-time with a clear error: "Bot can't share your Minecraft name — you'd kick it on every join."
- **If a name-collision kick still happens, you'll know why.** The mod now recognizes the kick reason and tells you in chat: "If you named the bot after yourself, that's why — rename in /vm Edit." The respawn loop also stops automatically so you don't have to manually despawn.

---

## 0.3.3 (2026-05-11)

### New

- **Choose how your bot talks to you.** When creating or editing a bot, you can now pick a chat mode:
  - **Public** (default): Bot talks in the public chat, visible to everyone on the server.
  - **Whisper**: Bot only whispers to you privately via `/msg`. No spam in the public chat.
  - **Mixed**: Bot picks the channel itself depending on context — the old behavior.
  - The button cycles through the three options on the bot config screen.

### Fix

- **Bots stop endlessly retrying on Fabric servers.** If your server requires Fabric Loader or Fabric API, the bot used to loop spawn → kick → respawn until you manually despawned it. Now the mod recognizes the kick reason, stops the retry, and tells you exactly what's needed: "Server requires Fabric Loader or Fabric API — bots can only join vanilla servers."
- **Fewer duplicate bot messages.** The brain had a habit of sending the same message twice in quick succession (slightly different wording). Two layers of defense now prevent that — the LLM sees its own recent words and is told not to repeat, and a backup check kills exact-duplicate messages within 5 seconds.

---

## 0.3.2 (2026-05-02)

### Fix

- **Better diagnostics for failed spawns** — When your bot doesn't connect within 12 seconds, the mod now tells you the most likely reason (e.g. server online-mode=true) instead of leaving you guessing.

---

## 0.3.1 (2026-05-01)

### Under the hood

- **Added: Telemetry** — The mod now sends critical events (LAN open/fail, tunnel status changes, OAuth errors, bot spawn attempts) to our server for diagnostic purposes. When something goes wrong, we can actually see what happened instead of guessing. No PII collected — only connection state info like port numbers and status codes.

---

## 0.3.0-alpha (2026-04-09)

### Your bot talks without getting kicked

- **Chat bug fixed:** Bots used to get kicked with "Chat message validation failure" after 2-3 whispers. Gone. Your bot can now have a full conversation without dropping out.
- **Tunnel cleanup:** When your bot disconnects, the tunnel shuts down cleanly instead of spamming warnings. No more log noise.

### New stuff in the mod GUI

- **⚡ Sparks display:** See how many sparks you have left, right in the VoxelMind panel. A progress bar turns yellow when you're running low, red when you're out.
- **Feedback button:** Tell us what your bot did — good or bad. There's now a "Feedback" button in the main screen and a `/vm feedback` command. Every bit helps us make the AI smarter.
- **Account button + "Get More Sparks" link:** One click opens your account page or the pricing page in your browser.
- **New commands:** `/vm recharge`, `/vm account`, `/vm feedback`
- **Spawn errors in plain English:** "Connection refused — is the server running?" instead of cryptic error codes. Also covers "Out of sparks", timeouts, kicks, and online-mode mismatches.
- **Loading states:** When you hit Spawn or Despawn, the button shows "working…" so you know something's happening.
- **Smoother bot list:** Fixed a tiny overlap between bot names and their personality label.

### Better bot creation

- **OCEAN personality sliders:** Pick a preset like "Curious" or "Grumpy" and the five personality sliders (Openness, Conscientiousness, Extraversion, Agreeableness, Neuroticism) fill in automatically. You can see what makes each character tick.
- **Side-by-side layout:** The create-bot screen now shows personality presets on the left and OCEAN sliders on the right, so everything fits on one page and the Create button never hides behind the controls.
- *(Heads up: custom slider tweaking is preview-only for now — the AI still uses the preset, but we'll wire the sliders through in a future update.)*

### Smarter under the hood

- **MC username is set automatically:** No more fiddling with your owner name in the web dashboard. The mod knows your Minecraft name and tells our server, so your bot recognizes you from the start.
- **New bots remember who owns them:** A bug where freshly created bots had no owner is fixed — the bot will now follow your whispers and commands from day one.
- **Shorter, sharper bot replies:** We cleaned up the AI's instructions. Bots are more direct, waste fewer words, and get back to what they were doing instead of monologuing.
- **Owner-aware behavior:** If you're online, your bot stays near you, follows your lead, and protects you in combat. If you're offline, it survives and progresses on its own.

### Notes

- Your server still needs `online-mode=false` — bots don't have Mojang accounts.
- Your bot's feedback is stored privately and only read by the VoxelMind team.
- If you had issues with bots getting kicked from chat: update to this version and the problem is gone.

---

## 0.2.0-alpha (2026-04-08)

### Singleplayer just works now

- **No more port forwarding!** Open a singleplayer world, spawn your bot — done. No playit.gg, no router settings, no hassle. We built a tunnel that handles everything automatically.
- **One-click spawn:** Hit Spawn and your bot joins your world. The mod opens LAN, connects to our relay, and gets your bot in — all behind the scenes.
- **Delete bots:** You can now delete bots you don't need anymore. The [Delete] button shows up when a bot is offline.
- **Cleaner UI:** Removed the "Open LAN" button and `/vm lan` command — they're no longer needed since everything is automatic.
- **Tunnel status:** The status bar shows whether the tunnel is connected, connecting, or had an error.

### Multiplayer unchanged
- Hosted servers (Aternos, Apex, etc.) still work exactly like before — no tunnel needed.

### Notes
- Your server must have `online-mode=false` (cracked) — bots don't have Mojang accounts
- Singleplayer works out of the box now — just spawn!
- Multiplayer servers work out of the box too

---

## 0.1.0-alpha (2026-04-08)

Initial alpha release.

### Features
- **Login:** Sign in with your VoxelMind account via browser (Discord/Google OAuth)
- **Bot Management:** Create, spawn, despawn, edit and delete AI companions
- **In-Game GUI:** Press V to open the VoxelMind panel with bot list and controls
- **Chat Commands:** /vm login, list, spawn, stop, stopall, status, logout
- **10 Personalities:** Stoic, Anxious, Curious, Cheerful, Grumpy, Competitive, Gentle, Reckless, Methodical, Sarcastic
- **LAN Support:** Auto-opens singleplayer worlds to LAN on a fixed port
- **Translations:** English and German

### Notes
- Your server must have `online-mode=false` (cracked) — bots don't have Mojang accounts
- Hosted servers (Aternos, Apex, etc.) work out of the box
