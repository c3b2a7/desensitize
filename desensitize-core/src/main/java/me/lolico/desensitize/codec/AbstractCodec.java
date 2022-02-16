package me.lolico.desensitize.codec;

public abstract class AbstractCodec<S, R> implements Codec<S, R> {

    @Override
    public Encoder<S, R> getEncoder() {
        return new InternalEncoder<>(this);
    }

    @Override
    public Decoder<R, S> getDecoder() {
        return new InternalDecoder<>(this);
    }

    private static class InternalEncoder<S, R> implements Encoder<S, R> {

        private final Codec<S, R> codec;

        private InternalEncoder(Codec<S, R> codec) {
            this.codec = codec;
        }

        @Override
        public R encode(S source) {
            return codec.encode(source);
        }
    }

    private static class InternalDecoder<R, S> implements Decoder<R, S> {

        private final Codec<S, R> codec;

        private InternalDecoder(Codec<S, R> codec) {
            this.codec = codec;
        }

        @Override
        public S decode(R encoded) {
            return codec.decode(encoded);
        }
    }
}
