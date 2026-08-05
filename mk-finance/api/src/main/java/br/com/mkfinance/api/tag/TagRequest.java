package br.com.mkfinance.api.tag;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Contract schema {@code TagRequest}. */
public record TagRequest(@NotBlank @Size(max = 40) String name) {
}
