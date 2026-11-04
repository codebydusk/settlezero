package api

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"app.settlezero/backend/internal/model"
	"app.settlezero/backend/internal/store"
)

func setupTestServer() *Server {
	s := store.NewMemoryStore()
	return NewServer(s)
}

func TestHealthCheck(t *testing.T) {
	server := setupTestServer()

	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	w := httptest.NewRecorder()

	server.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", w.Code)
	}

	var res map[string]string
	if err := json.NewDecoder(w.Body).Decode(&res); err != nil {
		t.Fatalf("failed to decode response: %v", err)
	}

	if res["status"] != "ok" {
		t.Fatalf("expected status 'ok', got %s", res["status"])
	}
}

func TestSyncBatchAndSnapshotRoundTrip(t *testing.T) {
	server := setupTestServer()

	// 1. Send sync batch with CREATE_GROUP, ADD_MEMBER, CREATE_EXPENSE
	createGroupPayload := `{"groupId":"grp-1","name":"Goa 2026","currencyCode":"INR","currencyScale":2,"creatorMemberId":"mem-1","creatorName":"Sayantan","inviteToken":"inv-12345"}`
	addMemberPayload := `{"memberId":"mem-2","groupId":"grp-1","name":"Rahul"}`
	createExpensePayload := `{"id":"exp-1","groupId":"grp-1","description":"Seafood Dinner","amountMinor":60000,"payerMemberId":"mem-1","createdByMemberId":"mem-1","expenseDate":1000,"createdAt":1000,"splitType":"EQUAL","allocations":[{"memberId":"mem-1","amountMinor":30000},{"memberId":"mem-2","amountMinor":30000}]}`

	batchReq := model.SyncBatchRequest{
		Operations: []model.SyncOperation{
			{
				IdempotencyKey: "op-1",
				GroupID:        "grp-1",
				OperationType:  "CREATE_GROUP",
				PayloadJSON:    createGroupPayload,
				CreatedAt:      1000,
			},
			{
				IdempotencyKey: "op-2",
				GroupID:        "grp-1",
				OperationType:  "ADD_MEMBER",
				PayloadJSON:    addMemberPayload,
				CreatedAt:      1001,
			},
			{
				IdempotencyKey: "op-3",
				GroupID:        "grp-1",
				OperationType:  "CREATE_EXPENSE",
				PayloadJSON:    createExpensePayload,
				CreatedAt:      1002,
			},
		},
	}

	body, _ := json.Marshal(batchReq)
	req := httptest.NewRequest(http.MethodPost, "/api/v1/sync/batch", bytes.NewReader(body))
	w := httptest.NewRecorder()

	server.ServeHTTP(w, req)

	if w.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", w.Code)
	}

	var batchRes model.SyncBatchResponse
	_ = json.NewDecoder(w.Body).Decode(&batchRes)

	if len(batchRes.Results) != 3 {
		t.Fatalf("expected 3 results, got %d", len(batchRes.Results))
	}
	for _, r := range batchRes.Results {
		if r.Status != "ACCEPTED" {
			t.Fatalf("expected status ACCEPTED, got %s: %s", r.Status, r.ErrorMessage)
		}
	}

	// 2. Test Idempotency with exact same keys
	reqDup := httptest.NewRequest(http.MethodPost, "/api/v1/sync/batch", bytes.NewReader(body))
	wDup := httptest.NewRecorder()
	server.ServeHTTP(wDup, reqDup)

	var dupRes model.SyncBatchResponse
	_ = json.NewDecoder(wDup.Body).Decode(&dupRes)
	for _, r := range dupRes.Results {
		if r.Status != "DUPLICATE" {
			t.Fatalf("expected DUPLICATE for resubmitted key, got %s", r.Status)
		}
	}

	// 3. Get Group Snapshot
	reqSnap := httptest.NewRequest(http.MethodGet, "/api/v1/groups/grp-1/snapshot", nil)
	wSnap := httptest.NewRecorder()
	server.ServeHTTP(wSnap, reqSnap)

	if wSnap.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d: %s", wSnap.Code, wSnap.Body.String())
	}

	var snapshot model.GroupSnapshot
	_ = json.NewDecoder(wSnap.Body).Decode(&snapshot)

	if snapshot.Group.Name != "Goa 2026" {
		t.Fatalf("expected group name 'Goa 2026', got '%s'", snapshot.Group.Name)
	}
	if len(snapshot.Members) != 2 {
		t.Fatalf("expected 2 members, got %d", len(snapshot.Members))
	}
	if len(snapshot.Expenses) != 1 {
		t.Fatalf("expected 1 expense, got %d", len(snapshot.Expenses))
	}

	// 4. Test Privacy-Preserving Invite Lookup
	reqInv := httptest.NewRequest(http.MethodGet, "/api/v1/invites/inv-12345", nil)
	wInv := httptest.NewRecorder()
	server.ServeHTTP(wInv, reqInv)

	if wInv.Code != http.StatusOK {
		t.Fatalf("expected status 200, got %d", wInv.Code)
	}

	var inviteRes map[string]any
	_ = json.NewDecoder(wInv.Body).Decode(&inviteRes)

	if inviteRes["groupName"] != "Goa 2026" {
		t.Fatalf("expected invite group name 'Goa 2026', got %v", inviteRes["groupName"])
	}
	// Verify privacy: members and balances must NOT be in invite preview
	if _, hasMembers := inviteRes["members"]; hasMembers {
		t.Fatal("privacy leak: members must not be exposed in invite lookup")
	}
}

