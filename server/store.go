package main

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	_ "modernc.org/sqlite"
)

var (
	errNotFound       = errors.New("not found")
	errAlreadyClaimed = errors.New("already claimed")
	errOutOfRange     = errors.New("out of range")
	errUnauthorized   = errors.New("unauthorized")
	errConflict       = errors.New("conflict")
	errInvalidInput   = errors.New("invalid input")
	errPrivacyGuard   = errors.New("privacy guard rejected data")
)

type itemRecord struct {
	ID                 string
	Kind               itemKind
	Generation         int
	State              string
	CapabilityHash     []byte
	ProvenanceHeadHash []byte
	Latitude           sql.NullFloat64
	Longitude          sql.NullFloat64
	AccuracyM          sql.NullFloat64
	DroppedDay         sql.NullString
	PublicCapsule      []byte
	ClaimID            sql.NullString
	ClaimCapsule       []byte
	LastDropID         sql.NullString
}

type store struct {
	db     *sql.DB
	signer *signer
	now    func() time.Time
}

func openStore(path string, provenanceSigner *signer) (*store, error) {
	db, err := sql.Open("sqlite", path+"?_pragma=busy_timeout(5000)&_pragma=foreign_keys(1)&_pragma=journal_mode(WAL)")
	if err != nil {
		return nil, err
	}
	db.SetMaxOpenConns(8)
	value := &store{db: db, signer: provenanceSigner, now: time.Now}
	if err := value.migrate(); err != nil {
		db.Close()
		return nil, err
	}
	return value, nil
}

func (value *store) close() error {
	return value.db.Close()
}

func (value *store) migrate() error {
	_, err := value.db.Exec(`
CREATE TABLE IF NOT EXISTS items (
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL CHECK(kind IN ('STRAWBERRY', 'PEAR', 'BANANA')),
    generation INTEGER NOT NULL CHECK(generation >= 0),
    state TEXT NOT NULL CHECK(state IN ('OWNED', 'PUBLIC', 'CLAIMING')),
    capability_hash BLOB NOT NULL,
    provenance_head_hash BLOB NOT NULL,
    latitude REAL,
    longitude REAL,
    accuracy_m REAL,
    dropped_day TEXT,
    public_capsule BLOB,
    claim_id TEXT,
    claim_capsule BLOB,
    last_drop_id TEXT,
    CHECK((state = 'PUBLIC') = (latitude IS NOT NULL AND longitude IS NOT NULL AND accuracy_m IS NOT NULL AND public_capsule IS NOT NULL)),
    CHECK((state = 'CLAIMING') = (claim_capsule IS NOT NULL)),
    CHECK(claim_capsule IS NULL OR claim_id IS NOT NULL)
);
CREATE INDEX IF NOT EXISTS items_public_location ON items(state, latitude, longitude);
`)
	if err != nil {
		return err
	}
	return validatePrivacySchema(context.Background(), value.db)
}

type dropInput struct {
	ID               string
	Kind             itemKind
	Generation       int
	CapabilitySecret string
	Capsule          provenanceCapsule
	Location         location
	IdempotencyID    string
}

type publicItem struct {
	ID         string            `json:"id"`
	Kind       itemKind          `json:"kind"`
	Generation int               `json:"generation"`
	Location   location          `json:"location"`
	DroppedDay string            `json:"dropped_day"`
	Provenance []provenanceEvent `json:"public_provenance"`
}

