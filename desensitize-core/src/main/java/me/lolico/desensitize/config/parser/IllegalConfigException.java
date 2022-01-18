package me.lolico.desensitize.config.parser;

public class IllegalConfigException extends RuntimeException {

    private static final long serialVersionUID = -4583899259546865110L;

    public IllegalConfigException(String message) {
        super(message);
    }

    public IllegalConfigException(String message, Throwable cause) {
        super(message, cause);
    }

    public IllegalConfigException(Throwable cause) {
        super(cause);
    }
}
