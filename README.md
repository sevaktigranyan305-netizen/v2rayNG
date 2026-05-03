# v2rayVN

**v2rayVN** is an Android VPN client built on a fork of
[v2rayNG](https://github.com/2dust/v2rayNG). It is the client side of the
[v2rayVN fork stack](#the-v2rayvn-fork-stack), which turns VLESS into a real
Layer-3 VPN: the phone gets a virtual IPv4 address on the server's virtual
subnet, `ping` works across the tunnel, host services on the VPS are reachable
through a gateway IP, and multiple phones behind the same server can talk to
each other peer-to-peer.

v2rayVN is an **APK-only** client distribution — we do not publish to Play
Store or F-Droid. Get every release as a signed APK from
[Releases](https://github.com/sevaktigranyan305-netizen/v2rayNG/releases).

> **Repository note.** This repo is named `v2rayNG` (we kept the upstream name
> so submodule / git history diffs stay clean), but the *app itself* is
> renamed. Everywhere outside the repo name — application id, launcher label,
> Android quick-settings tile, about screen — it is **v2rayVN**.

## The v2rayVN fork stack

| Component | Role | Repository |
|---|---|---|
| **Xray-core (fork)** | Server / core with L3 virtualnet (VLESS-as-VPN) | https://github.com/sevaktigranyan305-netizen/Xray-core |
| **3x-ui (fork)** | Admin panel with per-client virtual-IP (`vnetIp`) IPAM | https://github.com/sevaktigranyan305-netizen/3x-ui |
| **AndroidLibXrayLite (fork)** | gomobile bindings producing `libv2ray.aar` | https://github.com/sevaktigranyan305-netizen/AndroidLibXrayLite |
| **v2rayVN (Android client)** | Android VPN app built on top of `libv2ray.aar` | *this repo* |

## What's different from upstream v2rayNG

| | Upstream v2rayNG | v2rayVN |
|---|---|---|
| VLESS / REALITY / XHTTP / XUDP proxying | ✓ | ✓ |
| Per-app routing (`VpnService.addDisallowedApplication`) | ✓ | ✓ |
| Reach server host services through a gateway IP (`curl http://10.0.0.1:port`) | — | ✓ |
| Peer-to-peer traffic between connected clients (`ping 10.0.0.7` from phone A to phone B) | — | ✓ |
| `ping` / ICMP across the VPN | — | ✓ |
| `vless://…&vnetIp=10.0.0.X` QR parsing | — | ✓ |
| Quick-settings tile with paw icon | — | ✓ |
| Package id / launcher name / icon | `com.v2ray.ang` / "v2rayNG" / V-letter | `com.v2rayvn.app` / "v2rayVN" / magenta paw |
| Version lineage | upstream monotonic (`2.x.y`) | reset to `1.0.0` because the Android `applicationId` changed |

Apart from the above, the app behaves exactly like upstream v2rayNG — the
entire upstream settings surface (routing rules, sniffing, subscription
import, backup / restore, DNS, logs) is preserved.

## Install

1. On your Android device, go to **Settings → Apps → Special access → Install
   unknown apps** and allow your browser / file manager to install APKs.
2. Download the right APK for your device from
   [Releases](https://github.com/sevaktigranyan305-netizen/v2rayNG/releases/latest):
   - `v2rayVN_<version>_arm64-v8a.apk` — **most phones made since 2017**.
   - `v2rayVN_<version>_armeabi-v7a.apk` — old 32-bit phones.
   - `v2rayVN_<version>_x86_64.apk` / `v2rayVN_<version>_x86.apk` — emulators,
     WSA, Chromebooks.
   - `v2rayVN_<version>_universal.apk` — works everywhere, ~2× the size.
3. Open the APK and confirm the install prompt. Minimum Android version is
   **7.0 Nougat** (API 24).
4. On first launch, Android will ask for VPN permission. Accept — without it
   the app cannot create a TUN.

Updates: because every release is signed with the same keystore, installing a
newer APK on top of an older one preserves configs and settings. **Do not
uninstall** the old app first, or you will lose them.

### If Android says "Package damaged" / "App not installed"

That happens only when you downloaded an unsigned APK (e.g. from a PR's CI
artefacts, not from the Releases page). Always install from
[Releases](https://github.com/sevaktigranyan305-netizen/v2rayNG/releases/latest)
where every APK is signed.

If the error persists even from Releases, enable **Play Protect → Scan apps
with Play Protect → Off** temporarily, then re-install.

## Configure

### The fast path: QR code from 3x-ui

The companion panel (our 3x-ui fork) generates v2rayVN-compatible share links
out of the box, including a pre-allocated `vnetIp` for the virtual-network
mode.

1. In the 3x-ui panel, open the VLESS inbound, click the client's name, then
   **More info → QR code**.
2. In v2rayVN: tap the `+` button → **Scan QR code** → point the camera at
   the QR on the panel page.
3. The imported server appears in the list. Long-press it → **Connect**.

If the inbound has `virtualNetwork.enabled = true` on the panel side, the QR
already carries `vnet=1`, `vnetSubnet=…` and `vnetIp=10.0.0.X`. v2rayVN
detects those and switches to VPN mode automatically — no extra toggles.

### Manual link import

Paste a `vless://` link (URL-encoded) via `+` → **Import config from
clipboard**. The parser accepts four fork-specific extensions on top of the
stock VLESS query string:

| Param | Meaning |
|---|---|
| `vnet=1` | Enable virtualNetwork on the outbound. |
| `vnetSubnet=10.0.0.0%2F24` | Virtual subnet (URL-encode the `/` as `%2F`). |
| `vnetIp=10.0.0.2` | Pre-allocated virtual IP the client must bind on the TUN. |
| `vnetDefaultRoute=1` | `1` = all traffic through VPN, `0` = split-tunnel (only `vnetSubnet` routed through TUN). Default `1`. |

A full link looks like:

```
vless://<uuid>@vps.example.com:443?type=tcp&security=reality&pbk=…&fp=chrome&sni=…&sid=…&spx=%2F&vnet=1&vnetSubnet=10.0.0.0%2F24&vnetDefaultRoute=1&vnetIp=10.0.0.2#vps
```

Stock v2rayNG installs (and our v2rayVN running in non-VPN mode) silently
ignore the `vnet*` params and connect as a plain proxy.

### What the L3 VPN mode looks like on the device

When `vnet=1` is active, v2rayVN:

1. Asks Android for VPN permission (system dialog, granted once per install).
2. Creates a `VpnService` TUN and binds it to the `vnetIp` (e.g. `10.0.0.2`)
   with the gateway being the first usable address of `vnetSubnet`
   (`10.0.0.1` for `10.0.0.0/24`).
3. Starts xray-core with the TUN file descriptor passed through the
   `xray.tun.fd` env variable; xray adopts it and drives packet I/O directly.
4. **Does not** start a userspace tun2socks — the whole TUN is owned by
   xray-core's L3 device backend.

From your phone you can then:

```
ping 10.0.0.1              # the server's virtual-network gateway
curl http://10.0.0.1:8080  # a service the VPS runs on 0.0.0.0:8080
ping 10.0.0.7              # another phone on the same virtual subnet
```

This all works without any manual routing-table tweaks on the phone.

## Per-app proxy

Per-app proxying filters traffic by Android UID *before* it enters the TUN.
It works identically to upstream v2rayNG on top of our L3 VPN.

Open **Settings → Per-app proxy**:

1. Flip **Enable per-app** (the leftmost toggle — without it, the mode is
   silently disabled regardless of the toggles below).
2. Flip **Bypass mode**:
   - **On** ("Bypass apps"): selected apps go **direct**, everything else
     goes through the VPN. Useful for banking / CDN apps.
   - **Off** ("Proxy-only apps"): **only** selected apps go through the VPN,
     everything else goes direct.
3. Tick the apps you care about.
4. Reconnect the VPN — the per-app filter is applied at TUN-creation time.
   The app disconnects the VPN for you automatically when you change these
   settings.

> Heads-up: the app whose name starts with `com.v2rayvn.app` is **always**
> excluded from its own VPN (added to `addDisallowedApplication`) to avoid a
> loop where xray-core's own control sockets go through the tunnel it runs.

## Quick-settings tile

v2rayVN ships a tile service so you can add **Switch-v2rayVN** to the Android
quick-settings panel (the shade above the notifications where Wi-Fi /
Bluetooth toggles live). The tile icon is the v2rayVN paw; Android tints it
automatically for light / dark themes.

To add it: pull down the quick-settings panel fully, tap the pencil / "Edit"
button, and drag **Switch-v2rayVN** into the active tiles.

Tapping the tile toggles the currently-selected VLESS profile on/off without
opening the app.

## Default routing preferences

The fork changes one upstream default:

- **Settings → Does VPN bypass LAN** defaults to **"Not Bypass"** (upstream
  defaulted to "Bypass"). Rationale: our virtual subnet is `10.0.0.0/24` which
  sits inside the RFC1918 range upstream's bypass rule excludes; "Not Bypass"
  prevents the OS more-specific-route heuristic from getting anywhere near
  our own subnet.
- You can still flip this back to "Bypass" (or "Follow Config") if you need
  local Wi-Fi services (printer, NAS, LAN game) to stay reachable while the
  VPN is up.

## Troubleshooting

### "Connected" but no internet

1. Open **Logs** (drawer menu) — look for `StartCore-VPN: per-app config:` to
   confirm per-app toggles matched your intent (`enabled=true`, right list
   size).
2. Look for `StartCore-L3: …` lines — they print the assigned vnetIp and the
   gateway on every VPN start. If `assignedIp` does **not** match `vnetIp=`
   from your QR, the server's IPAM reassigned you to a different address
   (happened because the old link expired). Re-generate the QR from 3x-ui
   and re-import.
3. Try `curl http://10.0.0.1:<panel-port>` from an SSH session on the VPS —
   this rules out a server-side firewall blocking the gateway IP.

### Per-app proxy ignored

Most common cause: the leftmost **Enable per-app** toggle is off. The other
two (Bypass mode + the app checklist) mean nothing without it. The logs will
say `enabled=false` in this case.

Second most common: the app is a *browser that uses its own VPN-aware
networking stack* (Firefox's DoH, Chrome's Secure DNS). Disable that in the
browser — otherwise Chrome resolves DNS through DoH that bypasses the VPN
even if its TCP traffic goes through.

### VPN drops right after "Connected"

Check **Settings → Battery → v2rayVN → Unrestricted**. Without this, Android
kills the foreground service under Doze mode after 10-30 minutes.

### Install fails with "App not installed"

You have an older v2rayVN installed that was signed with a different keystore
(e.g. a debug-built APK, or an artefact from a CI build before we set up the
release keystore). Uninstall the old app, then install the new one. **You
will lose your configs** — export them first via **Settings → Backup**.

## Building from source

```bash
git clone --recursive \
    https://github.com/sevaktigranyan305-netizen/v2rayNG.git
cd v2rayNG

# Build libv2ray.aar from the AndroidLibXrayLite submodule (pinned to the
# fork). The same script the CI runs.
pushd AndroidLibXrayLite
mkdir -p assets data
bash gen_assets.sh download
cp -v data/*.dat assets/
go install golang.org/x/mobile/cmd/gomobile@latest
export PATH=$PATH:$(go env GOPATH)/bin
gomobile init
go mod tidy
gomobile bind -v -androidapi 24 -trimpath -ldflags='-s -w -buildid=' ./
popd
mkdir -p V2rayNG/app/libs
cp -v AndroidLibXrayLite/libv2ray.aar V2rayNG/app/libs/libv2ray.aar

# Build libhevtun (old-style tun2socks fallback, still bundled so upstream
# non-L3 profiles keep working).
bash compile-hevtun.sh
cp -r libs V2rayNG/app

# Build the APKs.
cd V2rayNG
./gradlew assemblePlaystoreRelease assembleFdroidRelease
```

Prerequisites (JDK 21, Android SDK API 36+, NDK 28.2.13676358, Go 1.26+) are
listed in the [AndroidLibXrayLite
README](https://github.com/sevaktigranyan305-netizen/AndroidLibXrayLite#build-requirements).

CI (`.github/workflows/build.yml`) automates all of the above on every push
and on `v*` tag push — tag pushes additionally publish a GitHub Release with
the signed APKs attached as assets.

### Signing keys

CI signs release APKs with four repository secrets:

| Secret | Meaning |
|---|---|
| `APP_KEYSTORE_BASE64` | `base64 -w0` of the release keystore (JKS or PKCS12). |
| `APP_KEYSTORE_PASSWORD` | Store password. |
| `APP_KEY_PASSWORD` | Key password. For PKCS12 keystores this must equal `APP_KEYSTORE_PASSWORD` — PKCS12 doesn't support separate passwords and keytool silently ignores any mismatch. |
| `APP_KEYSTORE_ALIAS` | Key alias. |

Without these four secrets the CI still builds, but the resulting APKs are
unsigned (and so Android refuses to install them with "package damaged").

To cut a signed release on your own fork, generate your own keystore:

```bash
keytool -genkeypair -v -keystore v2rayvn-release.jks -keyalg RSA \
    -keysize 4096 -validity 36500 -storetype JKS \
    -alias v2rayvn \
    -dname "CN=v2rayVN, OU=v2rayVN, O=v2rayVN, L=Internet, ST=Internet, C=RU" \
    -storepass 'CHANGE-ME' -keypass 'CHANGE-ME'
base64 -w0 v2rayvn-release.jks > v2rayvn-release.b64
```

Upload the four secrets at
`https://github.com/<you>/<v2rayVN-repo>/settings/secrets/actions`, then push
a `v*` tag.

### Release process

```bash
# bump versionName in V2rayNG/app/build.gradle.kts, commit, merge to master

git tag vX.Y.Z
git push origin vX.Y.Z
# CI runs, produces 5 signed APK variants, publishes a GitHub Release
# marked "latest".

# To republish a tag with updated APKs (e.g. after a CI fix):
git tag -f vX.Y.Z
git push -f origin vX.Y.Z
```

Force-pushing a tag is only meaningful before users start installing from
that version — do not do this after the release is public or user updates
will refuse to apply ("signature mismatch" style errors; in our case since
the keystore is stable it still works, but you lose audit trail).

## Credits

- Upstream [2dust/v2rayNG](https://github.com/2dust/v2rayNG) — everything in
  the Android project except the fork-specific bits
  (`applicationId` / branding, `virtualNetwork` config parsing, `vnetIp` URL
  parsing, L3 `VpnService.Builder` wiring, per-app config diagnostic logs,
  paw icon, QS tile icon) is upstream v2rayNG, GPL-3.0.
- [XTLS/Xray-core](https://github.com/XTLS/Xray-core) — the upstream of the
  core fork we pin through the AndroidLibXrayLite submodule.
- [2dust/AndroidLibXrayLite](https://github.com/2dust/AndroidLibXrayLite) —
  the upstream of our gomobile binding fork.

## License

Same license as upstream v2rayNG — [GPL-3.0](./LICENSE).
