package main

import (
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"
)

const testItemID = "123e4567-e89b-42d3-a456-426614174000"

func TestDropRegistersItemAndSignsCoarseProvenance(t *testing.T) {
	itemStore := newTestStore(t)
	itemStore.now = func() time.Time { return time.Date(2026, 8, 14, 18, 30, 0, 0, time.FixedZone("CEST", 2*60*60)) }

	item, err := itemStore.drop(context.Background(), dropInput{
		ID: testItemID, Kind: kindStrawberry, Generation: 0,
		CapabilitySecret: testSecret(1), Capsule: provenanceCapsule{},
		Location:      location{Latitude: 52.52031, Longitude: 13.40491, AccuracyM: 4.5},
		IdempotencyID: testRequestID(1),
	})
	if err != nil {
		t.Fatalf("drop: %v", err)
	}
	if item.DroppedDay != "2026-08-14" || item.Location.Latitude != 52.52031 || item.Location.AccuracyM != 4.5 {
		t.Fatalf("unexpected public item: %+v", item)
	}
	if len(item.Provenance) != 1 {
		t.Fatalf("expected one provenance event, got %d", len(item.Provenance))
	}
	event := item.Provenance[0]
	if event.CoarseLatitude != "52.52" || event.CoarseLongitude != "13.40" || event.DayUTC != "2026-08-14" {
		t.Fatalf("provenance must be day-only and coarse: %+v", event)
	}
	if _, err := itemStore.signer.verify(provenanceCapsule{Events: item.Provenance}); err != nil {
		t.Fatalf("server signature should verify: %v", err)
	}

	record, err := readItem(context.Background(), itemStore.db, testItemID)
	if err != nil {
		t.Fatal(err)
	}
	if record.State != statePublic || len(record.CapabilityHash) != sha256.Size {
		t.Fatalf("unexpected stored record: %+v", record)
	}
}

func TestClaimRecoverAcknowledgeAndRedropRotatesCapability(t *testing.T) {
	itemStore := newTestStore(t)
	firstDrop := dropTestItem(t, itemStore, testItemID, testSecret(1))
	newSecret := testSecret(2)
	newHash, _ := capabilityHash(newSecret)

	claimed, err := itemStore.claim(context.Background(), claimInput{
		ID: testItemID, NewCapabilityHash: newHash,
		Location:      location{Latitude: 52.520005, Longitude: 13.405, AccuracyM: 5},
		IdempotencyID: testRequestID(2),
	})
	if err != nil {
		t.Fatalf("claim: %v", err)
	}
	if claimed.Generation != 1 || len(claimed.Capsule.Events) != 1 {
		t.Fatalf("unexpected claim: %+v", claimed)
	}

	recovered, err := itemStore.recover(context.Background(), testItemID, testRequestID(2), newSecret)
	if err != nil || recovered.Generation != claimed.Generation {
		t.Fatalf("recover: %+v, %v", recovered, err)
	}
	if err := itemStore.acknowledge(context.Background(), testItemID, testRequestID(2), newSecret); err != nil {
		t.Fatalf("ack: %v", err)
	}
	if err := itemStore.acknowledge(context.Background(), testItemID, testRequestID(2), newSecret); err != nil {
		t.Fatalf("idempotent ack: %v", err)
	}
	record, err := readItem(context.Background(), itemStore.db, testItemID)
	if err != nil {
		t.Fatal(err)
	}
	if record.ClaimID.Valid || record.LastDropID.Valid || len(record.ClaimCapsule) != 0 || len(record.PublicCapsule) != 0 {
		t.Fatalf("temporary transfer state survived ack: %+v", record)
	}

	_, err = itemStore.drop(context.Background(), dropInput{
		ID: testItemID, Kind: kindStrawberry, Generation: 1,
		CapabilitySecret: testSecret(1), Capsule: firstDropCapsule(firstDrop),
		Location:      location{Latitude: 52.521, Longitude: 13.406, AccuracyM: 4},
		IdempotencyID: testRequestID(3),
	})
	if !errors.Is(err, errUnauthorized) {
		t.Fatalf("old capability should fail, got %v", err)
	}

	redropped, err := itemStore.drop(context.Background(), dropInput{
		ID: testItemID, Kind: kindStrawberry, Generation: 1,
		CapabilitySecret: newSecret, Capsule: recovered.Capsule,
		Location:      location{Latitude: 52.521, Longitude: 13.406, AccuracyM: 4},
		IdempotencyID: testRequestID(4),
	})
	if err != nil {
		t.Fatalf("redrop: %v", err)
	}
	if len(redropped.Provenance) != 2 || redropped.Provenance[1].Generation != 1 {
		t.Fatalf("provenance chain did not advance: %+v", redropped.Provenance)
	}
}

