package me.lolico.desensitize.visitor.stat;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLIdentifierExpr;
import com.alibaba.druid.sql.ast.expr.SQLPropertyExpr;
import com.alibaba.druid.util.FnvHash;


public class Column {

    private final String table;
    private final String name;
    private final long hashCode64;

    private SQLExpr expr;

    private boolean where;
    private boolean groupBy;
    private boolean having;
    private boolean join;
    private boolean select;
    private boolean update;

    public Column(String table, String name) {
        this.table = table;
        this.name = name;
        if (table.indexOf('.') != -1) {
            SQLExpr owner = SQLUtils.toSQLExpr(table, DbType.mysql);
            hashCode64 = new SQLPropertyExpr(owner, name).hashCode64();
        } else {
            hashCode64 = FnvHash.hashCode64(table, name);
        }
    }

    public Column(String table, String name, long hashCode64) {
        this.table = table;
        this.name = name;
        this.hashCode64 = hashCode64;
    }

    public SQLExpr getExpr() {
        return expr;
    }

    public void setExpr(SQLExpr expr) {
        this.expr = expr;
    }

    public String getTable() {
        return table;
    }

    public String getFullName() {
        return table == null ? name : table + '.' + name;
    }

    public long hashCode64() {
        return hashCode64;
    }

    public boolean isWhere() {
        return where;
    }

    public void setWhere(boolean where) {
        this.where = where;
    }

    public boolean isSelect() {
        return select;
    }

    public void setSelec(boolean select) {
        this.select = select;
    }

    public boolean isGroupBy() {
        return groupBy;
    }

    public void setGroupBy(boolean groupBy) {
        this.groupBy = groupBy;
    }

    public boolean isHaving() {
        return having;
    }

    public boolean isJoin() {
        return join;
    }

    public void setJoin(boolean join) {
        this.join = join;
    }

    public void setHaving(boolean having) {
        this.having = having;
    }

    public boolean isUpdate() {
        return update;
    }

    public void setUpdate(boolean update) {
        this.update = update;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (expr == null) {
            return;
        }
        if (expr instanceof SQLIdentifierExpr) {
            ((SQLIdentifierExpr) expr).setName(name);
        } else if (expr instanceof SQLPropertyExpr) {
            ((SQLPropertyExpr) expr).setName(name);
        }
    }

    public int hashCode() {
        long hash = hashCode64();
        return (int) (hash ^ (hash >>> 32));
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Column)) {
            return false;
        }

        Column column = (Column) obj;
        return hashCode64 == column.hashCode64;
    }

    public String toString() {
        if (table != null) {
            return SQLUtils.normalize(table) + "." + SQLUtils.normalize(name);
        }
        return SQLUtils.normalize(name);
    }
}
