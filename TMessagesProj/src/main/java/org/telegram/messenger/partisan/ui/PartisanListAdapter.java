package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.partisan.ui.items.AbstractSourceItem;
import org.telegram.messenger.partisan.ui.items.AbstractViewItem;
import org.telegram.messenger.partisan.ui.items.DelimiterItem;
import org.telegram.messenger.partisan.ui.items.DescriptionItem;
import org.telegram.messenger.partisan.ui.items.HeaderItem;
import org.telegram.messenger.partisan.ui.items.ItemType;
import org.telegram.ui.Components.RecyclerListView;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PartisanListAdapter extends RecyclerListView.SelectionAdapter {
    private final AbstractSourceItem[] sourceItems;
    private List<AbstractViewItem> currentItems;
    private Context context;
    private int rowCount;

    public PartisanListAdapter(AbstractSourceItem[] items) {
        this.sourceItems = items;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void updateRows() {
        rowCount = 0;
        currentItems = Arrays.stream(sourceItems)
                .flatMap(sourceItem -> sourceItem.generateViewItems().stream())
                .collect(Collectors.toList());
        for (AbstractViewItem item : currentItems) {
            if (item.needAddRow()) {
                item.setPosition(rowCount++);
            } else {
                item.setPosition(-1);
            }
        }
        for (int i = 0; i < currentItems.size(); i++) {
            if (currentItems.get(i).getPosition() == -1) {
                continue;
            }
            AbstractViewItem next = findNextVisible(i + 1);
            currentItems.get(i).setDrawDivider(needsDivider(next));
        }
    }

    private AbstractViewItem findNextVisible(int startIndex) {
        for (int i = startIndex; i < currentItems.size(); i++) {
            if (currentItems.get(i).getPosition() != -1) {
                return currentItems.get(i);
            }
        }
        return null;
    }

    private boolean needsDivider(AbstractViewItem next) {
        return next != null
                && !(next instanceof DescriptionItem)
                && !(next instanceof DelimiterItem)
                && !(next instanceof HeaderItem);
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        boolean[] enabled = new boolean[]{ true };
        doForItemAtPosition(holder.getAdapterPosition(), item -> enabled[0] = item.isEnabled());
        return enabled[0];
    }

    @Override
    public int getItemCount() {
        return rowCount;
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemType itemType = ItemType.values()[viewType];
        return new RecyclerListView.Holder(itemType.createView(context));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        doForItemAtPosition(position, item -> item.onBindViewHolder(holder, position));
    }

    @Override
    public int getItemViewType(int position) {
        int[] viewType = new int[]{ 0 };
        doForItemAtPosition(position, item -> viewType[0] = item.getViewType());
        return viewType[0];
    }

    @Override
    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        doForItemAtPosition(holder.getAdapterPosition(), item -> item.onViewAttachedToWindow(holder));
    }

    public void onItemClickExtended(View view, int position, float x, float y) {
        if (!view.isEnabled()) {
            return;
        }
        doForItemAtPosition(position, item -> item.onClickExtended(view, x, y));
    }

    private void doForItemAtPosition(int position, Consumer<AbstractViewItem> action) {
        for (AbstractViewItem item : currentItems) {
            if (item.positionMatch(position)) {
                action.accept(item);
                break;
            }
        }
    }
}
