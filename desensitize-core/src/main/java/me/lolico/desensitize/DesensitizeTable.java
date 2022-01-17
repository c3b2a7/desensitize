package me.lolico.desensitize;

import java.util.Objects;

public class DesensitizeTable {
    // 表名
    private String name;
    // 从加密表查询
    private boolean selectFromEncryptedTable;
    // 表名是否区分大小写 ( 默认大小写不敏感）
    private boolean tableNameCaseInsensitive = true;

    public DesensitizeTable(String name) {
        this.name = name;
    }

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

    public boolean isTableNameCaseInsensitive() {
        return tableNameCaseInsensitive;
    }

    public void setTableNameCaseInsensitive(boolean tableNameCaseInsensitive) {
        this.tableNameCaseInsensitive = tableNameCaseInsensitive;
    }

    /**
     * 根据大小写敏感判断是否同一张表
     */
    public boolean isSameTable(String tableName) {
        return tableNameCaseInsensitive ? name.equalsIgnoreCase(tableName) : name.equals(tableName);
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
