package me.lolico.desensitize.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.*;
import com.alibaba.druid.sql.ast.expr.*;
import com.alibaba.druid.sql.ast.statement.*;
import com.alibaba.druid.sql.dialect.hive.stmt.HiveCreateTableStatement;
import com.alibaba.druid.sql.repository.SchemaObject;
import com.alibaba.druid.sql.repository.SchemaRepository;
import com.alibaba.druid.sql.visitor.SQLASTVisitorAdapter;
import com.alibaba.druid.sql.visitor.SQLEvalVisitor;
import com.alibaba.druid.sql.visitor.SQLEvalVisitorUtils;
import com.alibaba.druid.util.FnvHash;
import me.lolico.desensitize.visitor.stat.*;

import java.util.*;

/**
 * 解析sql信息。获取表名、列名、条件、关系
 *
 * @author l00998
 */
public class MySqlSchemaVisitor extends SQLASTVisitorAdapter {

    protected final SchemaRepository repository = new SchemaRepository(DbType.mysql);

    protected final List<SQLName> originalTables = new ArrayList<>();
    protected final Map<Long, TableName> tables = new LinkedHashMap<>();
    protected final Map<Long, Column> columns = new LinkedHashMap<>();
    protected final List<Condition> conditions = new ArrayList<>();
    protected final Set<Relationship> relationships = new LinkedHashSet<>();

    private final List<Object> parameters;

    private Mode mode;

    public MySqlSchemaVisitor() {
        this(new ArrayList<>());
    }

    public MySqlSchemaVisitor(List<Object> parameters) {
        this.parameters = parameters;
        this.dbType = DbType.mysql;
    }

    protected TableName addTableName(String tableName) {
        tableName = SQLUtils.normalize(tableName, true);
        long hashCode64 = FnvHash.hashCode64(tableName);
        TableName stat = tables.get(hashCode64);
        if (stat == null) {
            stat = new TableName(tableName, hashCode64);
            tables.put(hashCode64, stat);
        }
        return stat;
    }

    protected TableName addTableName(SQLName tableName) {
        String strName = resolveTableName(tableName);

        long hashCode64 = tableName.hashCode64();
        if (hashCode64 == FnvHash.Constants.DUAL) {
            return null;
        }
        originalTables.add(tableName);
        TableName stat = tables.get(hashCode64);
        if (stat == null) {
            stat = new TableName(strName, hashCode64);
            tables.put(hashCode64, stat);
        }
        return stat;
    }

    protected Column addColumn(String tableName, String columnName) {
        Column c = new Column(tableName, columnName);
        Column column = this.columns.get(c.hashCode64());
        if (column == null && columnName != null) {
            column = c;
            columns.put(c.hashCode64(), c);
        }
        return column;
    }

    protected Column addColumn(SQLName table, String columnName) {
        String tableName = resolveTableName(table);

        long basic = table.hashCode64();
        basic ^= '.';
        basic *= FnvHash.PRIME;
        long columnHashCode64 = FnvHash.hashCode64(basic, columnName);

        Column column = this.columns.get(columnHashCode64);
        if (column == null && columnName != null) {
            column = new Column(tableName, columnName, columnHashCode64);
            columns.put(columnHashCode64, column);
        }
        return column;
    }

    public Set<Relationship> getRelationships() {
        return relationships;
    }

    public List<Condition> getConditions() {
        return conditions;
    }

    public List<SQLName> getOriginalTables() {
        return originalTables;
    }

    public Mode getMode() {
        return mode;
    }

    protected void setModeOrigin(SQLObject x) {
        mode = (Mode) x.getAttribute("_original_use_mode");
    }

    protected Mode setMode(SQLObject x, Mode mode) {
        Mode oldMode = this.mode;
        x.putAttribute("_original_use_mode", oldMode);
        this.mode = mode;
        return oldMode;
    }

    @Override
    public boolean visit(SQLBetweenExpr x) {
        SQLExpr test = x.getTestExpr();
        SQLExpr begin = x.getBeginExpr();
        SQLExpr end = x.getEndExpr();

        statExpr(test);
        statExpr(begin);
        statExpr(end);

        handleCondition(test, "BETWEEN", begin, end);

        return false;
    }

