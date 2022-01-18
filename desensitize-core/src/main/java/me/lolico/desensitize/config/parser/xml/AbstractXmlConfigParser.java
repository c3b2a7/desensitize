package me.lolico.desensitize.config.parser.xml;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeTable;
import me.lolico.desensitize.config.parser.DesensitizeConfigParser;
import me.lolico.desensitize.config.parser.IllegalConfigException;
import org.w3c.dom.Element;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从{@code InputStream}解析xml
 *
 * @author l00998
 */
public abstract class AbstractXmlConfigParser implements DesensitizeConfigParser {

    protected static final String TABLE = "table";
    protected static final String COLUM = "column";
    protected static final String NAME = "name";
    // table label properties
    protected static final String SELECT_FROM_ENCRYPTED_TABLE = "selectFromEncryptedTable";
    protected static final String TABLE_NAME_CASE_INSENSITIVE = "tableNameCaseInsensitive";
    // column label properties
    protected static final String ORIGINAL = "original";
    protected static final String MINIMUM_MATCH = "minimumMatch";
    protected static final String REGEX = "regex";

    @Override
    public boolean support(String config) {
        return config != null && config.startsWith(getPrefix());
    }

    @Override
    public Map<DesensitizeTable, List<DesensitizeColumn>> parse(String config) throws Exception {
        if (!support(config)) {
            throw new UnsupportedOperationException(config);
        }
        InputStream inputStream = newInputStream(config.substring(getPrefix().length()));
        Element root = XmlUtil.root(inputStream);
        List<Element> elements = XmlUtil.elements(root, TABLE);

        Map<DesensitizeTable, List<DesensitizeColumn>> configuration = new HashMap<>();

        for (Element table : elements) {
            DesensitizeTable desensitizeTable = resolveDesensitizeTable(table);
            if (configuration.containsKey(desensitizeTable)) {
                throw new IllegalConfigException("duplicate table configuration: " + desensitizeTable.getName());
            }
            configuration.put(desensitizeTable, initializeColumns(table));
        }
        return configuration;
    }

    /**
     * 从Element解析table配置
     */
    private DesensitizeTable resolveDesensitizeTable(Element table) {
        String name = table.getAttribute(NAME).trim();
        String selectFromEncryptedTable = table.getAttribute(SELECT_FROM_ENCRYPTED_TABLE);
        String tableNameCaseInsensitive = table.getAttribute(TABLE_NAME_CASE_INSENSITIVE);
        if (name.length() == 0) {
            throw new IllegalConfigException("name cannot be empty");
        }
        DesensitizeTable desensitizeTable = new DesensitizeTable(name);
        if (selectFromEncryptedTable.length() > 0) {
            desensitizeTable.setSelectFromEncryptedTable(Boolean.parseBoolean(selectFromEncryptedTable));
        }
        if (tableNameCaseInsensitive.length() > 0) {
            desensitizeTable.setTableNameCaseInsensitive(Boolean.parseBoolean(tableNameCaseInsensitive));
        }
        return desensitizeTable;
    }

    /**
     * 加载column配置
     */
    private List<DesensitizeColumn> initializeColumns(Element table) {
        List<DesensitizeColumn> columns = new ArrayList<>();
        List<Element> elements = XmlUtil.elements(table, COLUM);
        for (Element column : elements) {
            DesensitizeColumn desensitizeColumn = resolveDesensitizeColumn(column);
            columns.add(desensitizeColumn);
        }
        validate(columns);
        return columns;
    }

    /**
     * 从Element解析column配置
     */
    private DesensitizeColumn resolveDesensitizeColumn(Element column) {
        String name = column.getAttribute(NAME).trim();
        String original = column.getAttribute(ORIGINAL);
        String minimumMatch = column.getAttribute(MINIMUM_MATCH);
        String regex = column.getAttribute(REGEX);
        if (name.length() == 0) {
            throw new IllegalConfigException("name cannot be empty");
        }
        DesensitizeColumn desensitizeColumn = new DesensitizeColumn();
        desensitizeColumn.setName(name);
        desensitizeColumn.setOriginal(Boolean.parseBoolean(original));
        desensitizeColumn.setRegex(Boolean.parseBoolean(regex));
        if (minimumMatch.length() > 0) {
            try {
                desensitizeColumn.setMinimumMatch(Integer.parseInt(minimumMatch));
            } catch (NumberFormatException ignored) {
            }
        }
        return desensitizeColumn;
    }

    /**
     * 重复列配置校验
     */
    private void validate(List<DesensitizeColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            DesensitizeColumn column1 = columns.get(i);
            for (int j = i + 1; j < columns.size(); j++) {
                DesensitizeColumn column2 = columns.get(j);
                if (column1.getName().equals(column2.getName())) {
                    throw new IllegalConfigException("duplicate column configuration: " + column1.getName());
                }
            }
        }
    }

    protected abstract String getPrefix();

    protected abstract InputStream newInputStream(String source) throws IOException;
}
