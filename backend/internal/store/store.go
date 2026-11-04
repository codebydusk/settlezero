package store

import (
	"encoding/json"
	"fmt"
	"sync"
	"time"

	"app.settlezero/backend/internal/model"
)

type Store interface {
	ProcessSyncBatch(ops []model.SyncOperation) []model.SyncResult
	GetSnapshot(groupID string) (*model.GroupSnapshot, error)
	GetGroupByInviteToken(token string) (*model.Group, error)
	SubmitClaim(claim model.MemberClaim) error
	ApproveClaim(groupID, claimID string) error
	GetClaims(groupID string) []model.MemberClaim
}

type MemoryStore struct {
	mu            sync.RWMutex
	groups        map[string]model.Group
	members       map[string][]model.Member
	expenses      map[string][]model.Expense
	settlements   map[string][]model.Settlement
	history       map[string][]model.ActivityHistory
	claims        map[string][]model.MemberClaim
	processedKeys map[string]bool
}

func NewMemoryStore() *MemoryStore {
	return &MemoryStore{
		groups:        make(map[string]model.Group),
		members:       make(map[string][]model.Member),
		expenses:      make(map[string][]model.Expense),
		settlements:   make(map[string][]model.Settlement),
		history:       make(map[string][]model.ActivityHistory),
		claims:        make(map[string][]model.MemberClaim),
		processedKeys: make(map[string]bool),
	}
}

func (s *MemoryStore) ProcessSyncBatch(ops []model.SyncOperation) []model.SyncResult {
	s.mu.Lock()
	defer s.mu.Unlock()

	results := make([]model.SyncResult, 0, len(ops))

	for _, op := range ops {
		// Idempotency check
		if s.processedKeys[op.IdempotencyKey] {
			results = append(results, model.SyncResult{
				IdempotencyKey: op.IdempotencyKey,
				Status:         "DUPLICATE",
			})
			continue
		}

		err := s.applyOperation(op)
		if err != nil {
			results = append(results, model.SyncResult{
				IdempotencyKey: op.IdempotencyKey,
				Status:         "REJECTED",
				ErrorCode:      "OPERATION_FAILED",
				ErrorMessage:   err.Error(),
			})
		} else {
			s.processedKeys[op.IdempotencyKey] = true
			results = append(results, model.SyncResult{
				IdempotencyKey: op.IdempotencyKey,
				Status:         "ACCEPTED",
			})
		}
	}

	return results
}

