package org.telegram.messenger.partisan.ui;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.Components.RecyclerListView;

import java.util.function.Consumer;

public class PartisanListAdapter extends RecyclerListView.SelectionAdapter {
    private final AbstractItem[] items;
    private Context context;
    private int rowCount;

    public PartisanListAdapter(AbstractItem[] items) {
        this.items = items;
    }

    public void setContext(Context context) {
        this.context = context;
    }

    public void updateRows() {
        rowCount = 0;
        for (AbstractItem item : items) {
            if (item.needAddRow()) {
                item.setPosition(rowCount++);
            }
        }
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

    public void onItemClick(View view, int position) {
        if (!view.isEnabled()) {
            return;
        }
        doForItemAtPosition(position, item -> item.onClick(view));
    }

    private void doForItemAtPosition(int position, Consumer<AbstractItem> action) {
        for (AbstractItem item : items) {
            if (item.positionMatch(position)) {
                action.accept(item);
                break;
            }
        }
    }
}