func TestSASClaimAndApprovalFlow(t *testing.T) {
	server := setupTestServer()

	// Initialize group and an unclaimed member
	server.store.ProcessSyncBatch([]model.SyncOperation{
		{
			IdempotencyKey: "setup-1",
			GroupID:        "grp-claim",
			OperationType:  "CREATE_GROUP",
			PayloadJSON:    `{"groupId":"grp-claim","name":"Flat 202","currencyCode":"INR","currencyScale":2,"creatorMemberId":"mem-admin","creatorName":"Admin","inviteToken":"tok-202"}`,
		},
		{
			IdempotencyKey: "setup-2",
			GroupID:        "grp-claim",
			OperationType:  "ADD_MEMBER",
			PayloadJSON:    `{"memberId":"mem-vikram","groupId":"grp-claim","name":"Vikram"}`,
		},
	})

	// 1. Submit claim with SAS code 7294
	claimBody, _ := json.Marshal(map[string]string{
		"memberId":         "mem-vikram",
		"claimantDeviceId": "device-uuid-999",
		"sasCode":          "7294",
	})
	reqClaim := httptest.NewRequest(http.MethodPost, "/api/v1/groups/grp-claim/claims", bytes.NewReader(claimBody))
	wClaim := httptest.NewRecorder()
	server.ServeHTTP(wClaim, reqClaim)

	if wClaim.Code != http.StatusOK {
		t.Fatalf("expected claim status 200, got %d", wClaim.Code)
	}

	var claim model.MemberClaim
	_ = json.NewDecoder(wClaim.Body).Decode(&claim)
	if claim.SASCode != "7294" {
		t.Fatalf("expected SAS code 7294, got %s", claim.SASCode)
	}

	// 2. Admin approves claim
	reqApprove := httptest.NewRequest(http.MethodPost, "/api/v1/groups/grp-claim/claims/"+claim.ID+"/approve", nil)
	wApprove := httptest.NewRecorder()
	server.ServeHTTP(wApprove, reqApprove)

	if wApprove.Code != http.StatusOK {
		t.Fatalf("expected approve status 200, got %d: %s", wApprove.Code, wApprove.Body.String())
	}

	// Verify member is now ACTIVE_CLAIMED
	snap, _ := server.store.GetSnapshot("grp-claim")
	for _, m := range snap.Members {
		if m.ID == "mem-vikram" {
			if m.Status != model.StatusActiveClaimed {
				t.Fatalf("expected member status ACTIVE_CLAIMED, got %s", m.Status)
			}
			return
		}
	}
	t.Fatal("member not found in snapshot")
}

