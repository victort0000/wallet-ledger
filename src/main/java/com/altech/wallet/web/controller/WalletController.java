package com.altech.wallet.web.controller;

import com.altech.wallet.domain.model.TransactionReason;
import com.altech.wallet.domain.model.WalletTransaction;
import com.altech.wallet.domain.service.WalletService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @PostMapping("/{playerId}/credit")
    public ResponseEntity<WalletTransaction> credit(
            @PathVariable UUID playerId,
            @RequestBody TransactionRequest request) {
        WalletTransaction tx = walletService.credit(
                playerId, request.getAmount(), request.getReason(), request.getReferenceId(), request.getDescription());
        return ResponseEntity.ok(tx);
    }

    @PostMapping("/{playerId}/debit")
    public ResponseEntity<WalletTransaction> debit(
            @PathVariable UUID playerId,
            @RequestBody TransactionRequest request) {
        WalletTransaction tx = walletService.debit(
                playerId, request.getAmount(), request.getReason(), request.getReferenceId(), request.getDescription());
        return ResponseEntity.ok(tx);
    }

    @Data
    public static class TransactionRequest {
        private BigDecimal amount;
        private TransactionReason reason;
        private String referenceId;
        private String description;
    }
}
