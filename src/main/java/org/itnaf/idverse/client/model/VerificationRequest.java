package org.itnaf.idverse.client.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotBlank(message = "Phone code is required")
    @Pattern(regexp = "^\\+?[1-9]\\d{0,3}$", message = "Invalid phone code format (e.g., +1)")
    private String phoneCode;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\d{4,15}$", message = "Invalid phone number format (4-15 digits)")
    private String phoneNumber;

    @NotBlank(message = "Reference ID is required")
    private String referenceId;

    // Optional in form, but required in API (will be auto-generated if empty)
    @Size(min = 10, max = 128, message = "Transaction ID must be between 10 and 128 characters")
    @Pattern(regexp = "^[a-zA-Z0-9\\s_-]*$", message = "Transaction ID can only contain alphanumeric characters, spaces, hyphens, and underscores")
    private String transactionId;

    // Optional - name field
    private String name;

    // Optional - supplied first name
    private String suppliedFirstName;
}
