package epidemic_core.message.common;

public record MessageId(MessageTopic topic,
                        long timestamp)
{}
