package me.lolico.desensitize.visitor.stat;

import com.alibaba.druid.sql.SQLUtils;
import com.alibaba.druid.util.FnvHash;

public class TableName {
    private final String name;
    private final long hashCode64;

    public TableName(String name) {
        this(name, FnvHash.hashCode64(name));
    }

    public TableName(String name, long hashCode64) {
        this.name = name;
        this.hashCode64 = hashCode64;
    }

    public String getName() {
        return this.name;
    }

    public int hashCode() {
        long value = hashCode64();
        return (int) (value ^ (value >>> 32));
    }

    public long hashCode64() {
        return hashCode64;
    }

    public boolean equals(Object o) {
        if (!(o instanceof TableName)) {
            return false;
        }

        TableName other = (TableName) o;
        return this.hashCode64 == other.hashCode64;
    }

    public String toString() {
        return SQLUtils.normalize(this.name);
    }
}