    @Override
    public boolean visit(SQLBinaryOpExpr x) {
        SQLObject parent = x.getParent();

        if (parent instanceof SQLIfStatement) {
            return true;
        }

        SQLBinaryOperator op = x.getOperator();
        SQLExpr left = x.getLeft();
        SQLExpr right = x.getRight();

        if ((op == SQLBinaryOperator.BooleanAnd || op == SQLBinaryOperator.BooleanOr)
                && left instanceof SQLBinaryOpExpr
                && ((SQLBinaryOpExpr) left).getOperator() == op) {
            accept(SQLBinaryOpExpr.split(x, op));
            return false;
        }

        switch (op) {
            case Equality:
            case NotEqual:
            case GreaterThan:
            case GreaterThanOrEqual:
            case LessThan:
            case LessThanOrGreater:
            case LessThanOrEqual:
            case LessThanOrEqualOrGreaterThan:
            case SoudsLike:
            case Like:
            case NotLike:
            case Is:
            case IsNot:
                handleCondition(left, op.name, right);

                String reverseOp = op.name;
                switch (op) {
                    case LessThan:
                        reverseOp = SQLBinaryOperator.GreaterThan.name;
                        break;
                    case LessThanOrEqual:
                        reverseOp = SQLBinaryOperator.GreaterThanOrEqual.name;
                        break;
                    case GreaterThan:
                        reverseOp = SQLBinaryOperator.LessThan.name;
                        break;
                    case GreaterThanOrEqual:
                        reverseOp = SQLBinaryOperator.LessThanOrEqual.name;
                        break;
                    default:
                        break;
                }
                handleCondition(right, reverseOp, left);
                handleRelationship(left, op.name, right);
                break;
            case BooleanOr: {
                List<SQLExpr> list = SQLBinaryOpExpr.split(x, op);

                for (SQLExpr item : list) {
                    if (item instanceof SQLBinaryOpExpr) {
                        visit((SQLBinaryOpExpr) item);
                    } else {
                        item.accept(this);
                    }
                }

                return false;
            }
            case Modulus:
                if (right instanceof SQLIdentifierExpr) {
                    long hashCode64 = ((SQLIdentifierExpr) right).hashCode64();
                    if (hashCode64 == FnvHash.Constants.ISOPEN) {
                        left.accept(this);
                        return false;
                    }
                }
                break;
            default:
                break;
        }

        if (left instanceof SQLBinaryOpExpr) {
            visit((SQLBinaryOpExpr) left);
        } else {
            statExpr(left);
        }
        statExpr(right);
        return false;
    }

    protected void handleRelationship(SQLExpr left, String operator, SQLExpr right) {
        Column leftColumn = getColumn(left);
        if (leftColumn == null) {
            return;
        }

        Column rightColumn = getColumn(right);
        if (rightColumn == null) {
            return;
        }

        Relationship relationship = new Relationship(leftColumn, rightColumn, operator);
        this.relationships.add(relationship);
    }

    protected void handleCondition(SQLExpr expr, String operator, SQLExpr... valueExprs) {
        if (expr instanceof SQLCastExpr) {
            expr = ((SQLCastExpr) expr).getExpr();
        } else if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr func = (SQLMethodInvokeExpr) expr;
            List<SQLExpr> arguments = func.getArguments();
            if (func.methodNameHashCode64() == FnvHash.Constants.COALESCE
                    && arguments.size() > 0) {
                boolean allLiteral = true;
                for (int i = 1; i < arguments.size(); ++i) {
                    SQLExpr arg = arguments.get(i);
                    if (!(arg instanceof SQLLiteralExpr)) {
                        allLiteral = false;
                        break;
                    }
                }
                if (allLiteral) {
                    expr = arguments.get(0);
                }
            }
        }

        Column column = getColumn(expr);

        if (column == null
                && expr instanceof SQLBinaryOpExpr
                && valueExprs.length == 1 && valueExprs[0] instanceof SQLLiteralExpr) {
            SQLBinaryOpExpr left = (SQLBinaryOpExpr) expr;
            SQLLiteralExpr right = (SQLLiteralExpr) valueExprs[0];

            if (left.getRight() instanceof SQLIntegerExpr && right instanceof SQLIntegerExpr) {
                long v0 = ((SQLIntegerExpr) left.getRight()).getNumber().longValue();
                long v1 = ((SQLIntegerExpr) right).getNumber().longValue();

                SQLBinaryOperator op = left.getOperator();

                long v;
                switch (op) {
                    case Add:
                        v = v1 - v0;
                        break;
                    case Subtract:
                        v = v1 + v0;
                        break;
                    default:
                        return;
                }

                handleCondition(
                        left.getLeft(), operator, new SQLIntegerExpr(v));
                return;
            }
        }

        if (column == null) {
            return;
        }

        Condition condition = null;
        for (Condition item : this.getConditions()) {
            if (item.getColumn().equals(column) && item.getOperator().equals(operator)) {
                condition = item;
                break;
            }
        }

        if (condition == null) {
            condition = new Condition(column, operator);
            this.conditions.add(condition);
        }

