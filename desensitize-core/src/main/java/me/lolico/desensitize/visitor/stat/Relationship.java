package me.lolico.desensitize.visitor.stat;

public class Relationship {
    private final Column left;
    private final Column right;
    private final String operator;

    public Relationship(Column left, Column right, String operator) {
        this.left = left;
        this.right = right;
        this.operator = operator;
    }

    public Column getLeft() {
        return left;
    }

    public Column getRight() {
        return right;
    }

    public String getOperator() {
        return operator;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((left == null) ? 0 : left.hashCode());
        result = prime * result + ((operator == null) ? 0 : operator.hashCode());
        result = prime * result + ((right == null) ? 0 : right.hashCode());
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
        Relationship other = (Relationship) obj;
        if (left == null) {
            if (other.left != null) {
                return false;
            }
        } else if (!left.equals(other.left)) {
            return false;
        }
        if (operator == null) {
            if (other.operator != null) {
                return false;
            }
        } else if (!operator.equals(other.operator)) {
            return false;
        }
        if (right == null) {
            return other.right == null;
        } else return right.equals(other.right);
    }

    @Override
    public String toString() {
        return left + " " + operator + " " + right;
    }
}
