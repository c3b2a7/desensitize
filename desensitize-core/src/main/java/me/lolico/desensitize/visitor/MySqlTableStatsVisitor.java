package me.lolico.desensitize.visitor;

import com.alibaba.druid.sql.ast.statement.SQLExprTableSource;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitor;
import me.lolico.desensitize.visitor.stat.TableStat;

import java.util.ArrayList;
import java.util.List;

/**
 * 获取表名
 *
 * @author l00998
 */
public class MySqlTableStatsVisitor implements MySqlASTVisitor {

    protected final List<TableStat> tableStats = new ArrayList<>();

    public boolean visit(SQLExprTableSource x) {
        tableStats.add(new TableStat(x));
        return false;
    }

    public List<TableStat> getTableStats() {
        return tableStats;
    }

}
