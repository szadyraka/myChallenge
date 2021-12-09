package com.db.awmd.challenge.web.request;

import lombok.Data;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class TransferMoneyRequest {

    @NotNull
    @NotEmpty
    private String sourceAccountId;

    @NotNull
    @NotEmpty
    private String targetAccountId;

    @NotNull
    @Min(value = 0)
    @Digits(integer = 9, fraction = 2)
    private BigDecimal amount;

}