func TestDropAndClaimAreIdempotent(t *testing.T) {
	itemStore := newTestStore(t)
	first := dropTestItem(t, itemStore, testItemID, testSecret(1))
	second := dropTestItem(t, itemStore, testItemID, testSecret(1))
	if len(second.Provenance) != 1 || second.Provenance[0].ServerSignature != first.Provenance[0].ServerSignature {
		t.Fatal("drop retry must return the original result")
	}

	hash, _ := capabilityHash(testSecret(2))
	request := claimInput{
		ID: testItemID, NewCapabilityHash: hash,
		Location:      location{Latitude: 52.52, Longitude: 13.405, AccuracyM: 3},
		IdempotencyID: testRequestID(2),
	}
	firstClaim, err := itemStore.claim(context.Background(), request)
	if err != nil {
		t.Fatal(err)
	}
	secondClaim, err := itemStore.claim(context.Background(), request)
	if err != nil || secondClaim.Generation != firstClaim.Generation {
		t.Fatalf("claim retry: %+v, %v", secondClaim, err)
	}
}

func TestClaimRejectsLocationsBeyondTenMeters(t *testing.T) {
	itemStore := newTestStore(t)
	dropTestItem(t, itemStore, testItemID, testSecret(1))
	hash, _ := capabilityHash(testSecret(2))

	_, err := itemStore.claim(context.Background(), claimInput{
		ID: testItemID, NewCapabilityHash: hash,
		Location:      location{Latitude: 52.5202, Longitude: 13.405, AccuracyM: 2},
		IdempotencyID: testRequestID(5),
	})
	if !errors.Is(err, errOutOfRange) {
		t.Fatalf("expected range rejection, got %v", err)
	}
}

func TestTwoParallelClaimsHaveExactlyOneWinner(t *testing.T) {
	itemStore := newTestStore(t)
	dropTestItem(t, itemStore, testItemID, testSecret(1))

	start := make(chan struct{})
	results := make(chan error, 2)
	var wait sync.WaitGroup
	for index := 0; index < 2; index++ {
		wait.Add(1)
		go func(index int) {
			defer wait.Done()
			secret := testSecret(byte(index + 2))
			hash, _ := capabilityHash(secret)
			<-start
			_, err := itemStore.claim(context.Background(), claimInput{
				ID: testItemID, NewCapabilityHash: hash,
				Location:      location{Latitude: 52.52, Longitude: 13.405, AccuracyM: 3},
				IdempotencyID: testRequestID(index + 2),
			})
			results <- err
		}(index)
	}
	close(start)
	wait.Wait()
	close(results)

	winners := 0
	alreadyClaimed := 0
	for err := range results {
		switch {
		case err == nil:
			winners++
		case errors.Is(err, errAlreadyClaimed):
			alreadyClaimed++
		default:
			t.Fatalf("unexpected parallel result: %v", err)
		}
	}
	if winners != 1 || alreadyClaimed != 1 {
		t.Fatalf("winners=%d alreadyClaimed=%d", winners, alreadyClaimed)
	}
}

func TestSchemaContainsNoIdentityOrHistoricalLocationFields(t *testing.T) {
	itemStore := newTestStore(t)
	rows, err := itemStore.db.Query("PRAGMA table_info(items)")
	if err != nil {
		t.Fatal(err)
	}
	defer rows.Close()
	var columns []string
	for rows.Next() {
		var cid, notNull, primaryKey int
		var name, dataType string
		var defaultValue any
		if err := rows.Scan(&cid, &name, &dataType, &notNull, &defaultValue, &primaryKey); err != nil {
			t.Fatal(err)
		}
		columns = append(columns, name)
	}
	joined := strings.ToLower(strings.Join(columns, " "))
	for _, forbidden := range []string{"user", "owner", "device", "advert", "ip", "agent", "history", "previous_lat", "previous_lon"} {
		if strings.Contains(joined, forbidden) {
			t.Fatalf("privacy-sensitive schema field %q in %v", forbidden, columns)
		}
	}
}

