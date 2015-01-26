package com.singlagrv;


import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.RecyclerView.ViewHolder;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Filter;
import android.widget.FilterQueryProvider;
import android.widget.Filterable;

public abstract class CursorRecyclerAdapter<VH extends android.support.v7.widget.RecyclerView.ViewHolder>
		extends RecyclerView.Adapter<ViewHolder> implements Filterable,
		CursorFilter.CursorFilterClient {

	protected boolean mDataValid;

	protected boolean mAutoRequery;

	protected Cursor mCursor;

	protected Context mContext;

	protected int mRowIDColumn;

	protected ChangeObserver mChangeObserver;

	protected DataSetObserver mDataSetObserver;

	protected CursorFilter mCursorFilter;

	protected FilterQueryProvider mFilterQueryProvider;

	@Deprecated
	public static final int FLAG_AUTO_REQUERY = 0x01;

	public static final int FLAG_REGISTER_CONTENT_OBSERVER = 0x02;

	public static final int VIEW_TYPE_HEADER = 1;
	public static final int VIEW_TYPE_FOOTER = 2;
	public static final int VIEW_TYPE_LIST_ITEM = 3;
	private View headerView;
	private View footerView;

	public CursorRecyclerAdapter(Context context, Cursor c) {
		init(context, c, FLAG_AUTO_REQUERY);
	}

	public CursorRecyclerAdapter(Context context, Cursor c, boolean autoRequery) {
		init(context, c, autoRequery ? FLAG_AUTO_REQUERY
				: FLAG_REGISTER_CONTENT_OBSERVER);
	}

	public CursorRecyclerAdapter(Context context, Cursor c, int flags) {
		init(context, c, flags);
	}

	@Deprecated
	protected void init(Context context, Cursor c, boolean autoRequery) {
		init(context, c, autoRequery ? FLAG_AUTO_REQUERY
				: FLAG_REGISTER_CONTENT_OBSERVER);
	}

	void init(Context context, Cursor c, int flags) {
		if ((flags & FLAG_AUTO_REQUERY) == FLAG_AUTO_REQUERY) {
			flags |= FLAG_REGISTER_CONTENT_OBSERVER;
			mAutoRequery = true;
		} else {
			mAutoRequery = false;
		}
		boolean cursorPresent = c != null;
		mCursor = c;
		mDataValid = cursorPresent;
		mContext = context;
		mRowIDColumn = cursorPresent ? c.getColumnIndexOrThrow("_id") : -1;
		if ((flags & FLAG_REGISTER_CONTENT_OBSERVER) == FLAG_REGISTER_CONTENT_OBSERVER) {
			mChangeObserver = new ChangeObserver();
			mDataSetObserver = new MyDataSetObserver();
		} else {
			mChangeObserver = null;
			mDataSetObserver = null;
		}

		if (cursorPresent) {
			if (mChangeObserver != null)
				c.registerContentObserver(mChangeObserver);
			if (mDataSetObserver != null)
				c.registerDataSetObserver(mDataSetObserver);
		}
	}

	@Override
	public int getItemCount() {
		int itemsCount = 0;
		if (mDataValid) {
			if (mCursor != null) {
				itemsCount = mCursor.getCount();
			}
			if (headerView != null) {
				itemsCount = itemsCount + 1;
			}
			if (footerView != null) {
				itemsCount = itemsCount + 1;
			}
		}
		return itemsCount;
	}

	public int getCursorCount() {
		int itemsCount = 0;
		if (mDataValid && mCursor != null) {
			itemsCount = mCursor.getCount();
		}
		return itemsCount;
	}

	@Override
	public int getItemViewType(int position) {
		if (position == 0 && headerView != null) {
			return VIEW_TYPE_HEADER;
		} else if (position == getItemCount() - 1 && footerView != null) {
			return VIEW_TYPE_FOOTER;
		}
		return VIEW_TYPE_LIST_ITEM;
	}

	@Override
	public void onBindViewHolder(ViewHolder holder, int i) {
		if (!mDataValid) {
			throw new IllegalStateException(
					"this should only be called when the cursor is valid");
		}
		int viewType = getItemViewType(i);
		if (viewType == CursorRecyclerAdapter.VIEW_TYPE_LIST_ITEM
				&& !mCursor.moveToPosition(i - getHeaderViewsCount())) {

			throw new IllegalStateException("couldn't move cursor to position "
					+ (i - getFooterViewsCount()));
		}
		onBindViewHolderCursor(holder, mCursor, i);
	}

	public abstract void onBindViewHolderCursor(ViewHolder holder,
			Cursor cursor, int position);

	public abstract ViewHolder onCreateItemViewHolder(ViewGroup arg0,
			int position);

	@Override
	public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup arg0,
			int viewType) {

		if (viewType == CursorRecyclerAdapter.VIEW_TYPE_FOOTER) {
			VHFooter vHFooter = new VHFooter(footerView);
			return vHFooter;
		} else if (viewType == CursorRecyclerAdapter.VIEW_TYPE_HEADER) {
			VHHeader vHHeader = new VHHeader(headerView);
			return vHHeader;
		} else {
			return onCreateItemViewHolder(arg0, viewType);
		}

	}

	public void changeCursor(Cursor cursor) {
		Cursor old = swapCursor(cursor);
		if (old != null) {
			old.close();
		}
	}

	public Cursor swapCursor(Cursor newCursor) {
		if (newCursor == mCursor) {
			return null;
		}
		Cursor oldCursor = mCursor;
		if (oldCursor != null) {
			if (mChangeObserver != null)
				oldCursor.unregisterContentObserver(mChangeObserver);
			if (mDataSetObserver != null)
				oldCursor.unregisterDataSetObserver(mDataSetObserver);
		}
		mCursor = newCursor;
		if (newCursor != null) {
			if (mChangeObserver != null)
				newCursor.registerContentObserver(mChangeObserver);
			if (mDataSetObserver != null)
				newCursor.registerDataSetObserver(mDataSetObserver);
			mRowIDColumn = newCursor.getColumnIndexOrThrow("_id");
			mDataValid = true;
			// notify the observers about the new cursor
			notifyDataSetChanged();
		} else {
			mRowIDColumn = -1;
			mDataValid = false;
			// notify the observers about the lack of a data set
			notifyItemRangeRemoved(0, getItemCount());
		}
		return oldCursor;
	}

	public CharSequence convertToString(Cursor cursor) {
		return cursor == null ? "" : cursor.toString();
	}

	public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
		if (mFilterQueryProvider != null) {
			return mFilterQueryProvider.runQuery(constraint);
		}

		return mCursor;
	}

	public Filter getFilter() {
		if (mCursorFilter == null) {
			mCursorFilter = new CursorFilter(this);
		}
		return mCursorFilter;
	}

	public FilterQueryProvider getFilterQueryProvider() {
		return mFilterQueryProvider;
	}

	public void setFilterQueryProvider(FilterQueryProvider filterQueryProvider) {
		mFilterQueryProvider = filterQueryProvider;
	}

	protected void onContentChanged() {
		if (mAutoRequery && mCursor != null && !mCursor.isClosed()) {
			mDataValid = mCursor.requery();
		}
	}

	@Override
	public Cursor getCursor() {
		// TODO Auto-generated method stub
		return mCursor;
	}

	public void addHeaderView(View view) {
		headerView = view;
		if (mDataValid) {
			notifyDataSetChanged();
		}
	}

	public void addFooterView(View view) {
		footerView = view;
		if (mDataValid) {
			notifyDataSetChanged();
		}
	}

	private class ChangeObserver extends ContentObserver {
		public ChangeObserver() {
			super(new Handler());
		}

		@Override
		public boolean deliverSelfNotifications() {
			return true;
		}

		@Override
		public void onChange(boolean selfChange) {
			onContentChanged();
		}
	}

	private class MyDataSetObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			mDataValid = true;
			notifyDataSetChanged();
		}

		@Override
		public void onInvalidated() {
			mDataValid = false;
			notifyItemRangeRemoved(0, getItemCount());
		}
	}

	class VHFooter extends RecyclerView.ViewHolder {

		public VHFooter(View itemView) {
			super(itemView);
		}
	}

	class VHHeader extends RecyclerView.ViewHolder {

		public VHHeader(View itemView) {
			super(itemView);
		}
	}

	public int getHeaderViewsCount() {
		return headerView != null ? 1 : 0;
	}

	public int getFooterViewsCount() {
		return footerView != null ? 1 : 0;
	}
}

class CursorFilter extends Filter {

	CursorFilterClient mClient;

	interface CursorFilterClient {
		CharSequence convertToString(Cursor cursor);

		Cursor runQueryOnBackgroundThread(CharSequence constraint);

		Cursor getCursor();

		void changeCursor(Cursor cursor);
	}

	CursorFilter(CursorFilterClient client) {
		mClient = client;
	}

	@Override
	public CharSequence convertResultToString(Object resultValue) {
		return mClient.convertToString((Cursor) resultValue);
	}

	@Override
	protected FilterResults performFiltering(CharSequence constraint) {
		Cursor cursor = mClient.runQueryOnBackgroundThread(constraint);

		FilterResults results = new FilterResults();
		if (cursor != null) {
			results.count = cursor.getCount();
			results.values = cursor;
		} else {
			results.count = 0;
			results.values = null;
		}
		return results;
	}

	@Override
	protected void publishResults(CharSequence constraint, FilterResults results) {
		Cursor oldCursor = mClient.getCursor();

		if (results.values != null && results.values != oldCursor) {
			mClient.changeCursor((Cursor) results.values);
		}
	}

}
