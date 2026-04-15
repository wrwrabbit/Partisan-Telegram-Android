package org.telegram.messenger.partisan.ui.items;

import java.util.List;

public interface AbstractSourceItem {
    List<AbstractViewItem> generateViewItems();
}
