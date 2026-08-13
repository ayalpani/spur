package main

import (
	"errors"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"time"
)

func main() {
	dataDirectory := environment("SPUR_DATA_DIR", "/data")
	provenanceSigner, err := loadOrCreateSigner(filepath.Join(dataDirectory, "provenance-key.pem"))
	if err != nil {
		log.Fatal("provenance key unavailable")
	}
	itemStore, err := openStore(filepath.Join(dataDirectory, "items.db"), provenanceSigner)
	if err != nil {
		log.Fatal("item database unavailable")
	}
	defer itemStore.close()

	server := &http.Server{
		Addr:              environment("SPUR_LISTEN_ADDR", ":8080"),
		Handler:           (&api{store: itemStore}).handler(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      10 * time.Second,
		IdleTimeout:       60 * time.Second,
	}
	log.Print("spur items service ready")
	if err := server.ListenAndServe(); !errors.Is(err, http.ErrServerClosed) {
		log.Fatal("spur items service stopped")
	}
}

func environment(name, fallback string) string {
	if value := os.Getenv(name); value != "" {
		return value
	}
	return fallback
}
