package api

import (
	"encoding/json"
	"fmt"
	"net/http"
	"time"

	"app.settlezero/backend/internal/model"
	"app.settlezero/backend/internal/store"
)

type Server struct {
	store store.Store
	mux   *http.ServeMux
}

func NewServer(store store.Store) *Server {
	s := &Server{
		store: store,
		mux:   http.NewServeMux(),
	}
	s.registerRoutes()
	return s
}

func (s *Server) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	// Enable CORS for web development and inspection
	w.Header().Set("Access-Control-Allow-Origin", "*")
	w.Header().Set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE")
	w.Header().Set("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Device-Id")

	if r.Method == http.MethodOptions {
		w.WriteHeader(http.StatusOK)
		return
	}

	s.mux.ServeHTTP(w, r)
}

func (s *Server) registerRoutes() {
	s.mux.HandleFunc("GET /api/v1/health", s.handleHealth)
	s.mux.HandleFunc("POST /api/v1/sync/batch", s.handleSyncBatch)
	s.mux.HandleFunc("GET /api/v1/groups/{id}/snapshot", s.handleGetSnapshot)
	s.mux.HandleFunc("GET /api/v1/invites/{token}", s.handleGetInvite)
	s.mux.HandleFunc("POST /api/v1/groups/{id}/claims", s.handleSubmitClaim)
	s.mux.HandleFunc("POST /api/v1/groups/{id}/claims/{claimId}/approve", s.handleApproveClaim)
}

func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	respondJSON(w, http.StatusOK, map[string]string{
		"status":  "ok",
		"service": "settlezero-backend",
		"version": "1.0.0",
	})
}

func (s *Server) handleSyncBatch(w http.ResponseWriter, r *http.Request) {
	var req model.SyncBatchRequest
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Invalid JSON payload")
		return
	}

	results := s.store.ProcessSyncBatch(req.Operations)
	respondJSON(w, http.StatusOK, model.SyncBatchResponse{Results: results})
}

func (s *Server) handleGetSnapshot(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("id")
	if groupID == "" {
		respondError(w, http.StatusBadRequest, "Missing group ID")
		return
	}

	snapshot, err := s.store.GetSnapshot(groupID)
	if err != nil {
		respondError(w, http.StatusNotFound, fmt.Sprintf("Group not found: %s", groupID))
		return
	}

	respondJSON(w, http.StatusOK, snapshot)
}

func (s *Server) handleGetInvite(w http.ResponseWriter, r *http.Request) {
	token := r.PathValue("token")
	if token == "" {
		respondError(w, http.StatusBadRequest, "Missing invite token")
		return
	}

	group, err := s.store.GetGroupByInviteToken(token)
	if err != nil {
		respondError(w, http.StatusNotFound, "Invite token not found")
		return
	}

	// Privacy-preserving response: reveals group name & currency without leaking member identities or balances
	respondJSON(w, http.StatusOK, map[string]any{
		"groupId":       group.ID,
		"groupName":     group.Name,
		"currencyCode":  group.CurrencyCode,
		"currencyScale": group.CurrencyScale,
		"createdAt":     group.CreatedAt,
	})
}

func (s *Server) handleSubmitClaim(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("id")
	var req struct {
		MemberID         string `json:"memberId"`
		ClaimantDeviceID string `json:"claimantDeviceId"`
		SASCode          string `json:"sasCode"`
	}
	if err := json.NewDecoder(r.Body).Decode(&req); err != nil {
		respondError(w, http.StatusBadRequest, "Invalid JSON payload")
		return
	}

	claim := model.MemberClaim{
		ID:               fmt.Sprintf("claim-%d", time.Now().UnixNano()),
		GroupID:          groupID,
		MemberID:         req.MemberID,
		ClaimantDeviceID: req.ClaimantDeviceID,
		SASCode:          req.SASCode,
		Status:           "PENDING",
		CreatedAt:        time.Now().UnixMilli(),
	}

	if err := s.store.SubmitClaim(claim); err != nil {
		respondError(w, http.StatusInternalServerError, err.Error())
		return
	}

	respondJSON(w, http.StatusOK, claim)
}

func (s *Server) handleApproveClaim(w http.ResponseWriter, r *http.Request) {
	groupID := r.PathValue("id")
	claimID := r.PathValue("claimId")

	if err := s.store.ApproveClaim(groupID, claimID); err != nil {
		respondError(w, http.StatusNotFound, err.Error())
		return
	}

	respondJSON(w, http.StatusOK, map[string]string{
		"status":  "APPROVED",
		"message": "Member claim successfully verified and approved",
	})
}

func respondJSON(w http.ResponseWriter, status int, data any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(data)
}

func respondError(w http.ResponseWriter, status int, message string) {
	respondJSON(w, status, map[string]string{"error": message})
}

