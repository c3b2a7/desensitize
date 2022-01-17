package me.lolico.desensitize.mybatis;

import java.io.Serializable;

public class Sensitive implements Serializable {
    private String databaseName;
    private String tableName;
    private String hiveTable;
    private String sensitiveField;

    public String getDatabaseName() {
        return databaseName;
    }

    public void setDatabaseName(String databaseName) {
        this.databaseName = databaseName;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public String getHiveTable() {
        return hiveTable;
    }

    public void setHiveTable(String hiveTable) {
        this.hiveTable = hiveTable;
    }

    public String getSensitiveField() {
        return sensitiveField;
    }

    public void setSensitiveField(String sensitiveField) {
        this.sensitiveField = sensitiveField;
    }
}
