package me.lolico.desensitize.codec;

public abstract class AbstractCodec implements Codec {

    @Override
    public Encoder getEncoder() {
        return new InternalEncoder(this);
    }

    @Override
    public Decoder getDecoder() {
        return new InternalDecoder(this);
    }

    private static class InternalEncoder implements Encoder {

        private final Codec codec;

        private InternalEncoder(Codec codec) {
            this.codec = codec;
        }

        @Override
        public String encode(String source) {
            return codec.encode(source);
        }
    }

    private static class InternalDecoder implements Decoder {

        private final Codec codec;

        private InternalDecoder(Codec codec) {
            this.codec = codec;
        }

        @Override
        public String decode(String encoded) {
            return codec.decode(encoded);
        }
    }
}
