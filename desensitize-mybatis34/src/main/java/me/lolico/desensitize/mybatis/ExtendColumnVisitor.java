package me.lolico.desensitize.mybatis;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCaseExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.sql.ast.statement.SQLInsertStatement;
import com.alibaba.druid.sql.ast.statement.SQLSelectStatement;
import com.alibaba.druid.sql.ast.statement.SQLUpdateSetItem;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlInsertStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.Lexeme;
import me.lolico.desensitize.visitor.MySqlConditionVisitor;
import me.lolico.desensitize.visitor.stat.Column;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

public class ExtendColumnVisitor extends MySqlConditionVisitor {

    private final List<ParameterMapping> parameterMappings;
    private final Function<ParameterMapping, Object> valueResolver;
    private final Function<String, String> encoder;

    private boolean changed;

    public ExtendColumnVisitor(List<ParameterMapping> parameterMappings, Function<ParameterMapping, Object> valueResolver, Function<String, String> encoder) {
        this.parameterMappings = parameterMappings;
        this.valueResolver = valueResolver;
        this.encoder = encoder;
    }

    @Override
    public boolean visit(MySqlInsertStatement x) {
        List<SQLExpr> columns = x.getColumns();

        for (int i = 0; i < columns.size(); i++) {
            SQLExpr expr = columns.get(i);
            Column column = getColumn(x.getTableName(), expr);
            if (column == null) {
                continue;
            }

            String table = column.getTable();
            String columnName = column.getName();

            if (DesensitizeRule.supportLike(table, columnName)) {
                // 扩展列
                columns.add(SQLUtils.toMySqlExpr(column.getName() + DesensitizeRule.LIKE_COLUMN_SUFFIX));
                this.changed = true;
                for (SQLInsertStatement.ValuesClause valuesClause : x.getValuesList()) {
                    SQLExpr sqlExpr = valuesClause.getValues().get(i);
                    if (sqlExpr == null) {
                        valuesClause.addValue(null);
                        continue;
                    }
                    if (sqlExpr instanceof SQLVariantRefExpr) {
                        String value = getValue((SQLVariantRefExpr) sqlExpr);
                        if (value == null) {
                            continue;
                        }
                        int minimumMatch = DesensitizeRule.getMinimumMatch(table, column.getName());
                        if (minimumMatch <= 0) {
                            continue;
                        }
                        Lexeme lexeme = Lexeme.of(value, minimumMatch);
                        // 扩展参数
                        valuesClause.addValue(new SQLCharExpr(lexeme.map(encoder)));
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean visit(MySqlUpdateStatement x) {

        List<SQLUpdateSetItem> items = x.getItems();
        int size = items.size();
        // avoid ConcurrentModificationException
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < size; i++) {
            accept(items.get(i));
        }

        return false;
    }

    @Override
    public boolean visit(SQLUpdateSetItem x) {
        SQLExpr column = x.getColumn();
        if (column == null) {
            return false;
        }

        MySqlUpdateStatement statement = (MySqlUpdateStatement) getCurrentSqlStatement();

        SQLExpr sqlExpr = x.getValue();
        Column resolvedColumn = getColumn(column);

        String columnName = resolvedColumn.getName();
        String table = resolvedColumn.getTable();

        if (sqlExpr instanceof SQLCaseExpr) {
            SQLExpr expr = SQLUtils.toMySqlExpr(columnName + DesensitizeRule.LIKE_COLUMN_SUFFIX);
            SQLUpdateSetItem updateSetItem = new SQLUpdateSetItem();
            updateSetItem.setColumn(expr);
            SQLCaseExpr sqlCaseExpr = (SQLCaseExpr) sqlExpr.clone();

            for (SQLCaseExpr.Item item : sqlCaseExpr.getItems()) {
                SQLExpr valueExpr = item.getValueExpr();
                if (valueExpr instanceof SQLVariantRefExpr) {
                    Lexeme lexeme = resolveLexeme(columnName, table, (SQLVariantRefExpr) valueExpr);
                    if (lexeme == null) continue;
                    this.changed = true;
                    item.setValueExpr(new SQLCharExpr(lexeme.map(encoder)));
                }
            }

            SQLExpr elseExpr = sqlCaseExpr.getElseExpr();
            if (elseExpr instanceof SQLVariantRefExpr) {
                Lexeme lexeme = resolveLexeme(columnName, table, (SQLVariantRefExpr) elseExpr);
                if (lexeme == null) return false;
                this.changed = true;
                sqlCaseExpr.setElseExpr(new SQLCharExpr(lexeme.map(encoder)));
            }

            statement.addItem(updateSetItem);
        } else if (sqlExpr instanceof SQLVariantRefExpr) {
            String value = getValue((SQLVariantRefExpr) sqlExpr);
            if (value == null) {
                return false;
            }
            int minimumMatch = DesensitizeRule.getMinimumMatch(table, columnName);
            if (minimumMatch <= 0) {
                return false;
            }
            Lexeme lexeme = Lexeme.of(value, minimumMatch);
            this.changed = true;

            SQLUpdateSetItem updateSetItem = new SQLUpdateSetItem();
            updateSetItem.setColumn(SQLUtils.toMySqlExpr(columnName + DesensitizeRule.LIKE_COLUMN_SUFFIX));
            updateSetItem.setValue(new SQLCharExpr(lexeme.map(encoder)));

            statement.addItem(updateSetItem);
        }
        return false;
    }

    private Lexeme resolveLexeme(String columnName, String table, SQLVariantRefExpr valueExpr) {
        String value = getValue(valueExpr);
        if (value == null) {
            return null;
        }
        int minimumMatch = DesensitizeRule.getMinimumMatch(table, columnName);
        if (minimumMatch <= 0) {
            return null;
        }
        return Lexeme.of(value, minimumMatch);
    }

    private String getValue(SQLVariantRefExpr reference) {
        ParameterMapping parameterMapping = parameterMappings.get(reference.getIndex());
        if (parameterMapping.getMode() == ParameterMode.OUT) {
            return null;
        }
        Object value = valueResolver.apply(parameterMapping);
        if (value instanceof String) {
            return (String) value;
        }
        return null;
    }

    public boolean isChanged() {
        return changed;
    }

    @Override
    public boolean visit(MySqlDeleteStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLSelectStatement x) {
        return false;
    }
}
