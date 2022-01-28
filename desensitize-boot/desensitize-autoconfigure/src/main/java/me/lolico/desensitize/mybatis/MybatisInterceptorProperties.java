package me.lolico.desensitize.mybatis;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Properties;

@ConfigurationProperties("desensitize")
public class MybatisInterceptorProperties {
    private Config config;

    public Config getConfig() {
        return config;
    }

    public void setConfig(Config config) {
        this.config = config;
    }

    public Properties getProperties() {
        if (config == null) {
            throw new IllegalStateException("desensitize.config is not configure");
        }
        Properties properties = new Properties();
        properties.put("config", config.toString());
        properties.put("aesKey", config.getAesKey());
        return properties;
    }

    static class Config {
        /**
         * The identity for desensitize config.
         */
        private Identity identity;
        /**
         * Desensitize config location.
         */
        private String location;
        /**
         * The aes key is used to desensitization.
         */
        private String aesKey;

        public Identity getIdentity() {
            return identity;
        }

        public void setIdentity(Identity identity) {
            this.identity = identity;
        }

        public String getLocation() {
            return location;
        }

        public void setLocation(String location) {
            this.location = location;
        }

        public String getAesKey() {
            return aesKey;
        }

        public void setAesKey(String aesKey) {
            this.aesKey = aesKey;
        }

        @Override
        public String toString() {
            return identity.name + location;
        }
    }

    enum Identity {
        CLASSPATH("classpath:"),
        APOLLO("apollo:");

        final String name;

        Identity(String name) {
            this.name = name;
        }
    }
}
