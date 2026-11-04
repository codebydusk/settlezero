package model

type GroupStatus string

const (
	GroupStatusActive   GroupStatus = "ACTIVE"
	GroupStatusArchived GroupStatus = "ARCHIVED"
)

type MemberRole string

const (
	RoleSuperAdmin MemberRole = "SUPER_ADMIN"
	RoleAdmin      MemberRole = "ADMIN"
	RoleMember     MemberRole = "MEMBER"
)

type MemberStatus string

const (
	StatusActiveClaimed   MemberStatus = "ACTIVE_CLAIMED"
	StatusActiveUnclaimed MemberStatus = "ACTIVE_UNCLAIMED"
	StatusArchived        MemberStatus = "ARCHIVED"
)

type SplitType string

const (
	SplitTypeEqual   SplitType = "EQUAL"
	SplitTypeUnequal SplitType = "UNEQUAL"
)

type Group struct {
	ID               string      `json:"id"`
	Name             string      `json:"name"`
	CurrencyCode     string      `json:"currencyCode"`
	CurrencyScale    int         `json:"currencyScale"`
	Status           GroupStatus `json:"status"`
	InviteToken      string      `json:"inviteToken"`
	IsCurrencyLocked bool        `json:"isCurrencyLocked"`
	IsReadOnly       bool        `json:"isReadOnly"`
	CreatedAt        int64       `json:"createdAt"`
	UpdatedAt        int64       `json:"updatedAt"`
}

type Member struct {
	ID        string       `json:"id"`
	GroupID   string       `json:"groupId"`
	Name      string       `json:"name"`
	Role      MemberRole   `json:"role"`
	Status    MemberStatus `json:"status"`
	CreatedAt int64        `json:"createdAt"`
}

type ExpenseSplitAllocation struct {
	MemberID    string `json:"memberId"`
	AmountMinor int64  `json:"amountMinor"`
}

type Expense struct {
	ID                string                   `json:"id"`
	GroupID           string                   `json:"groupId"`
	Description       string                   `json:"description"`
	AmountMinor       int64                    `json:"amountMinor"`
	PayerMemberID     string                   `json:"payerMemberId"`
	CreatedByMemberID string                   `json:"createdByMemberId"`
	ExpenseDate       int64                    `json:"expenseDate"`
	CreatedAt         int64                    `json:"createdAt"`
	Note              *string                  `json:"note,omitempty"`
	SplitType         SplitType                `json:"splitType"`
	IsDeleted         bool                     `json:"isDeleted"`
	DeletedAt         *int64                   `json:"deletedAt,omitempty"`
	DeletedByMemberID *string                  `json:"deletedByMemberId,omitempty"`
	Allocations       []ExpenseSplitAllocation `json:"allocations"`
}

type Settlement struct {
	ID                 string  `json:"id"`
	GroupID            string  `json:"groupId"`
	PayerMemberID      string  `json:"payerMemberId"`
	RecipientMemberID  string  `json:"recipientMemberId"`
	AmountMinor        int64   `json:"amountMinor"`
	SettlementDate     int64   `json:"settlementDate"`
	CreatedAt          int64   `json:"createdAt"`
	RecordedByMemberID string  `json:"recordedByMemberId"`
	Note               *string `json:"note,omitempty"`
	IsDeleted          bool    `json:"isDeleted"`
	DeletedAt          *int64  `json:"deletedAt,omitempty"`
	DeletedByMemberID  *string `json:"deletedByMemberId,omitempty"`
}

type ActivityHistory struct {
	ID            string `json:"id"`
	GroupID       string `json:"groupId"`
	ActorMemberID string `json:"actorMemberId"`
	EventType     string `json:"eventType"`
	Summary       string `json:"summary"`
	EntityID      string `json:"entityId"`
	CreatedAt     int64  `json:"createdAt"`
}

type MemberClaim struct {
	ID               string `json:"id"`
	GroupID          string `json:"groupId"`
	MemberID         string `json:"memberId"`
	ClaimantDeviceID string `json:"claimantDeviceId"`
	SASCode          string `json:"sasCode"`
	Status           string `json:"status"` // "PENDING", "APPROVED", "REJECTED"
	CreatedAt        int64  `json:"createdAt"`
}

type GroupSnapshot struct {
	Group           Group             `json:"group"`
	Members         []Member          `json:"members"`
	Expenses        []Expense         `json:"expenses"`
	Settlements     []Settlement      `json:"settlements"`
	ActivityHistory []ActivityHistory `json:"activityHistory"`
}

type SyncOperation struct {
	IdempotencyKey string `json:"idempotencyKey"`
	GroupID        string `json:"groupId"`
	OperationType  string `json:"operationType"`
	PayloadJSON    string `json:"payloadJson"`
	CreatedAt      int64  `json:"createdAt"`
}

type SyncBatchRequest struct {
	Operations []SyncOperation `json:"operations"`
}

type SyncResult struct {
	IdempotencyKey string `json:"idempotencyKey"`
	Status         string `json:"status"` // "ACCEPTED", "REJECTED", "DUPLICATE"
	ErrorCode      string `json:"errorCode,omitempty"`
	ErrorMessage   string `json:"errorMessage,omitempty"`
}

type SyncBatchResponse struct {
	Results []SyncResult `json:"results"`
}

