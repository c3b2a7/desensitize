package me.lolico.desensitize.codec;

import java.util.function.Function;

public interface Decoder extends Function<String, String> {
    String decode(String encoded);

    @Override
    default String apply(String s) {
        return decode(s);
    }
}
