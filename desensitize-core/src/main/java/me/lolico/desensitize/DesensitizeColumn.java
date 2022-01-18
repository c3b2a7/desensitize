package me.lolico.desensitize;

import java.util.Objects;

public class DesensitizeColumn {
    // 列名
    private String name;
    // true -> 原始表；false -> 加密表
    private boolean original;
    // 最小匹配长度（like分词长度）
    private int minimumMatch;
    // 是否为正则表达式
    private boolean regex;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isOriginal() {
        return original;
    }

    public void setOriginal(boolean original) {
        this.original = original;
    }

    public int getMinimumMatch() {
        return minimumMatch;
    }

    public void setMinimumMatch(int minimumMatch) {
        this.minimumMatch = minimumMatch;
    }

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DesensitizeColumn that = (DesensitizeColumn) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }
}