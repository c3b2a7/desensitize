package me.lolico.desensitize.config.parser;

public class ParseException extends RuntimeException {

    private static final long serialVersionUID = -4583899259546865110L;

    public ParseException(String message) {
        super(message);
    }

    public ParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
