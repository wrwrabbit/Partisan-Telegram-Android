package org.telegram.messenger.partisan.ui;

import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;

import java.util.function.Supplier;

public abstract class AbstractItem {
    private int position = -1;
    private final int viewType;
    protected final BaseFragment fragment;
    private Supplier<Boolean> condition = null;
    private Supplier<Boolean> enabledCondition = null;

    protected AbstractItem(BaseFragment fragment, int viewType) {
        this.fragment = fragment;
        this.viewType = viewType;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean positionMatch(int targetPosition) {
        return position == targetPosition;
    }

    public int getViewType() {
        return viewType;
    }

    public AbstractItem addCondition(Supplier<Boolean> condition) {
        this.condition = condition;
        return this;
    }

    public AbstractItem addEnabledCondition(Supplier<Boolean> enabledCondition) {
        this.enabledCondition = enabledCondition;
        return this;
    }

    public boolean needAddRow() {
        if (condition != null) {
            return condition.get();
        }
        return true;
    }

    protected static View initializeView(View view) {
        view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        return view;
    }

    public boolean isEnabled() {
        if (enabledCondition != null) {
            return enabledCondition.get();
        } else {
            return isEnabledInternal();
        }
    }

    public void onViewAttachedToWindow(RecyclerView.ViewHolder holder) {
        setEnabled(holder.itemView, isEnabled());
    }

    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
         View view = holder.itemView;
         if (view.isEnabled() != isEnabled()) {
             setEnabled(view, isEnabled());
         }
         onBindViewHolderInternal(holder, position);
    }

    protected void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
    }

    protected abstract void onBindViewHolderInternal(RecyclerView.ViewHolder holder, int position);
    public abstract void onClick(View view);
    protected abstract boolean isEnabledInternal();
}
