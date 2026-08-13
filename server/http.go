package main

import (
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strconv"
	"strings"
)

type api struct {
	store *store
}

func (value *api) handler() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("GET /healthz", value.health)
	mux.HandleFunc("GET /v1/healthz", value.health)
	mux.HandleFunc("GET /v1/items", value.listItems)
	mux.HandleFunc("GET /v1/items/{id}", value.getItem)
	mux.HandleFunc("POST /v1/items/{id}/drop", value.dropItem)
	mux.HandleFunc("POST /v1/items/{id}/claim", value.claimItem)
	mux.HandleFunc("POST /v1/items/{id}/claim/recover", value.recoverClaim)
	mux.HandleFunc("POST /v1/items/{id}/claim/ack", value.acknowledgeClaim)
	return securityHeaders(limitBody(mux))
}

func (value *api) health(response http.ResponseWriter, _ *http.Request) {
	writeJSON(response, http.StatusOK, map[string]string{"status": "ok"})
}

func (value *api) listItems(response http.ResponseWriter, request *http.Request) {
	bounds, err := parseBounds(request.URL.Query().Get("bbox"))
	if err != nil {
		writeAPIError(response, http.StatusBadRequest, "invalid_bbox", "bbox muss west,south,east,north enthalten")
		return
	}
	zoom, err := strconv.ParseFloat(request.URL.Query().Get("zoom"), 64)
	if err != nil || zoom < 0 || zoom > 24 {
		writeAPIError(response, http.StatusBadRequest, "invalid_zoom", "zoom muss zwischen 0 und 24 liegen")
		return
	}
	items, err := value.store.listPublic(request.Context(), bounds)
	if err != nil {
		writeAPIError(response, http.StatusInternalServerError, "internal", "Items konnten nicht geladen werden")
		return
	}
	if zoom >= 12 {
		writeJSON(response, http.StatusOK, map[string]any{"items": items, "clusters": []any{}})
		return
	}
	writeJSON(response, http.StatusOK, map[string]any{"items": []any{}, "clusters": clusterItems(items, zoom)})
}

func (value *api) getItem(response http.ResponseWriter, request *http.Request) {
	record, err := readItem(request.Context(), value.store.db, request.PathValue("id"))
	if err != nil || record.State != statePublic {
		writeAPIError(response, http.StatusNotFound, "not_found", "Item ist nicht öffentlich")
		return
	}
	item, err := record.public()
	if err != nil {
		writeAPIError(response, http.StatusInternalServerError, "internal", "Item konnte nicht geladen werden")
		return
	}
	writeJSON(response, http.StatusOK, item)
}

type dropRequest struct {
	Kind             itemKind          `json:"kind"`
	Generation       int               `json:"generation"`
	CapabilitySecret string            `json:"capability_secret"`
	Capsule          provenanceCapsule `json:"provenance_capsule"`
	Location         location          `json:"location"`
	IdempotencyID    string            `json:"idempotency_id"`
}

func (value *api) dropItem(response http.ResponseWriter, request *http.Request) {
	var body dropRequest
	if !decodeJSON(response, request, &body) {
		return
	}
	id := request.PathValue("id")
	if !itemIDPattern.MatchString(id) || !body.Kind.valid() || !body.Location.valid() || body.IdempotencyID == "" {
		writeAPIError(response, http.StatusBadRequest, "invalid_drop", "Ablageangaben sind ungültig")
		return
	}
	item, err := value.store.drop(request.Context(), dropInput{
		ID: id, Kind: body.Kind, Generation: body.Generation, CapabilitySecret: body.CapabilitySecret,
		Capsule: body.Capsule, Location: body.Location, IdempotencyID: body.IdempotencyID,
	})
	if err != nil {
		writeStoreError(response, err)
		return
	}
	writeJSON(response, http.StatusOK, item)
}

type claimRequest struct {
	NewCapabilityHash string   `json:"new_capability_hash"`
	Location          location `json:"location"`
	IdempotencyID     string   `json:"idempotency_id"`
}

func (value *api) claimItem(response http.ResponseWriter, request *http.Request) {
	var body claimRequest
	if !decodeJSON(response, request, &body) {
		return
	}
	hash, err := decodeHash(body.NewCapabilityHash)
	if err != nil || !body.Location.valid() || body.IdempotencyID == "" {
		writeAPIError(response, http.StatusBadRequest, "invalid_claim", "Aufnahmeangaben sind ungültig")
		return
	}
	item, err := value.store.claim(request.Context(), claimInput{
		ID: request.PathValue("id"), NewCapabilityHash: hash,
		Location: body.Location, IdempotencyID: body.IdempotencyID,
	})
	if err != nil {
		writeStoreError(response, err)
		return
	}
	writeJSON(response, http.StatusOK, item)
}

type claimContinuationRequest struct {
	CapabilitySecret string `json:"capability_secret"`
	IdempotencyID    string `json:"idempotency_id"`
}

