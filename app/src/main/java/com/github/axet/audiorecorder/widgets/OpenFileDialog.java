package com.github.axet.audiorecorder.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.preference.EditTextPreference;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Display;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class OpenFileDialog extends AlertDialog.Builder {
    private String currentPath = Environment.getExternalStorageDirectory().getPath();
    private TextView title;
    private ListView listView;
    private FilenameFilter filenameFilter;
    private int selectedIndex = -1;
    private OpenDialogListener listener;
    private Drawable folderIcon;
    private Drawable fileIcon;
    private Drawable upIcon;
    FileAdapter adapter;

    public interface OpenDialogListener {
        public void onFileSelected(String fileName);
    }

    static class SortFiles implements Comparator<File> {
        @Override
        public int compare(File file, File file2) {
            if (file.isDirectory() && file2.isFile())
                return -1;
            else if (file.isFile() && file2.isDirectory())
                return 1;
            else
                return file.getPath().compareTo(file2.getPath());
        }
    }

    class FileAdapter extends ArrayAdapter<File> {
        public FileAdapter(Context context, List<File> files) {
            super(context, android.R.layout.simple_list_item_1, files);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView view = (TextView) super.getView(position, convertView, parent);
            File file = getItem(position);
            if (view != null) {
                view.setText(file.getName());
                if (file.isDirectory()) {
                    setDrawable(view, folderIcon);
                } else {
                    setDrawable(view, fileIcon);
                }
                if (selectedIndex == position)
                    view.setBackgroundColor(getContext().getResources().getColor(android.R.color.holo_blue_dark));
                else
                    view.setBackgroundColor(getContext().getResources().getColor(android.R.color.transparent));
            }
            return view;
        }

        private void setDrawable(TextView view, Drawable drawable) {
            if (view != null) {
                if (drawable != null) {
                    drawable.setBounds(0, 0, 60, 60);
                    view.setCompoundDrawables(drawable, null, null, null);
                } else {
                    view.setCompoundDrawables(null, null, null, null);
                }
            }
        }
    }

    public OpenFileDialog(Context context) {
        super(context);
    }

    public void init() {
        Context context = getContext();

        title = createTitle(context);
        changeTitle();
        LinearLayout linearLayout = createMainLayout(context);
        linearLayout.addView(createBackItem(context));
        listView = createListView(context);
        linearLayout.addView(listView);
        setCustomTitle(title);
        setView(linearLayout);
        setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (listener != null) {
                    if (selectedIndex > -1)
                        listener.onFileSelected(listView.getItemAtPosition(selectedIndex).toString());
                    else
                        listener.onFileSelected(currentPath.toString());
                }
            }
        });
        setNegativeButton(android.R.string.cancel, null);
    }

    @Override
    public AlertDialog show() {
        adapter = new FileAdapter(getContext(), getFiles(currentPath));
        adapter.sort(new SortFiles());
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

    public OpenFileDialog setOpenDialogListener(OpenDialogListener listener) {
        this.listener = listener;
        return this;
    }

    public OpenFileDialog setFolderIcon(Drawable drawable) {
        this.folderIcon = drawable;
        return this;
    }

    public OpenFileDialog setUpIcon(Drawable drawable) {
        this.upIcon = drawable;
        return this;
    }

    public void setCurrentPath(String path) {
        currentPath = path;
    }

    public OpenFileDialog setFileIcon(Drawable drawable) {
        this.fileIcon = drawable;
        return this;
    }

    private static Display getDefaultDisplay(Context context) {
        return ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay();
    }

    private static Point getScreenSize(Context context) {
        Point screeSize = new Point();
        getDefaultDisplay(context).getSize(screeSize);
        return screeSize;
    }

    private static int getLinearLayoutMinHeight(Context context) {
        return getScreenSize(context).y;
    }

    private LinearLayout createMainLayout(Context context) {
        LinearLayout linearLayout = new LinearLayout(context);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        linearLayout.setMinimumHeight(getLinearLayoutMinHeight(context));
        return linearLayout;
    }

    private int getItemHeight(Context context) {
        TypedValue value = new TypedValue();
        DisplayMetrics metrics = new DisplayMetrics();
        context.getTheme().resolveAttribute(android.R.attr.listPreferredItemHeightSmall, value, true);
        getDefaultDisplay(context).getMetrics(metrics);
        return (int) TypedValue.complexToDimension(value.data, metrics);
    }

    private TextView createTextView(Context context, int style) {
        TextView textView = new TextView(context);
        textView.setTextAppearance(context, style);
        int itemHeight = getItemHeight(context);
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, itemHeight));
        textView.setMinHeight(itemHeight);
        textView.setGravity(Gravity.CENTER_VERTICAL);
        textView.setPadding(15, 0, 0, 0);
        return textView;
    }

    private TextView createTitle(Context context) {
        TextView textView = createTextView(context, android.R.style.TextAppearance_DeviceDefault_DialogWindowTitle);
        return textView;
    }

    private View createBackItem(Context context) {
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.HORIZONTAL);
        ll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView textView = createTextView(context, android.R.style.TextAppearance_DeviceDefault_Small);
        Drawable drawable = getContext().getResources().getDrawable(android.R.drawable.ic_menu_directions);
        drawable.setBounds(0, 0, 60, 60);
        if (upIcon != null)
            upIcon.setBounds(0, 0, 60, 60);
        textView.setCompoundDrawables(upIcon == null ? drawable : upIcon, null, null, null);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.weight = 1;
        textView.setLayoutParams(lp);
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File file = new File(currentPath);
                File parentDirectory = file.getParentFile();
                if (parentDirectory != null) {
                    currentPath = parentDirectory.getPath();
                    RebuildFiles();
                }
            }
        });

        ll.addView(textView);

        ll.addView(createNewFolder(context));

        return ll;
    }

    public interface EditClick {
        public void click(String text);
    }

    AlertDialog.Builder createEditDialog(Context context, String title, String value, final EditClick ok) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        LinearLayout ll = new LinearLayout(context);
        ll.setOrientation(LinearLayout.VERTICAL);
        TextView text = createTitle(context);
        text.setText(title);
        ll.addView(text);
        final EditText input = new EditText(context);
        input.setText(value);
        ll.addView(input);
        builder.setView(ll);
        builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                ok.click(input.getText().toString().trim());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                dialog.cancel();
            }
        });
        return builder;
    }

    private TextView createNewFolder(final Context context) {
        Button textView = new Button(context);
        textView.setPadding(15, 0, 15, 0);
        textView.setText("New Folder");
        textView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        textView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                AlertDialog.Builder builder = createEditDialog(context, "Folder Name", "", new EditClick() {
                    @Override
                    public void click(String value) {
                        File f = new File(currentPath, value);
                        if (!f.mkdir()) {
                            Toast.makeText(context, "Unable create folder: '" + value + "'", Toast.LENGTH_SHORT).show();
                        }
                        RebuildFiles();
                    }
                });
                builder.show();
            }
        });
        return textView;
    }

    public int getTextWidth(String text, Paint paint) {
        Rect bounds = new Rect();
        paint.getTextBounds(text, 0, text.length(), bounds);
        return bounds.left + bounds.width() + 80;
    }

    private void changeTitle() {
        String titleText = currentPath;
        int screenWidth = getScreenSize(getContext()).x;
        int maxWidth = (int) (screenWidth * 0.99);
        if (getTextWidth(titleText, title.getPaint()) > maxWidth) {
            while (getTextWidth("..." + titleText, title.getPaint()) > maxWidth) {
                int start = titleText.indexOf("/", 2);
                if (start > 0)
                    titleText = titleText.substring(start);
                else
                    titleText = titleText.substring(2);
            }
            title.setText("..." + titleText);
        } else {
            title.setText(titleText);
        }
    }

    private List<File> getFiles(String directoryPath) {
        File directory = new File(directoryPath);
        File[] files = directory.listFiles(filenameFilter);
        if (files == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(files));
    }

    private void RebuildFiles() {
        selectedIndex = -1;
        adapter.clear();
        adapter.addAll(getFiles(currentPath));
        adapter.sort(new SortFiles());
        adapter.notifyDataSetChanged();
        changeTitle();
    }

    public static final String RENAME = "Rename";
    public static final String DELETE = "Delete";

    private ListView createListView(final Context context) {
        ListView listView = new ListView(context);
        listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                PopupMenu p = new PopupMenu(context, view);
                p.getMenu().add(RENAME);
                p.getMenu().add(DELETE);
                p.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getTitle().equals(RENAME)) {
                            final File ff = (File) adapter.getItem(position);
                            AlertDialog.Builder b = createEditDialog(context, "Folder Name", ff.getName(), new EditClick() {
                                @Override
                                public void click(String text) {
                                    File f = new File(ff.getParent(), text);
                                    ff.renameTo(f);
                                    RebuildFiles();
                                }
                            });
                            b.show();
                            return true;
                        }
                        if (item.getTitle().equals(DELETE)) {
                            File ff = (File) adapter.getItem(position);
                            ff.delete();
                            RebuildFiles();
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
                    currentPath = file.getPath();
                    RebuildFiles();
                } else {
                    if (index != selectedIndex)
                        selectedIndex = index;
                    else
                        selectedIndex = -1;
                    adapter.notifyDataSetChanged();
                }
            }
        });
        return listView;
    }
}