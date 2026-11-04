package main

import (
	"context"
	"fmt"
	"log"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"app.settlezero/backend/internal/api"
	"app.settlezero/backend/internal/store"
)

func main() {
	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	dbPath := os.Getenv("SETTLEZERO_DB_PATH")
	var backendStore store.Store

	if dbPath != "" {
		schemaPath := os.Getenv("SETTLEZERO_SCHEMA_PATH")
		if schemaPath == "" {
			schemaPath = filepath.Join("migrations", "001_initial_schema.sql")
		}
		schemaBytes, err := os.ReadFile(schemaPath)
		if err != nil {
			log.Printf("Warning: Failed to read schema file at %s: %v. Creating sqlite store without initial schema.", schemaPath, err)
		}
		sqliteStore, err := store.NewSQLiteStore(dbPath, string(schemaBytes))
		if err != nil {
			log.Fatalf("Failed to initialize SQLite persistent store: %v", err)
		}
		defer sqliteStore.Close()
		backendStore = sqliteStore
		log.Printf("💾 SettleZero using persistent SQLite storage at: %s\n", dbPath)
	} else {
		backendStore = store.NewMemoryStore()
		log.Println("🧠 SettleZero using in-memory ephemeral store")
	}

	server := api.NewServer(backendStore)

	httpServer := &http.Server{
		Addr:         ":" + port,
		Handler:      server,
		ReadTimeout:  15 * time.Second,
		WriteTimeout: 15 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		log.Printf("🚀 SettleZero Backend Sync Server listening on http://localhost:%s\n", port)
		if err := httpServer.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatalf("Server failed to start: %v\n", err)
		}
	}()

	// Graceful shutdown
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Println("Shutting down server gracefully...")
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	if err := httpServer.Shutdown(ctx); err != nil {
		log.Fatalf("Server forced to shutdown: %v\n", err)
	}
	fmt.Println("Server exited cleanly.")
}
