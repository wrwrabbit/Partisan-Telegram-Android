package org.telegram.ui;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_account;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.CheckableSessionCell;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.Components.EmptyTextProgressView;
import org.telegram.ui.Components.FlickerLoadingView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerItemsEnterAnimator;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class CheckableSessionsActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate, AlertsCreator.CheckabeSettingModeAlertDelegate {

    private ListAdapter listAdapter;
    private RecyclerListView listView;
    private ImageView imageView;
    private EmptyTextProgressView emptyView;
    private FlickerLoadingView globalFlickerLoadingView;

    private ArrayList<Object> sessions = new ArrayList<>();
    private List<Long> selectedSessions = new ArrayList<>();
    private ArrayList<TLRPC.TL_authorization> passwordSessions = new ArrayList<>();
    private TLRPC.TL_authorization currentSession;
    private boolean loading;
    private LinearLayout emptyLayout;
    private RecyclerItemsEnterAnimator itemsEnterAnimator;

    private int selectedAccount;

    private int modeSectionRow;
    private int checkAllRow;
    private int passwordSessionsSectionRow;
    private int passwordSessionsStartRow;
    private int passwordSessionsEndRow;
    private int passwordSessionsDetailRow;
    private int otherSessionsSectionRow;
    private int otherSessionsStartRow;
    private int otherSessionsEndRow;
    private int noOtherSessionsRow;
    private int rowCount;

    protected abstract List<Long> loadCheckedSessions();

    protected abstract void saveCheckedSession(List<Long> checkedSessions);

    protected abstract String getTitle();

    public CheckableSessionsActivity(int selectedAccount) {
        this.selectedAccount = selectedAccount;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        selectedSessions = loadCheckedSessions();
        if (selectedSessions == null) {
            selectedSessions = new ArrayList<>();
        }
        loadSessions(false);
        NotificationCenter.getInstance(selectedAccount).addObserver(this, NotificationCenter.newSessionReceived);
        return true;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        NotificationCenter.getInstance(selectedAccount).removeObserver(this, NotificationCenter.newSessionReceived);
    }

    @Override
    public View createView(Context context) {
        globalFlickerLoadingView = new FlickerLoadingView(context);
        globalFlickerLoadingView.setIsSingleCell(true);

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getTitle());
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        listAdapter = new ListAdapter(context);

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        emptyLayout = new LinearLayout(context);
        emptyLayout.setOrientation(LinearLayout.VERTICAL);
        emptyLayout.setGravity(Gravity.CENTER);
        emptyLayout.setBackgroundDrawable(Theme.getThemedDrawable(context, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
        emptyLayout.setLayoutParams(new AbsListView.LayoutParams(AbsListView.LayoutParams.MATCH_PARENT, AndroidUtilities.displaySize.y - ActionBar.getCurrentActionBarHeight()));

        imageView = new ImageView(context);
        imageView.setImageResource(R.drawable.devices);
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_sessions_devicesImage), PorterDuff.Mode.MULTIPLY));
        emptyLayout.addView(imageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

        emptyView = new EmptyTextProgressView(context);
        emptyView.showProgress();
        frameLayout.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setEmptyView(emptyView);
        listView.setAnimateEmptyView(true, 0);
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener((view, position) -> {
            if (getParentActivity() == null) {
                return;
            }
            if (otherSessionsStartRow != -1 && position >= otherSessionsStartRow && position < otherSessionsEndRow
                    || passwordSessionsStartRow != -1 && position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                CheckableSessionCell checkableSessionCell = ((CheckableSessionCell) view);
                boolean isChecked = !checkableSessionCell.isChecked();
                if (otherSessionsStartRow != -1 && position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                    if (isChecked) {
                        selectedSessions.add(getSessionHash(sessions.get(position - otherSessionsStartRow)));
                    } else {
                        selectedSessions.remove(getSessionHash(sessions.get(position - otherSessionsStartRow)));
                    }
                    saveCheckedSession(selectedSessions);
                    listAdapter.notifyItemChanged(checkAllRow);
                } else {
                    if (isChecked) {
                        selectedSessions.add(passwordSessions.get(position - passwordSessionsStartRow).hash);
                    } else {
                        selectedSessions.remove(passwordSessions.get(position - passwordSessionsStartRow).hash);
                    }
                }
                checkableSessionCell.setChecked(isChecked);
            } else if (position == modeSectionRow) {
                AlertsCreator.showCheckableSettingModesAlert(this, getParentActivity(), getTitle(), this, null);
            } else if (position == checkAllRow) {
                AlertsCreator.showConfirmationDialog(this, context, selectedSessions.size() > 0
                        ? LocaleController.getString("Clear", R.string.Clear)
                        : LocaleController.getString("CheckAll", R.string.CheckAll),
                () -> {
                    if (selectedSessions.size() > 0) {
                        selectedSessions = new ArrayList<>();
                    } else {
                        selectedSessions = Stream.concat(sessions.stream(), passwordSessions.stream()).map(this::getSessionHash).collect(Collectors.toList());
                    }
                    listAdapter.notifyDataSetChanged();
                    saveCheckedSession(selectedSessions);
                });
            }
        });

        itemsEnterAnimator = new RecyclerItemsEnterAnimator(listView, true) {
            @Override
            public View getProgressView() {
                View progressView = null;
                for (int i = 0; i < listView.getChildCount(); i++) {
                    View child = listView.getChildAt(i);
                    if (listView.getChildAdapterPosition(child) >= 0 && child instanceof CheckableSessionCell && ((CheckableSessionCell) child).isStub()) {
                        progressView = child;
                    }
                }
                return progressView;
            }
        };
        itemsEnterAnimator.animateAlphaProgressView = false;

        updateRows();
        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.newSessionReceived) {
            loadSessions(true);
        }
    }

    private void loadSessions(boolean silent) {
        if (loading) {
            return;
        }
        if (!silent) {
            loading = true;
        }
        TL_account.getAuthorizations req = new TL_account.getAuthorizations();
        int reqId = ConnectionsManager.getInstance(selectedAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loading = false;
            int oldItemsCount = listAdapter.getItemCount();
            if (error == null) {
                sessions.clear();
                passwordSessions.clear();
                TL_account.authorizations res = (TL_account.authorizations) response;
                for (int a = 0, N = res.authorizations.size(); a < N; a++) {
                    TLRPC.TL_authorization authorization = res.authorizations.get(a);
                    if ((authorization.flags & 1) != 0) {
                        currentSession = authorization;
                    } else if (authorization.password_pending) {
                        passwordSessions.add(authorization);
                    } else {
                        sessions.add(authorization);
                    }
                }
                List<Long> terminatedSessions = selectedSessions.stream().filter(selected -> sessions.stream().noneMatch(s -> getSessionHash(s).equals(selected))
                        && passwordSessions.stream().noneMatch(s -> s.hash == selected)).collect(Collectors.toList());
                sessions.addAll(0, terminatedSessions);
                updateRows();
            }
            itemsEnterAnimator.showItemsAnimated(oldItemsCount + 1);
            if (listAdapter != null) {
                listAdapter.notifyDataSetChanged();
            }
        }));
        ConnectionsManager.getInstance(selectedAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void updateRows() {
        rowCount = 0;
        modeSectionRow = -1;
        checkAllRow = -1;
        passwordSessionsSectionRow = -1;
        passwordSessionsStartRow = -1;
        passwordSessionsEndRow = -1;
        passwordSessionsDetailRow = -1;
        otherSessionsSectionRow = -1;
        otherSessionsStartRow = -1;
        otherSessionsEndRow = -1;
        noOtherSessionsRow = -1;

        if (!passwordSessions.isEmpty() || !sessions.isEmpty()) {
            noOtherSessionsRow = -1;
        } else {
            if (currentSession != null) {
                noOtherSessionsRow = rowCount++;
            } else {
                noOtherSessionsRow = -1;
            }
        }
        modeSectionRow = rowCount++;
        checkAllRow = rowCount++;
        if (!passwordSessions.isEmpty()) {
            passwordSessionsSectionRow = rowCount++;
            passwordSessionsStartRow = rowCount;
            rowCount += passwordSessions.size();
            passwordSessionsEndRow = rowCount;
            passwordSessionsDetailRow = rowCount++;
        }
        if (!sessions.isEmpty()) {
            otherSessionsSectionRow = rowCount++;
            otherSessionsStartRow = rowCount;
            otherSessionsEndRow = rowCount + sessions.size();
            rowCount += sessions.size();
        }
    }

    Long getSessionHash(Object session) {
        if (session instanceof TLRPC.TL_authorization) {
            return ((TLRPC.TL_authorization) session).hash;
        } else {
            return (Long)session;
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int position = holder.getAdapterPosition();
            return otherSessionsStartRow != -1 && position >= otherSessionsStartRow && position < otherSessionsEndRow
                    || passwordSessionsStartRow != -1 && position >= passwordSessionsStartRow && position < passwordSessionsEndRow
                    || position == modeSectionRow || position == checkAllRow;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case 0:
                    view = new TextCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 1:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case 2:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case 6:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                default:
                    view = new CheckableSessionCell(mContext, 0);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
            }
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
               case 0:
                   TextCell textCell = (TextCell) holder.itemView;
                   if (position == checkAllRow) {
                       if (selectedSessions.size() > 0) {
                           textCell.setText(LocaleController.getString("Clear", R.string.Clear), true);
                       } else {
                           textCell.setText(LocaleController.getString("CheckAll", R.string.CheckAll), true);
                       }
                   }
                   break;
                case 1:
                    TextInfoPrivacyCell privacyCell = (TextInfoPrivacyCell) holder.itemView;
                    privacyCell.setFixedSize(0);
                    if (position == passwordSessionsDetailRow) {
                        privacyCell.setText(LocaleController.getString("LoginAttemptsInfo", R.string.LoginAttemptsInfo));
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
                    } else if (position == noOtherSessionsRow) {
                        privacyCell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider, Theme.key_windowBackgroundGrayShadow));
                        privacyCell.setText("");
                        privacyCell.setFixedSize(12);
                    }
                    break;
                case 2:
                    HeaderCell headerCell = (HeaderCell) holder.itemView;
                    if (position == otherSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("OtherSessions", R.string.OtherSessions));
                    } else if (position == passwordSessionsSectionRow) {
                        headerCell.setText(LocaleController.getString("LoginAttempts", R.string.LoginAttempts));
                    }
                    break;
                case 6:
                    TextSettingsCell textSettingsCell = (TextSettingsCell) holder.itemView;
                    String value;
                    switch (getSelectedMode()) {
                        case 0:
                            value = LocaleController.getString("Selected", R.string.Selected);
                            break;
                        case 1:
                            value = LocaleController.getString("ExceptSelected", R.string.ExceptSelected);
                            break;
                        default:
                            value = "";
                            break;
                    }
                    textSettingsCell.setTextAndValue(getTitle(), value, true);
                    break;
                default:
                    CheckableSessionCell sessionCell = (CheckableSessionCell) holder.itemView;
                    if (otherSessionsStartRow != -1 && position >= otherSessionsStartRow && position < otherSessionsEndRow) {
                        Object session = sessions.get(position - otherSessionsStartRow);
                        if (session instanceof TLRPC.TL_authorization) {
                            sessionCell.setSession((TLRPC.TL_authorization)session, position != otherSessionsEndRow - 1, selectedSessions.contains(getSessionHash(session)));
                        } else {
                            sessionCell.setTerminatedSession(position != otherSessionsEndRow - 1, selectedSessions.contains(getSessionHash(session)));
                        }
                    } else if (passwordSessionsStartRow != -1 && position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                        TLRPC.TL_authorization session = passwordSessions.get(position - passwordSessionsStartRow);
                        sessionCell.setSession(session, position != passwordSessionsEndRow - 1, selectedSessions.contains(session.hash));
                    }
                    break;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == checkAllRow) {
                return 0;
            } else if (position == passwordSessionsDetailRow || position == noOtherSessionsRow) {
                return 1;
            } else if (position == otherSessionsSectionRow || position == passwordSessionsSectionRow) {
                return 2;
            } else if (otherSessionsStartRow != -1 && position >= otherSessionsStartRow && position < otherSessionsEndRow
                    || passwordSessionsStartRow != -1 && position >= passwordSessionsStartRow && position < passwordSessionsEndRow) {
                return 4;
            } else if (position == modeSectionRow) {
                return 6;
            }
            return 0;
        }
    }

    @Override
    public ArrayList<ThemeDescription> getThemeDescriptions() {
        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextSettingsCell.class, HeaderCell.class, CheckableSessionCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_windowBackgroundGray));

        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));

        themeDescriptions.add(new ThemeDescription(imageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_sessions_devicesImage));
        themeDescriptions.add(new ThemeDescription(emptyView, ThemeDescription.FLAG_PROGRESSBAR, null, null, null, null, Theme.key_progressCircle));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_color_red));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_TEXTCOLOR | ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueText4));

        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{HeaderCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlueHeader));

        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckableSessionCell.class}, new String[]{"nameTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{CheckableSessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{CheckableSessionCell.class}, new String[]{"onlineTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckableSessionCell.class}, new String[]{"detailTextView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{CheckableSessionCell.class}, new String[]{"detailExTextView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText3));

        return themeDescriptions;
    }

    @Override
    public void didSelectedMode(int mode) {
        listAdapter.notifyDataSetChanged();
    }
}
