package me.lolico.desensitize.config.parser.xml;


import com.ctrip.framework.apollo.ConfigFile;
import com.ctrip.framework.apollo.ConfigService;
import com.ctrip.framework.apollo.core.enums.ConfigFileFormat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 解析apollo配置中心xml
 *
 * @author l00998
 */
public class ApolloXmlConfigParser extends AbstractXmlConfigParser {

    private static final String APOLLO_PREFIX = "apollo:";

    @Override
    public String getIdentity() {
        return APOLLO_PREFIX;
    }

    @Override
    protected InputStream getInputStream(String namespace) throws IOException {
        ConfigFile configFile = ConfigService.getConfigFile(namespace, ConfigFileFormat.XML);
        if (configFile == null) {
            throw new IOException("apollo resource [" + namespace + "] cannot be resolved because it does not exist");
        }
        return new ByteArrayInputStream(configFile.getContent().getBytes(StandardCharsets.UTF_8));
    }
}