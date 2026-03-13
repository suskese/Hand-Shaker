package me.mklv.handshaker.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.time.Instant;

public final class Gsons {
    private Gsons() {
    }

    public static Gson create() {
        return new GsonBuilder()
            .disableHtmlEscaping()
            .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
            .create();
    }

    private static final class InstantTypeAdapter implements JsonSerializer<Instant>, JsonDeserializer<Instant> {
        @Override
        public JsonElement serialize(Instant src, Type typeOfSrc, JsonSerializationContext context) {
            return src == null ? null : new JsonPrimitive(src.toString());
        }

        @Override
        public Instant deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            if (json == null || json.isJsonNull()) {
                return null;
            }
            try {
                return Instant.parse(json.getAsString());
            } catch (Exception exception) {
                throw new JsonParseException("Invalid Instant value: " + json, exception);
            }
        }
    }
}