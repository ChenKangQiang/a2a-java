package io.a2a.spec;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * A fundamental unit with a Message or Artifact.
 * @param <T> the type of unit
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = TextPart.class, name = "text"),
        @JsonSubTypes.Type(value = FilePart.class, name = "file"),
        @JsonSubTypes.Type(value = DataPart.class, name = "data")
})
public abstract class Part<T> {
    public enum Type {
        TEXT,
        FILE,
        DATA;
    }

    public abstract Type getType();

    public abstract Map<String, Object> getMetadata();

}