package me.lolico.desensitize.config;

import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.config.parser.DesensitizeConfigParser;

import java.util.ServiceLoader;

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
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to initialize DesensitizeRule", e);
                }
            }
        }
        throw new IllegalStateException("No DesensitizeConfigParser support config: " + config);
    }

}
