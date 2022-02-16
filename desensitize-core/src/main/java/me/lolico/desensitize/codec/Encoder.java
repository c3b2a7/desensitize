package me.lolico.desensitize.codec;

import java.util.function.Function;

public interface Encoder<IN, OUT> extends Function<IN, OUT> {
    OUT encode(IN IN);

    @Override
    default OUT apply(IN s) {
        return encode(s);
    }
}
