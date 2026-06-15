# SDRTrunk RadioResolve Agent Notes

This workspace is the local SDRTrunk fork used for RadioResolve integration and receiver-node deployments.

## Golden Rules

- Do not launch SDRTrunk hidden, detached, or without a visible console window unless the user explicitly asks.
- On macOS, launch SDRTrunk in Terminal with `osascript` so the console remains visible.
- On Windows nodes, launch SDRTrunk in an interactive console window, for example through `cmd.exe /k` in the logged-in desktop session or an interactive scheduled task.
- Never print or paste RadioResolve API keys in final answers, logs, docs, release notes, or screenshots.
- Keep upstreamable RadioResolve Streaming-tab changes isolated under the standard broadcast/streaming provider path.
- Keep node-specific/custom features separate from the upstreamable streaming provider.
- Do not revert unrelated local changes. This repo often has uncommitted work.

## Important Nodes

### Mac Node

- Host/user: local macOS machine.
- SDRTrunk app root: `/Users/owner/SDRTrunk`
- Playlist: `/Users/owner/SDRTrunk/playlist/default.xml`
- App log: `/Users/owner/SDRTrunk/logs/sdrtrunk_app.log`
- Installed build: `/Users/owner/Documents/SDRTrunk/build/install/sdr-trunk`
- Installed jar directory: `/Users/owner/Documents/SDRTrunk/build/install/sdr-trunk/lib`
- Java: `/Users/owner/Library/Java/JavaVirtualMachines/jdk-25.0.1-full.jdk/bin/java`
- Current Tailscale IP observed for the Mac: `100.93.207.84`

### CUBI / WILLOWICK Node

- SSH host alias: `CUBI`
- Windows app root: `C:\Users\Tyler\SDRTrunk`
- Playlist: `C:\Users\Tyler\SDRTrunk\playlist\default.xml`
- App log: `C:\Users\Tyler\SDRTrunk\logs\sdrtrunk_app.log`
- Installed build: `C:\Users\Tyler\Desktop\sdr-trunk-windows-x86_64-vnightly`
- Installed jar directory: `C:\Users\Tyler\Desktop\sdr-trunk-windows-x86_64-vnightly\lib`
- Current Tailscale IP observed for CUBI: `100.100.39.44`

### Calls Server

- SSH host alias: `rr-calls`
- Tailscale IP: `100.89.194.96`
- Caddy config: `/etc/caddy/Caddyfile`
- Caddy access log: `/var/log/caddy/calls_access.log`
- Receiver upload API service: `radioresolve-node-upload-api.service`
- Intake service: `radioresolve-receiver-call-intake.service`
- Upload API listens locally on `127.0.0.1:8095`
- Caddy should listen on `100.89.194.96:8080` and reverse proxy `/api/node/*` to `127.0.0.1:8095`
- Receiver nodes should use `http://100.89.194.96:8080` as the RadioResolve calls server URL.

Tailscale encrypts this private traffic. HTTP over the Tailscale IP avoids Java TLS certificate-name problems for a raw IP address.

## Build Commands

Use Gradle from the repo root:

```bash
./gradlew compileJava
./gradlew test --tests 'io.github.dsheirer.audio.broadcast.radioresolve.*'
./gradlew jar -x test
```

Primary built jar:

```text
build/libs/sdr-trunk-0.6.2-beta-1-radioresolve-1.jar
```

The exact jar name may change with project version. Check `build/libs` after building.

## RadioResolve Streaming Provider

The upstreamable completed-call upload integration lives here:

- `src/main/java/io/github/dsheirer/audio/broadcast/radioresolve/RadioResolveBroadcaster.java`
- `src/main/java/io/github/dsheirer/audio/broadcast/radioresolve/RadioResolveBuilder.java`
- `src/main/java/io/github/dsheirer/audio/broadcast/radioresolve/RadioResolveConfiguration.java`
- `src/main/java/io/github/dsheirer/gui/playlist/streaming/RadioResolveEditor.java`

Current expected behavior:

- Uses the existing Streaming tab model.
- Uploads completed MP3 calls with multipart form fields.
- Uses HTTP/1.1 for call uploads.
- Streams the MP3 from disk with `BodyPublishers.ofFile()`.
- Limits RadioResolve uploads to 4 in-flight requests per stream.
- Keeps at most 500 queued pending recordings per stream.
- Retries temporary failures: `408`, `429`, `500`, `502`, `503`, `504`, connection failures, and timeouts.
- Treats `401/403` as credential/config failures.
- Uses the stream's `maximum_recording_age` as the retry/age-off window.

Do not move RadioResolve call-upload behavior into SDRTrunk core unless explicitly requested.

