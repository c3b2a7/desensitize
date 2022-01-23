package me.lolico.desensitize.util;

public class AllUtils {

    public static final String DEFAULT_IDENTITY = buildIdentity("0", "0", "~");

    public static String buildIdentity(String id, String version, String joiner) {
        return joiner + id + joiner + version + joiner;
    }
}
