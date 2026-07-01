package com.socgen.unibank.apiflow.health;

import com.socgen.unibank.apiflow.model.HealthState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.TimeoutException;

import javax.net.ssl.SSLException;

import static org.assertj.core.api.Assertions.assertThat;

class HealthCheckerTest {

    @ParameterizedTest
    @CsvSource({
            "200, UP",
            "201, UP",
            "204, UP",
            "299, UP",
            "301, DEGRADED",
            "401, DEGRADED",
            "403, DEGRADED",
            "404, DEGRADED",
            "500, DOWN",
            "502, DOWN",
            "503, DOWN",
            "418, DOWN"
    })
    void classify_mapsStatusCodeToExpectedHealthState(int code, HealthState expected) {
        assertThat(HealthChecker.classify(code)).isEqualTo(expected);
    }

    @Test
    void reasonFor_timeout() {
        assertThat(HealthChecker.reasonFor(new HttpTimeoutException("timed out"))).isEqualTo("TIMEOUT");
        assertThat(HealthChecker.reasonFor(new TimeoutException("timed out"))).isEqualTo("TIMEOUT");
    }

    @Test
    void reasonFor_dnsFailure() {
        assertThat(HealthChecker.reasonFor(new UnknownHostException("no.such.host"))).isEqualTo("DNS_FAIL");
    }

    @Test
    void reasonFor_connectionRefused() {
        assertThat(HealthChecker.reasonFor(new ConnectException("refused"))).isEqualTo("CONNECTION_REFUSED");
    }

    @Test
    void reasonFor_tlsError() {
        assertThat(HealthChecker.reasonFor(new SSLException("bad cert"))).isEqualTo("TLS_ERROR");
    }

    @Test
    void reasonFor_unwrapsCompletableFutureCause() {
        // java.net.http wraps failures; CompletableFuture.whenComplete hands us the wrapper, not the real cause.
        RuntimeException wrapper = new RuntimeException(new ConnectException("refused"));
        assertThat(HealthChecker.reasonFor(wrapper)).isEqualTo("CONNECTION_REFUSED");
    }

    @Test
    void reasonFor_fallsBackToExceptionSimpleName() {
        assertThat(HealthChecker.reasonFor(new IllegalStateException("boom"))).isEqualTo("IllegalStateException");
    }
}
