package me.lolico.desensitize.config.parser;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeTable;
import me.lolico.desensitize.config.UnsupportedConfigException;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public abstract class IdentityDesensitizeConfigParser implements DesensitizeConfigParser {

    @Override
    public boolean support(String config) {
        return config != null && config.startsWith(getIdentity());
    }

    @Override
    public Map<DesensitizeTable, List<DesensitizeColumn>> parse(String config) throws ParseException {
        if (!support(config)) {
            throw new UnsupportedConfigException(config + "is unsupported");
        }
        String configWithoutIdentity = config.substring(getIdentity().length());
        InputStream inputStream;
        try {
            inputStream = getInputStream(configWithoutIdentity);
        } catch (IOException e) {
            throw new ParseException("cannot get input stream from config", e);
        }
        return parse(inputStream);
    }

    public abstract String getIdentity();

    protected abstract Map<DesensitizeTable, List<DesensitizeColumn>> parse(InputStream inputStream) throws ParseException;

    protected abstract InputStream getInputStream(String configWithoutIdentity) throws IOException;
}