func TestPrivacyGuardRollsBackMutationWhenSchemaChanges(t *testing.T) {
	itemStore := newTestStore(t)
	if _, err := itemStore.db.Exec("ALTER TABLE items ADD COLUMN device_id TEXT"); err != nil {
		t.Fatal(err)
	}

	_, err := itemStore.drop(context.Background(), dropInput{
		ID: testItemID, Kind: kindStrawberry, Generation: 0,
		CapabilitySecret: testSecret(1), Capsule: provenanceCapsule{},
		Location:      location{Latitude: 52.52, Longitude: 13.405, AccuracyM: 3},
		IdempotencyID: testRequestID(1),
	})
	if !errors.Is(err, errPrivacyGuard) {
		t.Fatalf("expected privacy rejection, got %v", err)
	}
	var count int
	if err := itemStore.db.QueryRow("SELECT COUNT(*) FROM items").Scan(&count); err != nil {
		t.Fatal(err)
	}
	if count != 0 {
		t.Fatalf("privacy rejection committed %d rows", count)
	}
}

func TestPrivacyGuardRejectsExtraProvenanceJSON(t *testing.T) {
	itemStore := newTestStore(t)
	dropTestItem(t, itemStore, testItemID, testSecret(1))
	record, err := readItem(context.Background(), itemStore.db, testItemID)
	if err != nil {
		t.Fatal(err)
	}
	var capsule map[string]any
	if err := json.Unmarshal(record.PublicCapsule, &capsule); err != nil {
		t.Fatal(err)
	}
	capsule["device_id"] = "phone-123"
	record.PublicCapsule, err = json.Marshal(capsule)
	if err != nil {
		t.Fatal(err)
	}
	if err := itemStore.validatePersistedItemPrivacy(record); !errors.Is(err, errPrivacyGuard) {
		t.Fatalf("expected provenance privacy rejection, got %v", err)
	}
}

func TestClientRequestIDsAreScopedBeforeStorage(t *testing.T) {
	itemStore := newTestStore(t)
	encodedDeviceData := "44455649-4345-4441-9441-313233343536"
	_, err := itemStore.drop(context.Background(), dropInput{
		ID: testItemID, Kind: kindStrawberry, Generation: 0,
		CapabilitySecret: testSecret(1), Capsule: provenanceCapsule{},
		Location:      location{Latitude: 52.52, Longitude: 13.405, AccuracyM: 3},
		IdempotencyID: encodedDeviceData,
	})
	if err != nil {
		t.Fatal(err)
	}
	record, err := readItem(context.Background(), itemStore.db, testItemID)
	if err != nil {
		t.Fatal(err)
	}
	if !record.LastDropID.Valid || record.LastDropID.String == encodedDeviceData || record.LastDropID.String != scopedRequestID("drop", testItemID, encodedDeviceData) {
		t.Fatalf("raw client request id was stored: %q", record.LastDropID.String)
	}
}

func newTestStore(t *testing.T) *store {
	t.Helper()
	provenanceSigner, err := newTestSigner()
	if err != nil {
		t.Fatal(err)
	}
	itemStore, err := openStore(filepath.Join(t.TempDir(), "items.db"), provenanceSigner)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { itemStore.close() })
	return itemStore
}

func dropTestItem(t *testing.T, itemStore *store, id, secret string) publicItem {
	t.Helper()
	item, err := itemStore.drop(context.Background(), dropInput{
		ID: id, Kind: kindStrawberry, Generation: 0,
		CapabilitySecret: secret, Capsule: provenanceCapsule{},
		Location:      location{Latitude: 52.52, Longitude: 13.405, AccuracyM: 3},
		IdempotencyID: testRequestID(1),
	})
	if err != nil {
		t.Fatal(err)
	}
	return item
}

func firstDropCapsule(item publicItem) provenanceCapsule {
	return provenanceCapsule{Events: item.Provenance}
}

func testSecret(value byte) string {
	return base64.RawURLEncoding.EncodeToString(bytes.Repeat([]byte{value}, 32))
}

func testRequestID(value int) string {
	return fmt.Sprintf("00000000-0000-4000-8000-%012d", value)
}
