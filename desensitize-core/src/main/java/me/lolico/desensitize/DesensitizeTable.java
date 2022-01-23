package me.lolico.desensitize;

import java.util.Objects;
import java.util.regex.Pattern;

public class DesensitizeTable {
    // 表名
    private String name;
    // 从加密表查询
    private boolean selectFromEncryptedTable;
    // 表名是否大小写铭感
    private boolean tableNameCaseSensitive = true;
    // 是否为正则表达式
    private boolean regex;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isSelectFromEncryptedTable() {
        return selectFromEncryptedTable;
    }

    public void setSelectFromEncryptedTable(boolean selectFromEncryptedTable) {
        this.selectFromEncryptedTable = selectFromEncryptedTable;
    }

    public boolean isTableNameCaseSensitive() {
        return tableNameCaseSensitive;
    }

    public void setTableNameCaseSensitive(boolean tableNameCaseSensitive) {
        this.tableNameCaseSensitive = tableNameCaseSensitive;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    /**
     * 根据大小写敏感和正则判断是否同一张表
     */
    public boolean isSameTable(String tableName) {
        if (isRegex()) {
            Pattern pattern;
            if (tableNameCaseSensitive) {
                pattern = Pattern.compile(name);
            } else {
                pattern = Pattern.compile(name, Pattern.CASE_INSENSITIVE);
            }
            return pattern.matcher(tableName).matches();
        }
        return tableNameCaseSensitive ? name.equals(tableName) : name.equalsIgnoreCase(tableName);
    }

    // 不可能存在一个表名相同，但一个区分大小写一个不区分
    // equals和hashcode不需要根据大小写是否敏感去判断

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesensitizeTable that = (DesensitizeTable) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}
