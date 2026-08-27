package com.altech.wallet.domain.repository;

import com.altech.wallet.domain.model.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, String> {

    Optional<Wallet> findByPlayerId(String playerId);

    // Acquires row-level lock (SELECT ... FOR UPDATE) to prevent double-spending
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.playerId = :playerId")
    Optional<Wallet> findByPlayerIdWithPessimisticLock(@Param("playerId") String playerId);
}