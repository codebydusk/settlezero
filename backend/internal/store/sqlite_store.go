package store

import (
	"database/sql"
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"app.settlezero/backend/internal/model"
	_ "modernc.org/sqlite"
)

type SQLiteStore struct {
	db *sql.DB
	mu sync.RWMutex
}

func NewSQLiteStore(dbPath string, schemaSQL string) (*SQLiteStore, error) {
	db, err := sql.Open("sqlite", dbPath)
	if err != nil {
		return nil, fmt.Errorf("failed to open sqlite database: %w", err)
	}

	if _, err := db.Exec("PRAGMA foreign_keys = ON;"); err != nil {
		db.Close()
		return nil, fmt.Errorf("failed to enable foreign keys: %w", err)
	}

	if schemaSQL != "" {
		if _, err := db.Exec(schemaSQL); err != nil {
			db.Close()
			return nil, fmt.Errorf("failed to execute schema: %w", err)
		}
	}

	return &SQLiteStore{db: db}, nil
}

func (s *SQLiteStore) Close() error {
	return s.db.Close()
}

func (s *SQLiteStore) CreateGroup(group model.Group) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `
		INSERT INTO groups (id, name, currency_code, currency_scale, status, invite_token, is_currency_locked, is_read_only, created_at, updated_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
	`
	locked := 0
	if group.IsCurrencyLocked {
		locked = 1
	}
	readOnly := 0
	if group.IsReadOnly {
		readOnly = 1
	}

	_, err := s.db.Exec(query, group.ID, group.Name, group.CurrencyCode, group.CurrencyScale, string(group.Status), group.InviteToken, locked, readOnly, group.CreatedAt, group.UpdatedAt)
	return err
}

func (s *SQLiteStore) GetGroup(id string) (model.Group, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `SELECT id, name, currency_code, currency_scale, status, invite_token, is_currency_locked, is_read_only, created_at, updated_at FROM groups WHERE id = ?;`
	row := s.db.QueryRow(query, id)

	var g model.Group
	var status string
	var locked, readOnly int
	err := row.Scan(&g.ID, &g.Name, &g.CurrencyCode, &g.CurrencyScale, &status, &g.InviteToken, &locked, &readOnly, &g.CreatedAt, &g.UpdatedAt)
	if err != nil {
		return model.Group{}, fmt.Errorf("group not found: %s", id)
	}
	g.Status = model.GroupStatus(status)
	g.IsCurrencyLocked = (locked == 1)
	g.IsReadOnly = (readOnly == 1)
	return g, nil
}

func (s *SQLiteStore) GetGroupByInviteToken(token string) (*model.Group, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `SELECT id, name, currency_code, currency_scale, status, invite_token, is_currency_locked, is_read_only, created_at, updated_at FROM groups WHERE invite_token = ?;`
	row := s.db.QueryRow(query, token)

	var g model.Group
	var status string
	var locked, readOnly int
	err := row.Scan(&g.ID, &g.Name, &g.CurrencyCode, &g.CurrencyScale, &status, &g.InviteToken, &locked, &readOnly, &g.CreatedAt, &g.UpdatedAt)
	if err != nil {
		return nil, fmt.Errorf("group with token not found: %s", token)
	}
	g.Status = model.GroupStatus(status)
	g.IsCurrencyLocked = (locked == 1)
	g.IsReadOnly = (readOnly == 1)
	return &g, nil
}

func (s *SQLiteStore) ListGroups() []model.Group {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(`SELECT id, name, currency_code, currency_scale, status, invite_token, is_currency_locked, is_read_only, created_at, updated_at FROM groups ORDER BY updated_at DESC;`)
	if err != nil {
		return []model.Group{}
	}
	defer rows.Close()

	var list []model.Group
	for rows.Next() {
		var g model.Group
		var status string
		var locked, readOnly int
		if err := rows.Scan(&g.ID, &g.Name, &g.CurrencyCode, &g.CurrencyScale, &status, &g.InviteToken, &locked, &readOnly, &g.CreatedAt, &g.UpdatedAt); err == nil {
			g.Status = model.GroupStatus(status)
			g.IsCurrencyLocked = (locked == 1)
			g.IsReadOnly = (readOnly == 1)
			list = append(list, g)
		}
	}
	return list
}

func (s *SQLiteStore) AddMember(member model.Member) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `INSERT INTO members (id, group_id, name, role, status, created_at) VALUES (?, ?, ?, ?, ?, ?);`
	_, err := s.db.Exec(query, member.ID, member.GroupID, member.Name, string(member.Role), string(member.Status), member.CreatedAt)
	return err
}

