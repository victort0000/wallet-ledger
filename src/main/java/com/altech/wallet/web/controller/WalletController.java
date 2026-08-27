package com.altech.wallet.web.controller;

import com.altech.wallet.domain.model.WalletTransaction;
import com.altech.wallet.domain.service.WalletService;
import com.altech.wallet.web.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Tag(name = "Wallet API", description = "Endpoints for money movement, balance inquiries, and auditing")
public class WalletController {

    private final WalletService walletService;

    @GetMapping("/{playerId}/balance")
    @Operation(summary = "Get current player wallet balance")
    public ResponseEntity<BalanceResponse> getBalance(@PathVariable String playerId) {
        return ResponseEntity.ok(walletService.getBalance(playerId));
    }

    @PostMapping("/{playerId}/credit")
    @Operation(summary = "Credit money to player wallet")
    public ResponseEntity<WalletTransaction> credit(
            @PathVariable String playerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreditRequest request) {

        WalletTransaction tx = walletService.credit(
                playerId, request.getAmount(), request.getReason(), request.getReferenceId(), request.getDescription());
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/{playerId}/debit")
    @Operation(summary = "Debit money from player wallet")
    public ResponseEntity<WalletTransaction> debit(
            @PathVariable String playerId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody DebitRequest request) {

        WalletTransaction tx = walletService.debit(
                playerId, request.getAmount(), request.getReason(), request.getReferenceId(), request.getDescription());
        return ResponseEntity.ok(tx);
    }

    @GetMapping("/{playerId}/transactions")
    @Operation(summary = "Get paginated transaction history")
    public ResponseEntity<Page<WalletTransaction>> getTransactionHistory(
            @PathVariable String playerId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(sortBy).descending());
        return ResponseEntity.ok(walletService.getTransactionHistory(playerId, pageable));
    }

    @PostMapping("/transfer")
    @Operation(summary = "Atomically transfer currency between two players")
    public ResponseEntity<String> transfer(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {

        walletService.transfer(request.getFromPlayerId(), request.getToPlayerId(), request.getAmount(), request.getDescription());
        return ResponseEntity.ok("Transfer completed successfully");
    }

    @GetMapping("/{playerId}/audit")
    @Operation(summary = "Audit wallet balance against ledger entry sum")
    public ResponseEntity<AuditResponse> auditWallet(@PathVariable String playerId) {
        return ResponseEntity.ok(walletService.auditWallet(playerId));
    }
}