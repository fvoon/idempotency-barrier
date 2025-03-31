package com.moneylion.fvoon.idempotencybarrier;

import lombok.Builder;
import lombok.Value;

import java.time.ZonedDateTime;

@Value
@Builder
public class TransferStep {
    String id;
    int amount;
    ZonedDateTime updatedAt;
    TransferStepStatus status;
    int version;
    ZonedDateTime lockedUntil;
}
