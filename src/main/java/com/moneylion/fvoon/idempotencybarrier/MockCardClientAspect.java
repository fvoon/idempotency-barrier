package com.moneylion.fvoon.idempotencybarrier;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.net.http.HttpTimeoutException;
import java.util.Optional;

@Aspect
@Component
@Slf4j
public class MockCardClientAspect {
    @Around("execution(* com.moneylion.fvoon.idempotencybarrier.MockCardClient.doPayment(..)) && args(scenarioId)")
    public Object mockDoPayment(ProceedingJoinPoint pjp, Optional<ScenarioId> scenarioId) throws Throwable {
        if (scenarioId.isPresent()) {
            ScenarioId scenario = scenarioId.get();

            log.info("intercepted doPayment with scenario id: [{}]", scenario);

            if (ScenarioId.MOCK_CARD_CLIENT_CLIENT_ERROR == scenario)
                throw new HttpTimeoutException("a blip");
        }

        return pjp.proceed();
    }
}
