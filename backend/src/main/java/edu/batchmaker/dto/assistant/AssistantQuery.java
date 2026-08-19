package edu.batchmaker.dto.assistant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** A natural-language question typed into the assistant drawer. */
public record AssistantQuery(
        @NotBlank @Size(max = 500) String question) {
}