func (value *store) drop(ctx context.Context, input dropInput) (publicItem, error) {
	if !opaqueIDPattern.MatchString(input.ID) || !opaqueIDPattern.MatchString(input.IdempotencyID) {
		return publicItem{}, errInvalidInput
	}
	dropID := scopedRequestID("drop", input.ID, input.IdempotencyID)
	capHash, err := capabilityHash(input.CapabilitySecret)
	if err != nil {
		return publicItem{}, err
	}
	incomingHead, err := value.signer.verify(input.Capsule)
	if err != nil {
		return publicItem{}, err
	}

	tx, err := value.db.BeginTx(ctx, nil)
	if err != nil {
		return publicItem{}, err
	}
	defer tx.Rollback()

	record, err := readItem(ctx, tx, input.ID)
	if errors.Is(err, errNotFound) {
		if input.Generation != 0 || len(input.Capsule.Events) != 0 {
			return publicItem{}, errConflict
		}
		record = itemRecord{
			ID:                 input.ID,
			Kind:               input.Kind,
			Generation:         0,
			State:              stateOwned,
			CapabilityHash:     capHash,
			ProvenanceHeadHash: []byte{},
		}
	} else if err != nil {
		return publicItem{}, err
	} else {
		if record.LastDropID.Valid && record.LastDropID.String == dropID && record.State == statePublic {
			result, decodeErr := record.public()
			return result, decodeErr
		}
		if record.State != stateOwned || record.Generation != input.Generation || record.Kind != input.Kind {
			return publicItem{}, errConflict
		}
		if !bytes.Equal(record.CapabilityHash, capHash) {
			return publicItem{}, errUnauthorized
		}
		if !bytes.Equal(record.ProvenanceHeadHash, incomingHead) {
			return publicItem{}, errConflict
		}
	}

	capsule, head, err := value.signer.append(input.Capsule, input.Generation, input.Location, value.now())
	if err != nil {
		return publicItem{}, err
	}
	encodedCapsule, err := json.Marshal(capsule)
	if err != nil {
		return publicItem{}, err
	}
	day := value.now().UTC().Format(time.DateOnly)
	if record.State == stateOwned && record.ID == input.ID {
		var result sql.Result
		result, err = tx.ExecContext(ctx, `
INSERT INTO items(id, kind, generation, state, capability_hash, provenance_head_hash, latitude, longitude, accuracy_m, dropped_day, public_capsule, claim_id, claim_capsule, last_drop_id)
VALUES(?, ?, ?, 'PUBLIC', ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?)
ON CONFLICT(id) DO UPDATE SET
    state = 'PUBLIC', provenance_head_hash = excluded.provenance_head_hash,
    latitude = excluded.latitude, longitude = excluded.longitude,
    accuracy_m = excluded.accuracy_m, dropped_day = excluded.dropped_day, public_capsule = excluded.public_capsule,
    claim_id = NULL, claim_capsule = NULL, last_drop_id = excluded.last_drop_id
WHERE items.state = 'OWNED' AND items.capability_hash = excluded.capability_hash
`, input.ID, input.Kind, input.Generation, capHash, head, input.Location.Latitude, input.Location.Longitude, input.Location.AccuracyM, day, encodedCapsule, dropID)
		if err == nil {
			changed, rowsErr := result.RowsAffected()
			if rowsErr != nil {
				err = rowsErr
			} else if changed != 1 {
				err = errConflict
			}
		}
	}
	if err != nil {
		return publicItem{}, err
	}
	if err := value.validatePrivacyAfterMutation(ctx, tx, input.ID); err != nil {
		return publicItem{}, err
	}
	if err := tx.Commit(); err != nil {
		return publicItem{}, err
	}
	return publicItem{
		ID:         input.ID,
		Kind:       input.Kind,
		Generation: input.Generation,
		Location: location{
			Latitude:  input.Location.Latitude,
			Longitude: input.Location.Longitude,
			AccuracyM: input.Location.AccuracyM,
		},
		DroppedDay: day,
		Provenance: capsule.Events,
	}, nil
}

type claimInput struct {
	ID                string
	NewCapabilityHash []byte
	Location          location
	IdempotencyID     string
}

type claimedItem struct {
	ID         string            `json:"id"`
	Kind       itemKind          `json:"kind"`
	Generation int               `json:"generation"`
	Capsule    provenanceCapsule `json:"provenance_capsule"`
}

