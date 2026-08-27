package com.altech.wallet.web.dto;

import com.altech.wallet.domain.model.TransactionReason;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DebitRequest {

    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.0001", message = "Amount must be greater than zero")
    private BigDecimal amount;

    @NotNull(message = "Reason is required")
    private TransactionReason reason;

    private String referenceId;
    private String description;
}