func (s *SQLiteStore) GetMember(id string) (model.Member, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `SELECT id, group_id, name, role, status, created_at FROM members WHERE id = ?;`
	row := s.db.QueryRow(query, id)

	var m model.Member
	var role, status string
	err := row.Scan(&m.ID, &m.GroupID, &m.Name, &role, &status, &m.CreatedAt)
	if err != nil {
		return model.Member{}, fmt.Errorf("member not found: %s", id)
	}
	m.Role = model.MemberRole(role)
	m.Status = model.MemberStatus(status)
	return m, nil
}

func (s *SQLiteStore) ListMembers(groupID string) []model.Member {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(`SELECT id, group_id, name, role, status, created_at FROM members WHERE group_id = ? ORDER BY created_at ASC;`, groupID)
	if err != nil {
		return []model.Member{}
	}
	defer rows.Close()

	var list []model.Member
	for rows.Next() {
		var m model.Member
		var role, status string
		if err := rows.Scan(&m.ID, &m.GroupID, &m.Name, &role, &status, &m.CreatedAt); err == nil {
			m.Role = model.MemberRole(role)
			m.Status = model.MemberStatus(status)
			list = append(list, m)
		}
	}
	return list
}

func (s *SQLiteStore) CreateExpense(expense model.Expense) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	query := `
		INSERT INTO expenses (id, group_id, description, amount_minor, payer_member_id, created_by_member_id, expense_date, created_at, note, split_type, is_deleted)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0);
	`
	_, err = tx.Exec(query, expense.ID, expense.GroupID, expense.Description, expense.AmountMinor, expense.PayerMemberID, expense.CreatedByMemberID, expense.ExpenseDate, expense.CreatedAt, expense.Note, string(expense.SplitType))
	if err != nil {
		return err
	}

	splitQuery := `INSERT INTO expense_splits (expense_id, member_id, amount_minor) VALUES (?, ?, ?);`
	for _, alloc := range expense.Allocations {
		if _, err := tx.Exec(splitQuery, expense.ID, alloc.MemberID, alloc.AmountMinor); err != nil {
			return err
		}
	}

	return tx.Commit()
}

func (s *SQLiteStore) DeleteExpense(expenseID, deletedByMemberID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	now := time.Now().UnixMilli()
	query := `UPDATE expenses SET is_deleted = 1, deleted_at = ?, deleted_by_member_id = ? WHERE id = ?;`
	_, err := s.db.Exec(query, now, deletedByMemberID, expenseID)
	return err
}

func (s *SQLiteStore) ListExpenses(groupID string) []model.Expense {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `
		SELECT id, group_id, description, amount_minor, payer_member_id, created_by_member_id, expense_date, created_at, note, split_type, is_deleted, deleted_at, deleted_by_member_id
		FROM expenses WHERE group_id = ? ORDER BY expense_date DESC, created_at DESC;
	`
	rows, err := s.db.Query(query, groupID)
	if err != nil {
		return []model.Expense{}
	}
	defer rows.Close()

	var list []model.Expense
	for rows.Next() {
		var exp model.Expense
		var splitType string
		var isDeleted int
		if err := rows.Scan(&exp.ID, &exp.GroupID, &exp.Description, &exp.AmountMinor, &exp.PayerMemberID, &exp.CreatedByMemberID, &exp.ExpenseDate, &exp.CreatedAt, &exp.Note, &splitType, &isDeleted, &exp.DeletedAt, &exp.DeletedByMemberID); err == nil {
			exp.SplitType = model.SplitType(splitType)
			exp.IsDeleted = (isDeleted == 1)

			// Load splits
			splitRows, sErr := s.db.Query(`SELECT member_id, amount_minor FROM expense_splits WHERE expense_id = ?;`, exp.ID)
			if sErr == nil {
				for splitRows.Next() {
					var alloc model.ExpenseSplitAllocation
					if err := splitRows.Scan(&alloc.MemberID, &alloc.AmountMinor); err == nil {
						exp.Allocations = append(exp.Allocations, alloc)
					}
				}
				splitRows.Close()
			}

			list = append(list, exp)
		}
	}
	return list
}

