package com.possaas.ops;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Keeps the instance from being stopped for idleness.
 *
 * <p>The API runs on a free hosting tier that stops the instance after about
 * fifteen minutes without inbound traffic. Restarting it takes roughly a minute,
 * which the first cashier of the morning reads as a broken till.
 *
 * <p>A scheduled ping at the service's own public URL leaves and re-enters through
 * the platform's router, so it counts as the inbound traffic that resets the idle
 * timer. A loopback call would not - it never reaches the router - which is why
 * this uses the public address rather than localhost.
 *
 * <p>There is a GitHub Actions schedule that does the same thing from outside. It
 * is kept because it can also wake an instance that has already stopped, which
 * this cannot: a stopped instance runs no scheduler. But GitHub states plainly
 * that scheduled workflows are best effort and are delayed or dropped under load,
 * and in practice the five-minute schedule did not fire at all, so the shops
 * cannot depend on it alone.
 *
 * <p>Disabled unless {@code pos.keep-awake.url} is set, so it never runs in tests
 * or against a developer machine.
 */
@Component
// ConditionalOnProperty would match the empty default, leaving a bean pinging an
// empty URI, so the condition tests for a non-empty value instead.
@ConditionalOnExpression("'${pos.keep-awake.url:}'.trim().length() > 0")
public class KeepAwakeTask {

    private static final Logger log = LoggerFactory.getLogger(KeepAwakeTask.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final URI target;
    private boolean confirmed;

    public KeepAwakeTask(@Value("${pos.keep-awake.url}") String url,
                         @Value("${pos.keep-awake.interval:PT10M}") String interval) {
        this.target = URI.create(url);
        // Whether this is running is not otherwise visible: the free tier surfaces
        // no request logs, so without a line here there is no way to tell an armed
        // pinger from a silent one.
        log.info("Keep-awake armed: pinging {} every {}", target, interval);
    }

    /**
     * Ten minutes, comfortably inside the fifteen minute idle window while still
     * being a handful of requests an hour. fixedDelay rather than fixedRate so a
     * slow ping cannot queue another behind it.
     */
    @Scheduled(initialDelayString = "PT2M", fixedDelayString = "${pos.keep-awake.interval:PT10M}")
    public void ping() {
        try {
            HttpResponse<Void> response = http.send(
                    HttpRequest.newBuilder(target)
                            .timeout(Duration.ofSeconds(30))
                            .header("User-Agent", "possaas-keep-awake")
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.discarding());
            if (!confirmed) {
                // One line the first time a ping actually lands, so the mechanism can
                // be confirmed working. After that it is routine and stays at debug.
                confirmed = true;
                log.info("Keep-awake ping succeeded ({}); the instance will not be "
                        + "stopped for idleness", response.statusCode());
            } else if (log.isDebugEnabled()) {
                log.debug("Keep-awake ping to {} returned {}", target, response.statusCode());
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Never propagate: failing to stay awake is not worth a stack trace on
            // every attempt, and the scheduler would keep the next one anyway.
            log.warn("Keep-awake ping to {} failed: {}", target, ex.toString());
        }
    }
}
