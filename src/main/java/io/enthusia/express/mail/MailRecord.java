package io.enthusia.express.mail;

import java.util.UUID;

public record MailRecord(
    long id,
    UUID sender,
    String senderName,
    UUID recipient,
    String recipientName,
    MailType type,
    MailStatus status,
    byte[] payload,
    int packedItemCount,
    long createdAt,
    long updatedAt,
    boolean unread,
    boolean returnDelivery) {}
