package me.lolico.desensitize.mybatis;


import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface SensitiveDao {

    List<Sensitive> selectBySensitive(@Param("param") Sensitive sensitive);

    List<Sensitive> selectByHiveTableLike(@Param("param") String hiveTable);

    int updateHiveTable(@Param("hiveTable") String hiveTable, @Param("query") String query);

    int updateSensitiveField(String s, @Param("list1") List<String> list);

    int addSensitive(Sensitive sensitive);

    int insertBatch(List<Sensitive> sensitiveCollection);
}