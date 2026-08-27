package com.altech.wallet.domain.service;

import com.altech.wallet.domain.model.*;
import com.altech.wallet.domain.repository.WalletRepository;
import com.altech.wallet.domain.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
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
    public WalletTransaction credit(UUID playerId, BigDecimal amount, TransactionReason reason, String referenceId, String description) {
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
    public WalletTransaction debit(UUID playerId, BigDecimal amount, TransactionReason reason, String referenceId, String description) {
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

    private Wallet createWalletForPlayer(UUID playerId) {
        Wallet newWallet = Wallet.builder()
                .id(UUID.randomUUID())
                .playerId(playerId)
                .balance(BigDecimal.ZERO)
                .currency("USD")
                .build();
        return walletRepository.save(newWallet);
    }
}
