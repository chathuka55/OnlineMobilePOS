package com.possaas.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Every tenant-facing endpoint must state the permission it requires.
 *
 * <p>The permission catalogue and the role grants were always there - CASHIER is
 * documented as "no cost visibility, no voids, no refunds" - but for a long time
 * nothing checked them on the sales, repairs, wholesale, quotations, reporting or
 * shift controllers, so a cashier's token could void a bill or read margins. This
 * fails the build rather than letting that happen again silently.
 */
class EndpointAuthorizationTest {

    private static final Pattern MAPPING =
            Pattern.compile("^\\s*@(Get|Post|Put|Patch|Delete)Mapping");

    /**
     * Controllers that legitimately carry no method-level check, each for a stated
     * reason. Anything not on this list must annotate every handler.
     */
    private static final Set<String> EXEMPT = Set.of(
            // Sign-in, sign-up and token refresh: permitAll by definition, since the
            // caller has no identity yet.
            "AuthController.java",
            // Guarded in one place by SecurityConfiguration:
            // /api/v1/platform/** requires ROLE_PLATFORM_ADMIN.
            "PlatformTenantController.java",
            "PlatformPlanController.java",
            "PlatformMetricsController.java",
            // Called by payment gateways, authenticated by signature not by user.
            "WebhookController.java"
    );

    /** Handlers that act only on the caller's own account, so a permission would be wrong. */
    private static final Set<String> SELF_SERVICE = Set.of(
            "stepUp(", "changePassword(", "setPin(", "verifyPin(", "currentUser(", "me("
    );

    @Test
    void everyEndpointDeclaresItsPermission() {
        List<String> unguarded = new ArrayList<>();

        for (Path controller : controllers()) {
            String name = controller.getFileName().toString();
            if (EXEMPT.contains(name)) {
                continue;
            }
            List<String> lines = read(controller);
            for (int i = 0; i < lines.size(); i++) {
                if (!MAPPING.matcher(lines.get(i)).find()) {
                    continue;
                }
                List<String> block = lines.subList(i, Math.min(i + 12, lines.size()));
                boolean guarded = block.stream().anyMatch(l -> l.contains("@PreAuthorize"));
                boolean selfService = block.stream()
                        .anyMatch(l -> SELF_SERVICE.stream().anyMatch(l::contains));
                if (!guarded && !selfService) {
                    unguarded.add(name + " -> " + lines.get(i).trim()
                            + signature(block));
                }
            }
        }

        assertThat(unguarded)
                .as("these endpoints have no @PreAuthorize; add the permission they need, "
                        + "or exempt the controller here with a reason")
                .isEmpty();
    }

    private static String signature(List<String> block) {
        return block.stream()
                .filter(l -> l.contains("public "))
                .findFirst()
                .map(l -> "  " + l.trim())
                .orElse("");
    }

    private static List<Path> controllers() {
        Path backend = Path.of("..").toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(backend)) {
            List<Path> found = paths
                    .filter(p -> p.getFileName().toString().endsWith("Controller.java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .toList();
            assertThat(found).as("no controllers found under %s", backend).isNotEmpty();
            return found;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
