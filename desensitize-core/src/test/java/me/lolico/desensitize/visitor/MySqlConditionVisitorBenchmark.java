package me.lolico.desensitize.visitor;

import com.alibaba.druid.DbType;
import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.sql.ast.SQLStatement;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;


@BenchmarkMode({Mode.AverageTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class MySqlConditionVisitorBenchmark {

    @Benchmark
    public void accept() {
        String union = "select id,order_no,cust_no from t_lmt_acct_order_info where id = ? and cust_no = ? and cust_name like ? union select t1.c1,t1.c2,t1.c3,t1.c4 from t1 join t2 on t1.c1 = t2.c1 where t1.c5 = ? and t2.c5 = ?";
        SQLStatement statement = SQLUtils.parseStatements(union, DbType.mysql).get(0);
        statement.accept(new MySqlConditionVisitor());
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
                .include(MySqlConditionVisitorBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(10)
                .measurementIterations(10)
                .build();
        new Runner(options).run();
    }

}