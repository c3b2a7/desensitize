package me.lolico.desensitize;

import com.alibaba.druid.sql.ast.SQLExpr;
import com.alibaba.druid.sql.ast.expr.SQLCharExpr;
import com.alibaba.druid.sql.ast.expr.SQLMethodInvokeExpr;
import com.alibaba.druid.sql.ast.expr.SQLVariantRefExpr;
import com.alibaba.druid.util.FnvHash;
import me.lolico.desensitize.visitor.stat.Column;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * like元信息
 *
 * @author l00998
 */
public class LikeMeta {

    /**
     * 列
     */
    private final Column column;
    /**
     * 引用下标
     */
    private final List<Integer> references = new ArrayList<>();
    /**
     * 是否以%开始
     */
    private boolean startsWithPercentSymbol;
    /**
     * 是否以%结束
     */
    private boolean endsWithPercentSymbol;

    /**
     * 是否有函数调用
     */
    private final boolean hasMethodInvoke;

    public LikeMeta(Column column, SQLExpr x) {
        this.column = column;

        Queue<SQLExpr> queue = new LinkedList<>();
        queue.offer(x);

        boolean hasMethodInvoke = false;

        while (!queue.isEmpty()) {
            SQLExpr expr = queue.poll();
            if (expr instanceof SQLVariantRefExpr) {
                references.add(((SQLVariantRefExpr) expr).getIndex());
            } else if (expr instanceof SQLCharExpr) {
                SQLCharExpr charExpr = (SQLCharExpr) expr;
                if (references.isEmpty() && "%".equals(charExpr.getText())) {
                    startsWithPercentSymbol = true;
                } else if (queue.isEmpty() && "%".equals(charExpr.getText())) {
                    endsWithPercentSymbol = true;
                }
            } else if (expr instanceof SQLMethodInvokeExpr) {
                expandChild(expr, queue);
                hasMethodInvoke = true;
            }
        }

        this.hasMethodInvoke = hasMethodInvoke;
    }

    private void expandChild(SQLExpr expr, Queue<SQLExpr> queue) {
        if (expr instanceof SQLMethodInvokeExpr) {
            SQLMethodInvokeExpr methodInvokeExpr = (SQLMethodInvokeExpr) expr;
            List<SQLExpr> arguments = methodInvokeExpr.getArguments();
            long nameHash = methodInvokeExpr.methodNameHashCode64();
            if (nameHash == FnvHash.Constants.CONCAT) {
                for (SQLExpr argument : arguments) {
                    // 递归展开子项
                    expandChild(argument, queue);
                }
            }
        } else {
            queue.offer(expr);
        }
    }


    public Column getColumn() {
        return column;
    }

    public List<Integer> getReferences() {
        return references;
    }

    public boolean isStartsWithPercentSymbol() {
        return startsWithPercentSymbol;
    }

    public boolean isEndsWithPercentSymbol() {
        return endsWithPercentSymbol;
    }

    public boolean hasMethodInvoke() {
        return hasMethodInvoke;
    }
}
