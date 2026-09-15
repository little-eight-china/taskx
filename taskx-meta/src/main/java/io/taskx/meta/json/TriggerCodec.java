package io.taskx.meta.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.taskx.core.domain.Trigger;
import io.taskx.core.domain.TriggerType;
import io.taskx.meta.MetaException;

import java.io.IOException;

public final class TriggerCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TriggerCodec() {
    }

    public static String toSpecJson(Trigger trigger) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            switch (trigger) {
                case Trigger.Cron cron -> node.put("expression", cron.expression());
                case Trigger.FixedRate rate -> node.put("intervalSeconds", rate.intervalSeconds());
                case Trigger.FixedDelay delay -> node.put("delaySeconds", delay.delaySeconds());
                case Trigger.Delay delay -> node.put("delaySeconds", delay.delaySeconds());
                case Trigger.Once once -> node.put("fireEpochSecond", once.fireEpochSecond());
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException ex) {
            throw new MetaException("encode trigger_spec", ex);
        }
    }

    public static Trigger from(String type, String specJson) {
        try {
            JsonNode node = MAPPER.readTree(specJson);
            return switch (TriggerType.valueOf(type)) {
                case CRON -> new Trigger.Cron(node.get("expression").asText());
                case FIXED_RATE -> new Trigger.FixedRate(node.get("intervalSeconds").asInt());
                case FIXED_DELAY -> new Trigger.FixedDelay(node.get("delaySeconds").asInt());
                case DELAY -> new Trigger.Delay(node.get("delaySeconds").asInt());
                case ONCE -> new Trigger.Once(node.get("fireEpochSecond").asLong());
            };
        } catch (IOException | IllegalArgumentException | NullPointerException ex) {
            throw new MetaException("decode trigger_spec type=" + type, ex);
        }
    }
}
