package me.lolico.desensitize.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlDeleteStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlInsertStatement;
import com.alibaba.druid.sql.dialect.mysql.ast.statement.MySqlUpdateStatement;
import com.alibaba.druid.sql.dialect.mysql.visitor.MySqlASTVisitor;
import com.alibaba.druid.sql.repository.SchemaObject;
import com.alibaba.druid.sql.repository.SchemaRepository;
import com.alibaba.druid.util.FnvHash;
import me.lolico.desensitize.visitor.stat.Column;
import me.lolico.desensitize.visitor.stat.Mode;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

/**
 * 处理SQL condition
 *
 * @author l00998
 */
public class MySqlConditionVisitor implements MySqlASTVisitor {

    protected final SchemaRepository repository = new SchemaRepository(DbType.mysql);

    private Mode mode;
    private final Deque<SQLStatement> statementStack = new LinkedList<>();

    public Mode getMode() {
        return mode;
    }

    protected void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void preVisit(SQLObject x) {
        if (x instanceof SQLStatement) {
            statementStack.push((SQLStatement) x);
        }
    }

    @Override
    public void postVisit(SQLObject x) {
        if (x instanceof SQLStatement) {
            statementStack.pop();
        }
    }

    protected SQLStatement getCurrentSqlStatement() {
        return statementStack.peek();
    }

    @Override
    public boolean visit(MySqlInsertStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(Mode.INSERT);

        List<SQLExpr> columns = x.getColumns();
        List<SQLInsertStatement.ValuesClause> valuesList = x.getValuesList();
        for (SQLInsertStatement.ValuesClause clause : valuesList) {
            List<SQLExpr> values = clause.getValues();
            for (int i = 0; i < values.size(); i++) {
                SQLExpr expr = columns.get(i);
                SQLExpr value = values.get(i);
                Column column = getColumn(x.getTableName(), expr);
                if (column == null || value == null) {
                    continue;
                }
                handleCondition(column, value);
            }
        }
        return false;
    }

    @Override
    public boolean visit(MySqlDeleteStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(Mode.DELETE);

        accept(x.getWhere());

        return false;
    }

    @Override
    public boolean visit(MySqlUpdateStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(Mode.UPDATE);

        accept(x.getItems());
        accept(x.getWhere());

        return false;
    }

    @Override
    public boolean visit(SQLSelectStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(Mode.SELECT);

        return true;
    }

    @Override
    public boolean visit(SQLUpdateSetItem x) {
        SQLExpr column = x.getColumn();
        if (column == null) {
            return false;
        }

        SQLExpr value = x.getValue();
        Column resolvedColumn = getColumn(column);
        if (value instanceof SQLCaseExpr) {

            SQLCaseExpr caseExpr = (SQLCaseExpr) value;
            List<SQLCaseExpr.Item> items = caseExpr.getItems();

            for (SQLCaseExpr.Item item : items) {
                accept(item.getConditionExpr());

                SQLExpr valueExpr = item.getValueExpr();
                if (valueExpr != null) {
                    handleCondition(resolvedColumn, valueExpr);
                }
            }

            SQLExpr elseExpr = caseExpr.getElseExpr();
            if (elseExpr != null) {
                handleCondition(resolvedColumn, elseExpr);
            }
        } else {
            handleCondition(resolvedColumn, value);
        }
        return false;
    }

    @Override
    public boolean visit(SQLBinaryOpExpr x) {
        SQLObject parent = x.getParent();
        if (parent instanceof SQLIfStatement) {
            return true;
        }

        final SQLExpr left = x.getLeft();
        final SQLBinaryOperator op = x.getOperator();
        final SQLExpr right = x.getRight();

        if ((op == SQLBinaryOperator.BooleanAnd || op == SQLBinaryOperator.BooleanOr)
                && left instanceof SQLBinaryOpExpr
                && ((SQLBinaryOpExpr) left).getOperator() == op) {
            accept(SQLBinaryOpExpr.split(x, op));
            return false;
        }

        switch (op) {
            case Like:
            case NotLike:
                Column column = getColumn(left);
                if (column == null) {
                    return false;
                }
                column.setExpr(left);
                handleLikeCondition(column, right);
                return false;
            case Equality:
            case NotEqual:
                column = getColumn(left);
                handleCondition(column, right);
                return false;
            default:
                break; // not support
        }

        return true;
    }

