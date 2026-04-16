package org.telegram.messenger.partisan.ui.items;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

public class ItemsGenerator implements AbstractSourceItem {
    private final Supplier<Integer> countSupplier;
    private final Function<Integer, AbstractViewItem> subItemsSupplier;

    public ItemsGenerator(Supplier<Integer> countSupplier, Function<Integer, AbstractViewItem> subItemsSupplier) {
        this.countSupplier = countSupplier;
        this.subItemsSupplier = subItemsSupplier;
    }

    @Override
    public List<AbstractViewItem> generateViewItems() {
        int count = countSupplier.get();
        List<AbstractViewItem> subItems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            subItems.add(subItemsSupplier.apply(i));
        }
        return subItems;
    }
}