func (s *SQLiteStore) RecordSettlement(settlement model.Settlement) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `
		INSERT INTO settlements (id, group_id, payer_member_id, recipient_member_id, amount_minor, settlement_date, created_at, recorded_by_member_id, note, is_deleted)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0);
	`
	_, err := s.db.Exec(query, settlement.ID, settlement.GroupID, settlement.PayerMemberID, settlement.RecipientMemberID, settlement.AmountMinor, settlement.SettlementDate, settlement.CreatedAt, settlement.RecordedByMemberID, settlement.Note)
	return err
}

func (s *SQLiteStore) ListSettlements(groupID string) []model.Settlement {
	s.mu.RLock()
	defer s.mu.RUnlock()

	query := `
		SELECT id, group_id, payer_member_id, recipient_member_id, amount_minor, settlement_date, created_at, recorded_by_member_id, note, is_deleted, deleted_at, deleted_by_member_id
		FROM settlements WHERE group_id = ? ORDER BY settlement_date DESC, created_at DESC;
	`
	rows, err := s.db.Query(query, groupID)
	if err != nil {
		return []model.Settlement{}
	}
	defer rows.Close()

	var list []model.Settlement
	for rows.Next() {
		var st model.Settlement
		var isDeleted int
		if err := rows.Scan(&st.ID, &st.GroupID, &st.PayerMemberID, &st.RecipientMemberID, &st.AmountMinor, &st.SettlementDate, &st.CreatedAt, &st.RecordedByMemberID, &st.Note, &isDeleted, &st.DeletedAt, &st.DeletedByMemberID); err == nil {
			st.IsDeleted = (isDeleted == 1)
			list = append(list, st)
		}
	}
	return list
}

func (s *SQLiteStore) LogActivity(activity model.ActivityHistory) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `INSERT INTO activity_history (id, group_id, actor_member_id, event_type, summary, entity_id, created_at) VALUES (?, ?, ?, ?, ?, ?, ?);`
	_, err := s.db.Exec(query, activity.ID, activity.GroupID, activity.ActorMemberID, activity.EventType, activity.Summary, activity.EntityID, activity.CreatedAt)
	return err
}

func (s *SQLiteStore) ListActivities(groupID string) []model.ActivityHistory {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(`SELECT id, group_id, actor_member_id, event_type, summary, entity_id, created_at FROM activity_history WHERE group_id = ? ORDER BY created_at DESC;`, groupID)
	if err != nil {
		return []model.ActivityHistory{}
	}
	defer rows.Close()

	var list []model.ActivityHistory
	for rows.Next() {
		var a model.ActivityHistory
		if err := rows.Scan(&a.ID, &a.GroupID, &a.ActorMemberID, &a.EventType, &a.Summary, &a.EntityID, &a.CreatedAt); err == nil {
			list = append(list, a)
		}
	}
	return list
}

func (s *SQLiteStore) SubmitClaim(claim model.MemberClaim) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	query := `INSERT INTO member_claims (id, group_id, member_id, claimant_device_id, sas_code, status, created_at) VALUES (?, ?, ?, ?, ?, ?, ?);`
	_, err := s.db.Exec(query, claim.ID, claim.GroupID, claim.MemberID, claim.ClaimantDeviceID, claim.SASCode, claim.Status, claim.CreatedAt)
	return err
}

func (s *SQLiteStore) ApproveClaim(groupID, claimID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	var memberID string
	err := s.db.QueryRow(`SELECT member_id FROM member_claims WHERE id = ? AND group_id = ?;`, claimID, groupID).Scan(&memberID)
	if err != nil {
		return fmt.Errorf("claim not found: %s", claimID)
	}

	tx, err := s.db.Begin()
	if err != nil {
		return err
	}
	defer tx.Rollback()

	if _, err := tx.Exec(`UPDATE member_claims SET status = 'APPROVED' WHERE id = ?;`, claimID); err != nil {
		return err
	}
	if _, err := tx.Exec(`UPDATE members SET status = 'ACTIVE_CLAIMED' WHERE id = ?;`, memberID); err != nil {
		return err
	}

	return tx.Commit()
}

func (s *SQLiteStore) GetClaims(groupID string) []model.MemberClaim {
	s.mu.RLock()
	defer s.mu.RUnlock()

	rows, err := s.db.Query(`SELECT id, group_id, member_id, claimant_device_id, sas_code, status, created_at FROM member_claims WHERE group_id = ?;`, groupID)
	if err != nil {
		return []model.MemberClaim{}
	}
	defer rows.Close()

	var list []model.MemberClaim
	for rows.Next() {
		var c model.MemberClaim
		if err := rows.Scan(&c.ID, &c.GroupID, &c.MemberID, &c.ClaimantDeviceID, &c.SASCode, &c.Status, &c.CreatedAt); err == nil {
			list = append(list, c)
		}
	}
	return list
}

