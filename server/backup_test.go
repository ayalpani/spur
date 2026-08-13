package main

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestEncryptedBackupIsRestorableAndRetainsSevenDays(t *testing.T) {
	directory := t.TempDir()
	signer, err := newTestSigner()
	if err != nil {
		t.Fatal(err)
	}
	itemStore, err := openStore(filepath.Join(directory, "items.db"), signer)
	if err != nil {
		t.Fatal(err)
	}
	dropTestItem(t, itemStore, testItemID, testSecret(1))
	itemStore.close()

	for day := 1; day <= 9; day++ {
		if err := runEncryptedBackup(
			directory,
			time.Date(2026, 8, day, 12, 0, 0, 0, time.UTC),
		); err != nil {
			t.Fatalf("backup day %d: %v", day, err)
		}
	}
	matches, _ := filepath.Glob(filepath.Join(directory, "backups", "items-*.db.aesgcm"))
	if len(matches) != 7 {
		t.Fatalf("expected seven backups, got %d", len(matches))
	}
	if filepath.Base(matches[0]) != "items-2026-08-03.db.aesgcm" {
		t.Fatalf("old backups were not removed: %v", matches)
	}

	key, _ := os.ReadFile(filepath.Join(directory, "backup-key"))
	encrypted, _ := os.ReadFile(matches[len(matches)-1])
	cleartext, err := decryptBackup(encrypted, key)
	if err != nil {
		t.Fatal(err)
	}
	if !bytes.HasPrefix(cleartext, []byte("SQLite format 3")) {
		t.Fatal("decrypted backup is not a SQLite database")
	}
}
