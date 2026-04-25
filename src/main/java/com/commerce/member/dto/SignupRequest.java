package com.commerce.member.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SignupRequest {

    @Schema(description = "이메일", example = "user@example.com")
    @NotBlank
    @Email
    private String email;

    @Schema(description = "비밀번호 (영문 대소문자, 숫자 포함 8자 이상)", example = "Password1")
    @NotBlank
    @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다.")
    @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*\\d).+$",
             message = "비밀번호는 영문 대문자, 소문자, 숫자를 각각 하나 이상 포함해야 합니다.")
    private String password;

    @Schema(description = "이름", example = "홍길동")
    @NotBlank
    private String name;
}