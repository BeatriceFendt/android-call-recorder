package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Environment;
import android.support.v7.app.AlertDialog;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

public class OpenFileDialog extends AlertDialog.Builder {
    public static String RENAME = "Rename";
    public static String DELETE = "Delete";
    public static String NEW_FOLDER = "New Folder";
    public static String FOLDER_NAME = "Folder Name";
    public static String UNABLE_CREATE_FOLDER = "Unable create folder: '%s'";
    public static String FOLDER_CREATED = "Folder '%s' created";
    public static String DELETED = "Folder '%s' deleted";
    public static String RENAMED = "Renamed to: '%s'";
    public static String EMPTY_LIST = "Empty List";

    File currentPath;
    TextMax textMax;
    ListView listView;
    FilenameFilter filenameFilter;
    int folderIcon;
    int fileIcon;
    FileAdapter adapter;

    int paddingLeft;
    int paddingRight;
    int paddingBottom;
    int paddingTop;
    int iconSize;

    static class SortFiles implements Comparator<File> {
        // for symlinks
        boolean isFile(File f) {
            return !f.isDirectory();
        }

        @Override
        public int compare(File f1, File f2) {
            if (f1.isDirectory() && isFile(f2))
                return -1;
            else if (isFile(f1) && f2.isDirectory())
                return 1;
            else
                return f1.getPath().compareTo(f2.getPath());
        }
    }

    public class FileAdapter extends ArrayAdapter<File> {
        int selectedIndex = -1;
        int colorSelected;
        int colorTransparent;

        public FileAdapter(Context context) {
            super(context, android.R.layout.simple_list_item_1);

            if (Build.VERSION.SDK_INT >= 23) {
                colorSelected = getContext().getResources().getColor(android.R.color.holo_blue_dark, getContext().getTheme());
                colorTransparent = getContext().getResources().getColor(android.R.color.transparent, getContext().getTheme());
            } else {
                colorSelected = getContext().getResources().getColor(android.R.color.holo_blue_dark);
                colorTransparent = getContext().getResources().getColor(android.R.color.transparent);
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            File file = getItem(position);
            if (view != null) {
                view.setText(file.getName());
                if (file.isDirectory()) {
                    setDrawable(view, getDrawable(folderIcon));
                } else {
                    setDrawable(view, getDrawable(fileIcon));
                }
                if (selectedIndex == position)
                    view.setBackgroundColor(colorSelected);
                else
                    view.setBackgroundColor(colorTransparent);
            }
            return view;
        }

        private void setDrawable(TextView view, Drawable drawable) {
            if (view != null) {
                if (drawable != null) {
                    drawable.setBounds(0, 0, iconSize, iconSize);
                    view.setCompoundDrawables(drawable, null, null, null);
                } else {
                    view.setCompoundDrawables(null, null, null, null);
                }
            }
        }

        public void scan(File dir) {
            selectedIndex = -1;

            clear();

            File[] files = dir.listFiles(filenameFilter);

            if (files == null)
                return;

            ArrayList<File> list = new ArrayList<>(Arrays.asList(files));

            addAll(list);

            sort(new SortFiles());

            notifyDataSetChanged();
        }
    }

    public class TextMax extends ViewGroup {
        TextView text;

        public TextMax(Context context, TextView text) {
            super(context);
            this.text = text;

            addView(text);
        }

        public int getTextWidth(String text, Paint paint) {
            Rect bounds = new Rect();
            paint.getTextBounds(text, 0, text.length(), bounds);
            return bounds.left + bounds.width();
        }

        public String makePath(List<String> ss) {
            return TextUtils.join(File.separator, ss);
        }

        public List<String> splitPath(String s) {
            return new ArrayList<String>(Arrays.asList(s.split(Pattern.quote(File.separator))));
        }

        int getMaxWidth() {
            return text.getWidth() - text.getPaddingLeft() - text.getPaddingRight();
        }

