package com.altech.wallet;

import com.altech.wallet.domain.model.TransactionReason;
import com.altech.wallet.domain.repository.WalletRepository;
import com.altech.wallet.domain.service.WalletService;
import com.altech.wallet.web.dto.CreditRequest;
import com.altech.wallet.web.dto.TransferRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class WalletIdempotencyTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    private String playerId;
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;
    private String ver = "0";
    private String playerA = "TestIDA00" + ver;
    private String playerB = "TestIDB00" + ver;

    @BeforeEach
    void setup() {
        try {
            walletService.credit(playerA, new BigDecimal("100.00"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
            walletService.debit(playerA, new BigDecimal("100.00"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
            walletService.credit(playerB, new BigDecimal("100.00"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
            walletService.debit(playerB, new BigDecimal("100.00"), TransactionReason.ADMIN_ADJUSTMENT, "INIT", "Initial setup");
        } catch (Exception ignored) {}
    }

    @Test
    @DisplayName("Concurrent duplicate credit requests with same Idempotency-Key must credit account only once")
    void testConcurrentDuplicateCreditRequests() throws InterruptedException {
        String idempotencyKey = "key-credit-" + UUID.randomUUID();
        int threads = 10;

        CreditRequest request = new CreditRequest();
        request.setAmount(new BigDecimal("50.0000"));
        request.setReason(TransactionReason.ADMIN_ADJUSTMENT);
        request.setDescription("Initial seed");

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger success200 = new AtomicInteger(0);
        AtomicInteger conflict409 = new AtomicInteger(0);
        AtomicInteger rollbackCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    int statusCode = mockMvc.perform(post("/api/v1/wallets/" + playerA + "/credit")
                                    .header("Idempotency-Key", idempotencyKey)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(request)))
                            .andReturn().getResponse().getStatus();
                    assertEquals(200, statusCode, "Balance must be exactly 50.0000 after idempotent credit" + statusCode);

                    if (statusCode == 200) success200.incrementAndGet();
                    if (statusCode == 409) conflict409.incrementAndGet();
                }
                catch (Exception rollback)
                {
                    rollbackCount.incrementAndGet();
                }
                finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();

        // Exactly one request succeeds or gets cached answer; total credited balance must be exactly 50.0000
        BigDecimal balance = walletRepository.findByPlayerId(playerA).orElseThrow().getBalance();
        assertEquals(0, new BigDecimal("50.0000").compareTo(balance), "Balance must be exactly 50.0000 after idempotent credit" + balance);
    }

    @Test
    @DisplayName("Re-submitting transfer with modified payload and same key returns 422 Unprocessable Entity")
    void testPayloadMismatchReturnsBadRequest() throws Exception {
        String idempotencyKey = "key-transfer-" + UUID.randomUUID();

        // Seed playerA with funds first
        CreditRequest creditReq = new CreditRequest();
        creditReq.setAmount(new BigDecimal("20.0000"));
        creditReq.setReason(TransactionReason.ADMIN_ADJUSTMENT);
        mockMvc.perform(post("/api/v1/wallets/" + playerA + "/credit")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(creditReq)));

        TransferRequest originalReq = new TransferRequest();
        originalReq.setFromPlayerId(playerA);
        originalReq.setToPlayerId(playerB);
        originalReq.setAmount(new BigDecimal("20.0000"));

        // First transfer call
        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(originalReq)))
                .andExpect(status().isOk());

        // Second transfer call with modified amount ($50 instead of $20)
        TransferRequest alteredReq = new TransferRequest();
        alteredReq.setFromPlayerId(playerA);
        alteredReq.setToPlayerId(playerB);
        alteredReq.setAmount(new BigDecimal("50.0000"));

        mockMvc.perform(post("/api/v1/wallets/transfer")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(alteredReq)))
                .andExpect(status().isUnprocessableEntity());
    }
}
