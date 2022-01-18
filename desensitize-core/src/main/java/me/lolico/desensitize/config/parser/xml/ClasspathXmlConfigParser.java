package me.lolico.desensitize.config.parser.xml;

import java.io.IOException;
import java.io.InputStream;

/**
 * 解析类路径下xml
 *
 * @author l00998
 */
public class ClasspathXmlConfigParser extends AbstractXmlConfigParser {

    private static final String CLASSPATH_PREFIX = "classpath:";

    @Override
    protected String getPrefix() {
        return CLASSPATH_PREFIX;
    }

    @Override
    protected InputStream newInputStream(String path) throws IOException {
        ClassLoader cl = getDefaultClassLoader();
        InputStream inputStream = cl != null ? cl.getResourceAsStream(path) : ClassLoader.getSystemResourceAsStream(path);
        if (inputStream == null) {
            throw new IOException("class path resource [" + path + "] cannot be resolved because it does not exist");
        }
        return inputStream;
    }

    /**
     * Thread context ClassLoader -> ClassLoader -> SystemClassLoader
     */
    private ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;
        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ignored) {
        }
        if (cl == null) {
            cl = ClasspathXmlConfigParser.class.getClassLoader();
            if (cl == null) {
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable ignored) {
                }
            }
        }
        return cl;
    }
}