## Custom RadioResolve Features

Non-upstream/custom areas include:

- RadioResolve Preferences/node services.
- RF telemetry service.
- Node check-ins.
- Remote commands.
- Time/clock checks.
- Doctor/diagnostics.
- Now Playing control-channel ordering.
- Mute preference persistence.

Keep these separate from the standard Streaming-tab call upload provider.

## Updating Playlists

The RadioResolve stream is stored in SDRTrunk playlist XML as a stream entry. The `host` attribute is the full URL.

Current preferred URL:

```text
http://100.89.194.96:8080
```

Before editing a playlist, stop SDRTrunk so it does not overwrite the XML on shutdown.

Always back up the playlist first:

```bash
cp /Users/owner/SDRTrunk/playlist/default.xml /Users/owner/SDRTrunk/playlist/default.xml.bak-radioresolve-$(date +%Y%m%d-%H%M%S)
```

For CUBI, use PowerShell and avoid exposing API keys in command output.

## Deploying To The Mac Node

1. Build the jar:

```bash
./gradlew jar -x test
```

2. Stop the running macOS SDRTrunk process.

3. Back up old installed jars out of the wildcard classpath:

```bash
LIB=/Users/owner/Documents/SDRTrunk/build/install/sdr-trunk/lib
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p "$LIB/backup-radioresolve-$TS"
mv "$LIB"/sdr-trunk-*.jar "$LIB/backup-radioresolve-$TS"/
cp build/libs/sdr-trunk-0.6.2-beta-1-radioresolve-1.jar "$LIB"/
```

4. Launch in a visible Terminal window:

```bash
osascript <<'OSA'
tell application "Terminal"
    activate
    do script "cd /Users/owner/Documents/SDRTrunk && /Users/owner/Library/Java/JavaVirtualMachines/jdk-25.0.1-full.jdk/bin/java --add-exports=javafx.base/com.sun.javafx.event=ALL-UNNAMED --add-exports=java.desktop/com.sun.java.swing.plaf.windows=ALL-UNNAMED --add-modules=jdk.incubator.vector --enable-preview --enable-native-access=ALL-UNNAMED --enable-native-access=javafx.graphics --sun-misc-unsafe-memory-access=allow -XX:+UseCompactObjectHeaders -classpath '/Users/owner/Documents/SDRTrunk/build/install/sdr-trunk/lib/*' io.github.dsheirer.gui.SDRTrunk"
end tell
OSA
```

## Deploying To CUBI

1. Build the jar locally:

```bash
./gradlew jar -x test
```

2. Stop SDRTrunk on CUBI:

```bash
ssh CUBI 'powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force"'
```

3. Copy the jar:

```bash
scp build/libs/sdr-trunk-0.6.2-beta-1-radioresolve-1.jar CUBI:/C:/Users/Tyler/Desktop/sdr-trunk-windows-x86_64-vnightly/lib/sdr-trunk-0.6.2-beta-1-radioresolve-1.jar
```

4. Back up old jars out of the wildcard classpath on CUBI:

```powershell
$lib = "C:\Users\Tyler\Desktop\sdr-trunk-windows-x86_64-vnightly\lib"
$ts = Get-Date -Format yyyyMMdd-HHmmss
$bak = Join-Path $lib "backup-radioresolve-$ts"
New-Item -ItemType Directory -Path $bak | Out-Null
Get-ChildItem $lib -Filter "sdr-trunk-*.jar" |
  Where-Object { $_.Name -ne "sdr-trunk-0.6.2-beta-1-radioresolve-1.jar" } |
  Move-Item -Destination $bak
```

5. Start SDRTrunk in an interactive console window. From SSH, use an interactive scheduled task if needed:

```powershell
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force
$taskName = "SDRTrunk Console Launch"
$action = New-ScheduledTaskAction -Execute "cmd.exe" -Argument "/k cd /d C:\Users\Tyler\Desktop\sdr-trunk-windows-x86_64-vnightly && bin\sdr-trunk.bat"
$trigger = New-ScheduledTaskTrigger -Once -At ((Get-Date).AddMinutes(1))
$principal = New-ScheduledTaskPrincipal -UserId $env:USERNAME -LogonType Interactive -RunLevel Limited
Register-ScheduledTask -TaskName $taskName -Action $action -Trigger $trigger -Principal $principal -Force | Out-Null
Start-ScheduledTask -TaskName $taskName
```

If there is no logged-in interactive desktop session, the task may start headless or fail to show a window. Tell the user if that happens.

## Verifying Uploads

On the calls server:

```bash
tail -f /var/log/caddy/calls_access.log
```

Expected uploads:

