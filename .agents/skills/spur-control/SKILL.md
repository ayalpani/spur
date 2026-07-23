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

## Registered device

- Device: Samsung Galaxy A54, model `SM_A546B`
- Transport: Wi-Fi ADB
- Last known endpoint: `192.168.178.162:33439`
- Application ID: `app.spur`
- Launcher: `app.spur/.MainActivity`

Prefer an already-online transport for the registered model. Otherwise try the last endpoint, then ADB mDNS discovery. A Wi-Fi debugging port can change; treat the endpoint as a hint, not identity.

## Durable rules

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
