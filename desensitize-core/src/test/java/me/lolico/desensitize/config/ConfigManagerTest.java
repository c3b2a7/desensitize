package me.lolico.desensitize.config;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.DesensitizeTable;
import me.lolico.desensitize.config.parser.IllegalConfigException;
import me.lolico.desensitize.config.parser.xml.ClasspathXmlConfigParser;
import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.List;
import java.util.Map;


public class ConfigManagerTest {

    @Test
    public void load() {
        Assertions.assertThatNoException().isThrownBy(() -> {
            ConfigManager.load("classpath:desensitize-config.xml");
        });

        Assertions.assertThat(DesensitizeRule.isEncryptTable("table1")).isTrue();
        Assertions.assertThat(DesensitizeRule.isEncryptTable("table2")).isTrue();
        Assertions.assertThat(DesensitizeRule.isEncryptTable("table3")).isTrue();

        Assertions.assertThat(DesensitizeRule.isEncryptColumn("table1", "column1")).isTrue();
        Assertions.assertThat(DesensitizeRule.supportLike("table1", "column1")).isTrue();

        Assertions.assertThat(DesensitizeRule.isSelectFromEncryptedTable("table1")).isFalse();
        Assertions.assertThat(DesensitizeRule.isSelectFromEncryptedTable("table2")).isTrue();
        Assertions.assertThat(DesensitizeRule.isSelectFromEncryptedTable("table3")).isTrue();
    }

    @Test
    public void duplicateConfigShouldThrowIllegalConfigException() {
        Assertions.assertThatThrownBy(() -> {
            ConfigManager.load("classpath:duplicate-table-desensitize-config.xml");
        }).getRootCause().isInstanceOf(IllegalConfigException.class);
        Assertions.assertThatThrownBy(() -> {
            ConfigManager.load("classpath:duplicate-column-desensitize-config.xml");
        }).getRootCause().isInstanceOf(IllegalConfigException.class);
    }

    @Test
    public void regexTest() throws Exception {
        ClasspathXmlConfigParser parser = new ClasspathXmlConfigParser();
        Map<DesensitizeTable, List<DesensitizeColumn>> config = parser.parse("classpath:desensitize-config.xml");
        List<DesensitizeColumn> columns = config.get(new DesensitizeTable("table4"));
        for (DesensitizeColumn column : columns) {
            Assertions.assertThat(column.isRegex()).isTrue();
        }
    }
}