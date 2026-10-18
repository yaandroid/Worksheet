# BRAHM Office — Web / VPS version

The same BRAHM Office engine (PDF reader/editor, Excel, Word/PPT view+edit, convert hub)
running in any browser. Reuses the exact web engine from the Android/desktop app; file open
uses the browser file picker and save/share uses browser download (or the Web Share API).

## Run on a VPS (one command, self-contained)
The whole app is embedded inside `install.sh` (no internet/npm needed on the server; just Node.js or Python 3).

```bash
bash install.sh              # install to ~/brahm-office-web, start on port 8080
PORT=9000 bash install.sh    # choose a port
bash install.sh --service    # also install a systemd service (auto-restart, survives reboot)
```
Then open `http://<server-ip>:<port>`.

## Local dev
```bash
node build-web.js            # copy engine into web/public + inject the web file-bridge
node server.js 8080          # serve public/ on a port
```

## Rebuild the installer after engine changes
```bash
bash build.sh                # rebuilds public/ and regenerates install.sh
```

Brahm InnerBrain Technologies Pvt. Ltd. — Developed by Sandeep Kushwah