    @Override
    public boolean visit(SQLBetweenExpr x) {
        // not support
        return false;
    }

    @Override
    public boolean visit(SQLInListExpr x) {
        SQLExpr expr = x.getExpr();
        List<SQLExpr> targetList = x.getTargetList();
        if (expr instanceof SQLListExpr) {
            List<SQLExpr> items = ((SQLListExpr) expr).getItems();
            // row constructor expression (dispatch column)
            // see https://dev.mysql.com/doc/refman/5.7/en/row-constructor-optimization.html
            for (SQLExpr sqlExpr : targetList) {
                if (sqlExpr instanceof SQLListExpr) {
                    List<SQLExpr> values = ((SQLListExpr) sqlExpr).getItems();
                    for (int i = 0; i < items.size(); i++) {
                        SQLExpr column = items.get(i);
                        SQLExpr value = values.get(i);
                        handleCondition(getColumn(column), value);
                    }
                }
            }
        } else {
            // dispatch row
            Column column = getColumn(expr);
            handleCondition(column, targetList.toArray(new SQLExpr[0]));
        }
        return false;
    }

    @Override
    public boolean visit(SQLInSubQueryExpr x) {
        SQLExpr expr = x.getExpr();

        if (expr != null) {
            return false;
        }

        accept(x.getSubQuery());

        return false;
    }

    @Override
    public boolean visit(SQLSelect x) {

        accept(x.getWithSubQuery());
        accept(x.getQuery());

        return false;
    }

    @Override
    public boolean visit(SQLSelectQueryBlock x) {

        accept(x.getFrom());
        accept(x.getWhere());

        return false;
    }

    @Override
    public boolean visit(SQLUnionQuery x) {
        accept(x.getRelations());
        return false;
    }

    @Override
    public boolean visit(SQLJoinTableSource x) {
        SQLTableSource left = x.getLeft();
        SQLTableSource right = x.getRight();

        accept(left);
        accept(right);

        return false;
    }

    @Override
    public boolean visit(SQLUnionQueryTableSource x) {

        accept(x.getUnion());

        return false;
    }

    @Override
    public boolean visit(SQLSubqueryTableSource x) {

        accept(x.getSelect());

        return false;
    }

    @Override
    public boolean visit(SQLWithSubqueryClause.Entry x) {

        SQLSelect select = x.getSubQuery();
        if (select != null) {
            select.accept(this);
        } else {
            x.getReturningStatement().accept(this);
        }

        return false;
    }

    private void handleCondition(Column column, SQLExpr... right) {
        if (column == null) {
            return;
        }
        for (SQLExpr valueExpr : right) {
            if (valueExpr instanceof SQLVariantRefExpr) {
                SQLVariantRefExpr variantRefExpr = (SQLVariantRefExpr) valueExpr;
                handleRefValueCondition(column, variantRefExpr.getIndex());
            } else if (valueExpr instanceof SQLValuableExpr) {
                SQLValuableExpr valuableExpr = (SQLValuableExpr) valueExpr;
                handleValueCondition(column, valuableExpr);
            }
        }
    }

    protected void handleLikeCondition(Column column, SQLExpr expr) {

    }

    protected void handleRefValueCondition(Column column, int index) {

    }

    protected void handleValueCondition(Column column, SQLValuableExpr expr) {

    }

    protected Column getColumn(SQLName table, SQLExpr column) {
        if (column instanceof SQLName) {
            return new Column(table.getSimpleName(), ((SQLName) column).getSimpleName());
        }
        return getColumn(column);
    }

