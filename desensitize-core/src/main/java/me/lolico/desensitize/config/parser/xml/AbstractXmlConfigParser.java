package me.lolico.desensitize.config.parser.xml;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeTable;
import me.lolico.desensitize.config.parser.IdentityDesensitizeConfigParser;
import me.lolico.desensitize.config.parser.ParseException;
import org.w3c.dom.Element;

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
public abstract class AbstractXmlConfigParser extends IdentityDesensitizeConfigParser {

    protected static final String TABLE = "table";
    protected static final String COLUM = "column";
    protected static final String NAME = "name";
    // table label properties
    protected static final String SELECT_FROM_ENCRYPTED_TABLE = "selectFromEncryptedTable";
    protected static final String TABLE_NAME_CASE_INSENSITIVE = "tableNameCaseSensitive";
    protected static final String REGEX = "regex";
    // column label properties
    protected static final String ORIGINAL = "original";
    protected static final String MINIMUM_MATCH = "minimumMatch";

    @Override
    protected Map<DesensitizeTable, List<DesensitizeColumn>> parse(InputStream inputStream) throws ParseException {
        Element root;
        try {
            root = XmlUtil.root(inputStream);
        } catch (Exception e) {
            throw new ParseException("invalid xml", e);
        }
        List<Element> elements = XmlUtil.elements(root, TABLE);
        Map<DesensitizeTable, List<DesensitizeColumn>> configuration = new HashMap<>();
        for (Element table : elements) {
            DesensitizeTable desensitizeTable = resolveDesensitizeTable(table);
            if (configuration.containsKey(desensitizeTable)) {
                throw new ParseException("duplicate table configuration: " + desensitizeTable.getName());
            }
            configuration.put(desensitizeTable, initializeColumns(table));
        }
        configuration.values().forEach(this::validate);
        return configuration;
    }

    /**
     * 从Element解析table配置
     */
    private DesensitizeTable resolveDesensitizeTable(Element table) {
        String name = table.getAttribute(NAME).trim();
        String selectFromEncryptedTable = table.getAttribute(SELECT_FROM_ENCRYPTED_TABLE);
        String tableNameCaseSensitive = table.getAttribute(TABLE_NAME_CASE_INSENSITIVE);
        String regex = table.getAttribute(REGEX);
        if (name.length() == 0) {
            throw new ParseException("name cannot be empty");
        }
        DesensitizeTable desensitizeTable = new DesensitizeTable();
        desensitizeTable.setName(name);
        if (regex.length() > 0) {
            desensitizeTable.setRegex(Boolean.parseBoolean(regex));
        }
        if (selectFromEncryptedTable.length() > 0) {
            desensitizeTable.setSelectFromEncryptedTable(Boolean.parseBoolean(selectFromEncryptedTable));
        }
        if (tableNameCaseSensitive.length() > 0) {
            desensitizeTable.setTableNameCaseSensitive(Boolean.parseBoolean(tableNameCaseSensitive));
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
        return columns;
    }

    /**
     * 从Element解析column配置
     */
    private DesensitizeColumn resolveDesensitizeColumn(Element column) {
        String name = column.getAttribute(NAME).trim();
        String original = column.getAttribute(ORIGINAL);
        String minimumMatch = column.getAttribute(MINIMUM_MATCH);
        if (name.length() == 0) {
            throw new ParseException("name cannot be empty");
        }
        DesensitizeColumn desensitizeColumn = new DesensitizeColumn();
        desensitizeColumn.setName(name);
        if (original.length() > 0) {
            desensitizeColumn.setOriginal(Boolean.parseBoolean(original));
        }
        try {
            desensitizeColumn.setMinimumMatch(Integer.parseInt(minimumMatch));
        } catch (NumberFormatException ignored) {
        }
        return desensitizeColumn;
    }

    /**
     * 重复列配置校验
     */
    private void validate(List<DesensitizeColumn> columns) {
        for (int i = 0; i < columns.size(); i++) {
            DesensitizeColumn column = columns.get(i);
            for (int j = i + 1; j < columns.size(); j++) {
                if (column.equals(columns.get(j))) {
                    throw new ParseException("duplicate column configuration: " + column.getName());
                }
            }
        }
    }
}
