---
name: spur-control
description: Control the Spur Android app on the registered test phone over Wi-Fi ADB. Use for requests such as "Starte Spur", "Zeig's mir", "Spiel das raus", "Spiel diese Version aufs Handy", "Fahre Spur runter", "Öffne Spur", "Starte Spur neu", deployment of the current thread or worktree to the phone, app lifecycle control, installation status, or evolving the project's device-control rules.
---

# Spur Control

Use `scripts/spurctl` from the repository or worktree root as the control
plane. This stable wrapper delegates to the implementation bundled with the
local `spur-control` skill. The implementation resolves and builds the current
worktree rather than another checkout.

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

- Transport: Wi-Fi ADB
- Application ID: `app.spur`
- Launcher: `app.spur/.MainActivity`
- Device model, static Wi-Fi IP, and paired ADB service name live in
  `.agents/skills/spur-control/device.local.env` in the main checkout. Git
  ignores that file; `device.example.env` shows its keys. Never commit these
  values, an IP, a serial number, or a connection port.

Prefer an already-online transport for the registered model. Otherwise use ADB
mDNS discovery. If ADB's own mDNS listing is empty, resolve the configured
`_adb-tls-connect` service through macOS DNS-SD and connect its current port at
the configured static IP automatically. Do not ask the user to read out the
changing connection port while this paired service can be resolved.
For the exceptional case where automatic discovery fails, accept a temporary
override such as `SPUR_ADB_ENDPOINT=<ip>:<port> scripts/spurctl status`.

On Android 16, the system has been observed disabling wireless ADB after a
Wi-Fi network-change event, even without a reboot or a manual toggle. When the
ADB listener is off, the phone must enable Wireless debugging again before
host-side discovery can recover.

## Durable rules

- After completing and checking a requested Spur code or UI change, deploy the
  current worktree to the registered phone and launch it by default. Skip this
  automatic deployment only when the user explicitly asks not to deploy, requests
  analysis without implementation, or the device is unreachable.
- Keep exactly one Spur installation. Do not create `.debug` or other parallel application IDs.
- Let the most recently deployed thread replace the prior thread's build on the
  phone. Never merge other branches into a preview.
- Preserve every other thread in its own worktree, branch, and pull request;
  replacing its installed build is not permission to alter its code.
- Do not uninstall or clear either `app.spur` or the legacy `app.spur.debug`
  package as part of deployment. Existing local tours and media must survive.
- Install `app.spur` only as an in-place update with `adb install -r`.
- If Android reports an incompatible signature or another failure that would
  require uninstalling or clearing data, stop and report it. Never retry with a
  destructive fallback.
- Never uninstall unrelated packages or target a device that does not match the registered model.
- If the phone is unreachable, state that Wi-Fi debugging must be enabled and stop; do not silently switch to another device.

## Evolving the control plane

When the user establishes a recurring Spur-control preference, update this skill
and, when execution behavior changes, the implementation at
`.agents/skills/spur-control/scripts/spurctl`. Keep the public command at
`scripts/spurctl`, device-specific facts here, and deterministic operations in
the implementation.