        for (SQLExpr item : valueExprs) {
            Column valueColumn = getColumn(item);
            if (valueColumn != null) {
                continue;
            }

            Object value;

            if (item instanceof SQLCastExpr) {
                item = ((SQLCastExpr) item).getExpr();
            }

            if (item instanceof SQLMethodInvokeExpr
                    || item instanceof SQLCurrentTimeExpr) {
                value = item.toString();
            } else {
                value = SQLEvalVisitorUtils.eval(dbType, item, parameters, false);
                if (value == SQLEvalVisitor.EVAL_VALUE_NULL) {
                    value = null;
                }
            }

            condition.addValue(value);
        }
    }

    protected Column getColumn(SQLExpr expr) {
        // unwrap
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

                    Column columnObj = this.columns.get(columnHashCode64);
                    if (columnObj == null) {
                        columnObj = new Column(tableName, column, columnHashCode64);
                        if (!(resolvedOwnerObject instanceof SQLWithSubqueryClause.Entry)) {
                            this.columns.put(columnHashCode64, columnObj);
                        }
                    }

                    return columnObj;
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

                final Column old = columns.get(columnHashCode64);
                if (old != null) {
                    return old;
                }

                return new Column(table.toString(), column, columnHashCode64);
            }

            return new Column("UNKNOWN", column);
        }

        if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodInvokeExpr = (SQLMethodInvokeExpr) expr;
            List<SQLExpr> arguments = methodInvokeExpr.getArguments();
            long nameHash = methodInvokeExpr.methodNameHashCode64();
            if (nameHash == FnvHash.Constants.DATE_FORMAT) {
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
                                    && ((SQLMethodInvokeExpr) selectItemExpr).getArguments().size() == 1
                            ) {
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

    @Override
    public boolean visit(SQLInsertStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(x, Mode.INSERT);

        if (x.getTableName() != null) {
            addTableName(x.getTableName());
        }

        accept(x.getColumns());
        accept(x.getQuery());

        return false;
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
    public boolean visit(SQLSelectQueryBlock x) {
        SQLTableSource from = x.getFrom();

        setMode(x, Mode.SELECT);

        if (from == null) {
            for (SQLSelectItem selectItem : x.getSelectList()) {
                statExpr(selectItem.getExpr());
            }
            return false;
        }

        from.accept(this); // 提前执行，获得aliasMap

        SQLExprTableSource into = x.getInto();
        if (into != null && into.getExpr() instanceof SQLName) {
            SQLName intoExpr = (SQLName) into.getExpr();

            boolean isParam = intoExpr instanceof SQLIdentifierExpr && isParam((SQLIdentifierExpr) intoExpr);

            if (!isParam) {
                addTableName(intoExpr);
            }
            into.accept(this);
        }

        for (SQLSelectItem selectItem : x.getSelectList()) {
            if (selectItem.getClass() == SQLSelectItem.class) {
                statExpr(
                        selectItem.getExpr());
            } else {
                selectItem.accept(this);
            }
        }

        SQLExpr where = x.getWhere();
        if (where != null) {
            statExpr(where);
        }

        SQLExpr startWith = x.getStartWith();
        if (startWith != null) {
            statExpr(startWith);
        }

        SQLExpr connectBy = x.getConnectBy();
        if (connectBy != null) {
            statExpr(connectBy);
        }

        SQLSelectGroupByClause groupBy = x.getGroupBy();
        if (groupBy != null) {
            for (SQLExpr expr : groupBy.getItems()) {
                statExpr(expr);
            }
        }

        List<SQLWindow> windows = x.getWindows();
        if (windows != null && windows.size() > 0) {
            for (SQLWindow window : windows) {
                window.accept(this);
            }
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            this.visit(orderBy);
        }

        SQLExpr first = x.getFirst();
        if (first != null) {
            statExpr(first);
        }

        List<SQLSelectOrderByItem> distributeBy = x.getDistributeBy();
        if (distributeBy != null) {
            for (SQLSelectOrderByItem item : distributeBy) {
                statExpr(item.getExpr());
            }
        }

        List<SQLSelectOrderByItem> sortBy = x.getSortBy();
        if (sortBy != null) {
            for (SQLSelectOrderByItem orderByItem : sortBy) {
                statExpr(orderByItem.getExpr());
            }
        }

        for (SQLExpr expr : x.getForUpdateOf()) {
            statExpr(expr);
        }

        return false;
    }

    private static boolean isParam(SQLIdentifierExpr x) {
        return x.getResolvedParameter() != null
                || x.getResolvedDeclareItem() != null;
    }

    @Override
    public void endVisit(SQLSelectQueryBlock x) {
        setModeOrigin(x);
    }

    @Override
    public boolean visit(SQLJoinTableSource x) {
        SQLTableSource left = x.getLeft(), right = x.getRight();

        left.accept(this);
        right.accept(this);

        SQLExpr condition = x.getCondition();
        if (condition != null) {
            condition.accept(this);
        }

        if (x.getUsing().size() > 0
                && left instanceof SQLExprTableSource && right instanceof SQLExprTableSource) {
            SQLExpr leftExpr = ((SQLExprTableSource) left).getExpr();
            SQLExpr rightExpr = ((SQLExprTableSource) right).getExpr();

            for (SQLExpr expr : x.getUsing()) {
                if (expr instanceof SQLIdentifierExpr) {
                    String name = ((SQLIdentifierExpr) expr).getName();
                    SQLPropertyExpr leftPropExpr = new SQLPropertyExpr(leftExpr, name);
                    SQLPropertyExpr rightPropExpr = new SQLPropertyExpr(rightExpr, name);

                    leftPropExpr.setResolvedTableSource(left);
                    rightPropExpr.setResolvedTableSource(right);

                    SQLBinaryOpExpr usingCondition = new SQLBinaryOpExpr(leftPropExpr, SQLBinaryOperator.Equality, rightPropExpr);
                    usingCondition.accept(this);
                }
            }
        }

        return false;
    }

    @Override
    public boolean visit(SQLPropertyExpr x) {
        Column column = null;
        String ident = SQLUtils.normalize(x.getName());

        SQLTableSource tableSource = x.getResolvedTableSource();

        if (tableSource instanceof SQLSubqueryTableSource) {
            SQLSelect subSelect = ((SQLSubqueryTableSource) tableSource).getSelect();
            SQLSelectQueryBlock subQuery = subSelect.getQueryBlock();
            if (subQuery != null) {
                SQLTableSource subTableSource = subQuery.findTableSourceWithColumn(x.nameHashCode64());
                if (subTableSource != null) {
                    tableSource = subTableSource;
                }
            }
        }

        if (tableSource instanceof SQLExprTableSource) {
            SQLExpr expr = ((SQLExprTableSource) tableSource).getExpr();

            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr table = (SQLIdentifierExpr) expr;
                SQLTableSource resolvedTableSource = table.getResolvedTableSource();
                if (resolvedTableSource instanceof SQLExprTableSource) {
                    expr = ((SQLExprTableSource) resolvedTableSource).getExpr();
                }
            } else if (expr instanceof SQLPropertyExpr) {
                SQLPropertyExpr table = (SQLPropertyExpr) expr;
                SQLTableSource resolvedTableSource = table.getResolvedTableSource();
                if (resolvedTableSource instanceof SQLExprTableSource) {
                    expr = ((SQLExprTableSource) resolvedTableSource).getExpr();
                }
            }

            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr table = (SQLIdentifierExpr) expr;

                SQLTableSource resolvedTableSource = table.getResolvedTableSource();
                if (resolvedTableSource instanceof SQLWithSubqueryClause.Entry) {
                    return false;
                }

                String tableName = table.getName();
                SchemaObject schemaObject = ((SQLExprTableSource) tableSource).getSchemaObject();
                if (schemaObject != null
                        && schemaObject.getStatement() instanceof SQLCreateTableStatement
                        && !"*".equals(ident)) {
                    SQLColumnDefinition columnDef = schemaObject.findColumn(x.nameHashCode64());
                    if (columnDef == null) {
                        column = addColumn("UNKNOWN", ident);
                    }
                }

                if (column == null) {
                    column = addColumn(table, ident);
                }
            } else if (expr instanceof SQLPropertyExpr) {
                SQLPropertyExpr table = (SQLPropertyExpr) expr;
                column = addColumn(table, ident);

            } else if (expr instanceof SQLMethodInvokeExpr) {
                SQLMethodInvokeExpr methodInvokeExpr = (SQLMethodInvokeExpr) expr;
                if ("table".equalsIgnoreCase(methodInvokeExpr.getMethodName())
                        && methodInvokeExpr.getArguments().size() == 1
                        && methodInvokeExpr.getArguments().get(0) instanceof SQLName) {
                    SQLName table = (SQLName) methodInvokeExpr.getArguments().get(0);

                    String tableName = null;
                    if (table instanceof SQLPropertyExpr) {
                        SQLPropertyExpr propertyExpr = (SQLPropertyExpr) table;
                        SQLIdentifierExpr owner = (SQLIdentifierExpr) propertyExpr.getOwner();
                        if (propertyExpr.getResolvedTableSource() != null
                                && propertyExpr.getResolvedTableSource() instanceof SQLExprTableSource) {
                            SQLExpr resolveExpr = ((SQLExprTableSource) propertyExpr.getResolvedTableSource()).getExpr();
                            if (resolveExpr instanceof SQLName) {
                                tableName = resolveExpr + "." + propertyExpr.getName();
                            }
                        }
                    }

                    if (tableName == null) {
                        tableName = table.toString();
                    }

                    column = addColumn(tableName, ident);
                }
            }
        } else if (tableSource instanceof SQLWithSubqueryClause.Entry
                || tableSource instanceof SQLSubqueryTableSource
                || tableSource instanceof SQLUnionQueryTableSource
                || tableSource instanceof SQLValuesTableSource
                || tableSource instanceof SQLLateralViewTableSource) {
            return false;
        } else {
            if (x.getResolvedProcudure() != null) {
                return false;
            }

            if (x.getResolvedOwnerObject() instanceof SQLParameter) {
                return false;
            }

            boolean skip = false;
            for (SQLObject parent = x.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof SQLSelectQueryBlock) {
                    SQLTableSource from = ((SQLSelectQueryBlock) parent).getFrom();

                    if (from instanceof SQLValuesTableSource) {
                        skip = true;
                        break;
                    }
                } else if (parent instanceof SQLSelectQuery) {
                    break;
                }
            }
            if (!skip) {
                column = handleUnknownColumn(ident);
            }
        }

        if (column != null) {
            setColumn(x, column);
        }

        return false;
    }

    @Override
    public boolean visit(SQLIdentifierExpr x) {
        if (isParam(x)) {
            return false;
        }

        SQLTableSource tableSource = x.getResolvedTableSource();
        if (x.getParent() instanceof SQLSelectOrderByItem) {
            SQLSelectOrderByItem selectOrderByItem = (SQLSelectOrderByItem) x.getParent();
            if (selectOrderByItem.getResolvedSelectItem() != null) {
                return false;
            }
        }

        if (tableSource == null
                && (x.getResolvedParameter() != null
                || x.getResolvedDeclareItem() != null)) {
            return false;
        }

        long hash = x.nameHashCode64();
        if ((hash == FnvHash.Constants.LEVEL
                || hash == FnvHash.Constants.CONNECT_BY_ISCYCLE
                || hash == FnvHash.Constants.ROWNUM)
                && x.getResolvedColumn() == null
                && tableSource == null) {
            return false;
        }

        Column column = null;
        String ident = x.normalizedName();

        if (tableSource instanceof SQLExprTableSource) {
            SQLExpr expr = ((SQLExprTableSource) tableSource).getExpr();

            if (expr instanceof SQLMethodInvokeExpr) {
                SQLMethodInvokeExpr func = (SQLMethodInvokeExpr) expr;
                if (func.methodNameHashCode64() == FnvHash.Constants.ANN) {
                    expr = func.getArguments().get(0);
                }
            }

            if (expr instanceof SQLIdentifierExpr) {
                SQLIdentifierExpr table = (SQLIdentifierExpr) expr;
                column = addColumn(table, ident);
            } else if (expr instanceof SQLPropertyExpr || expr instanceof SQLDbLinkExpr) {
                String tableName;
                if (expr instanceof SQLPropertyExpr) {
                    tableName = ((SQLPropertyExpr) expr).normalizedName();
                } else {
                    tableName = expr.toString();
                }

                column = addColumn(tableName, ident);
            } else if (expr instanceof SQLMethodInvokeExpr) {
                SQLMethodInvokeExpr methodInvokeExpr = (SQLMethodInvokeExpr) expr;
                if ("table".equalsIgnoreCase(methodInvokeExpr.getMethodName())
                        && methodInvokeExpr.getArguments().size() == 1
                        && methodInvokeExpr.getArguments().get(0) instanceof SQLName) {
                    SQLName table = (SQLName) methodInvokeExpr.getArguments().get(0);

                    String tableName = null;
                    if (table instanceof SQLPropertyExpr) {
                        SQLPropertyExpr propertyExpr = (SQLPropertyExpr) table;
                        SQLIdentifierExpr owner = (SQLIdentifierExpr) propertyExpr.getOwner();
                        if (propertyExpr.getResolvedTableSource() != null
                                && propertyExpr.getResolvedTableSource() instanceof SQLExprTableSource) {
                            SQLExpr resolveExpr = ((SQLExprTableSource) propertyExpr.getResolvedTableSource()).getExpr();
                            if (resolveExpr instanceof SQLName) {
                                tableName = resolveExpr + "." + propertyExpr.getName();
                            }
                        }
                    }

                    if (tableName == null) {
                        tableName = table.toString();
                    }

                    column = addColumn(tableName, ident);
                }
            }
        } else if (tableSource instanceof SQLWithSubqueryClause.Entry
                || tableSource instanceof SQLSubqueryTableSource
                || tableSource instanceof SQLLateralViewTableSource) {
            return false;
        } else {
            boolean skip = false;
            for (SQLObject parent = x.getParent(); parent != null; parent = parent.getParent()) {
                if (parent instanceof SQLSelectQueryBlock) {
                    SQLTableSource from = ((SQLSelectQueryBlock) parent).getFrom();

                    if (from instanceof SQLValuesTableSource) {
                        skip = true;
                        break;
                    }
                } else if (parent instanceof SQLSelectQuery) {
                    break;
                }
            }
            if (x.getParent() instanceof SQLMethodInvokeExpr
                    && ((SQLMethodInvokeExpr) x.getParent()).methodNameHashCode64() == FnvHash.Constants.ANN) {
                skip = true;
            }

            if (!skip) {
                column = handleUnknownColumn(ident);
            }
        }

        if (column != null) {
            setColumn(x, column);
        }

        return false;
    }

    private boolean isParentSelectItem(SQLObject parent) {
        for (int i = 0; parent != null; parent = parent.getParent(), ++i) {
            if (i > 100) {
                break;
            }

            if (parent instanceof SQLSelectItem) {
                return true;
            }

            if (parent instanceof SQLSelectQueryBlock) {
                return false;
            }
        }
        return false;
    }

    private void setColumn(SQLExpr x, Column column) {
        SQLObject current = x;
        for (int i = 0; i < 100; ++i) {
            SQLObject parent = current.getParent();

            if (parent == null) {
                break;
            }

            if (parent instanceof SQLSelectQueryBlock) {
                SQLSelectQueryBlock query = (SQLSelectQueryBlock) parent;
                if (query.getWhere() == current) {
                    column.setWhere(true);
                }
                break;
            }

            if (parent instanceof SQLSelectGroupByClause) {
                SQLSelectGroupByClause groupBy = (SQLSelectGroupByClause) parent;
                if (current == groupBy.getHaving()) {
                    column.setHaving(true);
                } else if (groupBy.getItems().contains(current)) {
                    column.setGroupBy(true);
                }
                break;
            }

            if (isParentSelectItem(parent)) {
                column.setSelec(true);
                break;
            }

            if (parent instanceof SQLJoinTableSource) {
                SQLJoinTableSource join = (SQLJoinTableSource) parent;
                if (join.getCondition() == current) {
                    column.setJoin(true);
                }
                break;
            }

            current = parent;
        }
    }

    protected Column handleUnknownColumn(String columnName) {
        return addColumn("UNKNOWN", columnName);
    }

    @Override
    public boolean visit(SQLAllColumnExpr x) {
        SQLTableSource tableSource = x.getResolvedTableSource();
        if (tableSource == null) {
            return false;
        }

        statAllColumn(x, tableSource);

        return false;
    }

    private void statAllColumn(SQLAllColumnExpr x, SQLTableSource tableSource) {
        if (tableSource instanceof SQLExprTableSource) {
            statAllColumn(x, (SQLExprTableSource) tableSource);
            return;
        }

        if (tableSource instanceof SQLJoinTableSource) {
            SQLJoinTableSource join = (SQLJoinTableSource) tableSource;
            statAllColumn(x, join.getLeft());
            statAllColumn(x, join.getRight());
        }
    }

    private void statAllColumn(SQLAllColumnExpr x, SQLExprTableSource tableSource) {
        SQLName expr = tableSource.getName();
        SQLCreateTableStatement createStmt = null;

        SchemaObject tableObject = tableSource.getSchemaObject();
        if (tableObject != null) {
            SQLStatement stmt = tableObject.getStatement();
            if (stmt instanceof SQLCreateTableStatement) {
                createStmt = (SQLCreateTableStatement) stmt;
            }
        }

        if (createStmt != null
                && createStmt.getTableElementList().size() > 0) {
            SQLName tableName = createStmt.getName();
            for (SQLTableElement e : createStmt.getTableElementList()) {
                if (e instanceof SQLColumnDefinition) {
                    SQLColumnDefinition columnDefinition = (SQLColumnDefinition) e;
                    SQLName columnName = columnDefinition.getName();
                    Column column = addColumn(tableName, columnName.toString());
                    if (isParentSelectItem(x.getParent())) {
                        column.setSelec(true);
                    }
                }
            }
        } else if (expr != null) {
            Column column = addColumn(expr, "*");
            if (isParentSelectItem(x.getParent())) {
                column.setSelec(true);
            }
        }
    }

    public Collection<TableName> getTables() {
        return tables.values();
    }

    public boolean containsTable(String tableName) {
        return tables.containsKey(FnvHash.hashCode64(tableName));
    }

    public boolean containsColumn(String tableName, String columnName) {
        long hashCode;

        int p = tableName.indexOf('.');
        if (p != -1) {
            SQLExpr owner = SQLUtils.toSQLExpr(tableName, dbType);
            hashCode = new SQLPropertyExpr(owner, columnName).hashCode64();
        } else {
            hashCode = FnvHash.hashCode64(tableName, columnName);
        }
        return columns.containsKey(hashCode);
    }

    public Collection<Column> getColumns() {
        return columns.values();
    }

    public Column getColumn(String tableName, String columnName) {
        Column column = new Column(tableName, columnName);

        return this.columns.get(column.hashCode64());
    }

    @Override
    public boolean visit(SQLSelectStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        visit(x.getSelect());

        return false;
    }

    @Override
    public boolean visit(SQLWithSubqueryClause.Entry x) {
        String alias = x.getAlias();
        SQLWithSubqueryClause with = (SQLWithSubqueryClause) x.getParent();

        SQLSelect select = x.getSubQuery();
        if (select != null) {
            select.accept(this);
        } else {
            x.getReturningStatement().accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLSubqueryTableSource x) {
        x.getSelect().accept(this);
        return false;
    }

    protected boolean isSimpleExprTableSource(SQLExprTableSource x) {
        return x.getExpr() instanceof SQLName;
    }

    protected TableName getTableStatWithUnwrap(SQLExpr expr) {
        SQLExpr identExpr = null;

        expr = unwrapExpr(expr);

        if (expr instanceof SQLIdentifierExpr) {
            SQLIdentifierExpr identifierExpr = (SQLIdentifierExpr) expr;

            if (identifierExpr.nameHashCode64() == FnvHash.Constants.DUAL) {
                return null;
            }

            if (isSubQueryOrParamOrVariant(identifierExpr)) {
                return null;
            }
        }

        SQLTableSource tableSource = null;
        if (expr instanceof SQLIdentifierExpr) {
            tableSource = ((SQLIdentifierExpr) expr).getResolvedTableSource();
        } else if (expr instanceof SQLPropertyExpr) {
            tableSource = ((SQLPropertyExpr) expr).getResolvedTableSource();
        }

        if (tableSource instanceof SQLExprTableSource) {
            SQLExpr tableSourceExpr = ((SQLExprTableSource) tableSource).getExpr();
            if (tableSourceExpr instanceof SQLName) {
                identExpr = tableSourceExpr;
            }
        }

        if (identExpr == null) {
            identExpr = expr;
        }

        if (identExpr instanceof SQLName) {
            return addTableName((SQLName) identExpr);
        }
        return addTableName(identExpr.toString());
    }

    @Override
    public boolean visit(SQLExprTableSource x) {
        SQLExpr expr = x.getExpr();
        if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr func = (SQLMethodInvokeExpr) expr;
            if (func.methodNameHashCode64() == FnvHash.Constants.ANN) {
                expr = func.getArguments().get(0);
            }
        }

        if (isSimpleExprTableSource(x)) {
            TableName stat = getTableStatWithUnwrap(expr);
            if (stat == null) {
                return false;
            }
        } else {
            accept(expr);
        }

        return false;
    }

    protected boolean isSubQueryOrParamOrVariant(SQLIdentifierExpr identifierExpr) {
        SQLObject resolvedColumnObject = identifierExpr.getResolvedColumnObject();
        if (resolvedColumnObject instanceof SQLWithSubqueryClause.Entry
                || resolvedColumnObject instanceof SQLParameter
                || resolvedColumnObject instanceof SQLDeclareItem) {
            return true;
        }

        SQLObject resolvedOwnerObject = identifierExpr.getResolvedOwnerObject();
        return resolvedOwnerObject instanceof SQLSubqueryTableSource
                || resolvedOwnerObject instanceof SQLWithSubqueryClause.Entry;
    }

    protected boolean isSubQueryOrParamOrVariant(SQLPropertyExpr x) {
        SQLObject resolvedOwnerObject = x.getResolvedOwnerObject();
        if (resolvedOwnerObject instanceof SQLSubqueryTableSource
                || resolvedOwnerObject instanceof SQLWithSubqueryClause.Entry) {
            return true;
        }

        SQLExpr owner = x.getOwner();
        if (owner instanceof SQLIdentifierExpr) {
            if (isSubQueryOrParamOrVariant((SQLIdentifierExpr) owner)) {
                return true;
            }
        }

        SQLTableSource tableSource = x.getResolvedTableSource();
        if (tableSource instanceof SQLExprTableSource) {
            SQLExprTableSource exprTableSource = (SQLExprTableSource) tableSource;
            if (exprTableSource.getSchemaObject() != null) {
                return false;
            }

            SQLExpr expr = exprTableSource.getExpr();

            if (expr instanceof SQLIdentifierExpr) {
                return isSubQueryOrParamOrVariant((SQLIdentifierExpr) expr);
            }

            if (expr instanceof SQLPropertyExpr) {
                return isSubQueryOrParamOrVariant((SQLPropertyExpr) expr);
            }
        }

        return false;
    }

    @Override
    public boolean visit(SQLSelectItem x) {
        statExpr(x.getExpr());
        return false;
    }

    @Override
    public boolean visit(SQLSelect x) {
        SQLWithSubqueryClause with = x.getWithSubQuery();
        if (with != null) {
            with.accept(this);
        }

        SQLSelectQuery query = x.getQuery();
        if (query != null) {
            query.accept(this);
        }

        SQLOrderBy orderBy = x.getOrderBy();
        if (orderBy != null) {
            accept(x.getOrderBy());
        }

        return false;
    }

    @Override
    public boolean visit(SQLUpdateStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(x, Mode.UPDATE);

        SQLTableSource tableSource = x.getTableSource();
        if (tableSource instanceof SQLExprTableSource) {
            SQLName identName = ((SQLExprTableSource) tableSource).getName();
            addTableName(identName);
        } else {
            tableSource.accept(this);
        }

        final SQLTableSource from = x.getFrom();
        if (from != null) {
            from.accept(this);
        }

        final List<SQLUpdateSetItem> items = x.getItems();
        for (SQLUpdateSetItem item : items) {
            visit(item);
        }

        final SQLExpr where = x.getWhere();
        if (where != null) {
            where.accept(this);
        }

        for (SQLExpr item : x.getReturning()) { // option
            item.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLUpdateSetItem x) {
        final SQLExpr column = x.getColumn();
        if (column != null) {
            statExpr(column);

            final Column columnStat = getColumn(column);
            if (columnStat != null) {
                columnStat.setUpdate(true);
            }
        }

        final SQLExpr value = x.getValue();
        if (value != null) {
            statExpr(value);
        }

        return false;
    }

    @Override
    public boolean visit(SQLDeleteStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        setMode(x, Mode.DELETE);

        if (x.getTableSource() instanceof SQLSubqueryTableSource) {
            SQLSelectQuery selectQuery = ((SQLSubqueryTableSource) x.getTableSource()).getSelect().getQuery();
            if (selectQuery instanceof SQLSelectQueryBlock) {
                SQLSelectQueryBlock subQueryBlock = ((SQLSelectQueryBlock) selectQuery);
                subQueryBlock.getWhere().accept(this);
            }
        }

        addTableName(x.getTableName());

        final SQLExpr where = x.getWhere();
        if (where != null) {
            where.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLInListExpr x) {
        List<SQLExpr> values = x.getTargetList();
        if (x.isNot()) {
            handleCondition(x.getExpr(), "NOT IN", values.toArray(new SQLExpr[0]));
        } else {
            handleCondition(x.getExpr(), "IN", values.toArray(new SQLExpr[0]));
        }

        return true;
    }

    @Override
    public boolean visit(SQLInSubQueryExpr x) {
        if (x.isNot()) {
            handleCondition(x.getExpr(), "NOT IN");
        } else {
            handleCondition(x.getExpr(), "IN");
        }
        return true;
    }

    @Override
    public boolean visit(SQLArrayExpr x) {
        accept(x.getValues());

        SQLExpr exp = x.getExpr();
        if (exp instanceof SQLIdentifierExpr) {
            if (((SQLIdentifierExpr) exp).getName().equals("ARRAY")) {
                return false;
            }
        }
        if (exp != null) {
            exp.accept(this);
        }
        return false;
    }

    @Override
    public boolean visit(SQLBlockStatement x) {
        if (x.getParent() == null) {
            repository.resolve(x);
        }

        for (SQLParameter param : x.getParameters()) {
            param.setParent(x);
            param.accept(this);
        }

        for (SQLStatement stmt : x.getStatementList()) {
            stmt.accept(this);
        }

        SQLStatement exception = x.getException();
        if (exception != null) {
            exception.accept(this);
        }

        return false;
    }

    @Override
    public boolean visit(SQLUnionQuery x) {
        SQLUnionOperator operator = x.getOperator();
        List<SQLSelectQuery> relations = x.getRelations();
        if (relations.size() > 2) {
            for (SQLSelectQuery relation : x.getRelations()) {
                relation.accept(this);
            }
            return false;
        }

        SQLSelectQuery left = x.getLeft();
        SQLSelectQuery right = x.getRight();

        boolean bracket = x.isParenthesized() && !(x.getParent() instanceof SQLUnionQueryTableSource);

        if ((!bracket)
                && left instanceof SQLUnionQuery
                && ((SQLUnionQuery) left).getOperator() == operator
                && !right.isParenthesized()
                && x.getOrderBy() == null) {

            SQLUnionQuery leftUnion = (SQLUnionQuery) left;

            List<SQLSelectQuery> rights = new ArrayList<>();
            rights.add(right);

            if (leftUnion.getRelations().size() > 2) {
                rights.addAll(leftUnion.getRelations());
            } else {
                for (; ; ) {
                    SQLSelectQuery leftLeft = leftUnion.getLeft();
                    SQLSelectQuery leftRight = leftUnion.getRight();

                    if ((!leftUnion.isParenthesized())
                            && leftUnion.getOrderBy() == null
                            && (!leftLeft.isParenthesized())
                            && (!leftRight.isParenthesized())
                            && leftLeft instanceof SQLUnionQuery
                            && ((SQLUnionQuery) leftLeft).getOperator() == operator) {
                        rights.add(leftRight);
                        leftUnion = (SQLUnionQuery) leftLeft;
                        continue;
                    } else {
                        rights.add(leftRight);
                        rights.add(leftLeft);
                    }
                    break;
                }
            }

            for (int i = rights.size() - 1; i >= 0; i--) {
                SQLSelectQuery item = rights.get(i);
                item.accept(this);
            }
            return false;
        }

        return true;
    }

    @Override
    public boolean visit(SQLExprStatement x) {
        SQLExpr expr = x.getExpr();
        return !(expr instanceof SQLName);
    }

    protected final void statExpr(SQLExpr x) {
        if (x == null) {
            return;
        }
        Class<?> clazz = x.getClass();
        if (clazz == SQLIdentifierExpr.class) {
            visit((SQLIdentifierExpr) x);
        } else if (clazz == SQLPropertyExpr.class) {
            visit((SQLPropertyExpr) x);
        } else if (clazz == SQLBinaryOpExpr.class) {
            visit((SQLBinaryOpExpr) x);
        } else if (x instanceof SQLLiteralExpr) {
            // skip
        } else {
            x.accept(this);
        }
    }

    @Override
    public boolean visit(SQLUnique x) {
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
    public boolean visit(HiveCreateTableStatement x) {
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
