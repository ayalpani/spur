package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHTTPDropListDetailAndClaimValidation(t *testing.T) {
	itemStore := newTestStore(t)
	handler := (&api{store: itemStore}).handler()

	dropBody := map[string]any{
		"kind": "STRAWBERRY", "generation": 0,
		"capability_secret":  testSecret(1),
		"provenance_capsule": map[string]any{"events": []any{}},
		"location":           map[string]any{"latitude": 52.52, "longitude": 13.405, "accuracy_m": 3},
		"idempotency_id":     "drop-1",
	}
	response := performJSON(t, handler, http.MethodPost, "/v1/items/"+testItemID+"/drop", dropBody)
	if response.Code != http.StatusOK {
		t.Fatalf("drop status=%d body=%s", response.Code, response.Body.String())
	}

	listRequest := httptest.NewRequest(http.MethodGet, "/v1/items?bbox=13,52,14,53&zoom=15", nil)
	listResponse := httptest.NewRecorder()
	handler.ServeHTTP(listResponse, listRequest)
	if listResponse.Code != http.StatusOK || !bytes.Contains(listResponse.Body.Bytes(), []byte(testItemID)) {
		t.Fatalf("list status=%d body=%s", listResponse.Code, listResponse.Body.String())
	}

	detailRequest := httptest.NewRequest(http.MethodGet, "/v1/items/"+testItemID, nil)
	detailResponse := httptest.NewRecorder()
	handler.ServeHTTP(detailResponse, detailRequest)
	if detailResponse.Code != http.StatusOK || !bytes.Contains(detailResponse.Body.Bytes(), []byte("52.52")) {
		t.Fatalf("detail status=%d body=%s", detailResponse.Code, detailResponse.Body.String())
	}

	invalidClaim := map[string]any{
		"new_capability_hash": "invalid",
		"location":            map[string]any{"latitude": 52.52, "longitude": 13.405, "accuracy_m": 30},
		"idempotency_id":      "claim-1",
	}
	claimResponse := performJSON(t, handler, http.MethodPost, "/v1/items/"+testItemID+"/claim", invalidClaim)
	if claimResponse.Code != http.StatusBadRequest || !bytes.Contains(claimResponse.Body.Bytes(), []byte("invalid_claim")) {
		t.Fatalf("claim status=%d body=%s", claimResponse.Code, claimResponse.Body.String())
	}
}

func TestLowZoomReturnsClusters(t *testing.T) {
	itemStore := newTestStore(t)
	dropTestItem(t, itemStore, testItemID, testSecret(1))
	handler := (&api{store: itemStore}).handler()
	request := httptest.NewRequest(http.MethodGet, "/v1/items?bbox=-180,-90,180,90&zoom=4", nil)
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	if response.Code != http.StatusOK || !bytes.Contains(response.Body.Bytes(), []byte(`"count":1`)) {
		t.Fatalf("status=%d body=%s", response.Code, response.Body.String())
	}
}

func performJSON(t *testing.T, handler http.Handler, method, path string, body any) *httptest.ResponseRecorder {
	t.Helper()
	encoded, err := json.Marshal(body)
	if err != nil {
		t.Fatal(err)
	}
	request := httptest.NewRequest(method, path, bytes.NewReader(encoded))
	request.Header.Set("Content-Type", "application/json")
	response := httptest.NewRecorder()
	handler.ServeHTTP(response, request)
	return response
}
