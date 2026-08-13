package main

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"database/sql"
	"errors"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"time"

	_ "modernc.org/sqlite"
)

const backupHeader = "SPURBACKUP1"

func runEncryptedBackup(dataDirectory string, now time.Time) error {
	key, err := loadOrCreateBackupKey(filepath.Join(dataDirectory, "backup-key"))
	if err != nil {
		return err
	}
	backupDirectory := filepath.Join(dataDirectory, "backups")
	if err := os.MkdirAll(backupDirectory, 0o700); err != nil {
		return err
	}
	temporaryDatabase := filepath.Join(backupDirectory, ".items-backup.db")
	_ = os.Remove(temporaryDatabase)
	if err := snapshotSQLite(filepath.Join(dataDirectory, "items.db"), temporaryDatabase); err != nil {
		return err
	}
	defer os.Remove(temporaryDatabase)
	cleartext, err := os.ReadFile(temporaryDatabase)
	if err != nil {
		return err
	}
	encrypted, err := encryptBackup(cleartext, key)
	if err != nil {
		return err
	}
	target := filepath.Join(
		backupDirectory,
		"items-"+now.UTC().Format(time.DateOnly)+".db.aesgcm",
	)
	temporaryTarget := target + ".new"
	if err := os.WriteFile(temporaryTarget, encrypted, 0o600); err != nil {
		return err
	}
	if err := os.Rename(temporaryTarget, target); err != nil {
		return err
	}
	return retainRecentBackups(backupDirectory, 7)
}

func snapshotSQLite(source, destination string) error {
	db, err := sql.Open("sqlite", source+"?_pragma=busy_timeout(5000)")
	if err != nil {
		return err
	}
	defer db.Close()
	if _, err := db.Exec("PRAGMA wal_checkpoint(FULL)"); err != nil {
		return err
	}
	escaped := strings.ReplaceAll(destination, "'", "''")
	_, err = db.Exec("VACUUM INTO '" + escaped + "'")
	return err
}

func loadOrCreateBackupKey(path string) ([]byte, error) {
	key, err := os.ReadFile(path)
	if err == nil {
		if len(key) != 32 {
			return nil, errors.New("invalid backup key")
		}
		return key, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return nil, err
	}
	key = make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		return nil, err
	}
	if err := os.WriteFile(path, key, 0o600); err != nil {
		return nil, err
	}
	return key, nil
}

func encryptBackup(cleartext, key []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return nil, err
	}
	result := append([]byte(backupHeader), nonce...)
	return gcm.Seal(result, nonce, cleartext, nil), nil
}

func decryptBackup(encrypted, key []byte) ([]byte, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	prefixLength := len(backupHeader)
	if len(encrypted) < prefixLength+gcm.NonceSize() || string(encrypted[:prefixLength]) != backupHeader {
		return nil, errors.New("invalid backup")
	}
	nonce := encrypted[prefixLength : prefixLength+gcm.NonceSize()]
	return gcm.Open(nil, nonce, encrypted[prefixLength+gcm.NonceSize():], nil)
}

func retainRecentBackups(directory string, count int) error {
	matches, err := filepath.Glob(filepath.Join(directory, "items-*.db.aesgcm"))
	if err != nil {
		return err
	}
	sort.Strings(matches)
	for _, obsolete := range matches[:max(0, len(matches)-count)] {
		if err := os.Remove(obsolete); err != nil {
			return fmt.Errorf("remove obsolete backup: %w", err)
		}
	}
	return nil
}
