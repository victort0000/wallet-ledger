package com.altech.wallet;

import com.altech.wallet.domain.model.TransactionReason;
import com.altech.wallet.domain.repository.WalletRepository;
import com.altech.wallet.domain.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class WalletMandatoryTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    private String playerId = "TestMan0001";

    @Test
    void testMandatory()
    {
        walletService.credit(playerId, new BigDecimal("100.0000"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        BigDecimal Balance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("100.0000").compareTo(Balance), "balance after credit must be exactly $100.00 " + Balance);
        walletService.debit(playerId, new BigDecimal("30.0000"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        Balance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("70.0000").compareTo(Balance), "balance after debit must be exactly $70.00 "+ Balance);
        try {
            walletService.debit(playerId, new BigDecimal("100.0000"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        }
        catch (Exception e) {
            assertEquals(IllegalStateException.class,e.getClass(),"");
        }
        Balance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("70.0000").compareTo(Balance), "debit should be reject and balance remain exactly $70.00 "+ Balance);
        walletService.debit(playerId, new BigDecimal("70.0000"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        Balance = walletRepository.findByPlayerId(playerId).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("0.0000").compareTo(Balance), "balance after clear should be $0.00"+ Balance);
    }
}
