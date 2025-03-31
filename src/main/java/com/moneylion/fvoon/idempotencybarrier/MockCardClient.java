package com.moneylion.fvoon.idempotencybarrier;

import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class MockCardClient {

    public String doPayment(Optional<ScenarioId> scenarioId) {
        return "SUCCESS";
    }
}
