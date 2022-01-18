package me.lolico.desensitize;

import com.alibaba.druid.sql.ast.SQLStatement;
import me.lolico.desensitize.util.CompositeCollection;
import me.lolico.desensitize.visitor.stat.TableStat;

import java.util.*;

/**
 * SQL上下文，传递相关信息
 *
 * @author l00998
 */
public final class SqlContext {

    // 原始sql
    private final String originalSql;

    // 原始表、加密表字段下标
    private final List<Integer> originalTableColumns = new ArrayList<>();
    private final List<Integer> encryptedTableColumns = new ArrayList<>();
    // like信息
    private final List<LikeMeta> likeMetas = new ArrayList<>();

    private List<TableStat> tableStats;
    private List<SQLStatement> statements;

    // 参数-值缓存
    private final Map<String, Object> parameterValueCachedMap = new HashMap<>();

    // 状态：是否重写表名、列名
    private boolean rewriteTable;
    private boolean rewriteColumn;

    public SqlContext(String originalSql) {
        this.originalSql = originalSql;
    }

    public String getOriginalSql() {
        return originalSql;
    }

    public void addOriginalTableColumn(int index) {
        this.originalTableColumns.add(index);
    }

    public void addEncryptedTableColumn(int index) {
        this.encryptedTableColumns.add(index);
    }

    public List<SQLStatement> getStatements() {
        return statements;
    }

    public void setStatements(List<SQLStatement> statements) {
        this.statements = statements;
    }

    public List<TableStat> getTableStats() {
        return tableStats;
    }

    public void setTableStats(List<TableStat> tableStats) {
        this.tableStats = tableStats;
    }

    public void addLikeMeta(LikeMeta likeMeta) {
        this.likeMetas.add(likeMeta);
    }

    public Collection<Integer> getOriginalTableColumns() {
        return Collections.unmodifiableList(originalTableColumns);
    }

    public Collection<Integer> getEncryptedTableColumns() {
        return Collections.unmodifiableList(encryptedTableColumns);
    }

    public Collection<LikeMeta> getLikeMetas() {
        return Collections.unmodifiableList(likeMetas);
    }

    public Collection<Integer> getEncryptColumns() {
        return new CompositeCollection<>(Arrays.asList(originalTableColumns, encryptedTableColumns));
    }

    public boolean isRewriteTable() {
        return rewriteTable;
    }

    public void setRewriteTable(boolean rewriteTable) {
        this.rewriteTable = rewriteTable;
    }

    public boolean isRewriteColumn() {
        return rewriteColumn;
    }

    public void setRewriteColumn(boolean rewriteColumn) {
        this.rewriteColumn = rewriteColumn;
    }

    public boolean isChanged() {
        return rewriteTable || rewriteColumn;
    }

    public Object getCachedValue(String parameter) {
        return parameterValueCachedMap.get(parameter);
    }

    public void putCachedValue(String parameter, Object value) {
        parameterValueCachedMap.put(parameter, value);
    }
}
