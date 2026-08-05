# Spur backup

Spur writes one versioned `.spurbackup` archive through Android's Storage
Access Framework. The user chooses a directory once; Spur does not own Drive,
Box, OneDrive, Dropbox, Nextcloud, or WebDAV credentials. Installed Android
document providers decide which local and cloud destinations appear.

## Included data

- the consistent `spur.db` tour database;
- stable preferences for Home, manual location, map settings, moments, photo
  places, and recent emojis;
- original photo, video, and voice moment files referenced by moment metadata.

Generated tour previews, video thumbnails, road traversal caches, pending
completion state, Home automation runtime diagnostics, and the device-local SAF
directory grant are excluded. They can be regenerated or are invalid on another
device.

## Archive and restore rules

Format version 1 is a ZIP container with a manifest, the database, one typed
preference document, original media, and SHA-256 checksums. Restore rejects
unknown entries, path traversal, oversized content, checksum mismatches,
unexpected database schemas, and corrupt SQLite files before changing current
data.

Restore is replace-only and unavailable during an active tour. Spur stages and
validates the complete archive, keeps rollback copies of the current database,
preferences, and media, then swaps them. A failed swap restores the previous
state. A restored Home automation setting is re-armed only when the device still
has the required permissions.

Automatic backup uses one delayed WorkManager job after a completed tour. Each
successful write creates a new document before deleting the previous successful
backup, so an interrupted provider write cannot destroy the last good copy.
