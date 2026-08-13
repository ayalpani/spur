package main

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"encoding/pem"
	"errors"
	"fmt"
	"math"
	"os"
	"path/filepath"
	"regexp"
	"time"
)

const (
	stateOwned    = "OWNED"
	statePublic   = "PUBLIC"
	stateClaiming = "CLAIMING"
)

// UUIDv4 excludes timestamp- and MAC-derived UUID variants and bounds client-controlled IDs.
var opaqueIDPattern = regexp.MustCompile(`^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-4[0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$`)

type itemKind string

const (
	kindStrawberry itemKind = "STRAWBERRY"
	kindPear       itemKind = "PEAR"
	kindBanana     itemKind = "BANANA"
)

func (kind itemKind) valid() bool {
	return kind == kindStrawberry || kind == kindPear || kind == kindBanana
}

type location struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	AccuracyM float64 `json:"accuracy_m"`
}

func (value location) valid() bool {
	return value.Latitude >= -90 && value.Latitude <= 90 &&
		value.Longitude >= -180 && value.Longitude <= 180 &&
		value.AccuracyM > 0 && value.AccuracyM <= 15
}

type unsignedProvenanceEvent struct {
	Generation      int    `json:"generation"`
	DayUTC          string `json:"day_utc"`
	CoarseLatitude  string `json:"coarse_latitude"`
	CoarseLongitude string `json:"coarse_longitude"`
	PreviousHash    string `json:"previous_hash"`
}

type provenanceEvent struct {
	Generation      int    `json:"generation"`
	DayUTC          string `json:"day_utc"`
	CoarseLatitude  string `json:"coarse_latitude"`
	CoarseLongitude string `json:"coarse_longitude"`
	PreviousHash    string `json:"previous_hash"`
	ServerSignature string `json:"server_signature"`
}

func (event provenanceEvent) unsigned() unsignedProvenanceEvent {
	return unsignedProvenanceEvent{
		Generation:      event.Generation,
		DayUTC:          event.DayUTC,
		CoarseLatitude:  event.CoarseLatitude,
		CoarseLongitude: event.CoarseLongitude,
		PreviousHash:    event.PreviousHash,
	}
}

type provenanceCapsule struct {
	Events []provenanceEvent `json:"events"`
}

type signer struct {
	key *ecdsa.PrivateKey
}

func loadOrCreateSigner(path string) (*signer, error) {
	encoded, err := os.ReadFile(path)
	if err == nil {
		block, _ := pem.Decode(encoded)
		if block == nil {
			return nil, errors.New("decode provenance key")
		}
		key, parseErr := x509.ParseECPrivateKey(block.Bytes)
		if parseErr != nil {
			return nil, fmt.Errorf("parse provenance key: %w", parseErr)
		}
		return &signer{key: key}, nil
	}
	if !errors.Is(err, os.ErrNotExist) {
		return nil, fmt.Errorf("read provenance key: %w", err)
	}

	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, fmt.Errorf("generate provenance key: %w", err)
	}
	der, err := x509.MarshalECPrivateKey(key)
	if err != nil {
		return nil, fmt.Errorf("marshal provenance key: %w", err)
	}
	if err := os.MkdirAll(filepath.Dir(path), 0o700); err != nil {
		return nil, fmt.Errorf("create key directory: %w", err)
	}
	temporary := path + ".new"
	if err := os.WriteFile(temporary, pem.EncodeToMemory(&pem.Block{Type: "EC PRIVATE KEY", Bytes: der}), 0o600); err != nil {
		return nil, fmt.Errorf("write provenance key: %w", err)
	}
	if err := os.Rename(temporary, path); err != nil {
		return nil, fmt.Errorf("install provenance key: %w", err)
	}
	return &signer{key: key}, nil
}

func newTestSigner() (*signer, error) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		return nil, err
	}
	return &signer{key: key}, nil
}

