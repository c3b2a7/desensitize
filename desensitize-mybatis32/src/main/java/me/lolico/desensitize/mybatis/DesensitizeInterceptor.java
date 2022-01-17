package me.lolico.desensitize.mybatis;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.SQLStatement;
import com.alibaba.druid.sql.ast.expr.SQLValuableExpr;
import me.lolico.desensitize.DesensitizeRule;
import me.lolico.desensitize.Lexeme;
import me.lolico.desensitize.LikeMeta;
import me.lolico.desensitize.SqlContext;
import me.lolico.desensitize.codec.AesCodec;
import me.lolico.desensitize.codec.Codec;
import me.lolico.desensitize.config.ConfigManager;
import me.lolico.desensitize.visitor.MySqlConditionVisitor;
import me.lolico.desensitize.visitor.MySqlTableStatsVisitor;
import me.lolico.desensitize.visitor.stat.Column;
import me.lolico.desensitize.visitor.stat.TableStat;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.executor.resultset.ResultSetHandler;
import org.apache.ibatis.executor.statement.PreparedStatementHandler;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.*;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.function.UnaryOperator;


/**
 * Mybatis脱敏插件
 *
 * @author l00998
 */
@Intercepts({
        @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = ResultSetHandler.class, method = "handleResultSets", args = {Statement.class})
})
public class DesensitizeInterceptor implements Interceptor {

    private static final Logger logger = LoggerFactory.getLogger(DesensitizeInterceptor.class);
    private static final String CONTEXT = "__INTERNAL_SQL_CONTEXT__";

