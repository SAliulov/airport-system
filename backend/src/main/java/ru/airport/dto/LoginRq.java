package ru.airport.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRq {

    @NotBlank
    @Schema(example = "dispatcher")
    private String username;

    @NotBlank
    @Schema(example = "dispatcher123")
    private String password;
}
