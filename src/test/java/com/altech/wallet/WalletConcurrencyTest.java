package com.altech.wallet;

import com.altech.wallet.domain.model.TransactionReason;
import com.altech.wallet.domain.repository.WalletRepository;
import com.altech.wallet.domain.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WalletConcurrencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    private String playerId = "TestCon0001";

    @Test
    void testConcurrentDebitsPreventOverdraft() throws InterruptedException {
        walletService.credit(playerId, new BigDecimal("100.00"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        int threadCount = 10;
        BigDecimal debitAmount = new BigDecimal("20.00"); // Total attempt: 10 * 20 = $200 (only 5 should succeed)

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Synchronize all threads to fire simultaneously
                    walletService.debit(playerId, debitAmount, TransactionReason.PURCHASE, "ITEM_" + UUID.randomUUID(), "Purchase");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads at once
        doneLatch.await();      // Wait for all threads to complete

        assertEquals(5, successCount.get(), "Only 5 debits of $20 should succeed for a $100 balance");
        assertEquals(5, failureCount.get(), "5 debits should fail due to insufficient funds");

        BigDecimal finalBalance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("0.0000").compareTo(finalBalance), "Final balance must be exactly $0.00 " + finalBalance);
    }
}
