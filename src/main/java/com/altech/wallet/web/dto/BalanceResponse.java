package com.altech.wallet.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceResponse {

    private UUID walletId;
    private String playerId;
    private BigDecimal balance;
    private BigDecimal reservedBalance;
    private String currency;
    private Instant updatedAt;
}