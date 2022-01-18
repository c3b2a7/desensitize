package me.lolico.desensitize.util;

import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class CompositeCollectionTest {

    List<Integer> collection1 = new ArrayList<>();
    List<Integer> collection2 = new ArrayList<>();
    List<Integer> collection3 = new ArrayList<>();

    Collection<Integer> unifiedView = null;

    @Before
    public void setup() {
        collection1.add(1);
        collection1.add(2);

        collection2.add(3);
        collection2.add(4);
        collection2.add(5);

        collection3.add(6);
        collection3.add(7);
        collection3.add(8);

        unifiedView = new CompositeCollection<>(Arrays.asList(collection1, collection2, collection3));
    }

    @Test
    public void unifiedTest() {
        Assertions.assertThat(unifiedView).size().isEqualTo(8);
        Assertions.assertThat(unifiedView).isUnmodifiable();
        Assertions.assertThat(unifiedView).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Test
    public void toArrayTest() {
        Assertions.assertThat(unifiedView.toArray()).hasSize(8);
        Assertions.assertThat(unifiedView.toArray()).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

        Assertions.assertThat(unifiedView.toArray(new Integer[0])).hasSize(8);
        Assertions.assertThat(unifiedView.toArray(new Integer[0])).containsExactly(1, 2, 3, 4, 5, 6, 7, 8);

        Assertions.assertThat(unifiedView.toArray(new Integer[10])).hasSize(10);
        Assertions.assertThat(unifiedView.toArray(new Integer[10])).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, null, null);
    }
}