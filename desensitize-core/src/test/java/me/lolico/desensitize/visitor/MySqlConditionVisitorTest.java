package me.lolico.desensitize.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import org.junit.Test;

import java.util.AbstractList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MySqlConditionVisitorTest {

    String update = "update `t_lmt_acct_order_info` set order_no = 1 where id = 1";
    String casewhen = "update t_lmt_acct_order_info set UPDATE_DATETIME = case when ORDER_NO = 123 then 1 when ORDER_NO = ? then ? when ORDER_NO = ? then ? when ORDER_NO = ? then ? when ORDER_NO = ? then ? else ? end, MOBILE_NO = case when ORDER_NO = ? then ? when ORDER_NO = ? then ? when ORDER_NO = ? then ? when ORDER_NO = ? then ? when ORDER_NO = ? then ? else ? end where ORDER_NO in ( ? , ? , ? , ? , ? )";
    String multiUpdate = "update t_lmt_acct_order_info set order_no = ? where id = ?;update t_lmt_acct_order_info set order_no = ? where id = ?";

    String select = "select id,order_no,cust_no from t_lmt_acct_order_info where id = 1 and cust_no = 1 and cust_name like '%lee%'";
    String funLike = "select id,order_no,cust_no from t_lmt_acct_order_info where id = ? and cust_name like CONCAT('%',?,'%')";
    String like = "select id,order_no,cust_no from t_lmt_acct_order_info where id = ? and cust_name like ?";

    String matrixSelect = "select * from t where (a, b) in ((\"x1\",\"y1\"), (\"x2\",\"y3\"), (\"x4\",\"y5\"))";
    String joinSelect = "select t1.c1,t1.c2,t1.c3,t1.c4 from t1 join t2 on t1.c1 = t2.c1 where t1.c5 = 5 and t2.c5 = 5";
    String subSelect = "select t1.c1,t1.c2,t2.c1 from t1 left join (select c1,c2,c3,c4 from t_lmt_acct_order_info where c5 = ?) as t2 on t1.c1 = t2.c1 where t1.c1 in (?,?,?) and t2.c2=? and (t2.c4 like ? or t2.c3 in (?,?))";
    String select1 = "select a,b,c from table1 where (a,b) > (1,2)";
    String between = "select a,b,c from table1 where d between ? and ?";
    String union = "select id,order_no,cust_no from t_lmt_acct_order_info where id = ? and cust_no = ? and cust_name like ? union select t1.c1,t1.c2,t1.c3,t1.c4 from t1 join t2 on t1.c1 = t2.c1 where t1.c5 = ? and t2.c5 = ?";

    String delete = "delete from t_lmt_acct_order_info where id in (1,2)";
    String subDelete = "delete from t_lmt_acct_order_info where id in (select id from cust_info where sex = ?)";
    String insert = "insert into t_lmt_acct_order_info (order_no,cust_no,price,cust_name) values (1, ?, ?, 'lee')";


    MySqlSchemaVisitor schemaStatVisitor = new MySqlSchemaVisitor();
    MySqlConditionVisitor rewriter = new MySqlConditionVisitor();

    MySqlConditionVisitor conditionVisitor = new MySqlConditionVisitor();

    @Test
    public void likeQuery() {
        List<SQLStatement> statements = SQLUtils.parseStatements("select * from table1 where column1 in (?,?,?) ", DbType.mysql);
        for (SQLStatement statement : statements) {
            statement.accept(conditionVisitor);
        }
        Map<String, String> map = new HashMap<>();
        map.put("table1", "table1_encrypt");
        List<Object> param = new AbstractList<Object>() {
            @Override
            public Object get(int index) {
                if (index == 0) {
                    return "1";
                } else if (index == 1) {
                    return "2";
                } else if (index == 2) {
                    return "3";
                }
                return null;
            }

            @Override
            public int size() {
                return 3;
            }
        };
        String refactor = SQLUtils.toSQLString(statements, DbType.mysql, param, null, map);
        System.out.println(refactor);
    }

    @Test
    public void update() {
        List<SQLStatement> statements = SQLUtils.parseStatements(multiUpdate, DbType.mysql);
        for (SQLStatement statement : statements) {
            statement.accept(rewriter);
        }
    }

    @Test
    public void select() {
        SQLStatement statement = SQLUtils.parseStatements(like, DbType.mysql).get(0);
        statement.accept(rewriter);
    }

    @Test
    public void delete() {
        SQLStatement statement = SQLUtils.parseStatements(subDelete, DbType.mysql).get(0);
        statement.accept(schemaStatVisitor);
        statement.accept(rewriter);
    }

    @Test
    public void insert() {
        SQLStatement statement = SQLUtils.parseStatements(insert, DbType.mysql).get(0);
        statement.accept(rewriter);
    }

}