package me.lolico.desensitize;

import com.alibaba.druid.sql.SQLUtils;
import me.lolico.desensitize.visitor.stat.Column;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

public class LikeMetaTest {

    Column column;

    @Before
    public void setup() {
        column = new Column("user_table", "username");
    }

    @Test
    public void getColumn() {
        LikeMeta meta = new LikeMeta(column, SQLUtils.toSQLExpr("?"));
        Assertions.assertThat(meta.getColumn()).isNotNull();
    }

    @Test
    public void getReferences() {
        LikeMeta meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?,'%')"));
        Assertions.assertThat(meta.getReferences()).size().isEqualTo(1);
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?,?,'%')"));
        Assertions.assertThat(meta.getReferences()).size().isEqualTo(2);
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',concat(?,'%'))"));
        Assertions.assertThat(meta.getReferences()).size().isEqualTo(1);
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?,concat(?,?,'%'))"));
        Assertions.assertThat(meta.getReferences()).size().isEqualTo(3);
    }

    @Test
    public void isStartsWithPercentSymbol() {
        LikeMeta meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?,'%')"));
        Assertions.assertThat(meta.isStartsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(concat('%',?),'%')"));
        Assertions.assertThat(meta.isStartsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',concat(?,'%'))"));
        Assertions.assertThat(meta.isStartsWithPercentSymbol()).isTrue();

        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(?,'%')"));
        Assertions.assertThat(meta.isStartsWithPercentSymbol()).isFalse();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(concat(?,?),'%')"));
        Assertions.assertThat(meta.isStartsWithPercentSymbol()).isFalse();
    }

    @Test
    public void isEndsWithPercentSymbol() {
        LikeMeta meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?,'%')"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(concat('%',?),'%')"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',concat(?,'%'))"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(?,'%')"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isTrue();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(concat(?,?),'%')"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isTrue();

        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat('%',?)"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isFalse();
        meta = new LikeMeta(column, SQLUtils.toSQLExpr("concat(concat('%',?),?)"));
        Assertions.assertThat(meta.isEndsWithPercentSymbol()).isFalse();
    }
}