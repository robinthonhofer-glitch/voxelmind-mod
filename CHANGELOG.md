# Changelog

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
