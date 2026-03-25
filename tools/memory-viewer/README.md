# Ambient Memory Viewer

Pull the latest on-device Room database (and the captured images) and view them locally in your browser.

## 1) Setup

This expects:
- `adb` installed and the device authorized
- a debuggable build so `adb exec-out run-as <pkg>` works
- `python3` available for the local server

From the **repository root** (so `tools/memory-viewer/...` exists):

```bash
chmod +x tools/memory-viewer/pull_and_view.sh
```

If you are already inside `tools/memory-viewer/`, run `./pull_and_view.sh` (not `tools/memory-viewer/...`).

## 2) Pull + view

From repo root:

```bash
./tools/memory-viewer/pull_and_view.sh
```

Or from `tools/memory-viewer/`:

```bash
./pull_and_view.sh
```

Optionally set:
- `DEVICE_SERIAL=...` (if you have multiple devices)
- `OPEN_BROWSER=false` (if you only want the local server)
- `PORT=8008` (or another free port; the script frees the port when possible and may pick `PORT+1`…`PORT+5` if needed)

If you see **Address already in use**, install/use `lsof` (macOS has it) or set `PORT=` to a free port.

Open the shown URL (default `http://127.0.0.1:8008/`).

## What you get

- Clickable 3D helix scatter of inferred events (activity color + scene confidence glow)
- Right panel shows a selected event: `what / how / why / activity / where`
- Session grouping: scrub/jump by `timeline_sessions`, with a per-session time scrubber
- Filters: Activity + Where

