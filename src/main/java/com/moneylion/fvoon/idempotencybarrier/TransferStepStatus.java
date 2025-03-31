package com.moneylion.fvoon.idempotencybarrier;

import java.util.Set;

public enum TransferStepStatus {
    CREATED,
    INITIATED,
    SETTLED,
    ERROR,
    CANCELED,
    PROCESSING,

    PRE_SUBMISSION, // ignore this
    POST_SUBMISSION, // ignore this
    ;

    private static final Set<TransferStepStatus> TERMINAL_STATUSES = Set.of(SETTLED, ERROR, CANCELED);

    public static boolean isOfTerminalStatus(TransferStepStatus status) {
        return TERMINAL_STATUSES.contains(status);
    }
}