func (value *signer) append(capsule provenanceCapsule, generation int, at location, now time.Time) (provenanceCapsule, []byte, error) {
	head, err := value.verify(capsule)
	if err != nil {
		return provenanceCapsule{}, nil, err
	}
	unsigned := unsignedProvenanceEvent{
		Generation:      generation,
		DayUTC:          now.UTC().Format(time.DateOnly),
		CoarseLatitude:  fmt.Sprintf("%.2f", at.Latitude),
		CoarseLongitude: fmt.Sprintf("%.2f", at.Longitude),
		PreviousHash:    hex.EncodeToString(head),
	}
	payload, err := json.Marshal(unsigned)
	if err != nil {
		return provenanceCapsule{}, nil, err
	}
	digest := sha256.Sum256(payload)
	signature, err := ecdsa.SignASN1(rand.Reader, value.key, digest[:])
	if err != nil {
		return provenanceCapsule{}, nil, err
	}
	event := provenanceEvent{
		Generation:      unsigned.Generation,
		DayUTC:          unsigned.DayUTC,
		CoarseLatitude:  unsigned.CoarseLatitude,
		CoarseLongitude: unsigned.CoarseLongitude,
		PreviousHash:    unsigned.PreviousHash,
		ServerSignature: base64.RawURLEncoding.EncodeToString(signature),
	}
	capsule.Events = append(capsule.Events, event)
	head = eventHash(payload, signature)
	return capsule, head, nil
}

func (value *signer) verify(capsule provenanceCapsule) ([]byte, error) {
	var head []byte
	for _, event := range capsule.Events {
		if event.PreviousHash != hex.EncodeToString(head) {
			return nil, errors.New("invalid provenance predecessor")
		}
		payload, err := json.Marshal(event.unsigned())
		if err != nil {
			return nil, err
		}
		digest := sha256.Sum256(payload)
		signature, err := base64.RawURLEncoding.DecodeString(event.ServerSignature)
		if err != nil || !ecdsa.VerifyASN1(&value.key.PublicKey, digest[:], signature) {
			return nil, errors.New("invalid provenance signature")
		}
		head = eventHash(payload, signature)
	}
	return head, nil
}

func eventHash(payload, signature []byte) []byte {
	digest := sha256.New()
	digest.Write(payload)
	digest.Write(signature)
	return digest.Sum(nil)
}

func capabilityHash(encodedSecret string) ([]byte, error) {
	secret, err := base64.RawURLEncoding.DecodeString(encodedSecret)
	if err != nil || len(secret) != 32 {
		return nil, errors.New("capability secret must contain 32 bytes")
	}
	digest := sha256.Sum256(secret)
	return digest[:], nil
}

func decodeHash(value string) ([]byte, error) {
	decoded, err := base64.RawURLEncoding.DecodeString(value)
	if err != nil || len(decoded) != sha256.Size {
		return nil, errors.New("capability hash must contain 32 bytes")
	}
	return decoded, nil
}

func scopedRequestID(action, itemID, requestID string) string {
	digest := sha256.Sum256([]byte(action + "\x00" + itemID + "\x00" + requestID))
	return base64.RawURLEncoding.EncodeToString(digest[:])
}

func distanceMeters(a, b location) float64 {
	const earthRadiusM = 6_371_000.0
	lat1 := a.Latitude * math.Pi / 180
	lat2 := b.Latitude * math.Pi / 180
	deltaLat := (b.Latitude - a.Latitude) * math.Pi / 180
	deltaLon := (b.Longitude - a.Longitude) * math.Pi / 180
	sinLat := math.Sin(deltaLat / 2)
	sinLon := math.Sin(deltaLon / 2)
	haversine := sinLat*sinLat + math.Cos(lat1)*math.Cos(lat2)*sinLon*sinLon
	return earthRadiusM * 2 * math.Atan2(math.Sqrt(haversine), math.Sqrt(1-haversine))
}
