package me.lolico.desensitize.codec;

import java.util.function.Function;

public interface Decoder<IN, OUT> extends Function<IN, OUT> {
    OUT decode(IN encoded);

    @Override
    default OUT apply(IN s) {
        return decode(s);
    }
}