func (value *store) claim(ctx context.Context, input claimInput) (claimedItem, error) {
	if !opaqueIDPattern.MatchString(input.ID) || !opaqueIDPattern.MatchString(input.IdempotencyID) {
		return claimedItem{}, errInvalidInput
	}
	claimID := scopedRequestID("claim", input.ID, input.IdempotencyID)
	record, err := readItem(ctx, value.db, input.ID)
	if err != nil {
		return claimedItem{}, err
	}
	if record.State == stateClaiming && record.ClaimID.String == claimID && bytes.Equal(record.CapabilityHash, input.NewCapabilityHash) {
		result, decodeErr := record.claimed()
		return result, decodeErr
	}
	if record.State != statePublic {
		return claimedItem{}, errAlreadyClaimed
	}
	itemLocation := location{Latitude: record.Latitude.Float64, Longitude: record.Longitude.Float64}
	if distanceMeters(itemLocation, input.Location) > 10 {
		return claimedItem{}, errOutOfRange
	}

	tx, err := value.db.BeginTx(ctx, nil)
	if err != nil {
		return claimedItem{}, err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
UPDATE items SET
    generation = generation + 1,
    state = 'CLAIMING', capability_hash = ?,
    latitude = NULL, longitude = NULL, accuracy_m = NULL, dropped_day = NULL,
    claim_id = ?, claim_capsule = public_capsule, public_capsule = NULL,
    last_drop_id = NULL
WHERE id = ? AND state = 'PUBLIC'
`, input.NewCapabilityHash, claimID, input.ID)
	if err != nil {
		return claimedItem{}, err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return claimedItem{}, err
	}
	if changed != 1 {
		return claimedItem{}, errAlreadyClaimed
	}
	record.Generation++
	record.State = stateClaiming
	record.CapabilityHash = input.NewCapabilityHash
	record.ClaimID = sql.NullString{String: claimID, Valid: true}
	record.ClaimCapsule = record.PublicCapsule
	claimed, err := record.claimed()
	if err != nil {
		return claimedItem{}, err
	}
	if err := value.validatePrivacyAfterMutation(ctx, tx, input.ID); err != nil {
		return claimedItem{}, err
	}
	if err := tx.Commit(); err != nil {
		return claimedItem{}, err
	}
	return claimed, nil
}

func (value *store) recover(ctx context.Context, id, claimID, capabilitySecret string) (claimedItem, error) {
	if !opaqueIDPattern.MatchString(id) || !opaqueIDPattern.MatchString(claimID) {
		return claimedItem{}, errInvalidInput
	}
	claimID = scopedRequestID("claim", id, claimID)
	hash, err := capabilityHash(capabilitySecret)
	if err != nil {
		return claimedItem{}, err
	}
	record, err := readItem(ctx, value.db, id)
	if err != nil {
		return claimedItem{}, err
	}
	if record.State != stateClaiming || !record.ClaimID.Valid || record.ClaimID.String != claimID || !bytes.Equal(record.CapabilityHash, hash) {
		return claimedItem{}, errUnauthorized
	}
	return record.claimed()
}

func (value *store) acknowledge(ctx context.Context, id, claimID, capabilitySecret string) error {
	if !opaqueIDPattern.MatchString(id) || !opaqueIDPattern.MatchString(claimID) {
		return errInvalidInput
	}
	claimID = scopedRequestID("claim", id, claimID)
	hash, err := capabilityHash(capabilitySecret)
	if err != nil {
		return err
	}
	tx, err := value.db.BeginTx(ctx, nil)
	if err != nil {
		return err
	}
	defer tx.Rollback()
	result, err := tx.ExecContext(ctx, `
UPDATE items SET state = 'OWNED', claim_capsule = NULL
    , claim_id = NULL
WHERE id = ? AND state = 'CLAIMING' AND claim_id = ? AND capability_hash = ?
`, id, claimID, hash)
	if err != nil {
		return err
	}
	changed, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if changed == 1 {
		if err := value.validatePrivacyAfterMutation(ctx, tx, id); err != nil {
			return err
		}
		return tx.Commit()
	}
	record, readErr := readItem(ctx, tx, id)
	if readErr == nil && record.State == stateOwned && !record.ClaimID.Valid && bytes.Equal(record.CapabilityHash, hash) {
		return tx.Commit()
	}
	return errUnauthorized
}

type rowQuerier interface {
	QueryRowContext(context.Context, string, ...any) *sql.Row
}

func readItem(ctx context.Context, querier rowQuerier, id string) (itemRecord, error) {
	var record itemRecord
	err := querier.QueryRowContext(ctx, `
SELECT id, kind, generation, state, capability_hash, provenance_head_hash,
       latitude, longitude, accuracy_m, dropped_day, public_capsule, claim_id, claim_capsule, last_drop_id
FROM items WHERE id = ?
	`, id).Scan(
		&record.ID, &record.Kind, &record.Generation, &record.State, &record.CapabilityHash,
		&record.ProvenanceHeadHash, &record.Latitude, &record.Longitude, &record.AccuracyM,
		&record.DroppedDay, &record.PublicCapsule, &record.ClaimID, &record.ClaimCapsule, &record.LastDropID,
	)
	if errors.Is(err, sql.ErrNoRows) {
		return itemRecord{}, errNotFound
	}
	return record, err
}

func (record itemRecord) public() (publicItem, error) {
	var capsule provenanceCapsule
	if err := json.Unmarshal(record.PublicCapsule, &capsule); err != nil {
		return publicItem{}, err
	}
	return publicItem{
		ID: record.ID, Kind: record.Kind, Generation: record.Generation,
		Location:   location{Latitude: record.Latitude.Float64, Longitude: record.Longitude.Float64, AccuracyM: record.AccuracyM.Float64},
		DroppedDay: record.DroppedDay.String, Provenance: capsule.Events,
	}, nil
}

func (record itemRecord) claimed() (claimedItem, error) {
	var capsule provenanceCapsule
	if err := json.Unmarshal(record.ClaimCapsule, &capsule); err != nil {
		return claimedItem{}, err
	}
	return claimedItem{ID: record.ID, Kind: record.Kind, Generation: record.Generation, Capsule: capsule}, nil
}

type itemBounds struct {
	West, South, East, North float64
}

func (value *store) listPublic(ctx context.Context, bounds itemBounds) ([]publicItem, error) {
	rows, err := value.db.QueryContext(ctx, `
SELECT id, kind, generation, state, capability_hash, provenance_head_hash,
       latitude, longitude, accuracy_m, dropped_day, public_capsule, claim_id, claim_capsule, last_drop_id
FROM items
WHERE state = 'PUBLIC' AND longitude >= ? AND longitude <= ? AND latitude >= ? AND latitude <= ?
ORDER BY dropped_day DESC, id
`, bounds.West, bounds.East, bounds.South, bounds.North)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	var result []publicItem
	for rows.Next() {
		var record itemRecord
		if err := rows.Scan(
			&record.ID, &record.Kind, &record.Generation, &record.State, &record.CapabilityHash,
			&record.ProvenanceHeadHash, &record.Latitude, &record.Longitude, &record.AccuracyM, &record.DroppedDay,
			&record.PublicCapsule, &record.ClaimID, &record.ClaimCapsule, &record.LastDropID,
		); err != nil {
			return nil, err
		}
		item, err := record.public()
		if err != nil {
			return nil, fmt.Errorf("decode public item: %w", err)
		}
		result = append(result, item)
	}
	return result, rows.Err()
}
