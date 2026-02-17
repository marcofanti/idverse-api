package org.itnaf.idverse.client.model;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model for webhook payload received from IDVerse API.
 * This payload is sent to both event and completion webhook endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookPayload {

    /**
     * The transaction ID associated with the verification.
     */
    @NotBlank(message = "transactionId is required")
    private String transactionId;

    /**
     * The event type representing the transaction stage.
     *
     * For Event Webhook (notifyUrlEvent):
     * - pending: Customer sent the transaction link to end-user (link not yet used)
     * - termsAndConditions: End-user is reviewing Terms and Conditions
     * - idSelection: End-user is selecting the ID for verification
     * - personalDetails: End-user is checking extracted information from submitted ID
     * - liveness: End-user is at liveness attempt phase (video or selfie)
     * - expired: Transaction exceeded time limit
     * - cancelled: Transaction has been cancelled
     * - completedPass: End-user completed and passed data checks
     * - completedFlagged: End-user completed but has conflicts (e.g., liveness mismatch)
     *
     * For Completion Webhook (notifyUrlComplete):
     * - completedPass: End-user completed and passed data checks
     * - completedFlagged: End-user completed but has conflicts
     * - expired: Transaction exceeded time limit
     * - cancelled: Transaction has been cancelled
     */
    @NotBlank(message = "event is required")
    private String event;
}
