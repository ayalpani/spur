package main

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"regexp"
	"slices"
	"sort"
	"strconv"
	"time"
)

var (
	itemTableColumns = []string{
		"accuracy_m", "capability_hash", "claim_capsule", "claim_id", "dropped_day",
		"generation", "id", "kind", "last_drop_id", "latitude", "longitude",
		"provenance_head_hash", "public_capsule", "state",
	}
	coarseCoordinatePattern = regexp.MustCompile(`^-?(0|[1-9][0-9]{0,2})\.[0-9]{2}$`)
	storedRequestIDPattern  = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	allowedAPIErrors        = map[string]struct{}{
		"invalid_bbox\x00bbox muss west,south,east,north enthalten": {},
		"invalid_zoom\x00zoom muss zwischen 0 und 24 liegen":        {},
		"internal\x00Items konnten nicht geladen werden":            {},
		"not_found\x00Item ist nicht öffentlich":                    {},
		"internal\x00Item konnte nicht geladen werden":              {},
		"invalid_drop\x00Ablageangaben sind ungültig":               {},
		"invalid_claim\x00Aufnahmeangaben sind ungültig":            {},
		"invalid_json\x00JSON-Anfrage ist ungültig":                 {},
		"not_found\x00Item wurde nicht gefunden":                    {},
		"already_claimed\x00Schon gefunden":                         {},
		"out_of_range\x00Item ist mehr als 10 m entfernt":           {},
		"invalid_capability\x00Besitznachweis ist ungültig":         {},
		"item_conflict\x00Itemzustand hat sich geändert":            {},
		"internal\x00Anfrage konnte nicht verarbeitet werden":       {},
	}
)

type privacyQuerier interface {
	rowQuerier
	QueryContext(context.Context, string, ...any) (*sql.Rows, error)
}

type healthResponse struct {
	Status string `json:"status"`
}

type itemListResponse struct {
	Items    []publicItem    `json:"items"`
	Clusters []publicCluster `json:"clusters"`
}

type acknowledgementResponse struct {
	Status string `json:"status"`
}

type apiError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type apiErrorResponse struct {
	Error apiError `json:"error"`
}

func privacyError(reason string) error {
	return fmt.Errorf("%w: %s", errPrivacyGuard, reason)
}

