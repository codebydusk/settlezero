package store_test

import (
	"os"
	"path/filepath"
	"testing"
	"time"

	"app.settlezero/backend/internal/model"
	"app.settlezero/backend/internal/store"
)

func TestSQLiteStore_CRUDAndIdempotency(t *testing.T) {
	tmpDir := t.TempDir()
	dbPath := filepath.Join(tmpDir, "test_settlezero.db")

	schemaBytes, err := os.ReadFile(filepath.Join("..", "..", "migrations", "001_initial_schema.sql"))
	if err != nil {
		t.Fatalf("Failed to read migration sql: %v", err)
	}

	s, err := store.NewSQLiteStore(dbPath, string(schemaBytes))
	if err != nil {
		t.Fatalf("Failed to initialize sqlite store: %v", err)
	}
	defer s.Close()

	// 1. Create Group
	now := time.Now().UnixMilli()
	grp := model.Group{
		ID:               "grp-sqlite-1",
		Name:             "Persistent Trip",
		CurrencyCode:     "INR",
		CurrencyScale:    2,
		Status:           model.GroupStatusActive,
		InviteToken:      "inv-sqlite-1",
		IsCurrencyLocked: true,
		CreatedAt:        now,
		UpdatedAt:        now,
	}

	if err := s.CreateGroup(grp); err != nil {
		t.Fatalf("CreateGroup failed: %v", err)
	}

	retrieved, err := s.GetGroup("grp-sqlite-1")
	if err != nil || retrieved.Name != "Persistent Trip" {
		t.Fatalf("GetGroup mismatch: %+v, err: %v", retrieved, err)
	}

	byToken, err := s.GetGroupByInviteToken("inv-sqlite-1")
	if err != nil || byToken.ID != "grp-sqlite-1" {
		t.Fatalf("GetGroupByInviteToken mismatch: %+v, err: %v", byToken, err)
	}

	// 2. Add Member
	mem := model.Member{
		ID:        "mem-1",
		GroupID:   "grp-sqlite-1",
		Name:      "Sayantan",
		Role:      model.RoleSuperAdmin,
		Status:    model.StatusActiveClaimed,
		CreatedAt: now,
	}
	if err := s.AddMember(mem); err != nil {
		t.Fatalf("AddMember failed: %v", err)
	}

	members := s.ListMembers("grp-sqlite-1")
	if len(members) != 1 || members[0].Name != "Sayantan" {
		t.Fatalf("ListMembers mismatch: %+v", members)
	}

	// 3. Create Expense with splits
	exp := model.Expense{
		ID:                "exp-1",
		GroupID:           "grp-sqlite-1",
		Description:       "Dinner",
		AmountMinor:       2000,
		PayerMemberID:     "mem-1",
		CreatedByMemberID: "mem-1",
		ExpenseDate:       now,
		CreatedAt:         now,
		SplitType:         model.SplitTypeEqual,
		Allocations: []model.ExpenseSplitAllocation{
			{MemberID: "mem-1", AmountMinor: 2000},
		},
	}
	if err := s.CreateExpense(exp); err != nil {
		t.Fatalf("CreateExpense failed: %v", err)
	}

	expenses := s.ListExpenses("grp-sqlite-1")
	if len(expenses) != 1 || expenses[0].Allocations[0].AmountMinor != 2000 {
		t.Fatalf("ListExpenses mismatch: %+v", expenses)
	}

	// 4. Test Sync Batch Idempotency
	batchOps := []model.SyncOperation{
		{
			IdempotencyKey: "op-unique-1",
			GroupID:        "grp-sqlite-1",
			OperationType:  "ARCHIVE_GROUP",
			PayloadJSON:    "{}",
			CreatedAt:      now,
		},
		{
			IdempotencyKey: "op-unique-1", // duplicate
			GroupID:        "grp-sqlite-1",
			OperationType:  "ARCHIVE_GROUP",
			PayloadJSON:    "{}",
			CreatedAt:      now,
		},
	}

	results := s.ProcessSyncBatch(batchOps)
	if len(results) != 2 {
		t.Fatalf("Expected 2 results, got %d", len(results))
	}
	if results[0].Status != "ACCEPTED" {
		t.Fatalf("First op should be ACCEPTED, got %s", results[0].Status)
	}
	if results[1].Status != "DUPLICATE" {
		t.Fatalf("Second op should be DUPLICATE, got %s", results[1].Status)
	}

	// 5. Verify Snapshot
	snap, err := s.GetSnapshot("grp-sqlite-1")
	if err != nil || snap.Group.Status != model.GroupStatusArchived {
		t.Fatalf("GetSnapshot mismatch: %+v, err: %v", snap, err)
	}
}

