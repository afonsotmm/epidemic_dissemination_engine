package epidemic_core.message.common;

public record MessageId(String subject,
                        long timestamp,
                        int sourceId)
{}
