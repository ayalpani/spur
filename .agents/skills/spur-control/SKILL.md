---
name: spur-control
description: Control the Spur Android app on Arash's registered Galaxy A54 over Wi-Fi ADB. Use for requests such as "Starte Spur", "Zeig's mir", "Spiel das raus", "Spiel diese Version aufs Handy", "Fahre Spur runter", "Öffne Spur", "Starte Spur neu", deployment of the current thread or worktree to the phone, app lifecycle control, installation status, or evolving the project's device-control rules.
---

# Spur Control

Use `scripts/spurctl` as the control plane. Run the copy bundled with the
current checkout. From anywhere inside a Spur worktree, it resolves and builds
that worktree rather than the checkout containing the script.

## Commands

| User intent | Command |
| --- | --- |
| “Starte Spur” / “Zeig's mir” / “Spiel das raus” | `scripts/spurctl start` |
| Build only | `scripts/spurctl build` |
| Install latest build without opening | `scripts/spurctl install` |
| Open the installed app without rebuilding | `scripts/spurctl launch` |
| Stop / shut down Spur | `scripts/spurctl stop` |
| Restart without rebuilding | `scripts/spurctl restart` |
| Inspect connection, package, and process | `scripts/spurctl status` |

Treat “Starte Spur”, “Zeig's mir”, and “Spiel das raus” as permission to build
the current worktree, replace the installed Spur build, and launch it on the
registered phone. A phone preview includes uncommitted files and requires
neither a commit nor a push. Report the source worktree, device, and final
state.

## Feature validation on the device

For a deployed feature, use the real device to check the applicable interaction
paths from the mandatory completion quality gate in `spur-change-workflow`.
Prioritize:

- changed entry points and primary interaction;
- visible back/up controls and Android system back;
- dismiss, cancel, repeated open/close, and rapid interaction;
- permission prompts and denial when the change touches permissions;
- app background/foreground or restart when lifecycle or persistence changed;
- visual result and any obvious crash, freeze, stale state, or private-data
  exposure.

Do not capture or retain unrelated private device content. Stop recording and
delete the artifact immediately if another app or unrelated content appears.
Deployment or launch success alone never means the feature is complete. Report
which device checks passed and which could not be performed.

## Registered device

- Device: Samsung Galaxy A54, model `SM_A546B`
- Transport: Wi-Fi ADB
- Static Wi-Fi IP: `192.168.178.162`
- Application ID: `app.spur`
- Launcher: `app.spur/.MainActivity`

Arash has confirmed that the device IP does not change. Prefer an already-online
transport for the registered model. Otherwise use ADB mDNS discovery. The
Wi-Fi debugging port changes and must never be committed to this repository.
If ADB's own mDNS listing is empty, resolve the registered device's existing
`_adb-tls-connect` service through macOS DNS-SD and connect its current port at
the static IP automatically. Do not ask Arash to read out the ordinary changing
connection port while this paired service can be resolved.
For the exceptional case where automatic discovery fails, accept a temporary
override such as `SPUR_ADB_ENDPOINT=192.168.178.162:<port> scripts/spurctl status`.

On this Galaxy A54 running Android 16, the system has been observed disabling
wireless ADB after a Wi-Fi network-change event, even without a reboot or a
manual toggle. A static IP does not prevent this. When the ADB listener is off,
the phone must enable Wireless debugging again before host-side discovery can
recover.

## Durable rules

- After completing and checking a requested Spur code or UI change, deploy the
  current worktree to the registered phone and launch it by default. Skip this
  automatic deployment only when Arash explicitly asks not to deploy, requests
  analysis without implementation, or the device is unreachable.
- Keep exactly one Spur installation. Do not create `.debug` or other parallel application IDs.
- Let the most recently deployed thread replace the prior thread's build on the
  phone. Never merge other branches into a preview.
- Preserve every other thread in its own worktree, branch, and pull request;
  replacing its installed build is not permission to alter its code.
- Remove legacy `app.spur.debug` when installing.
- Preserve `app.spur` data when an in-place update works.
- If Android reports an incompatible signature, uninstall `app.spur` and retry. Arash has explicitly accepted losing local Spur data during this development phase.
- Never uninstall unrelated packages or target a device that does not match the registered model.
- If the phone is unreachable, state that Wi-Fi debugging must be enabled and stop; do not silently switch to another device.

## Evolving the control plane

When Arash establishes a recurring Spur-control preference, update this skill and, when execution behavior changes, `scripts/spurctl`. Keep device-specific facts here and deterministic operations in the script.
