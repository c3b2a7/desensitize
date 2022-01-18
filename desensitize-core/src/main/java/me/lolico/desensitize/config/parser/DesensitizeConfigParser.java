package me.lolico.desensitize.config.parser;

import me.lolico.desensitize.DesensitizeColumn;
import me.lolico.desensitize.DesensitizeTable;

import java.util.List;
import java.util.Map;

public interface DesensitizeConfigParser {

    boolean support(String config);

    Map<DesensitizeTable, List<DesensitizeColumn>> parse(String config) throws Exception;
}
