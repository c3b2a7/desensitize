package me.lolico.desensitize.visitor.stat;

import com.alibaba.druid.support.json.JSONUtils;

import java.util.ArrayList;
import java.util.List;

public class Condition {

    private final Column column;
    private final String operator;
    private final List<Object> values = new ArrayList<Object>();

    public Condition(Column column, String operator) {
        this.column = column;
        this.operator = operator;
    }

    public Column getColumn() {
        return column;
    }

    public String getOperator() {
        return operator;
    }

    public List<Object> getValues() {
        return values;
    }

    public void addValue(Object value) {
        this.values.add(value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((column == null) ? 0 : column.hashCode());
        result = prime * result + ((operator == null) ? 0 : operator.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Condition other = (Condition) obj;
        if (column == null) {
            if (other.column != null) {
                return false;
            }
        } else if (!column.equals(other.column)) {
            return false;
        }
        if (operator == null) {
            return other.operator == null;
        } else return operator.equals(other.operator);
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(this.column.toString());
        buf.append(' ');
        buf.append(this.operator);

        if (values.size() == 1) {
            buf.append(' ');
            buf.append(this.values.get(0));
        } else if (values.size() > 0) {
            buf.append(" (");
            for (int i = 0; i < values.size(); ++i) {
                if (i != 0) {
                    buf.append(", ");
                }
                Object val = values.get(i);
                if (val instanceof String) {
                    String jsonStr = JSONUtils.toJSONString(val);
                    buf.append(jsonStr);
                } else {
                    buf.append(val);
                }
            }
            buf.append(")");
        }

        return buf.toString();
    }
}