func (value *api) recoverClaim(response http.ResponseWriter, request *http.Request) {
	var body claimContinuationRequest
	if !decodeJSON(response, request, &body) {
		return
	}
	item, err := value.store.recover(request.Context(), request.PathValue("id"), body.IdempotencyID, body.CapabilitySecret)
	if err != nil {
		writeStoreError(response, err)
		return
	}
	writeJSON(response, http.StatusOK, item)
}

func (value *api) acknowledgeClaim(response http.ResponseWriter, request *http.Request) {
	var body claimContinuationRequest
	if !decodeJSON(response, request, &body) {
		return
	}
	if err := value.store.acknowledge(request.Context(), request.PathValue("id"), body.IdempotencyID, body.CapabilitySecret); err != nil {
		writeStoreError(response, err)
		return
	}
	writeJSON(response, http.StatusOK, map[string]string{"status": "acknowledged"})
}

type publicCluster struct {
	Latitude  float64 `json:"latitude"`
	Longitude float64 `json:"longitude"`
	Count     int     `json:"count"`
}

func clusterItems(items []publicItem, zoom float64) []publicCluster {
	cellSize := 360.0 / float64(uint64(1)<<uint(min(int(zoom)+2, 22)))
	type accumulator struct {
		latitude, longitude float64
		count               int
	}
	groups := map[string]accumulator{}
	for _, item := range items {
		latCell := int((item.Location.Latitude + 90) / cellSize)
		lonCell := int((item.Location.Longitude + 180) / cellSize)
		key := fmt.Sprintf("%d:%d", latCell, lonCell)
		group := groups[key]
		group.latitude += item.Location.Latitude
		group.longitude += item.Location.Longitude
		group.count++
		groups[key] = group
	}
	result := make([]publicCluster, 0, len(groups))
	for _, group := range groups {
		result = append(result, publicCluster{
			Latitude: group.latitude / float64(group.count), Longitude: group.longitude / float64(group.count), Count: group.count,
		})
	}
	return result
}

func parseBounds(raw string) (itemBounds, error) {
	parts := strings.Split(raw, ",")
	if len(parts) != 4 {
		return itemBounds{}, errors.New("invalid bounds")
	}
	values := make([]float64, 4)
	for index, part := range parts {
		parsed, err := strconv.ParseFloat(part, 64)
		if err != nil {
			return itemBounds{}, err
		}
		values[index] = parsed
	}
	if values[0] < -180 || values[2] > 180 || values[1] < -90 || values[3] > 90 || values[0] > values[2] || values[1] > values[3] {
		return itemBounds{}, errors.New("invalid bounds")
	}
	return itemBounds{West: values[0], South: values[1], East: values[2], North: values[3]}, nil
}

func decodeJSON(response http.ResponseWriter, request *http.Request, destination any) bool {
	decoder := json.NewDecoder(request.Body)
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(destination); err != nil {
		writeAPIError(response, http.StatusBadRequest, "invalid_json", "JSON-Anfrage ist ungültig")
		return false
	}
	return true
}

func writeStoreError(response http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, errNotFound):
		writeAPIError(response, http.StatusNotFound, "not_found", "Item wurde nicht gefunden")
	case errors.Is(err, errAlreadyClaimed):
		writeAPIError(response, http.StatusConflict, "already_claimed", "Schon gefunden")
	case errors.Is(err, errOutOfRange):
		writeAPIError(response, http.StatusConflict, "out_of_range", "Item ist mehr als 10 m entfernt")
	case errors.Is(err, errUnauthorized):
		writeAPIError(response, http.StatusUnauthorized, "invalid_capability", "Besitznachweis ist ungültig")
	case errors.Is(err, errConflict):
		writeAPIError(response, http.StatusConflict, "item_conflict", "Itemzustand hat sich geändert")
	default:
		writeAPIError(response, http.StatusInternalServerError, "internal", "Anfrage konnte nicht verarbeitet werden")
	}
}

func writeAPIError(response http.ResponseWriter, status int, code, message string) {
	writeJSON(response, status, map[string]any{"error": map[string]string{"code": code, "message": message}})
}

func writeJSON(response http.ResponseWriter, status int, body any) {
	response.Header().Set("Content-Type", "application/json; charset=utf-8")
	response.WriteHeader(status)
	_ = json.NewEncoder(response).Encode(body)
}

func limitBody(next http.Handler) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		request.Body = http.MaxBytesReader(response, request.Body, 256<<10)
		next.ServeHTTP(response, request)
	})
}

func securityHeaders(next http.Handler) http.Handler {
	return http.HandlerFunc(func(response http.ResponseWriter, request *http.Request) {
		response.Header().Set("Cache-Control", "no-store")
		response.Header().Set("X-Content-Type-Options", "nosniff")
		next.ServeHTTP(response, request)
	})
}
