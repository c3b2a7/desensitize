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
        Properties properties = new Properties();
        properties.put("config", config.toString());
        return properties;
    }

    static class Config {
        /**
         * 配置类型
         */
        private Identity identity;
        /**
         * 配置位置
         */
        private String location;

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
