package me.lolico.desensitize;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * 迭代分词，惰性创建片段
 *
 * @author l00998
 */
public class Lexeme implements Iterable<String> {

    private final char[] chars;
    private final int count;

    private Character symbol;
    private int leftCount;
    private int rightCount;

    private Lexeme(String src, int count) {
        this.chars = src.toCharArray();
        this.count = count;
    }

    private Lexeme(String src, int count, char symbol) {
        char[] array = src.toCharArray();
        this.symbol = symbol;

        int left = 0, right = array.length - 1;
        while (array[left] == symbol) {
            left = left + 1;
            leftCount = leftCount + 1;
        }
        while (array[right] == symbol) {
            right = right - 1;
            rightCount = rightCount + 1;
        }
        char[] copy = new char[right - left + 1];
        System.arraycopy(array, left, copy, 0, copy.length);

        this.chars = copy;
        this.count = count;
    }


    /**
     * @param src   源字符串
     * @param count 长度
     * @return 分词
     * @throws IllegalArgumentException 如果 <code>src</code>为<code>null</code>
     *                                  或者 <code>count</code> 小于等于 <code>0</code>.
     */
    public static Lexeme of(String src, int count) {
        if (src == null) {
            throw new IllegalArgumentException("src is null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count <= 0");
        }
        return new Lexeme(src, count);
    }

    /**
     * @param src    源字符串
     * @param count  长度
     * @param symbol 忽略的前后缀符号
     * @return 分词
     * @throws IllegalArgumentException 如果 <code>src</code>为<code>null</code>
     *                                  或者 <code>count</code> 小于等于 <code>0</code>.
     */
    public static Lexeme of(String src, int count, char symbol) {
        if (src == null) {
            throw new IllegalArgumentException("src is null");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count <= 0");
        }
        return new Lexeme(src, count, symbol);
    }

    /**
     * 将所有分词片段应用函数，转换并合并
     *
     * @param mapper 映射函数
     * @return 合并结果
     */
    public String map(Function<String, String> mapper) {
        String collect = stream().map(mapper).collect(Collectors.joining());
        int left = leftCount, right = rightCount;
        while (left-- > 0) {
            collect = symbol + collect;
        }
        while (right-- > 0) {
            collect = collect + symbol;
        }
        return collect;
    }

    public Stream<String> stream() {
        return StreamSupport.stream(spliterator(), false);
    }

    @Override
    public Iterator<String> iterator() {
        return new LexemeIterator();
    }

    private class LexemeIterator implements Iterator<String> {

        int left = 0;
        int right = count - 1;

        @Override
        public boolean hasNext() {
            return right < chars.length || (count > chars.length && left == 0);
        }

        @Override
        public String next() {
            try {
                if (count > chars.length && left++ == 0) {
                    return new String(chars);
                }
                String next = new String(chars, left, count);
                left++;
                right++;
                return next;
            } catch (IndexOutOfBoundsException e) {
                throw new NoSuchElementException();
            }
        }
    }
}
