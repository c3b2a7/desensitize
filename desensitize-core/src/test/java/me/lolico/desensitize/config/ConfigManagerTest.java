package me.lolico.desensitize.config;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.DesensitizeTable;
import me.lolico.desensitize.config.parser.ParseException;
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
        }).hasCauseInstanceOf(ParseException.class);
        Assertions.assertThatThrownBy(() -> {
            ConfigManager.load("classpath:duplicate-column-desensitize-config.xml");
        }).hasCauseInstanceOf(ParseException.class);
    }

    @Test
    public void regexTest() {
        ClasspathXmlConfigParser parser = new ClasspathXmlConfigParser();
        Map<DesensitizeTable, List<DesensitizeColumn>> config = parser.parse("classpath:regex-desensitize-config.xml");
        for (DesensitizeTable table : config.keySet()) {
            Assertions.assertThat(table.isRegex()).isTrue();
            if (table.isTableNameCaseSensitive()) {
                Assertions.assertThat(table.isSameTable("table_0")).isTrue();
                Assertions.assertThat(table.isSameTable("TABLE_0")).isFalse();
            } else {
                Assertions.assertThat(table.isSameTable("table_2022_01")).isTrue();
                Assertions.assertThat(table.isSameTable("TABLE_2022_01")).isTrue();
            }
        }
    }
}