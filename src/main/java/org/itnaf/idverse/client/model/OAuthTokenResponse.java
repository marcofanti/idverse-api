package org.itnaf.idverse.client.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OAuthTokenResponse {

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Integer expiresIn;

    @JsonProperty("access_token")
    private String accessToken;

    // Error fields
    private String error;

    @JsonProperty("error_description")
    private String errorDescription;

    private String hint;

    private String message;

    public boolean isSuccess() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public boolean hasError() {
        return error != null || message != null;
    }
}
