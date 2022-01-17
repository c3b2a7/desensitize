package me.lolico.desensitize;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class LexemeTest {

    @Test
    public void iteratorNotNull() {
        Lexeme lexeme = Lexeme.of("19991108", 4);
        Assertions.assertThat(lexeme.iterator()).isNotNull();
    }

    @Test
    public void iterator() {
        Lexeme lexeme = Lexeme.of("19991108", 4);

        Assertions.assertThatNoException().isThrownBy(() -> {
            Iterator<String> iterator = lexeme.iterator();
            while (iterator.hasNext()) {
                iterator.next();
            }
        });

        Assertions.assertThatThrownBy(() -> {
            Iterator<String> iterator = lexeme.iterator();
            if (iterator.hasNext()) {
                iterator.remove();
            }
        }).isInstanceOf(UnsupportedOperationException.class);

        Assertions.assertThatThrownBy(() -> {
            Iterator<String> iterator = lexeme.iterator();
            while (iterator.hasNext()) {
                iterator.next();
            }
            iterator.next(); // NoSuchElementException
        }).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    public void streamNotNull() {
        Lexeme lexeme = Lexeme.of("19991108", 4);
        Assertions.assertThat(lexeme.stream()).isNotNull();
    }

    @Test
    public void lexeme() {
        // normal
        Lexeme lexeme = Lexeme.of("19991108", 4);
        List<String> collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(5);
        lexeme = Lexeme.of("19991108", 3);
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(6);

        // symbol
        lexeme = Lexeme.of("&19991108&", 3, '&');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(6);
        lexeme = Lexeme.of("%19991108%", 3, '%');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(6);
        lexeme = Lexeme.of("%19991108", 4, '%');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(5);

        // chinese
        lexeme = Lexeme.of("广东省深圳市", 2);
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(5);
        lexeme = Lexeme.of("%广东省深圳市%", 2, '%');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).hasSize(5);

        // src
        Assertions.assertThatThrownBy(() -> Lexeme.of(null, 4))
                .isInstanceOf(IllegalArgumentException.class);
        // step <= 0
        Assertions.assertThatThrownBy(() -> Lexeme.of("19991108", 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void content() {
        Lexeme lexeme = Lexeme.of("19991108", 4);
        List<String> collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).containsExactly("1999", "9991", "9911", "9110", "1108");

        lexeme = Lexeme.of("%19991108", 4, '%');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).containsExactly("1999", "9991", "9911", "9110", "1108");

        lexeme = Lexeme.of("广东省深圳市", 3, '广');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).containsExactly("东省深", "省深圳", "深圳市");

        lexeme = Lexeme.of("%广东省深圳市%", 4, '%');
        collect = lexeme.stream().collect(Collectors.toList());
        Assertions.assertThat(collect).containsExactly("广东省深", "东省深圳", "省深圳市");
    }

    @Test
    public void map() {
        Lexeme lexeme = Lexeme.of("19991108", 4);
        Assertions.assertThat(lexeme.map(s -> "@" + s + "@")).isEqualTo("@1999@@9991@@9911@@9110@@1108@");
        lexeme = Lexeme.of("%%19991108%%", 4, '%');
        Assertions.assertThat(lexeme.map(UnaryOperator.identity())).isEqualTo("%%19999991991191101108%%");
        // multiple mappings will produce the same result (Idempotence)
        Assertions.assertThat(lexeme.map(UnaryOperator.identity())).isEqualTo("%%19999991991191101108%%");

        lexeme = Lexeme.of("%1", 2, '%');
        Assertions.assertThat(lexeme.map(UnaryOperator.identity())).isEqualTo("%1");
        lexeme = Lexeme.of("%12", 2, '%');
        Assertions.assertThat(lexeme.map(s -> s + s)).isEqualTo("%1212");
    }

}