func (s *MemoryStore) applyOperation(op model.SyncOperation) error {
	switch op.OperationType {
	case "CREATE_GROUP":
		var payload struct {
			GroupID         string `json:"groupId"`
			Name            string `json:"name"`
			CurrencyCode    string `json:"currencyCode"`
			CurrencyScale   int    `json:"currencyScale"`
			CreatorMemberID string `json:"creatorMemberId"`
			CreatorName     string `json:"creatorName"`
			InviteToken     string `json:"inviteToken"`
			CreatedAt       int64  `json:"createdAt"`
		}
		if err := json.Unmarshal([]byte(op.PayloadJSON), &payload); err != nil {
			return err
		}

		now := payload.CreatedAt
		if now == 0 {
			now = time.Now().UnixMilli()
		}

		group := model.Group{
			ID:               payload.GroupID,
			Name:             payload.Name,
			CurrencyCode:     payload.CurrencyCode,
			CurrencyScale:    payload.CurrencyScale,
			Status:           model.GroupStatusActive,
			InviteToken:      payload.InviteToken,
			IsCurrencyLocked: false,
			IsReadOnly:       false,
			CreatedAt:        now,
			UpdatedAt:        now,
		}
		s.groups[group.ID] = group

		creator := model.Member{
			ID:        payload.CreatorMemberID,
			GroupID:   payload.GroupID,
			Name:      payload.CreatorName,
			Role:      model.RoleSuperAdmin,
			Status:    model.StatusActiveClaimed,
			CreatedAt: now,
		}
		s.members[group.ID] = []model.Member{creator}

		s.history[group.ID] = append(s.history[group.ID], model.ActivityHistory{
			ID:            fmt.Sprintf("hist-%d", now),
			GroupID:       group.ID,
			ActorMemberID: creator.ID,
			EventType:     "GROUP_CREATED",
			Summary:       fmt.Sprintf("%s created group '%s'", creator.Name, group.Name),
			EntityID:      group.ID,
			CreatedAt:     now,
		})
		return nil

	case "ADD_MEMBER":
		var payload struct {
			MemberID string `json:"memberId"`
			GroupID  string `json:"groupId"`
			Name     string `json:"name"`
		}
		if err := json.Unmarshal([]byte(op.PayloadJSON), &payload); err != nil {
			return err
		}

		now := op.CreatedAt
		if now == 0 {
			now = time.Now().UnixMilli()
		}

		member := model.Member{
			ID:        payload.MemberID,
			GroupID:   payload.GroupID,
			Name:      payload.Name,
			Role:      model.RoleMember,
			Status:    model.StatusActiveUnclaimed,
			CreatedAt: now,
		}
		s.members[payload.GroupID] = append(s.members[payload.GroupID], member)

		s.history[payload.GroupID] = append(s.history[payload.GroupID], model.ActivityHistory{
			ID:            fmt.Sprintf("hist-%d", now),
			GroupID:       payload.GroupID,
			ActorMemberID: payload.MemberID,
			EventType:     "MEMBER_ADDED",
			Summary:       fmt.Sprintf("Added member %s", member.Name),
			EntityID:      member.ID,
			CreatedAt:     now,
		})
		return nil

	case "CREATE_EXPENSE":
		var exp model.Expense
		if err := json.Unmarshal([]byte(op.PayloadJSON), &exp); err != nil {
			return err
		}
		s.expenses[exp.GroupID] = append(s.expenses[exp.GroupID], exp)

		// lock group currency on first expense
		if g, exists := s.groups[exp.GroupID]; exists && !g.IsCurrencyLocked {
			g.IsCurrencyLocked = true
			s.groups[exp.GroupID] = g
		}

		s.history[exp.GroupID] = append(s.history[exp.GroupID], model.ActivityHistory{
			ID:            fmt.Sprintf("hist-%d", exp.CreatedAt),
			GroupID:       exp.GroupID,
			ActorMemberID: exp.CreatedByMemberID,
			EventType:     "EXPENSE_CREATED",
			Summary:       fmt.Sprintf("Expense added: %s", exp.Description),
			EntityID:      exp.ID,
			CreatedAt:     exp.CreatedAt,
		})
		return nil

	case "DELETE_EXPENSE":
		var payload struct {
			ExpenseID string `json:"expenseId"`
			GroupID   string `json:"groupId"`
			DeletedBy string `json:"deletedByMemberId"`
		}
		if err := json.Unmarshal([]byte(op.PayloadJSON), &payload); err != nil {
			return err
		}

		now := time.Now().UnixMilli()
		expenses := s.expenses[payload.GroupID]
		for i, e := range expenses {
			if e.ID == payload.ExpenseID {
				expenses[i].IsDeleted = true
				expenses[i].DeletedAt = &now
				expenses[i].DeletedByMemberID = &payload.DeletedBy
				break
			}
		}

		s.history[payload.GroupID] = append(s.history[payload.GroupID], model.ActivityHistory{
			ID:            fmt.Sprintf("hist-%d", now),
			GroupID:       payload.GroupID,
			ActorMemberID: payload.DeletedBy,
			EventType:     "EXPENSE_DELETED",
			Summary:       "Deleted an expense",
			EntityID:      payload.ExpenseID,
			CreatedAt:     now,
		})
		return nil

	case "RECORD_SETTLEMENT":
		var set model.Settlement
		if err := json.Unmarshal([]byte(op.PayloadJSON), &set); err != nil {
			return err
		}
		s.settlements[set.GroupID] = append(s.settlements[set.GroupID], set)

		s.history[set.GroupID] = append(s.history[set.GroupID], model.ActivityHistory{
			ID:            fmt.Sprintf("hist-%d", set.CreatedAt),
			GroupID:       set.GroupID,
			ActorMemberID: set.PayerMemberID,
			EventType:     "SETTLEMENT_RECORDED",
			Summary:       "Settlement payment recorded",
			EntityID:      set.ID,
			CreatedAt:     set.CreatedAt,
		})
		return nil

	case "ARCHIVE_GROUP":
		if g, exists := s.groups[op.GroupID]; exists {
			g.Status = model.GroupStatusArchived
			g.UpdatedAt = time.Now().UnixMilli()
			s.groups[op.GroupID] = g
		}
		return nil
	}

	return nil
}

func (s *MemoryStore) GetSnapshot(groupID string) (*model.GroupSnapshot, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	group, exists := s.groups[groupID]
	if !exists {
		return nil, fmt.Errorf("group %s not found", groupID)
	}

	return &model.GroupSnapshot{
		Group:           group,
		Members:         s.members[groupID],
		Expenses:        s.expenses[groupID],
		Settlements:     s.settlements[groupID],
		ActivityHistory: s.history[groupID],
	}, nil
}

func (s *MemoryStore) GetGroupByInviteToken(token string) (*model.Group, error) {
	s.mu.RLock()
	defer s.mu.RUnlock()

	for _, g := range s.groups {
		if g.InviteToken == token {
			return &g, nil
		}
	}
	return nil, fmt.Errorf("invite token %s not found", token)
}

func (s *MemoryStore) SubmitClaim(claim model.MemberClaim) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	s.claims[claim.GroupID] = append(s.claims[claim.GroupID], claim)
	return nil
}

func (s *MemoryStore) ApproveClaim(groupID, claimID string) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	claims := s.claims[groupID]
	var approvedClaim *model.MemberClaim
	for i, c := range claims {
		if c.ID == claimID {
			claims[i].Status = "APPROVED"
			approvedClaim = &claims[i]
			break
		}
	}

	if approvedClaim == nil {
		return fmt.Errorf("claim %s not found", claimID)
	}

	// Update member status to ACTIVE_CLAIMED
	members := s.members[groupID]
	for i, m := range members {
		if m.ID == approvedClaim.MemberID {
			members[i].Status = model.StatusActiveClaimed
			break
		}
	}
	return nil
}

func (s *MemoryStore) GetClaims(groupID string) []model.MemberClaim {
	s.mu.RLock()
	defer s.mu.RUnlock()
	return s.claims[groupID]
}

