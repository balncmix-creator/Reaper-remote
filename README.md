# REAPER Remote

A lightweight Android remote for REAPER's built-in Web Control interface.

## Current version

The initial app provides transport controls, marker navigation, and a basic connection test. It is an early build; test the command mappings on your REAPER setup before relying on it in a session.

## Build

GitHub Actions builds a debug APK on every push to `main` and on manual dispatch. Open the **Actions** tab, select **Build Android APK**, then download the `REAPER-Remote-debug` artifact from a successful run.

## Setup

1. In REAPER, open Preferences → Control/OSC/web → Add → Web browser interface.
2. Note the Web Control port (default commonly 8080).
3. Install Tailscale on the Windows PC and Android phone and sign in to the same tailnet.
4. In the app, enter the PC's Tailscale IP and Web Control port, then tap Connect.

**Security:** Do not forward the REAPER Web Control port on your router or expose it to the public internet. Use a private VPN such as Tailscale.

## Controls

Play, pause, stop, record, go to project start, previous/next marker, and transport status polling.