    private Codec codec;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Method method = invocation.getMethod();
        if ("prepare".equals(method.getName())) {
            return handlePrepare(invocation);
        } else if ("update".equals(method.getName())) {
            return handleUpdate(invocation);
        } else if ("handleResultSets".equals(method.getName())) {
            return handleHandleResultSets(invocation);
        }
        return invocation.proceed();
    }

    protected Object handlePrepare(Invocation invocation) throws Throwable {
        Object handler = realTarget(invocation.getTarget());
        MetaObject statementHandlerMeta = SystemMetaObject.forObject(handler);

        // 1、获取相关属性，创建SqlContext
        // StatementHandler会使用RoutingStatementHandler来委托
        BoundSql boundSql = (BoundSql) statementHandlerMeta.getValue("delegate.boundSql");
        MappedStatement mappedStatement = (MappedStatement) statementHandlerMeta.getValue("delegate.mappedStatement");
        String sql = boundSql.getSql();
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        Configuration configuration = mappedStatement.getConfiguration();
        SqlContext sqlContext = createSqlContextIfNecessary(boundSql);

        // 2、解析SQL
        List<SQLStatement> statements = SQLUtils.parseStatements(sql, DbType.mysql);
        sqlContext.setStatements(statements);
        MySqlTableStatsVisitor tableStatsVisitor = new MySqlTableStatsVisitor();
        MySqlConditionCollectVisitor conditionCollectVisitor = new MySqlConditionCollectVisitor(sqlContext);
        for (SQLStatement statement : statements) {
            statement.accept(tableStatsVisitor);
            statement.accept(conditionCollectVisitor);
        }

        // 3、重写表名
        List<TableStat> tableStats = tableStatsVisitor.getTableStats();
        sqlContext.setTableStats(tableStats);
        for (TableStat tableStat : tableStats) {
            String tableName = tableStat.getTableName(true);
            if (mappedStatement.getSqlCommandType() == SqlCommandType.SELECT
                    && DesensitizeRule.isSelectFromEncryptedTable(tableName)) {
                tableStat.setTableName(tableName + DesensitizeRule.ENCRYPT_TABLE_SUFFIX);
                sqlContext.setRewriteTable(true);
            }
        }

        if (parameterMappings != null) {
            // 4、处理like
            // step1 条件分词加密
            processLike(boundSql, parameterMappings, configuration, sqlContext);
            // step2 扩展列、扩展参数
            ExtendColumnVisitor visitor = new ExtendColumnVisitor(parameterMappings,
                    pm -> this.getOriginValue(sqlContext, boundSql, configuration, pm), codec.getEncoder());
            statements.forEach(s -> s.accept(visitor));
            if (visitor.isChanged()) {
                sqlContext.setRewriteColumn(true);
            }

            // 5、加密值
            // 重写表名则加密所有加密字段，否则只加密原始表加密字段
            Collection<Integer> indexList = sqlContext.isRewriteTable() ?
                    sqlContext.getEncryptColumns() : sqlContext.getOriginalTableColumns();

            for (Integer index : indexList) {
                ParameterMapping parameterMapping = parameterMappings.get(index);
                if (parameterMapping.getMode() == ParameterMode.OUT) {
                    continue;
                }
                Object value = getOriginValue(sqlContext, boundSql, configuration, parameterMapping);
                if (!(value instanceof String)) {
                    continue;
                }
                value = codec.encode((String) value);
                boundSql.setAdditionalParameter(parameterMapping.getProperty(), value);
            }
        }

        // 6、生成SQL
        if (sqlContext.isChanged()) {
            String generatedSql = SQLUtils.toSQLString(statements, DbType.mysql);
            statementHandlerMeta.setValue("delegate.boundSql.sql", generatedSql);
            logger.debug("Rewrite sql: {} -> {}", sqlContext.getOriginalSql(), generatedSql);
        }

        return invocation.proceed();
    }

    private void processLike(BoundSql boundSql, List<ParameterMapping> parameterMappings, Configuration configuration, SqlContext sqlContext) {
        Collection<LikeMeta> likeMetas = sqlContext.getLikeMetas();
        for (LikeMeta likeMeta : likeMetas) {
            if (likeMeta.getReferences().size() != 1) {
                logger.warn("`like` condition reference multiple values");
                continue;
            }
            Column column = likeMeta.getColumn();
            if (!sqlContext.isRewriteTable() && !DesensitizeRule.isOriginalTableColumn(column.getTable(), column.getName())) {
                continue;
            }
            int index = likeMeta.getReferences().get(0);
            ParameterMapping parameterMapping = parameterMappings.get(index);
            if (parameterMapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            Object value = getOriginValue(sqlContext, boundSql, configuration, parameterMapping);
            if (!(value instanceof String)) {
                continue;
            }
            int minimumMatch = DesensitizeRule.getMinimumMatch(column.getTable(), column.getName());
            if (minimumMatch <= 0) {
                continue;
            }
            column.setName(column.getName() + DesensitizeRule.LIKE_COLUMN_SUFFIX);
            sqlContext.setRewriteColumn(true);
            Lexeme lexeme = Lexeme.of((String) value, minimumMatch, '%');
            value = lexeme.map(codec.getEncoder());
            boundSql.setAdditionalParameter(parameterMapping.getProperty(), value);
        }
    }

    protected Object handleUpdate(Invocation invocation) throws Throwable {
        Object handler = realTarget(invocation.getTarget());
        MetaObject metaRoutingObject = SystemMetaObject.forObject(handler);
        StatementHandler statementHandler = (StatementHandler) metaRoutingObject.getValue("delegate");
        if (statementHandler instanceof PreparedStatementHandler) {
            MetaObject statementHandlerMeta = SystemMetaObject.forObject(statementHandler);
            Executor executor = (Executor) statementHandlerMeta.getValue("executor");
            MappedStatement mappedStatement = (MappedStatement) statementHandlerMeta.getValue("mappedStatement");
            BoundSql boundSql = (BoundSql) statementHandlerMeta.getValue("boundSql");
            PreparedStatement stmt = (PreparedStatement) invocation.getArgs()[0];
            return doUpdate(invocation, executor, mappedStatement, boundSql, stmt);
        }
        return invocation.proceed();
    }

    protected Object handleHandleResultSets(Invocation invocation) throws Throwable {
        Object handler = realTarget(invocation.getTarget());
        MetaObject metaResultSetObject = SystemMetaObject.forObject(handler);
        Executor executor = (Executor) metaResultSetObject.getValue("executor");
        MappedStatement mappedStatement = (MappedStatement) metaResultSetObject.getValue("mappedStatement");
        RowBounds rowBounds = (RowBounds) metaResultSetObject.getValue("rowBounds");
        ParameterHandler parameterHandler = (ParameterHandler) metaResultSetObject.getValue("parameterHandler");
        ResultHandler resultHandler = (ResultHandler) metaResultSetObject.getValue("resultHandler");
        BoundSql boundSql = (BoundSql) metaResultSetObject.getValue("boundSql");
        //构造拦截器
        DecryptResultSetHandler decryptResultSetHandler = new DecryptResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds, codec.getDecoder());
        Object[] args = invocation.getArgs();
        Method method = invocation.getMethod();
        return method.invoke(decryptResultSetHandler, args); // 插件传递问题，考虑包装ParameterMapping#TypeHandler
    }

    private Object doUpdate(Invocation invocation, Executor executor, MappedStatement mappedStatement, BoundSql boundSql, PreparedStatement stmt) throws Throwable {

        SqlContext sqlContext = createSqlContextIfNecessary(boundSql);

        if (!isReplayNecessary(sqlContext)) {
            return invocation.proceed();
        }

        // 双写
        Connection connection = stmt.getConnection();
        boolean autoCommit = connection.getAutoCommit();
        try {
            if (autoCommit) {
                connection.setAutoCommit(false);
            }
            // 写原始表
            stmt.execute();
            try {
                // 写加密表
                replay(connection, mappedStatement, boundSql, sqlContext);
            } catch (Throwable e) {
                logger.error("[Replay-Exception]", e);
                throw e;
            }
            if (autoCommit) {
                connection.commit();
            }
        } catch (Throwable e) {
            logger.error("[Encrypt-Exception]", e);
            if (autoCommit) {
                connection.rollback();
            }
            throw e;
        } finally {
            if (autoCommit) {
                connection.setAutoCommit(true);
            }
        }

        int rows = stmt.getUpdateCount();
        Object parameterObject = boundSql.getParameterObject();
        KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
        keyGenerator.processAfter(executor, mappedStatement, stmt, parameterObject);

        return rows;
    }

    /**
     * 判断是否需要双写
     */
    protected boolean isReplayNecessary(SqlContext sqlContext) {
        // 加密表字段不为空->需要双写加密表
        if (!sqlContext.getEncryptedTableColumns().isEmpty()) {
            return true;
        }
        // sql中无加密表字段，但是配置了加密表字段 ->需要双写加密表
        for (TableStat tableStat : sqlContext.getTableStats()) {
            String tableName = tableStat.getTableName(true);
            if (DesensitizeRule.hasEncryptedColumn(tableName)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 在加密表进行重放
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    protected void replay(Connection connection, MappedStatement mappedStatement, BoundSql boundSql, SqlContext sqlContext) throws SQLException {
        List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
        if (parameterMappings == null) {
            return;
        }

        // 修改表名
        for (TableStat tableStat : sqlContext.getTableStats()) {
            String tableName = tableStat.getTableName(true);
            if (DesensitizeRule.isEncryptTable(tableName)) {
                tableStat.setTableName(tableName + DesensitizeRule.ENCRYPT_TABLE_SUFFIX);
                sqlContext.setRewriteTable(true);
            }
        }

        Configuration configuration = mappedStatement.getConfiguration();
        List<SQLStatement> statements = sqlContext.getStatements();

        // 处理like
        processLike(boundSql, parameterMappings, configuration, sqlContext);

        String generatedSql = SQLUtils.toSQLString(statements, DbType.mysql);
        PreparedStatement prepareStatement = connection.prepareStatement(generatedSql);

        Collection<Integer> encryptedTableColumns = sqlContext.getEncryptedTableColumns();

        // 加密值
        for (int i = 0; i < parameterMappings.size(); i++) {
            ParameterMapping parameterMapping = parameterMappings.get(i);
            if (parameterMapping.getMode() == ParameterMode.OUT) {
                continue;
            }
            Object value = obtainOriginValue(boundSql, configuration, parameterMapping);
            if (value instanceof String && encryptedTableColumns.contains(i)) {
                value = codec.encode((String) value);
            }
            TypeHandler typeHandler = parameterMapping.getTypeHandler();
            JdbcType jdbcType = parameterMapping.getJdbcType();
            if (value == null && jdbcType == null) {
                jdbcType = configuration.getJdbcTypeForNull();
            }
            typeHandler.setParameter(prepareStatement, i + 1, value, jdbcType);
        }

        prepareStatement.execute();
    }

    protected SqlContext createSqlContextIfNecessary(BoundSql boundSql) {
        SqlContext sqlContext = (SqlContext) boundSql.getAdditionalParameter(CONTEXT);
        if (sqlContext == null) {
            sqlContext = new SqlContext(boundSql.getSql());
            boundSql.setAdditionalParameter(CONTEXT, sqlContext);
        }
        return sqlContext;
    }

    /**
     * 获得真正的处理对象,可能多层代理.
     */
    @SuppressWarnings("unchecked")
    private <T> T realTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return realTarget(metaObject.getValue("h.target"));
        }
        return (T) target;
    }

    /**
     * 缓存获取到的参数值到SqlContext，避免多次获取同一参数值时多次加密问题
     */
    private Object getOriginValue(SqlContext sqlContext, BoundSql boundSql,
                                  Configuration configuration, ParameterMapping parameterMapping) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        value = sqlContext.getCachedValue(propertyName);
        if (value == null) {
            value = obtainOriginValue(boundSql, configuration, parameterMapping);
            if (value != null) {
                sqlContext.putCachedValue(propertyName, value);
            }
        }
        return value;
    }

    private Object obtainOriginValue(BoundSql boundSql, Configuration configuration, ParameterMapping parameterMapping) {
        Object value;
        String propertyName = parameterMapping.getProperty();
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
        Object parameterObject = boundSql.getParameterObject();
        // issue #448 ask first for additional params
        if (boundSql.hasAdditionalParameter(propertyName)) {
            value = boundSql.getAdditionalParameter(propertyName);
        } else if (parameterObject == null) {
            value = null;
        } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            value = parameterObject;
        } else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
        }
        return value;
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        String configPath = properties.getProperty("config");
        if (configPath == null) {
            throw new IllegalArgumentException("config is required");
        }
        String aesKey = properties.getProperty("aesKey");
        if (aesKey == null) {
            throw new IllegalArgumentException("aesKey is required");
        }
        this.codec = new AesCodec(aesKey);
        ConfigManager.load(configPath);
    }

    /**
     * 继承 <code>MyMySqlConditionVisitor</code> 并重写handle方法以将<code>Condition</code>
     * 收集到 <code>SqlContext</code> 中
     *
     * @author l00998
     * @see MySqlConditionVisitor
     * @see SqlContext
     */
    private static class MySqlConditionCollectVisitor extends MySqlConditionVisitor {

        private final SqlContext sqlContext;

        MySqlConditionCollectVisitor(SqlContext sqlContext) {
            this.sqlContext = sqlContext;
        }

        @Override
        protected void handleRefValueCondition(Column column, int index) {
            String table = column.getTable();
            String columnName = column.getName();

            // 1、原始表加密字段，进行加密
            // 2、加密表查询，原始表加密字段、加密表加密字段都进行加密
            // （双写保证了任何加密字段都会加密写到加密表）
            if (DesensitizeRule.isOriginalTableColumn(table, columnName)) {
                sqlContext.addOriginalTableColumn(index);
            } else if (DesensitizeRule.isEncryptedTableColumn(table, columnName)) {
                sqlContext.addEncryptedTableColumn(index);
            }
        }

        @Override
        protected void handleLikeCondition(Column column, SQLExpr expr) {
            String columnName = column.getName();
            String table = column.getTable();

            // 字段支持密文like，进行处理
            if (DesensitizeRule.supportLike(table, columnName)) {

                LikeMeta likeMeta = new LikeMeta(column, expr);

                sqlContext.addLikeMeta(likeMeta);
            }
        }

        @Override
        protected void handleValueCondition(Column column, SQLValuableExpr expr) {
            // 写死的值，或者使用${}，不处理
            if (DesensitizeRule.isEncryptColumn(column.getTable(), column.getName())) {
                logger.warn(column + " is constants or using '$' placeholder");
            }
        }
    }
}
