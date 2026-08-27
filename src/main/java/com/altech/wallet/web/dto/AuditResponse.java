package com.altech.wallet.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditResponse {

    private String playerId;
    private UUID walletId;
    private BigDecimal snapshotBalance;
    private BigDecimal ledgerSum;
    private boolean isReconciled;
    private String statusMessage;
}