    protected Column getColumn(SQLExpr expr) {
        expr = unwrapExpr(expr);

        if (expr instanceof SQLPropertyExpr) {
            SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;

            SQLExpr owner = propertyExpr.getOwner();
            String column = SQLUtils.normalize(propertyExpr.getName());

            if (owner instanceof SQLName) {
                SQLName table = (SQLName) owner;

                SQLObject resolvedOwnerObject = propertyExpr.getResolvedOwnerObject();
                if (resolvedOwnerObject instanceof SQLSubqueryTableSource
                        || resolvedOwnerObject instanceof SQLCreateProcedureStatement
                        || resolvedOwnerObject instanceof SQLCreateFunctionStatement) {
                    table = null;
                }

                if (resolvedOwnerObject instanceof SQLExprTableSource) {
                    SQLExpr tableSourceExpr = ((SQLExprTableSource) resolvedOwnerObject).getExpr();
                    if (tableSourceExpr instanceof SQLName) {
                        table = (SQLName) tableSourceExpr;
                    }
                } else if (resolvedOwnerObject instanceof SQLValuesTableSource) {
                    return null;
                }

                if (table != null) {
                    String tableName = resolveTableName(table);

                    long tableHashCode64 = table.hashCode64();

                    if (resolvedOwnerObject instanceof SQLExprTableSource) {
                        SchemaObject schemaObject = ((SQLExprTableSource) resolvedOwnerObject).getSchemaObject();
                        if (schemaObject != null && schemaObject.getStatement() instanceof SQLCreateTableStatement) {
                            SQLColumnDefinition columnDef = schemaObject.findColumn(propertyExpr.nameHashCode64());
                            if (columnDef == null) {
                                tableName = "UNKNOWN";
                                tableHashCode64 = FnvHash.Constants.UNKNOWN;
                            }
                        }
                    }

                    long basic = tableHashCode64;
                    basic ^= '.';
                    basic *= FnvHash.PRIME;
                    long columnHashCode64 = FnvHash.hashCode64(basic, column);

                    return new Column(tableName, column, columnHashCode64);
                }
            }
            return null;
        }

        if (expr instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr identifierExpr = (SQLIdentifierExpr) expr;
            if (identifierExpr.getResolvedParameter() != null) {
                return null;
            }

            if (identifierExpr.getResolvedTableSource() instanceof SQLSubqueryTableSource) {
                return null;
            }

            if (identifierExpr.getResolvedDeclareItem() != null) {
                return null;
            }

            String column = identifierExpr.getName();

            SQLName table = null;
            SQLTableSource tableSource = identifierExpr.getResolvedTableSource();
            if (tableSource instanceof SQLExprTableSource) {
                SQLExpr tableSourceExpr = ((SQLExprTableSource) tableSource).getExpr();

                if (tableSourceExpr != null && !(tableSourceExpr instanceof SQLName)) {
                    tableSourceExpr = unwrapExpr(tableSourceExpr);
                }

                if (tableSourceExpr instanceof SQLName) {
                    table = (SQLName) tableSourceExpr;
                }
            }

            if (table != null) {
                long basic = table.hashCode64();
                basic ^= '.';
                basic *= FnvHash.PRIME;
                long columnHashCode64 = FnvHash.hashCode64(basic, column);
                return new Column(table.toString(), column, columnHashCode64);
            }

            return new Column("UNKNOWN", column);
        }

        if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodInvokeExpr = (SQLMethodInvokeExpr) expr;
            List<SQLExpr> arguments = methodInvokeExpr.getArguments();
            long nameHash = methodInvokeExpr.methodNameHashCode64();
            if (nameHash == FnvHash.Constants.DATE_FORMAT) { // date_format(column,"")
                if (arguments.size() == 2
                        && arguments.get(0) instanceof SQLName
                        && arguments.get(1) instanceof SQLCharExpr) {
                    return getColumn(arguments.get(0));
                }
            }
        }

