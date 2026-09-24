package com.possaas.tenancy;

import static org.assertj.core.api.Assertions.assertThat;

import com.possaas.tenancy.domain.DocumentType;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * document_sequences constrains document_type to a fixed list, so adding a value
 * to {@link DocumentType} without widening that CHECK produces a runtime failure
 * the first time anyone allocates a number for it - which is exactly how the
 * SHIFT type shipped broken. This keeps the two definitions honest.
 */
class DocumentSequenceTypesTest {

    /** The CHECK is rewritten wholesale by later migrations, so the last one wins. */
    private static final String[] MIGRATIONS = {
            "db/migration/V2__tenancy.sql",
            "db/migration/V23__document_sequence_shift.sql",
    };

    private static final Pattern CHECK_BLOCK = Pattern.compile(
            "document_sequences_type_check\\s+CHECK\\s*\\(\\s*document_type\\s+IN\\s*\\((.*?)\\)",
            Pattern.DOTALL);

    @Test
    void everyDocumentTypeIsAllowedByTheCheckConstraint() {
        Set<String> allowed = latestAllowedTypes();

        Set<String> declared = Arrays.stream(DocumentType.values())
                .map(Enum::name)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        assertThat(allowed)
                .as("document_sequences_type_check must list every DocumentType; "
                        + "add a migration widening the CHECK when you add one")
                .containsAll(declared);
    }

    @Test
    void theCheckConstraintHasNoTypesTheEnumDropped() {
        Set<String> allowed = latestAllowedTypes();
        Set<String> declared = Arrays.stream(DocumentType.values())
                .map(Enum::name)
                .collect(Collectors.toSet());

        assertThat(declared)
                .as("the CHECK allows a document type the enum no longer has")
                .containsAll(allowed);
    }

    private static Set<String> latestAllowedTypes() {
        Set<String> allowed = null;
        for (String migration : MIGRATIONS) {
            String sql = read(migration);
            Matcher matcher = CHECK_BLOCK.matcher(sql);
            while (matcher.find()) {
                allowed = Arrays.stream(matcher.group(1).split(","))
                        .map(s -> s.trim().replace("'", ""))
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }
        assertThat(allowed).as("could not parse the document_type CHECK constraint").isNotNull();
        return allowed;
    }

    private static String read(String resource) {
        try (InputStream in = DocumentSequenceTypesTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(in).as("migration %s is on the classpath", resource).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read " + resource, ex);
        }
    }
}
