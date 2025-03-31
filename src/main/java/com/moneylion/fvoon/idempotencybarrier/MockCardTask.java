package com.moneylion.fvoon.idempotencybarrier;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class MockCardTask implements Runnable {
    private final TransferStepRepository transferStepRepository;
    private final MockCardClient cardClient;
    private final Optional<ScenarioId> scenarioId;

    public MockCardTask(TransferStepRepository transferStepRepository, MockCardClient cardClient) {
        this.transferStepRepository = transferStepRepository;
        this.cardClient = cardClient;
        this.scenarioId = Optional.empty();
    }

    public MockCardTask(TransferStepRepository transferStepRepository, MockCardClient cardClient, ScenarioId scenarioId) {
        this.transferStepRepository = transferStepRepository;
        this.cardClient = cardClient;
        this.scenarioId = Optional.of(scenarioId);
    }

    @Override
    public void run() {
        String id = "blabla123";
        Optional<TransferStep> maybeTransferStep = transferStepRepository.findById(id);

        if (maybeTransferStep.isEmpty())
            log.error("transfer step: [{}] does not exist", id);

        TransferStep transferStep = maybeTransferStep.get();

        if (TransferStepStatus.isOfTerminalStatus(transferStep.getStatus()))
            return;

        if (!transferStepRepository.isProcessingLockAvailable(transferStep.getId(), transferStep.getVersion(), Duration.ofSeconds(60))) {
            log.warn("detected duplicated processing of transfer step: [{}]", transferStep.getId());
            return;
        }

        log.info("processing transfer step: [{}]", transferStep.getId());

        transferStepRepository.updateStatus(transferStep.getId(), TransferStepStatus.PRE_SUBMISSION);

        processPayment().thenApply(res -> {
                    log.info("transfer step: [{}] submission status: [{}]", transferStep.getId(), res);
                    transferStepRepository.updateStatus(transferStep.getId(), TransferStepStatus.POST_SUBMISSION);
                    return res;
                }).thenRun(() -> transferStepRepository.updateStatus(transferStep.getId(), TransferStepStatus.SETTLED))
                .exceptionally(err -> {
                    log.error("transfer step: [{}] submission failed", transferStep.getId(), err);
                    return null;
                })
                .join();
    }

    private CompletableFuture<String> processPayment() {
        return CompletableFuture.supplyAsync(() -> cardClient.doPayment(scenarioId));
    }
}
