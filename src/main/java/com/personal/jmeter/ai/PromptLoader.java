package com.personal.jmeter.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Resolves the AI reporter system prompt using a 4-step fallback chain:
 *
 * <ol>
 *   <li>User-override directory: {@code <ai.reporter.prompt.dir>/ai-reporter-prompt.txt}</li>
 *   <li>JMeter bin directory: {@code $JMETER_HOME/bin/ai-reporter-prompt.txt}
 *       (auto-created from JAR resource on first use; write failures are logged and skipped)</li>
 *   <li>JAR resource: {@code /ai-reporter-prompt.txt}</li>
 *   <li>None found: returns {@code null} so the caller can show an error dialog</li>
 * </ol>
 *
 * <p>All public methods are static; this class is a stateless utility.</p>
 */
public final class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);

    static final String PROMPT_FILE_NAME = "ai-reporter-prompt.txt";
    static final String RESOURCE_PATH    = "/" + PROMPT_FILE_NAME;

    private PromptLoader() {}

    // ─────────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Loads the system prompt using the 4-step fallback chain.
     *
     * @param jmeterHome  JMeter home directory; may be {@code null}
     * @param promptDir   user-override directory (from {@code ai.reporter.prompt.dir});
     *                    may be {@code null} or blank
     * @return prompt text, or {@code null} if no source could be found
     */
    public static String load(File jmeterHome, String promptDir) {

        // Step 1 — user-override directory
        if (promptDir != null && !promptDir.isBlank()) {
            String text = readFile(new File(promptDir.trim(), PROMPT_FILE_NAME), "user override dir");
            if (text != null) return text;
            log.warn("load: prompt file not found in override dir='{}'. Continuing fallback.", promptDir.trim());
        }

        // Step 2 — $JMETER_HOME/bin
        if (jmeterHome != null) {
            File binFile = new File(jmeterHome, "bin/" + PROMPT_FILE_NAME);
            if (!binFile.exists()) {
                tryAutoCreate(binFile);
            }
            String text = readFile(binFile, "$JMETER_HOME/bin");
            if (text != null) return text;
        }

        // Step 3 — JAR resource
        String text = readResource();
        if (text != null) {
            log.debug("load: using bundled JAR resource prompt.");
            return text;
        }

        // Step 4 — nothing found
        log.error("load: could not locate prompt from any source. Caller must show error dialog.");
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static String readFile(File file, String sourceLabel) {
        if (!file.isFile()) return null;
        try {
            String content = Files.readString(file.toPath(), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                log.warn("readFile: prompt file is empty at {}={}. Skipping.", sourceLabel, file.getAbsolutePath());
                return null;
            }
            log.debug("readFile: loaded prompt from {}={}.", sourceLabel, file.getAbsolutePath());
            return content;
        } catch (IOException e) {
            log.warn("readFile: failed to read prompt from {}={}. reason={}",
                    sourceLabel, file.getAbsolutePath(), e.getMessage());
            return null;
        }
    }

    private static String readResource() {
        try (InputStream in = PromptLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.warn("readResource: resource not found: {}", RESOURCE_PATH);
                return null;
            }
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            return content.isEmpty() ? null : content;
        } catch (IOException e) {
            log.error("readResource: failed to read {}. reason={}", RESOURCE_PATH, e.getMessage(), e);
            return null;
        }
    }

    /**
     * Attempts to copy the JAR resource to {@code targetFile}.
     * Logs a warning and returns silently on any failure — the caller will
     * fall through to the JAR-resource fallback.
     */
    private static void tryAutoCreate(File targetFile) {
        try (InputStream in = PromptLoader.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                log.warn("tryAutoCreate: JAR resource not found; cannot auto-create {}.",
                        targetFile.getAbsolutePath());
                return;
            }
            Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("tryAutoCreate: created prompt file at {}.", targetFile.getAbsolutePath());
        } catch (IOException e) {
            log.warn("tryAutoCreate: could not write {}. reason={}",
                    targetFile.getAbsolutePath(), e.getMessage());
        }
    }
}