- Mac source IP: `100.93.207.84`
- CUBI source IP: `100.100.39.44`
- Host: `100.89.194.96:8080`
- Upload endpoint: `/api/node/upload-call`
- RF telemetry endpoint: `/api/node/rf-state`
- Successful upload status: `201`
- Successful telemetry status: `202`

On node app logs:

```bash
tail -f /Users/owner/SDRTrunk/logs/sdrtrunk_app.log
```

On CUBI:

```powershell
Get-Content C:\Users\Tyler\SDRTrunk\logs\sdrtrunk_app.log -Tail 200
```

Look for:

- `SDRTrunk Version`
- `Loading playlist`
- `RadioResolve upload failed`
- `Status Code`
- `INVALID_CREDENTIALS` or invalid API key logs

## Caddy/Tailscale Setup

The calls server Caddyfile should include this bind line:

```caddy
bind 127.0.0.1 192.168.64.85 100.89.194.96
```

Because Caddy has `admin off`, `systemctl reload caddy` may fail. Use:

```bash
sudo caddy validate --config /etc/caddy/Caddyfile
sudo systemctl restart caddy
```

Verify listeners:

```bash
ss -ltnp | grep 8080
```

From a node:

```bash
curl -sS -o /tmp/rr-test.out -w 'status=%{http_code}\n' http://100.89.194.96:8080/api/node/test
```

Without an API key, `401` is expected and proves routing works.

## CUBI Wedge Debug Context

The CUBI/LAKECO failure mode previously observed:

- Tuner/waterfall still appears active.
- Control channel remains yellow/control.
- Messages log stops.
- Voice grants stop creating now-playing voice channels.
- Channel spectrum can freeze with an old static image.
- Stop/start of the channel makes it work again.

Debug changes previously used:

- `MultiFrequencyTunerChannelSource` logs control-channel rotation attempts/success/failure.
- Rotation failure restores the previous inner source instead of leaving the chain with `innerSource:null`.
- `ChannelProcessingManager` includes multi-frequency source diagnostic state in processing reports.
- `DiagnosticMonitor` can generate reports from trigger files:
  - `C:\Users\Tyler\SDRTrunk\generate_processing_diagnostic_report.trigger`
  - `C:\Users\Tyler\SDRTrunk\generate_thread_dump.trigger`

Known captured pattern:

- SDRTrunk tried rotating from the current control frequency to an alternate.
- It failed to source the new frequency.
- The debug patch restored the previous control-channel source.

Keep debug builds separate from release builds. Do not publish debug builds unless explicitly requested.

## Publishing / Releases

Before publishing a user-facing release:

1. Confirm whether the build is release or debug.
2. Ensure debug-only rotation logging or diagnostic hacks are not included unless requested.
3. Run focused tests:

```bash
./gradlew test --tests 'io.github.dsheirer.audio.broadcast.radioresolve.*'
```

4. Build release artifacts:

```bash
./gradlew jar -x test
```

5. Use clear artifact names that distinguish:

- release RadioResolve build
- CUBI wedge debug build
- older backups

Do not publish a debug build to GitHub unless the user explicitly asks.

## Useful Remote Commands

Check CUBI Java:

```bash
ssh CUBI 'powershell -NoProfile -Command "Get-Process java -ErrorAction SilentlyContinue | Format-List Id,ProcessName,Path"'
```

Check CUBI playlist URL without printing API keys:

```bash
ssh CUBI 'powershell -NoProfile -Command "$text=Get-Content C:\Users\Tyler\SDRTrunk\playlist\default.xml -Raw; if ($text.Contains(\"host=`\"http://100.89.194.96:8080`\"\")) { \"playlist_url=ok\" } else { \"playlist_url=missing\" }"'
```

Check calls server upload API:

```bash
ssh rr-calls 'systemctl status radioresolve-node-upload-api.service --no-pager | sed -n "1,80p"'
ssh rr-calls 'systemctl status radioresolve-receiver-call-intake.service --no-pager | sed -n "1,80p"'
```

Check DB queue counts on the calls server:

```bash
ssh rr-calls 'cd /home/owner/radioresolve-node-intake && PYTHONPATH=/home/owner/radioresolve-node-intake:/home/owner/.local/lib/python3.13/site-packages python3 - <<'"'"'PY'"'"'
from app.common.config import load_env
from app.common.db import db_connect
env = load_env()
with db_connect(env) as conn:
    cur = conn.cursor()
    cur.execute("SELECT status, COUNT_BIG(*) c FROM dbo.app_receiver_call_upload_queue GROUP BY status ORDER BY status")
    print([dict(r) for r in cur.fetchall()])
PY'
```
