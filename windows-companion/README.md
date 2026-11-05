# REAPER Remote — Windows 11 Companion

This companion is the Windows-side bridge between the Android app and REAPER. It is designed to run locally on the REAPER PC and be exposed to the phone only through Tailscale.

## Architecture

- `server.py`: local HTTP API, authenticated file/project operations, upload/download endpoints.
- `ReaperRemoteBridge.lua`: install as a persistent ReaScript in REAPER. It polls the local companion for commands and publishes live project/transport state.
- Android app: connects to the companion API (not directly to REAPER Web Control) for project management and render workflows.

## Security model

1. The companion binds to loopback only (`127.0.0.1`).
2. Use Tailscale Serve to expose it privately to your tailnet:
   `tailscale serve --bg 8765 http://127.0.0.1:8765`
3. Set a long random API token in the `REAPER_REMOTE_TOKEN` environment variable on Windows.
4. Do not port-forward this service or REAPER's Web Control port on your router.
5. Keep the Windows account and Tailscale device access secured.

## Setup (development preview)

1. Install Python 3.11+ on Windows 11.
2. Set `REAPER_REMOTE_TOKEN` to a long random secret.
3. Run `python server.py`.
4. In REAPER, use Actions → Show action list → ReaScript: Load and run, then select `ReaperRemoteBridge.lua`.
5. Keep the script running while you use the phone app.
6. Run `tailscale serve --bg 8765 http://127.0.0.1:8765` from an elevated or normal terminal as permitted by your Tailscale setup.

The bridge currently implements project enumeration, activation, opening, closing, transport, and status reporting. Render configuration and live audio streaming are being built as separate stages; do not treat them as available until the Android client and companion report them as supported.

## Project uploads

The API accepts an `.RPP` file or a ZIP archive of a complete project folder. ZIP extraction is restricted to the configured projects directory and rejects path traversal. For portable projects, zip the complete project directory, including its media subfolder.

Default project directory: `%USERPROFILE%\\Documents\\REAPER Projects`. Set `REAPER_REMOTE_PROJECTS` to change it.
