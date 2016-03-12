package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.view.menu.ListMenuItemView;
import android.support.v7.view.menu.MenuBuilder;
import android.support.v7.view.menu.MenuItemImpl;
import android.support.v7.view.menu.MenuView;
import android.support.v7.view.menu.SubMenuBuilder;
import android.support.v7.widget.PopupMenu;
import android.support.v7.widget.ShareActionProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.ListPopupWindow;

/**
 * PopupMenu window for ShareActionProvider
 */
public class PopupShareActionProvider extends ListPopupWindow {
    PopupMenu popup;
    ShareActionProvider action;
    MenuAdapter adp;
    FrameLayout mMeasureParent;
    Context context;

    public class MenuAdapter extends BaseAdapter {
        static final int ITEM_LAYOUT = android.support.v7.appcompat.R.layout.abc_popup_menu_item_layout;

        private SubMenu menu;

        boolean mForceShowIcon = true;

        public MenuAdapter(SubMenu menu) {
            this.menu = menu;
        }

        public void setMenu(SubMenu menu) {
            this.menu = menu;
            notifyDataSetChanged();
        }

        public int getCount() {
            return menu.size();
        }

        public MenuItemImpl getItem(int position) {
            return (MenuItemImpl) menu.getItem(position);
        }

        public long getItemId(int position) {
            // Since action menu item's ID is optional, we'll use the position as an
            // ID for the item in the AdapterView
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                LayoutInflater inflater = LayoutInflater.from(parent.getContext());
                convertView = inflater.inflate(ITEM_LAYOUT, parent, false);
            }

            MenuView.ItemView itemView = (MenuView.ItemView) convertView;
            if (mForceShowIcon) {
                ((ListMenuItemView) convertView).setForceShowIcon(true);
            }
            itemView.initialize(getItem(position), 0);
            return convertView;
        }

        @Override
        public void notifyDataSetChanged() {
            super.notifyDataSetChanged();
        }
    }


    public PopupShareActionProvider(Context context, View anchor) {
        super(context);

        this.context = context;

        setAnchorView(anchor);

        action = new ShareActionProvider(context);
        action.setShareHistoryFileName(ShareActionProvider.DEFAULT_SHARE_HISTORY_FILE_NAME);

        popup = new PopupMenu(context, anchor);
    }

    public void setShareIntent(Intent shareIntent) {
        action.setShareIntent(shareIntent);

        MenuItem share = popup.getMenu().add("Share");
        MenuItemCompat.setActionProvider(share, action).setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        MenuItemImpl i = (MenuItemImpl) share;
        final MenuBuilder mb = (MenuBuilder) popup.getMenu();
        SubMenuBuilder sb = new SubMenuBuilder(context, mb, i);
        i.setSubMenu(sb);

        action.onPrepareSubMenu(sb);

        adp = new MenuAdapter(i.getSubMenu());

        setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MenuItem item = adp.getItem(position);
                if(item.hasSubMenu()) {
                    adp.setMenu(item.getSubMenu());
                }else {
                    mb.performItemAction(item, 0);
                    dismiss();
                }
            }
        });

        setAdapter(adp);

        setContentWidth(measureContentWidth(context));

        setModal(true);
    }

    public int measureContentWidth(Context mContext) {
        final Resources res = mContext.getResources();
        int mPopupMaxWidth = Math.max(res.getDisplayMetrics().widthPixels / 2,
                res.getDimensionPixelSize(android.support.v7.appcompat.R.dimen.abc_config_prefDialogWidth));

        // Menus don't tend to be long, so this is more sane than it looks.
        int maxWidth = 0;
        View itemView = null;
        int itemType = 0;

        final ListAdapter adapter = adp;
        final int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        final int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            final int positionType = adapter.getItemViewType(i);
            if (positionType != itemType) {
                itemType = positionType;
                itemView = null;
            }

            if (mMeasureParent == null) {
                mMeasureParent = new FrameLayout(mContext);
            }

            itemView = adapter.getView(i, itemView, mMeasureParent);
            itemView.measure(widthMeasureSpec, heightMeasureSpec);

            final int itemWidth = itemView.getMeasuredWidth();
            if (itemWidth >= mPopupMaxWidth) {
                return mPopupMaxWidth;
            } else if (itemWidth > maxWidth) {
                maxWidth = itemWidth;
            }
        }

        return maxWidth;
    }

}