func validatePrivacySchema(ctx context.Context, querier privacyQuerier) error {
	rows, err := querier.QueryContext(ctx, `
SELECT name FROM sqlite_schema
WHERE type = 'table' AND name NOT LIKE 'sqlite_%'
ORDER BY name
`)
	if err != nil {
		return err
	}
	defer rows.Close()
	var tables []string
	for rows.Next() {
		var name string
		if err := rows.Scan(&name); err != nil {
			return err
		}
		tables = append(tables, name)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	if len(tables) != 1 || tables[0] != "items" {
		return privacyError("unexpected database table")
	}

	columnRows, err := querier.QueryContext(ctx, "PRAGMA table_info(items)")
	if err != nil {
		return err
	}
	defer columnRows.Close()
	var columns []string
	for columnRows.Next() {
		var cid, notNull, primaryKey int
		var name, dataType string
		var defaultValue any
		if err := columnRows.Scan(&cid, &name, &dataType, &notNull, &defaultValue, &primaryKey); err != nil {
			return err
		}
		columns = append(columns, name)
	}
	if err := columnRows.Err(); err != nil {
		return err
	}
	sort.Strings(columns)
	if !slices.Equal(columns, itemTableColumns) {
		return privacyError("unexpected items column")
	}
	return nil
}

func (value *store) validatePrivacyAfterMutation(ctx context.Context, querier privacyQuerier, id string) error {
	if err := validatePrivacySchema(ctx, querier); err != nil {
		return err
	}
	record, err := readItem(ctx, querier, id)
	if err != nil {
		return err
	}
	return value.validatePersistedItemPrivacy(record)
}

func (value *store) validatePersistedItemPrivacy(record itemRecord) error {
	if !opaqueIDPattern.MatchString(record.ID) || !record.Kind.valid() || record.Generation < 0 {
		return privacyError("invalid public identity")
	}
	if len(record.CapabilityHash) != 32 || len(record.ProvenanceHeadHash) != 32 {
		return privacyError("invalid opaque hash")
	}

	locationPresent := record.Latitude.Valid || record.Longitude.Valid || record.AccuracyM.Valid
	switch record.State {
	case statePublic:
		if !record.Latitude.Valid || !record.Longitude.Valid || !record.AccuracyM.Valid ||
			!record.DroppedDay.Valid || len(record.PublicCapsule) == 0 ||
			record.ClaimID.Valid || len(record.ClaimCapsule) != 0 ||
			!record.LastDropID.Valid || !storedRequestIDPattern.MatchString(record.LastDropID.String) {
			return privacyError("invalid public item state")
		}
		if !(location{Latitude: record.Latitude.Float64, Longitude: record.Longitude.Float64, AccuracyM: record.AccuracyM.Float64}).valid() || !validDateOnly(record.DroppedDay.String) {
			return privacyError("invalid current location")
		}
		if err := value.validateCapsulePrivacy(record.PublicCapsule, record.Generation+1); err != nil {
			return err
		}
	case stateClaiming:
		if record.Generation < 1 || locationPresent || record.DroppedDay.Valid || len(record.PublicCapsule) != 0 ||
			!record.ClaimID.Valid || !storedRequestIDPattern.MatchString(record.ClaimID.String) ||
			len(record.ClaimCapsule) == 0 || record.LastDropID.Valid {
			return privacyError("invalid claiming item state")
		}
		if err := value.validateCapsulePrivacy(record.ClaimCapsule, record.Generation); err != nil {
			return err
		}
	case stateOwned:
		if record.Generation < 1 || locationPresent || record.DroppedDay.Valid || len(record.PublicCapsule) != 0 ||
			record.ClaimID.Valid || len(record.ClaimCapsule) != 0 || record.LastDropID.Valid {
			return privacyError("invalid owned item state")
		}
	default:
		return privacyError("invalid item state")
	}
	return nil
}

func (value *store) validateCapsulePrivacy(encoded []byte, expectedEvents int) error {
	var envelope map[string]json.RawMessage
	if err := json.Unmarshal(encoded, &envelope); err != nil || !hasExactKeys(envelope, "events") {
		return privacyError("invalid provenance envelope")
	}
	var rawEvents []map[string]json.RawMessage
	if err := json.Unmarshal(envelope["events"], &rawEvents); err != nil || len(rawEvents) != expectedEvents {
		return privacyError("invalid provenance event count")
	}
	for _, event := range rawEvents {
		if !hasExactKeys(event, "generation", "day_utc", "coarse_latitude", "coarse_longitude", "previous_hash", "server_signature") {
			return privacyError("unexpected provenance field")
		}
	}
	var capsule provenanceCapsule
	if err := json.Unmarshal(encoded, &capsule); err != nil {
		return privacyError("invalid provenance capsule")
	}
	if _, err := value.signer.verify(capsule); err != nil {
		return privacyError("invalid provenance signature")
	}
	for index, event := range capsule.Events {
		if event.Generation != index || !validProvenanceEvent(event, index == 0) {
			return privacyError("invalid provenance value")
		}
	}
	return nil
}

func hasExactKeys(values map[string]json.RawMessage, expected ...string) bool {
	if len(values) != len(expected) {
		return false
	}
	for _, key := range expected {
		if _, present := values[key]; !present {
			return false
		}
	}
	return true
}

func validProvenanceEvent(event provenanceEvent, first bool) bool {
	if !validDateOnly(event.DayUTC) || !validCoarseCoordinate(event.CoarseLatitude, 90) || !validCoarseCoordinate(event.CoarseLongitude, 180) {
		return false
	}
	if first {
		if event.PreviousHash != "" {
			return false
		}
	} else {
		decoded, err := hex.DecodeString(event.PreviousHash)
		if err != nil || len(decoded) != 32 || hex.EncodeToString(decoded) != event.PreviousHash {
			return false
		}
	}
	signature, err := base64.RawURLEncoding.DecodeString(event.ServerSignature)
	return err == nil && len(signature) > 0
}

func validDateOnly(value string) bool {
	parsed, err := time.Parse(time.DateOnly, value)
	return err == nil && parsed.Format(time.DateOnly) == value
}

func validCoarseCoordinate(value string, maximum float64) bool {
	if !coarseCoordinatePattern.MatchString(value) {
		return false
	}
	parsed, err := strconv.ParseFloat(value, 64)
	return err == nil && parsed >= -maximum && parsed <= maximum
}

func encodePublicJSON(body any) ([]byte, error) {
	switch value := body.(type) {
	case healthResponse:
		if value.Status != "ok" {
			return nil, privacyError("invalid health response")
		}
	case acknowledgementResponse:
		if value.Status != "acknowledged" {
			return nil, privacyError("invalid acknowledgement response")
		}
	case apiErrorResponse:
		if _, allowed := allowedAPIErrors[value.Error.Code+"\x00"+value.Error.Message]; !allowed {
			return nil, privacyError("invalid error response")
		}
	case publicItem:
		if !validPublicItem(value) {
			return nil, privacyError("invalid public item response")
		}
	case claimedItem:
		if !validClaimedItem(value) {
			return nil, privacyError("invalid claimed item response")
		}
	case itemListResponse:
		for _, item := range value.Items {
			if !validPublicItem(item) {
				return nil, privacyError("invalid public item list")
			}
		}
		for _, cluster := range value.Clusters {
			if cluster.Latitude < -90 || cluster.Latitude > 90 || cluster.Longitude < -180 || cluster.Longitude > 180 || cluster.Count < 1 {
				return nil, privacyError("invalid public cluster")
			}
		}
	default:
		return nil, privacyError("unknown response type")
	}

	payload, err := json.Marshal(body)
	if err != nil {
		return nil, err
	}
	if !hasExactPublicJSONShape(body, payload) {
		return nil, privacyError("unexpected response field")
	}
	return payload, nil
}

func hasExactPublicJSONShape(body any, payload []byte) bool {
	var object map[string]json.RawMessage
	if err := json.Unmarshal(payload, &object); err != nil {
		return false
	}
	switch body.(type) {
	case healthResponse, acknowledgementResponse:
		return hasExactKeys(object, "status")
	case apiErrorResponse:
		if !hasExactKeys(object, "error") {
			return false
		}
		var apiError map[string]json.RawMessage
		return json.Unmarshal(object["error"], &apiError) == nil && hasExactKeys(apiError, "code", "message")
	case publicItem:
		return hasExactPublicItemJSON(object)
	case claimedItem:
		return hasExactKeys(object, "id", "kind", "generation", "provenance_capsule") && hasExactCapsuleJSON(object["provenance_capsule"])
	case itemListResponse:
		if !hasExactKeys(object, "items", "clusters") {
			return false
		}
		var items, clusters []map[string]json.RawMessage
		if json.Unmarshal(object["items"], &items) != nil || json.Unmarshal(object["clusters"], &clusters) != nil {
			return false
		}
		for _, item := range items {
			if !hasExactPublicItemJSON(item) {
				return false
			}
		}
		for _, cluster := range clusters {
			if !hasExactKeys(cluster, "latitude", "longitude", "count") {
				return false
			}
		}
		return true
	default:
		return false
	}
}

func hasExactPublicItemJSON(item map[string]json.RawMessage) bool {
	if !hasExactKeys(item, "id", "kind", "generation", "location", "dropped_day", "public_provenance") {
		return false
	}
	var itemLocation map[string]json.RawMessage
	if json.Unmarshal(item["location"], &itemLocation) != nil || !hasExactKeys(itemLocation, "latitude", "longitude", "accuracy_m") {
		return false
	}
	return hasExactProvenanceEventsJSON(item["public_provenance"])
}

func hasExactCapsuleJSON(raw json.RawMessage) bool {
	var capsule map[string]json.RawMessage
	return json.Unmarshal(raw, &capsule) == nil && hasExactKeys(capsule, "events") && hasExactProvenanceEventsJSON(capsule["events"])
}

func hasExactProvenanceEventsJSON(raw json.RawMessage) bool {
	var events []map[string]json.RawMessage
	if json.Unmarshal(raw, &events) != nil {
		return false
	}
	for _, event := range events {
		if !hasExactKeys(event, "generation", "day_utc", "coarse_latitude", "coarse_longitude", "previous_hash", "server_signature") {
			return false
		}
	}
	return true
}

func validPublicItem(item publicItem) bool {
	if !opaqueIDPattern.MatchString(item.ID) || !item.Kind.valid() || item.Generation < 0 || !item.Location.valid() || !validDateOnly(item.DroppedDay) || len(item.Provenance) != item.Generation+1 {
		return false
	}
	for index, event := range item.Provenance {
		if event.Generation != index || !validProvenanceEvent(event, index == 0) {
			return false
		}
	}
	return true
}

func validClaimedItem(item claimedItem) bool {
	if !opaqueIDPattern.MatchString(item.ID) || !item.Kind.valid() || item.Generation < 1 || len(item.Capsule.Events) != item.Generation {
		return false
	}
	for index, event := range item.Capsule.Events {
		if event.Generation != index || !validProvenanceEvent(event, index == 0) {
			return false
		}
	}
	return true
}
