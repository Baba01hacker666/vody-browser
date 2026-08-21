package org.vody.browser;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-screen bookmarks / history list. Mode is passed via {@link #EXTRA_MODE}. Tapping an entry
 * hands the URL back to MainActivity through the "open_url" extra; long-press deletes it.
 */
public class ListActivity extends AppCompatActivity {
    public static final String EXTRA_MODE = "mode";
    public static final String MODE_BOOKMARKS = "bookmarks";
    public static final String MODE_HISTORY = "history";

    private boolean mBookmarks = true;
    private VodyApplication mApp;
    private final List<Bookmark> mItems = new ArrayList<>();
    private LinkAdapter mAdapter;
    private View mEmptyState;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);

        mApp = (VodyApplication) getApplication();
        mBookmarks = MODE_BOOKMARKS.equals(getIntent().getStringExtra(EXTRA_MODE));

        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            setTitle(mBookmarks ? R.string.bookmarks_title : R.string.history_title);
        }

        mEmptyState = findViewById(R.id.empty_state);
        ((ImageView) findViewById(R.id.empty_icon)).setImageResource(
                mBookmarks ? R.drawable.ic_bookmark : R.drawable.ic_history);
        ((TextView) findViewById(R.id.empty_title)).setText(
                mBookmarks ? R.string.bookmarks_empty_title : R.string.history_empty_title);
        ((TextView) findViewById(R.id.empty_sub)).setText(
                mBookmarks ? R.string.bookmarks_empty_sub : R.string.history_empty_sub);

        RecyclerView rv = findViewById(R.id.list);
        rv.setLayoutManager(new LinearLayoutManager(this));
        mAdapter = new LinkAdapter();
        rv.setAdapter(mAdapter);

        refresh();
    }

    private void refresh() {
        mItems.clear();
        mItems.addAll(mBookmarks ? mApp.getStore().getBookmarks() : mApp.getStore().getHistory());
        mAdapter.notifyDataSetChanged();
        boolean empty = mItems.isEmpty();
        mEmptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        findViewById(R.id.list).setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void deleteAt(int pos) {
        Bookmark b = mItems.get(pos);
        if (mBookmarks) {
            mApp.getStore().removeBookmark(b.url);
        } else {
            mApp.getStore().removeHistory(b.url);
        }
        refresh();
    }

    private class LinkAdapter extends RecyclerView.Adapter<LinkVH> {

        @NonNull
        @Override
        public LinkVH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_link_row, parent, false);
            return new LinkVH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull LinkVH h, int pos) {
            Bookmark b = mItems.get(pos);
            String host = hostOf(b.url);
            h.title.setText(b.title == null || b.title.isEmpty() ? host : b.title);
            h.sub.setText(host);
            String letter = host.isEmpty() ? "?" : host.substring(0, 1).toUpperCase();
            h.avatar.setText(letter);

            h.itemView.setOnClickListener(v -> {
                Intent i = new Intent(ListActivity.this, org.vody.browser.MainActivity.class);
                i.putExtra("open_url", b.url);
                i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(i);
                finish();
            });
            h.itemView.setOnLongClickListener(v -> {
                new MaterialAlertDialogBuilder(ListActivity.this)
                        .setTitle(h.title.getText())
                        .setMessage(b.url)
                        .setPositiveButton(R.string.delete_entry, (d, w) -> deleteAt(h.getBindingAdapterPosition()))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }
    }

    private static String hostOf(String url) {
        try {
            String s = url;
            int scheme = s.indexOf("://");
            if (scheme >= 0) s = s.substring(scheme + 3);
            int slash = s.indexOf('/');
            if (slash >= 0) s = s.substring(0, slash);
            return s;
        } catch (Exception e) {
            return url;
        }
    }

    private static class LinkVH extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView sub;
        final TextView avatar;

        LinkVH(@NonNull View v) {
            super(v);
            title = v.findViewById(R.id.row_title);
            sub = v.findViewById(R.id.row_sub);
            avatar = v.findViewById(R.id.link_avatar);
        }
    }
}
