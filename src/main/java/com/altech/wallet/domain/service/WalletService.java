package com.altech.wallet.domain.service;

import com.altech.wallet.domain.model.*;
import com.altech.wallet.domain.repository.WalletRepository;
import com.altech.wallet.domain.repository.WalletTransactionRepository;
import com.altech.wallet.web.dto.BalanceResponse;
import com.altech.wallet.web.exception.WalletNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;

    @Transactional
    public WalletTransaction credit(String playerId, BigDecimal amount, TransactionReason reason, String referenceId, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }

        Wallet wallet = walletRepository.findByPlayerIdWithPessimisticLock(playerId)
                .orElseGet(() -> createWalletForPlayer(playerId));

        BigDecimal newBalance = wallet.getBalance().add(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(wallet.getId())
                .amount(amount)
                .balanceAfter(newBalance)
                .type(TransactionType.CREDIT)
                .reason(reason)
                .referenceId(referenceId)
                .description(description)
                .build();

        return transactionRepository.save(tx);
    }

    @Transactional
    public WalletTransaction debit(String playerId, BigDecimal amount, TransactionReason reason, String referenceId, String description) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }

        Wallet wallet = walletRepository.findByPlayerIdWithPessimisticLock(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found for player: " + playerId));

        if (wallet.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance. Current: " + wallet.getBalance() + ", Requested: " + amount);
        }

        BigDecimal newBalance = wallet.getBalance().subtract(amount);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        WalletTransaction tx = WalletTransaction.builder()
                .id(UUID.randomUUID())
                .walletId(wallet.getId())
                .amount(amount.negate())
                .balanceAfter(newBalance)
                .type(TransactionType.DEBIT)
                .reason(reason)
                .referenceId(referenceId)
                .description(description)
                .build();

        return transactionRepository.save(tx);
    }

    private Wallet createWalletForPlayer(String playerId) {
        Wallet newWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .playerId(playerId)
                .balance(BigDecimal.ZERO)
                .build();
        return walletRepository.save(newWallet);
    }

    @Transactional
    public void ClearWalletBalanceForPlayer(String playerId) {
        BigDecimal Balance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        if(Balance.compareTo(BigDecimal.ZERO)>0) {
            debit(playerId, Balance, TransactionReason.ADMIN_ADJUSTMENT, "CLEAR", "Clear Balance");
        }
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String playerId) {
        Wallet wallet = walletRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for player: " + playerId));

        return BalanceResponse.builder()
                .walletId(wallet.getId())
                .playerId(wallet.getPlayerId())
                .balance(wallet.getBalance())
                .reservedBalance(BigDecimal.ZERO)
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Page<WalletTransaction> getTransactionHistory(String playerId, Pageable pageable) {
        Wallet wallet = walletRepository.findByPlayerId(playerId)
                .orElseThrow(() -> new WalletNotFoundException("Wallet not found for player: " + playerId));

        return transactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);
    }

    @Transactional
    public void transfer(String fromPlayerId, String toPlayerId, BigDecimal amount, String description) {
        if (fromPlayerId.equals(toPlayerId)) {
            throw new IllegalArgumentException("Cannot transfer funds to the same player");
        }

        // Lock wallets in deterministic order (by UUID) to avoid deadlocks
        String firstId = fromPlayerId.compareTo(toPlayerId) < 0 ? fromPlayerId : toPlayerId;
        String secondId = fromPlayerId.compareTo(toPlayerId) < 0 ? toPlayerId : fromPlayerId;

        walletRepository.findByPlayerIdWithPessimisticLock(firstId);
        walletRepository.findByPlayerIdWithPessimisticLock(secondId);

        // Debit source wallet
        debit(fromPlayerId, amount, TransactionReason.PLAYER_TRANSFER, toPlayerId, "Transfer to " + toPlayerId + " : " + description);

        // Credit target wallet
        credit(toPlayerId, amount, TransactionReason.PLAYER_TRANSFER, fromPlayerId, "Transfer from " + fromPlayerId + " : " + description);
    }
}
