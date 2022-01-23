package me.lolico.desensitize.config;

import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.config.parser.DesensitizeConfigParser;
import me.lolico.desensitize.config.parser.ParseException;

import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;

/**
 * SPI加载配置，默认xml
 */
public class ConfigManager {

    public static void load(String config) {
        ServiceLoader<DesensitizeConfigParser> parsers = ServiceLoader.load(DesensitizeConfigParser.class);
        for (DesensitizeConfigParser parser : parsers) {
            if (parser.support(config)) {
                try {
                    DesensitizeRule.init(parser.parse(config));
                    return;
                } catch (ParseException e) {
                    throw new IllegalStateException("Failed to initialize DesensitizeRule", e);
                }
            }
        }
        throw new UnsupportedConfigException(config + " is unsupported, available parsers: "
                + Arrays.toString(StreamSupport.stream(parsers.spliterator(), false)
                .map(p -> p.getClass().getName()).toArray(String[]::new)));
    }

}