        return null;
    }

    private String resolveTableName(SQLName table) {
        String tableName;
        if (table instanceof SQLIdentifierExpr) {
            tableName = ((SQLIdentifierExpr) table).normalizedName();
        } else if (table instanceof SQLPropertyExpr) {
            tableName = ((SQLPropertyExpr) table).normalizedName();
        } else {
            tableName = table.toString();
        }
        return tableName;
    }

    private SQLExpr unwrapExpr(SQLExpr expr) {
        SQLExpr original = expr;

        for (int i = 0; ; i++) {
            if (i > 1000) {
                return null;
            }

            if (expr instanceof SQLMethodInvokeExpr) {
                SQLMethodInvokeExpr methodInvokeExp = (SQLMethodInvokeExpr) expr;
                if (methodInvokeExp.getArguments().size() == 1) {
                    expr = methodInvokeExp.getArguments().get(0);
                    continue;
                }
            }

            if (expr instanceof SQLCastExpr) {
                expr = ((SQLCastExpr) expr).getExpr();
                continue;
            }

            if (expr instanceof SQLPropertyExpr) {
                SQLPropertyExpr propertyExpr = (SQLPropertyExpr) expr;

                SQLTableSource resolvedTableSource = propertyExpr.getResolvedTableSource();
                if (resolvedTableSource instanceof SQLSubqueryTableSource) {
                    SQLSelect select = ((SQLSubqueryTableSource) resolvedTableSource).getSelect();
                    SQLSelectQueryBlock queryBlock = select.getFirstQueryBlock();
                    if (queryBlock != null) {
                        if (queryBlock.getGroupBy() != null) {
                            if (original.getParent() instanceof SQLBinaryOpExpr) {
                                SQLExpr other = ((SQLBinaryOpExpr) original.getParent()).other(original);
                                if (!SQLExprUtils.isLiteralExpr(other)) {
                                    break;
                                }
                            }
                        }

                        SQLSelectItem selectItem = queryBlock.findSelectItem(propertyExpr.nameHashCode64());
                        if (selectItem != null) {
                            SQLExpr selectItemExpr = selectItem.getExpr();
                            if (selectItemExpr instanceof SQLMethodInvokeExpr
                                    && ((SQLMethodInvokeExpr) selectItemExpr).getArguments().size() == 1) {
                                selectItemExpr = ((SQLMethodInvokeExpr) selectItemExpr).getArguments().get(0);
                            }
                            if (selectItemExpr != expr) {
                                expr = selectItemExpr;
                                continue;
                            }
                        } else if (queryBlock.selectItemHasAllColumn()) {
                            SQLTableSource allColumnTableSource = null;

                            SQLTableSource from = queryBlock.getFrom();
                            if (from instanceof SQLJoinTableSource) {
                                SQLSelectItem allColumnSelectItem = queryBlock.findAllColumnSelectItem();
                                if (allColumnSelectItem != null && allColumnSelectItem.getExpr() instanceof SQLPropertyExpr) {
                                    SQLExpr owner = ((SQLPropertyExpr) allColumnSelectItem.getExpr()).getOwner();
                                    if (owner instanceof SQLName) {
                                        allColumnTableSource = from.findTableSource(((SQLName) owner).nameHashCode64());
                                    }
                                }
                            } else {
                                allColumnTableSource = from;
                            }

                            if (allColumnTableSource == null) {
                                break;
                            }

                            propertyExpr = propertyExpr.clone();
                            propertyExpr.setResolvedTableSource(allColumnTableSource);

                            if (allColumnTableSource instanceof SQLExprTableSource) {
                                propertyExpr.setOwner(((SQLExprTableSource) allColumnTableSource).getExpr().clone());
                            }
                            expr = propertyExpr;
                            continue;
                        }
                    }
                } else if (resolvedTableSource instanceof SQLExprTableSource) {
                    SQLExprTableSource exprTableSource = (SQLExprTableSource) resolvedTableSource;
                    if (exprTableSource.getSchemaObject() != null) {
                        break;
                    }

                    SQLTableSource redirectTableSource = null;
                    SQLExpr tableSourceExpr = exprTableSource.getExpr();
                    if (tableSourceExpr instanceof SQLIdentifierExpr) {
                        redirectTableSource = ((SQLIdentifierExpr) tableSourceExpr).getResolvedTableSource();
                    } else if (tableSourceExpr instanceof SQLPropertyExpr) {
                        redirectTableSource = ((SQLPropertyExpr) tableSourceExpr).getResolvedTableSource();
                    }

                    if (redirectTableSource == resolvedTableSource) {
                        redirectTableSource = null;
                    }

                    if (redirectTableSource != null) {
                        propertyExpr = propertyExpr.clone();
                        if (redirectTableSource instanceof SQLExprTableSource) {
                            propertyExpr.setOwner(((SQLExprTableSource) redirectTableSource).getExpr().clone());
                        }
                        propertyExpr.setResolvedTableSource(redirectTableSource);
                        expr = propertyExpr;
                        continue;
                    }

                    propertyExpr = propertyExpr.clone();
                    propertyExpr.setOwner(tableSourceExpr);
                    expr = propertyExpr;
                    break;
                }
            }
            break;
        }
        return expr;
    }

    protected void accept(SQLObject x) {
        if (x != null) {
            x.accept(this);
        }
    }

    protected void accept(List<? extends SQLObject> nodes) {
        for (SQLObject node : nodes) {
            accept(node);
        }
    }

    @Override
    public boolean visit(SQLUnique x) {
        return false;
    }

    @Override
    public boolean visit(SQLBlockStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLExprStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAggregateExpr x) {
        return false;
    }

    @Override
    public boolean visit(SQLMethodInvokeExpr x) {
        return false;
    }

    @Override
    public boolean visit(SQLOrderBy x) {
        return false;
    }

    @Override
    public boolean visit(SQLOver x) {
        return false;
    }

    @Override
    public boolean visit(SQLWindow x) {
        return false;
    }

    @Override
    public boolean visit(SQLTruncateStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLColumnDefinition x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCallStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCommentStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCurrentOfCursorExpr x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableAddColumn x) {
        return false;
    }

    @Override
    public boolean visit(SQLRollbackStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropForeignKey x) {
        return false;
    }

    @Override
    public boolean visit(SQLUseStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDisableConstraint x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableEnableConstraint x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropConstraint x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropIndexStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateIndexStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLForeignKeyImpl x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropSequenceStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropTriggerStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropUserStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLGrantStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLRevokeStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropDatabaseStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableAddIndex x) {
        return false;
    }

    @Override
    public boolean visit(SQLCheck x) {
        return false;
    }

    @Override
    public boolean visit(SQLDefault x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateTriggerStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropFunctionStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropTableSpaceStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropProcedureStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableRename x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateProcedureStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateFunctionStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLOpenStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLFetchStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropMaterializedViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowMaterializedViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLRefreshMaterializedViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCloseStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowTablesStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDeclareItem x) {
        return false;
    }

    @Override
    public boolean visit(SQLPartitionByHash x) {
        return false;
    }

    @Override
    public boolean visit(SQLPartitionByRange x) {
        return false;
    }

    @Override
    public boolean visit(SQLPartitionByList x) {
        return false;
    }

    @Override
    public boolean visit(SQLPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLSubPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLSubPartitionByHash x) {
        return false;
    }

    @Override
    public boolean visit(SQLPartitionValue x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterDatabaseStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableConvertCharSet x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableReOrganizePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableCoalescePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableTruncatePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDiscardPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableImportPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableAnalyzePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableCheckPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableOptimizePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableRebuildPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableRepairPartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLSequenceExpr x) {
        return false;
    }

    @Override
    public boolean visit(SQLMergeStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLSetStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateSequenceStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableAddConstraint x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropIndex x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropPrimaryKey x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableDropKey x) {
        return false;
    }

    @Override
    public boolean visit(SQLDescribeStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLExplainStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateMaterializedViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLReplaceStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterFunctionStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropSynonymStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTypeStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterProcedureStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropTypeStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLExternalRecordFormat x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateDatabaseStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateTableGroupStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropTableGroupStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowDatabasesStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowColumnsStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowCreateTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowTableGroupsStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableSetOption x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowCreateViewStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateRoleStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropRoleStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowViewsStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableExchangePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropCatalogStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLValuesTableSource x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterIndexStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowIndexesStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAnalyzeTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLExportTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLImportTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCreateOutlineStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDumpStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLDropOutlineStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterOutlineStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLAlterTableArchivePartition x) {
        return false;
    }

    @Override
    public boolean visit(SQLCopyFromStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLCloneTableStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLSyncMetaStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLSavePointStatement x) {
        return false;
    }

    @Override
    public boolean visit(SQLShowPartitionsStmt x) {
        return false;
    }

}