func (s *SQLiteStore) GetSnapshot(groupID string) (*model.GroupSnapshot, error) {
	group, err := s.GetGroup(groupID)
	if err != nil {
		return nil, err
	}

	snapshot := &model.GroupSnapshot{
		Group:           group,
		Members:         s.ListMembers(groupID),
		Expenses:        s.ListExpenses(groupID),
		Settlements:     s.ListSettlements(groupID),
		ActivityHistory: s.ListActivities(groupID),
	}
	return snapshot, nil
}

func (s *SQLiteStore) ProcessSyncBatch(operations []model.SyncOperation) []model.SyncResult {
	results := make([]model.SyncResult, 0, len(operations))

	for _, op := range operations {
		// Idempotency check
		var existingStatus string
		err := s.db.QueryRow(`SELECT status FROM processed_operations WHERE idempotency_key = ?;`, op.IdempotencyKey).Scan(&existingStatus)
		if err == nil {
			results = append(results, model.SyncResult{
				IdempotencyKey: op.IdempotencyKey,
				Status:         "DUPLICATE",
			})
			continue
		}

		res := s.applyOperation(op)
		results = append(results, res)

		// Record in processed_operations
		_, _ = s.db.Exec(
			`INSERT INTO processed_operations (idempotency_key, status, error_code, error_message, created_at) VALUES (?, ?, ?, ?, ?);`,
			op.IdempotencyKey, res.Status, res.ErrorCode, res.ErrorMessage, time.Now().UnixMilli(),
		)
	}

	return results
}

func (s *SQLiteStore) applyOperation(op model.SyncOperation) model.SyncResult {
	switch op.OperationType {
	case "CREATE_GROUP":
		var group model.Group
		if err := json.Unmarshal([]byte(op.PayloadJSON), &group); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "INVALID_JSON", ErrorMessage: err.Error()}
		}
		if err := s.CreateGroup(group); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "CREATE_FAILED", ErrorMessage: err.Error()}
		}

	case "ADD_MEMBER":
		var member model.Member
		if err := json.Unmarshal([]byte(op.PayloadJSON), &member); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "INVALID_JSON", ErrorMessage: err.Error()}
		}
		if err := s.AddMember(member); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "ADD_FAILED", ErrorMessage: err.Error()}
		}

	case "CREATE_EXPENSE":
		var expense model.Expense
		if err := json.Unmarshal([]byte(op.PayloadJSON), &expense); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "INVALID_JSON", ErrorMessage: err.Error()}
		}
		if err := s.CreateExpense(expense); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "CREATE_EXPENSE_FAILED", ErrorMessage: err.Error()}
		}

	case "DELETE_EXPENSE":
		var req struct {
			ExpenseID         string `json:"expenseId"`
			DeletedByMemberID string `json:"deletedByMemberId"`
		}
		if err := json.Unmarshal([]byte(op.PayloadJSON), &req); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "INVALID_JSON", ErrorMessage: err.Error()}
		}
		if err := s.DeleteExpense(req.ExpenseID, req.DeletedByMemberID); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "DELETE_FAILED", ErrorMessage: err.Error()}
		}

	case "RECORD_SETTLEMENT":
		var settlement model.Settlement
		if err := json.Unmarshal([]byte(op.PayloadJSON), &settlement); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "INVALID_JSON", ErrorMessage: err.Error()}
		}
		if err := s.RecordSettlement(settlement); err != nil {
			return model.SyncResult{IdempotencyKey: op.IdempotencyKey, Status: "REJECTED", ErrorCode: "RECORD_FAILED", ErrorMessage: err.Error()}
		}

	case "ARCHIVE_GROUP":
		s.mu.Lock()
		_, _ = s.db.Exec(`UPDATE groups SET status = 'ARCHIVED', is_read_only = 1 WHERE id = ?;`, op.GroupID)
		s.mu.Unlock()

	default:
		return model.SyncResult{
			IdempotencyKey: op.IdempotencyKey,
			Status:         "REJECTED",
			ErrorCode:      "UNKNOWN_OPERATION",
			ErrorMessage:   fmt.Sprintf("Unknown operation type: %s", op.OperationType),
		}
	}

	return model.SyncResult{
		IdempotencyKey: op.IdempotencyKey,
		Status:         "ACCEPTED",
	}
}

// Ensure interface compliance
var _ Store = (*SQLiteStore)(nil)

