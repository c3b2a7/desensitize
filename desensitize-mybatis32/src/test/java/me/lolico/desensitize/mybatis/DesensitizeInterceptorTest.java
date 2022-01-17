package me.lolico.desensitize.mybatis;

import me.lolico.desensitize.mybatis.util.SessionManager;
import org.assertj.core.api.Assertions;
import org.assertj.core.util.Lists;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

public class DesensitizeInterceptorTest {

    private SensitiveDao sensitiveDao;

    @Before
    public void setup() {
        sensitiveDao = SessionManager.getSession().getMapper(SensitiveDao.class);
    }

    @Test
    public void likeQuery() {
        Assertions.assertThat(sensitiveDao.selectByHiveTableLike("%e4")).hasSize(3);
    }

    @Test
    public void addSensitive() {
        Sensitive sensitive = new Sensitive();
        sensitive.setDatabaseName("database0");
        sensitive.setHiveTable("hiveTable0");
        sensitive.setTableName("tablename0");
        sensitive.setSensitiveField("sensitiveField0");
        Assertions.assertThat(sensitiveDao.addSensitive(sensitive)).isEqualTo(1);
    }

    @Test
    public void batchInsert() {
        ArrayList<Sensitive> list = new ArrayList<>();
        Sensitive sensitive = new Sensitive();
        sensitive.setDatabaseName("database1");
        sensitive.setHiveTable("hiveTable1");
        sensitive.setSensitiveField("sensitiveField1");
        list.add(sensitive);
        sensitive = new Sensitive();
        sensitive.setDatabaseName("database2");
        sensitive.setHiveTable("hiveTable2");
        sensitive.setSensitiveField("sensitiveField2");
        list.add(sensitive);
        Assertions.assertThat(sensitiveDao.insertBatch(list)).isEqualTo(2);
    }

    @Test
    public void update() {
        sensitiveDao.updateHiveTable("hiveTable4", "%le%");
    }

    @Test
    public void batchUpdateSensitive() {
        sensitiveDao.updateSensitiveField("sensitiveField",
                Lists.newArrayList("sensitiveField1", "sensitiveField2"));
    }

    @Test
    public void selectSensitive() {
        Sensitive sensitive = new Sensitive();
        sensitive.setSensitiveField("sensitiveField0");
        Assertions.assertThat(sensitiveDao.selectBySensitive(sensitive)).hasSize(1);
        sensitive.setSensitiveField("sensitiveField1");
        Assertions.assertThat(sensitiveDao.selectBySensitive(sensitive)).hasSize(1);
        sensitive.setSensitiveField("sensitiveField2");
        Assertions.assertThat(sensitiveDao.selectBySensitive(sensitive)).hasSize(1);
    }

}
