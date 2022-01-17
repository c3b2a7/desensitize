package me.lolico.desensitize;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DesensitizeRule {

    // <table,columns>
    private static final Map<DesensitizeTable, List<DesensitizeColumn>> CONFIG = new HashMap<>();

    public static final String ENCRYPT_TABLE_SUFFIX = "_encrypt";
    public static final String LIKE_COLUMN_SUFFIX = "_partial";

    public static void init(Map<DesensitizeTable, List<DesensitizeColumn>> config) {
        if (!CONFIG.isEmpty()) {
            CONFIG.clear();
        }
        DesensitizeRule.CONFIG.putAll(config);
    }

    public static boolean isSelectFromEncryptedTable(String table) {
        DesensitizeTable desensitizeTable = getDesensitizeTable(table);
        return desensitizeTable != null && desensitizeTable.isSelectFromEncryptedTable();
    }

    public static boolean isEncryptTable(String table) {
        return getDesensitizeTable(table) != null;
    }

    public static boolean isEncryptColumn(String table, String column) {
        return getDesensitizeColumn(table, column) != null;
    }

    public static boolean isEncryptedTableColumn(String table, String column) {
        DesensitizeColumn desensitizeColumn = getDesensitizeColumn(table, column);
        return desensitizeColumn != null && !desensitizeColumn.isOriginal();
    }

    public static boolean isOriginalTableColumn(String table, String column) {
        DesensitizeColumn desensitizeColumn = getDesensitizeColumn(table, column);
        return desensitizeColumn != null && desensitizeColumn.isOriginal();
    }

    public static boolean supportLike(String table, String column) {
        DesensitizeColumn desensitizeColumn = getDesensitizeColumn(table, column);
        if (desensitizeColumn == null) {
            return false;
        }
        return desensitizeColumn.getMinimumMatch() > 0;
    }

    public static int getMinimumMatch(String table, String column) {
        DesensitizeColumn desensitizeColumn = getDesensitizeColumn(table, column);
        if (desensitizeColumn == null) {
            return 0;
        }
        return desensitizeColumn.getMinimumMatch();
    }

    public static boolean hasEncryptedColumn(String table) {
        List<DesensitizeColumn> desensitizeColumns = getDesensitizeColumns(table);
        if (desensitizeColumns == null) {
            return false;
        }
        for (DesensitizeColumn desensitizeColumn : desensitizeColumns) {
            if (!desensitizeColumn.isOriginal()) {
                return true;
            }
        }
        return false;
    }

    private static DesensitizeTable getDesensitizeTable(String table) {
        if (table == null || table.length() == 0) {
            return null;
        }
        for (DesensitizeTable desensitizeTable : CONFIG.keySet()) {
            if (desensitizeTable.isSameTable(table)) {
                return desensitizeTable;
            }
        }
        return null;
    }

    private static DesensitizeColumn getDesensitizeColumn(String table, String column) {
        if (table == null || table.length() == 0
                || column == null || column.length() == 0) {
            return null;
        }
        List<DesensitizeColumn> desensitizeColumns = getDesensitizeColumns(table);
        if (desensitizeColumns == null) {
            return null;
        }
        for (DesensitizeColumn desensitizeColumn : desensitizeColumns) {
            // 数据库列不区分大小写
            if (column.equalsIgnoreCase(desensitizeColumn.getName())) {
                return desensitizeColumn;
            }
        }
        return null;
    }

    private static List<DesensitizeColumn> getDesensitizeColumns(String table) {
        if (table == null || table.length() == 0) {
            return null;
        }
        for (Map.Entry<DesensitizeTable, List<DesensitizeColumn>> entry : CONFIG.entrySet()) {
            DesensitizeTable desensitizeTable = entry.getKey();
            if (desensitizeTable.isSameTable(table)) {
                return entry.getValue();
            }
        }
        return null;
    }

}

