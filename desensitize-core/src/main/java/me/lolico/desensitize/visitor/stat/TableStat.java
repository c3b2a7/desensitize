package me.lolico.desensitize.visitor.stat;

import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;

public class TableStat {

    private final SQLExprTableSource sqlExprTableSource;

    public TableStat(SQLExprTableSource sqlExprTableSource) {
        this.sqlExprTableSource = sqlExprTableSource;
    }

    public void setTableName(String tableName) {
        sqlExprTableSource.setSimpleName(tableName);
    }

    public String getTableName(boolean normalize) {
        return sqlExprTableSource.getTableName(normalize);
    }

    public String getAlias(boolean normalize) {
        return normalize ? sqlExprTableSource.getAlias2()
                : sqlExprTableSource.getAlias();
    }

    public int hashCode() {
        long value = hashCode64();
        return (int) (value ^ (value >>> 32));
    }

    public boolean equals(Object o) {
        if (!(o instanceof TableStat)) {
            return false;
        }

        TableStat other = (TableStat) o;
        return this.hashCode64() == other.hashCode64();
    }

    long hashCode64() {
        return sqlExprTableSource.getName().hashCode64();
    }
}