        public void updateText() {
            String s = currentPath.getPath();

            List<String> ss = splitPath(s);
            List<String> ssdots = ss;

            String sdots = makePath(ssdots);

            while (getTextWidth(sdots, text.getPaint()) > getMaxWidth()) {
                int mid = (ss.size() - 1) / 2;
                ssdots = new ArrayList<>(ss);
                ssdots.set(mid, "...");
                ss.remove(mid);
                sdots = makePath(ssdots);
            }

            text.setText(sdots);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            text.layout(l, t, r, b);

            updateText();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int widthMode = MeasureSpec.getMode(widthMeasureSpec);
            int widthSize = MeasureSpec.getSize(widthMeasureSpec);

            text.measure(MeasureSpec.makeMeasureSpec(widthSize, MeasureSpec.EXACTLY), heightMeasureSpec);
            setMeasuredDimension(text.getMeasuredWidth(), text.getMeasuredHeight());
        }
    }

    public static class EditTextDialog extends AlertDialog.Builder {
        EditText input;

        public EditTextDialog(Context context) {
            super(context);

            input = new EditText(getContext());

            setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            });
            setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    hide();
                }
            });

            setView(input);
        }

        @Override
        public AlertDialog.Builder setPositiveButton(int textId, final DialogInterface.OnClickListener listener) {
            return super.setPositiveButton(textId, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    listener.onClick(dialog, which);
                    hide();
                }
            });
        }

        @Override
        public AlertDialog.Builder setPositiveButton(CharSequence text, final DialogInterface.OnClickListener listener) {
            return super.setPositiveButton(text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    listener.onClick(dialog, which);
                    hide();
                }
            });
        }

        void hide() {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(input.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }

        @Override
        public AlertDialog create() {
            AlertDialog d =  super.create();

            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

            return d;
        }

        public void setText(String s) {
            input.setText(s);
            input.setSelection(s.length());
        }

        public String getText() {
            return input.getText().toString();
        }
    }

    public OpenFileDialog(Context context) {
        super(context);

        currentPath = Environment.getExternalStorageDirectory();
        paddingLeft = dp2px(14);
        paddingRight = dp2px(14);
        paddingTop = dp2px(14);
        paddingBottom = dp2px(14);
        iconSize = dp2px(30);
    }

    public int dp2px(int dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getContext().getResources().getDisplayMetrics());
    }

    void toast(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
    }

    @Override
    public AlertDialog show() {
        TextView title = (TextView) LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, null);
        title.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        title.setPadding(paddingLeft, paddingTop, paddingRight, paddingBottom);
        textMax = new TextMax(getContext(), title);
        setCustomTitle(textMax);

        // main view, linearlayout
        final LinearLayout main = new LinearLayout(getContext());
        main.setOrientation(LinearLayout.VERTICAL);
        main.setMinimumHeight(getLinearLayoutMinHeight(getContext()));
        main.setPadding(paddingLeft, 0, paddingRight, 0);

        // add toolbar (UP / NEWFOLDER)
        {
            LinearLayout toolbar = new LinearLayout(getContext());
            toolbar.setOrientation(LinearLayout.HORIZONTAL);
            toolbar.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            {
                TextView textView = (TextView) LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, null);
                textView.setText("[..]");
                Drawable icon = getDrawable(folderIcon);
                icon.setBounds(0, 0, iconSize, iconSize);
                textView.setCompoundDrawables(icon, null, null, null);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.weight = 1;
                textView.setLayoutParams(lp);
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        File parentDirectory = currentPath.getParentFile();
                        if (parentDirectory != null) {
                            currentPath = parentDirectory;
                            RebuildFiles();
                        }
                    }
                });
                toolbar.addView(textView);
            }

            {
                Button textView = new Button(getContext());
                textView.setPadding(paddingLeft, 0, paddingRight, 0);
                textView.setText(NEW_FOLDER);
                ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                textView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final EditTextDialog builder = new EditTextDialog(getContext());
                        builder.setTitle(FOLDER_NAME);
                        builder.setText("");
                        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                File f = new File(currentPath, builder.getText());
                                if (!f.mkdirs()) {
                                    toast(String.format(UNABLE_CREATE_FOLDER, builder.getText()));
                                } else {
                                    toast(String.format(FOLDER_CREATED, builder.getText()));
                                }
                                adapter.scan(currentPath);
                            }
                        });
                        builder.show();
                    }
                });
                toolbar.addView(textView, lp);
            }

            main.addView(toolbar);
        }

        // ADD FILES LIST
        {
            listView = new ListView(getContext());
            listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
                @Override
                public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                    final PopupMenu p = new PopupMenu(getContext(), view);
                    p.getMenu().add(RENAME);
                    p.getMenu().add(DELETE);
                    p.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (item.getTitle().equals(RENAME)) {
                                final File ff = adapter.getItem(position);
                                final EditTextDialog b = new EditTextDialog(getContext());
                                b.setTitle(FOLDER_NAME);
                                b.setText(ff.getName());
                                b.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        File f = new File(ff.getParent(), b.getText());
                                        ff.renameTo(f);
                                        toast(String.format(RENAMED, f.getName()));
                                        adapter.scan(currentPath);
                                    }
                                });
                                b.show();
                                return true;
                            }
                            if (item.getTitle().equals(DELETE)) {
                                File ff = adapter.getItem(position);
                                ff.delete();
                                toast(String.format(DELETED, ff.getName()));
                                adapter.scan(currentPath);
                                return true;
                            }
                            return false;
                        }
                    });
                    p.show();
                    return true;
                }
            });
            listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> adapterView, View view, int index, long l) {
                    File file = adapter.getItem(index);
                    if (file.isDirectory()) {
                        currentPath = file;
                        RebuildFiles();
                    } else {
                        if (index != adapter.selectedIndex)
                            adapter.selectedIndex = index;
                        else
                            adapter.selectedIndex = -1;
                        adapter.notifyDataSetChanged();
                    }
                }
            });
            main.addView(listView);
        }

        {
            TextView text = (TextView) LayoutInflater.from(getContext()).inflate(android.R.layout.simple_list_item_1, null);
            text.setText(EMPTY_LIST);
            text.setVisibility(View.GONE);
            listView.setEmptyView(text);
            main.addView(text);
        }

        setView(main);
        setNegativeButton(android.R.string.cancel, null);

        adapter = new FileAdapter(getContext());
        adapter.scan(currentPath);
        listView.setAdapter(adapter);

        return super.show();
    }

    public OpenFileDialog setFilter(final String filter) {
        filenameFilter = new FilenameFilter() {
            @Override
            public boolean accept(File file, String fileName) {
                File tempFile = new File(String.format("%s/%s", file.getPath(), fileName));
                if (tempFile.isFile())
                    return tempFile.getName().matches(filter);
                return true;
            }
        };
        return this;
    }

    public OpenFileDialog setFolderIcon(int drawable) {
        this.folderIcon = drawable;
        return this;
    }

    public void setCurrentPath(File path) {
        currentPath = path;
    }

    public File getCurrentPath() {
        return currentPath;
    }

    public OpenFileDialog setFileIcon(int drawable) {
        this.fileIcon = drawable;
        return this;
    }

    private static Display getDefaultDisplay(Context context) {
        return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }

    Drawable getDrawable(int resid) {
        if (Build.VERSION.SDK_INT >= 21)
            return getContext().getResources().getDrawable(resid, getContext().getTheme());
        else
            return getContext().getResources().getDrawable(resid);
    }

    private static Point getScreenSize(Context context) {
        Point screeSize = new Point();
        getDefaultDisplay(context).getSize(screeSize);
        return screeSize;
    }

    private static int getLinearLayoutMinHeight(Context context) {
        return getScreenSize(context).y;
    }

    private void RebuildFiles() {
        adapter.scan(currentPath);
        listView.setSelection(0);
        textMax.updateText();
    }
}