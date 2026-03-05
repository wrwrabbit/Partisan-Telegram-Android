//package org.telegram.ui;
//
//import android.content.Context;
//import android.view.View;
//import android.view.ViewGroup;
//import android.view.ViewTreeObserver;
//import android.widget.FrameLayout;
//
//import androidx.recyclerview.widget.LinearLayoutManager;
//import androidx.recyclerview.widget.RecyclerView;
//
//import org.telegram.messenger.AndroidUtilities;
//import org.telegram.messenger.LocaleController;
//import org.telegram.messenger.MediaController;
//import org.telegram.messenger.R;
//import org.telegram.messenger.SendMessagesHelper;
//import org.telegram.messenger.SharedConfig;
//import org.telegram.ui.ActionBar.ActionBar;
//import org.telegram.ui.ActionBar.ActionBarMenu;
//import org.telegram.ui.ActionBar.BaseFragment;
//import org.telegram.ui.ActionBar.Theme;
//import org.telegram.ui.ActionBar.ThemeDescription;
//import org.telegram.ui.Cells.TextCheckCell;
//import org.telegram.ui.Cells.TextInfoPrivacyCell;
//import org.telegram.ui.Cells.TextSettingsCell;
//import org.telegram.ui.Components.LayoutHelper;
//import org.telegram.ui.Components.RecyclerListView;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//
//public class MaskingSettingsIconActivity extends BaseFragment {
//    private ListAdapter listAdapter;
//    private RecyclerListView listView;
//    private int rowCount;
//    private int textRow;
//    private int detailsRow;
//    private PhotoPickerActivity photoPicker;
//    private int mColor = 0;
//
//    private final static int done_button = 1;
//
//    public MaskingSettingsIconActivity() {
//        super();
//    }
//
//    @Override
//    public boolean onFragmentCreate() {
//        super.onFragmentCreate();
//        updateRows();
//        return true;
//    }
//
//    @Override
//    public View createView(Context context) {
//        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
//        actionBar.setAllowOverlayTitle(false);
//        ActionBarMenu menu = actionBar.createMenu();
//        menu.addItemWithWidth(done_button, R.drawable.ic_ab_done, AndroidUtilities.dp(56), LocaleController.getString("Done", R.string.Done));
//        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
//            @Override
//            public void onItemClick(int id) {
//                if (id == -1) {
//                    finishFragment();
//                } else if (id == done_button) {
//                    SharedConfig.maskingPrimaryColor = mColor;
//                    finishFragment();
//                }
//            }
//        });
//
//        fragmentView = new FrameLayout(context);
//        FrameLayout frameLayout = (FrameLayout) fragmentView;
//
//        actionBar.setTitle(LocaleController.getString(R.string.MaskingNotificationText));
//        frameLayout.setTag(Theme.key_windowBackgroundGray);
//        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//        listView = new RecyclerListView(context);
//        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false) {
//            @Override
//            public boolean supportsPredictiveItemAnimations() {
//                return false;
//            }
//        });
//        listView.setVerticalScrollBarEnabled(false);
//        listView.setItemAnimator(null);
//        listView.setLayoutAnimation(null);
//        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
//        listView.setAdapter(listAdapter = new ListAdapter(context));
//        return fragmentView;
//    }
//
//    @Override
//    public void onResume() {
//        super.onResume();
//        if (listAdapter != null) {
//            listAdapter.notifyDataSetChanged();
//        }
//    }
//
//    private void updateRows() {
//        rowCount = 0;
//        textRow = rowCount++;
//        detailsRow = rowCount++;
//    }
//
//    @Override
//    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
//        super.onConfigurationChanged(newConfig);
//        if (listView != null) {
//            ViewTreeObserver obs = listView.getViewTreeObserver();
//            obs.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//                @Override
//                public boolean onPreDraw() {
//                    listView.getViewTreeObserver().removeOnPreDrawListener(this);
//                    return true;
//                }
//            });
//        }
//    }
//
//    private class ListAdapter extends RecyclerListView.SelectionAdapter {
//
//        private Context mContext;
//
//        public ListAdapter(Context context) {
//            mContext = context;
//        }
//
//        @Override
//        public boolean isEnabled(RecyclerView.ViewHolder holder) {
//            int position = holder.getAdapterPosition();
//            return position != detailsRow;
//        }
//
//        @Override
//        public int getItemCount() {
//            return rowCount;
//        }
//
//        @Override
//        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
//            View view;
//            switch (viewType) {
//                case 0:
//                    view = new PhotoPickerActivity(0, null, SharedConfig.maskingIcons, SharedConfig.maskingIconsOrder, 1, false, null, false);
//                    fragment.setDelegate(new PhotoPickerActivity.PhotoPickerActivityDelegate() {
//
//                        private boolean sendPressed;
//
//                        @Override
//                        public void selectedPhotosChanged() {
//
//                        }
//
//                        private void sendSelectedPhotos(HashMap<Object, Object> photos, ArrayList<Object> order, boolean notify, int scheduleDate) {
//
//                        }
//
//                        @Override
//                        public void actionButtonPressed(boolean canceled, boolean notify, int scheduleDate) {
//                            if (photos.isEmpty() || delegate == null || sendPressed || canceled) {
//                                return;
//                            }
//                            sendPressed = true;
//
//                            ArrayList<SendMessagesHelper.SendingMediaInfo> media = new ArrayList<>();
//                            for (int a = 0; a < order.size(); a++) {
//                                Object object = photos.get(order.get(a));
//                                SendMessagesHelper.SendingMediaInfo info = new SendMessagesHelper.SendingMediaInfo();
//                                media.add(info);
//                                if (object instanceof MediaController.SearchImage) {
//                                    MediaController.SearchImage searchImage = (MediaController.SearchImage) object;
//                                    if (searchImage.imagePath != null) {
//                                        info.path = searchImage.imagePath;
//                                    } else {
//                                        info.searchImage = searchImage;
//                                    }
//                                    info.videoEditedInfo = searchImage.editedInfo;
//                                    info.thumbPath = searchImage.thumbPath;
//                                    info.caption = searchImage.caption != null ? searchImage.caption.toString() : null;
//                                    info.entities = searchImage.entities;
//                                    info.masks = searchImage.stickers;
//                                    info.ttl = searchImage.ttl;
//                                }
//                            }
//                            didSelectPhotos(false, media);
//                        }
//
//                        @Override
//                        public void onCaptionChanged(CharSequence caption) {
//
//                        }
//
//                        @Override
//                        public boolean canFinishFragment() {
//                            return delegate.canFinishFragment();
//                        }
//                    });
//                    fragment.setMaxSelectedPhotos(1, false);
//                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
//                    break;
//                case 1:
//                default:
//                    view = new TextInfoPrivacyCell(mContext);
//                    break;
//            }
//            return new RecyclerListView.Holder(view);
//        }
//
//        @Override
//        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
//            switch (holder.getItemViewType()) {
//                case 0: { // ColorPicker
//                    ColorPicker colorPicker = (ColorPicker) holder.itemView;
//                    MaskingSettingsPrimaryColorActivity.this.colorPicker = colorPicker;
//
//                    // Устанавливаем высоту 300dp
//                    colorPicker.setLayoutParams(new RecyclerView.LayoutParams(
//                            ViewGroup.LayoutParams.MATCH_PARENT,
//                            AndroidUtilities.dp(300)
//                    ));
//
//                    colorPicker.setType(-1, true, 1, 1, false, 0, false);
//                    colorPicker.setColor(mColor != 0 ? mColor : SharedConfig.maskingPrimaryColor, 0);
//                    break;
//                }
//                case 1: { // TextInfo
//                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
//
//                    // Устанавливаем высоту 60dp (300 / 5 = 60)
//                    cell.setLayoutParams(new RecyclerView.LayoutParams(
//                            ViewGroup.LayoutParams.MATCH_PARENT,
//                            AndroidUtilities.dp(60)
//                    ));
//
//                    cell.setText(LocaleController.getString(R.string.MaskingPrimaryColorInfo));
//                    cell.setBackgroundDrawable(Theme.getThemedDrawable(mContext, R.drawable.greydivider_bottom, Theme.key_windowBackgroundGrayShadow));
//                    break;
//                }
//            }
//        }
//
//        @Override
//        public int getItemViewType(int position) {
//            if (position == textRow) {
//                return 0;
//            } else if (position == detailsRow) {
//                return 1;
//            }
//            return 0;
//        }
//    }
//
//    @Override
//    public ArrayList<ThemeDescription> getThemeDescriptions() {
//        ArrayList<ThemeDescription> themeDescriptions = new ArrayList<>();
//
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CELLBACKGROUNDCOLOR, new Class[]{TextCheckCell.class, TextSettingsCell.class}, null, null, null, Theme.key_windowBackgroundWhite));
//        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundWhite));
//        themeDescriptions.add(new ThemeDescription(fragmentView, ThemeDescription.FLAG_BACKGROUND | ThemeDescription.FLAG_CHECKTAG, null, null, null, null, Theme.key_windowBackgroundGray));
//
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_BACKGROUND, null, null, null, null, Theme.key_actionBarDefault));
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_LISTGLOWCOLOR, null, null, null, null, Theme.key_actionBarDefault));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_ITEMSCOLOR, null, null, null, null, Theme.key_actionBarDefaultIcon));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_TITLECOLOR, null, null, null, null, Theme.key_actionBarDefaultTitle));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SELECTORCOLOR, null, null, null, null, Theme.key_actionBarDefaultSelector));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUBACKGROUND, null, null, null, null, Theme.key_actionBarDefaultSubmenuBackground));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM, null, null, null, null, Theme.key_actionBarDefaultSubmenuItem));
//        themeDescriptions.add(new ThemeDescription(actionBar, ThemeDescription.FLAG_AB_SUBMENUITEM | ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_actionBarDefaultSubmenuItemIcon));
//
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_SELECTOR, null, null, null, null, Theme.key_listSelector));
//
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{View.class}, Theme.dividerPaint, null, null, Theme.key_divider));
//
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrack));
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextCheckCell.class}, new String[]{"checkBox"}, null, null, null, Theme.key_switchTrackChecked));
//
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteBlackText));
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_CHECKTAG, new Class[]{TextSettingsCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText7));
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextSettingsCell.class}, new String[]{"valueTextView"}, null, null, null, Theme.key_windowBackgroundWhiteValueText));
//
//        themeDescriptions.add(new ThemeDescription(listView, ThemeDescription.FLAG_BACKGROUNDFILTER, new Class[]{TextInfoPrivacyCell.class}, null, null, null, Theme.key_windowBackgroundGrayShadow));
//        themeDescriptions.add(new ThemeDescription(listView, 0, new Class[]{TextInfoPrivacyCell.class}, new String[]{"textView"}, null, null, null, Theme.key_windowBackgroundWhiteGrayText4));
//
//        return themeDescriptions;
//    }
//}
