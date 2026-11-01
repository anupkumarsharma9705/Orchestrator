package com.example.orchestrator.core.fixer;

import java.util.List;

/**
 * Internal fix proposal from the Fixer.
 * Contains the edited file content to be staged.
 */
public record Fix(
    String id,
    String filePath,
    List<String> findingFingerprints,
    String editedContent,
    String summary
) {}