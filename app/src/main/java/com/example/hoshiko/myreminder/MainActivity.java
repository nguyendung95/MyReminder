package com.example.hoshiko.myreminder;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import android.widget.TextView;
import android.widget.Toast;

import com.amulyakhare.textdrawable.TextDrawable;
import com.amulyakhare.textdrawable.util.ColorGenerator;
import com.bignerdranch.android.multiselector.ModalMultiSelectorCallback;
import com.bignerdranch.android.multiselector.MultiSelector;
import com.bignerdranch.android.multiselector.SwappingHolder;
import com.example.hoshiko.myreminder.object.Reminder;
import com.example.hoshiko.myreminder.database.ReminderDatabase;
import com.example.hoshiko.myreminder.object.ReminderItem;
import com.example.hoshiko.myreminder.receiver.AlarmReceiver;
import com.example.hoshiko.myreminder.utils.DateTimeSorter;
import com.getbase.floatingactionbutton.FloatingActionButton;


import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private RecyclerView mList;
    private SimpleAdapter mAdapter;
    private Toolbar mToolbar;
    private TextView mNoReminderView;
    private com.getbase.floatingactionbutton.FloatingActionButton mAddReminderButton;
    private int mTempPost;
    private LinkedHashMap<Integer, Integer> IDmap = new LinkedHashMap<>();
    private ReminderDatabase rb;
    private MultiSelector mMultiSelector = new MultiSelector();
    private AlarmReceiver mAlarmReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize reminder database
        rb = new ReminderDatabase(getApplicationContext());

        // Khởi tạo views & recycle views & AlarmReceiver
        initializeView();

        // Kiểm tra trong db đã tồn tại Reminder nào chưa ?
        checkReminder();

        // Lắng nghe sự kiện trên floating action button
        mAddReminderButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(v.getContext(), AddReminderActivity.class);
                startActivity(intent);
            }
        });

    }

    // Khởi tạo Views, toolbar, recycler view, simple adapter...
    public void initializeView() {
        mToolbar = (Toolbar) findViewById(R.id.toolbar);
        mAddReminderButton = (FloatingActionButton) findViewById(R.id.add_reminder);
        mList = (RecyclerView) findViewById(R.id.reminder_list);
        mNoReminderView = (TextView) findViewById(R.id.no_reminder_text);

        // Tạo recycler view bằng simple Adapter
        mList.setLayoutManager(getLayoutManager());
        registerForContextMenu(mList);
        mAdapter = new SimpleAdapter();
        mAdapter.setItemCount(getDefaultItemCount());
        mList.setAdapter(mAdapter);

        // Setup toolbar
        setSupportActionBar(mToolbar);
        mToolbar.setTitle(R.string.app_name);

        // Khởi tạo alarm
        mAlarmReceiver = new AlarmReceiver();
    }

    // Kiểm tra xem thử có bản ghi nào đã có trong database chưa ?
    // Nếu chưa thì hiển thị thông báo (bằng textView)
    public void checkReminder() {

        List<Reminder> mTest = rb.getAllReminders();

        if (mTest.isEmpty()) {
            mNoReminderView.setVisibility(View.VISIBLE);
        } else {
            mNoReminderView.setVisibility(View.GONE);
        }
    }

    // Tạo context menu cho nhấn lâu (long press)
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        getMenuInflater().inflate(R.menu.menu_add_reminder, menu);
    }

    // Multi select items in recycler view
    private android.support.v7.view.ActionMode.Callback mDeleteMode = new ModalMultiSelectorCallback(mMultiSelector) {

        @Override
        public boolean onCreateActionMode(android.support.v7.view.ActionMode actionMode, Menu menu) {
            getMenuInflater().inflate(R.menu.menu_add_reminder, menu);
            return true;
        }

        @Override
        public boolean onActionItemClicked(android.support.v7.view.ActionMode actionMode, MenuItem menuItem) {
            switch (menuItem.getItemId()) {

                // Khi chọn vào icon thùng rác thì xóa item đang đc chọn
                case R.id.discard_reminder:
                    // Đóng context menu
                    actionMode.finish();

                    // Kiếm reminder id thông qua recycler view item
                    for (int i = IDmap.size(); i >= 0; i--) {
                        if (mMultiSelector.isSelected(i, 0)) {
                            int id = IDmap.get(i);

                            // Tìm reminder từ db sử dụng id
                            Reminder temp = rb.getReminder(id);
                            // Xóa reminder
                            rb.deleteReminder(temp);
                            // Xóa reminder khỏi recycler view
                            mAdapter.removeItemSelected(i);
                            // Xóa reminder alarm
                            mAlarmReceiver.cancelAlarm(getApplicationContext(), id);
                        }
                    }

                    // Xóa các items đã được chọn trong recycler view
                    mMultiSelector.clearSelections();
                    // Recreate the recycler items
                    // This is done to remap the item and reminder ids
                    mAdapter.onDeleteItem(getDefaultItemCount());

                    // Hiện toast thông báo cho user
                    Toast.makeText(getApplicationContext(), "Đã xóa", Toast.LENGTH_SHORT).show();

                    // Kiểm tra xem trong db còn reminder nào nữa không
                    // Nếu không thì hiện TextView thông báo
                    checkReminder();

                    return true;

                // Nếu chọn icon dấu tick
                case R.id.save_reminder:
                    // Đóng menu hiện tại lại
                    actionMode.finish();
                    // Xóa hết các items đã lựa chọn
                    mMultiSelector.clearSelections();
                    return true;

                default:
                    break;
            }
            return false;
        }
    };

    // Nếu click vào từng Reminder
    private void selectReminder(int mClickID) {
        String mStringClickID = Integer.toString(mClickID);

        // Tạo một intent mới mở Edit Activity
        // Truyền  reminder id vào extra
        Intent i = new Intent(this, EditReminderActivity.class);
        i.putExtra(EditReminderActivity.EXTRA_REMINDER_ID, mStringClickID);
        startActivityForResult(i, 1);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        mAdapter.setItemCount(getDefaultItemCount());
    }

    // Tái tạo lại recycler view
    // Reminder vừa tạo sẽ luôn được hiển thị
    @Override
    public void onResume() {
        super.onResume();

        // Kiểm tra xem trong db còn reminder nào nữa không
        // Nếu không thì hiện TextView thông báo
        checkReminder();
        mAdapter.setItemCount(getDefaultItemCount());
    }


    // Layout manager for recycler view
    protected RecyclerView.LayoutManager getLayoutManager() {
        return new LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false);
    }

    protected int getDefaultItemCount() {
        return 100;
    }

    // Tạo menu 3 chấm ở góc trái
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    // Setup menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Hành động khi thêm reminder bằng file excel
            case R.id.action_add_by_excel:
                Intent intent = new Intent(this, AddFromExcelActivity.class);
                startActivity(intent);
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Adapter class for recycler view
    public class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.VerticalItemHolder> {
        private ArrayList<ReminderItem> mItems;

        public SimpleAdapter() {
            mItems = new ArrayList<>();
        }

        public void setItemCount(int count) {
            mItems.clear();
            mItems.addAll(generateData(count));
            notifyDataSetChanged();
        }

        public void onDeleteItem(int count) {
            mItems.clear();
            mItems.addAll(generateData(count));
        }

        public void removeItemSelected(int selected) {
            if (mItems.isEmpty()) return;
            mItems.remove(selected);
            notifyItemRemoved(selected);
        }

        // View holder for recycler view items
        @Override
        public VerticalItemHolder onCreateViewHolder(ViewGroup container, int viewType) {
            LayoutInflater inflater = LayoutInflater.from(container.getContext());
            View root = inflater.inflate(R.layout.reminder_items, container, false);

            return new VerticalItemHolder(root, this);
        }

        @Override
        public void onBindViewHolder(VerticalItemHolder itemHolder, int position) {
            ReminderItem item = mItems.get(position);
            itemHolder.setReminderTitle(item.mTitle);
            itemHolder.setReminderDateTime(item.mDateTime);
            itemHolder.setReminderRepeatInfo(item.mRepeat, item.mRepeatNo, item.mRepeatType);
            itemHolder.setActiveImage(item.mActive);
        }

        @Override
        public int getItemCount() {
            return mItems.size();
        }

        // Class này để so sánh ngày giờ nên mỗi reminder sẽ được sắp xếp tăng dần theo ngày giờ
        public class DateTimeComparator implements Comparator {
            DateFormat f = new SimpleDateFormat("dd/mm/yyyy hh:mm");

            public int compare(Object a, Object b) {
                String o1 = ((DateTimeSorter) a).getDateTime();
                String o2 = ((DateTimeSorter) b).getDateTime();

                try {
                    return f.parse(o1).compareTo(f.parse(o2));
                } catch (ParseException e) {
                    throw new IllegalArgumentException(e);
                }
            }
        }


        // UI and data class for recycler view items
        public class VerticalItemHolder extends SwappingHolder
                implements View.OnClickListener, View.OnLongClickListener {

            private TextView mTitleText, mDateAndTimeText, mRepeatInfoText;
            private ImageView mActiveImage, mThumbnailImage;
            private ColorGenerator mColorGenerator = ColorGenerator.DEFAULT;
            private TextDrawable mDrawableBuilder;
            private SimpleAdapter mAdapter;

            public VerticalItemHolder(View itemView, SimpleAdapter adapter) {
                super(itemView, mMultiSelector);
                itemView.setOnClickListener(this);
                itemView.setOnLongClickListener(this);
                itemView.setLongClickable(true);

                // Khởi tạo adapter cho các items
                mAdapter = adapter;

                // Khởi tạo views
                mTitleText = (TextView) itemView.findViewById(R.id.recycle_title);
                mDateAndTimeText = (TextView) itemView.findViewById(R.id.recycle_date_time);
                mRepeatInfoText = (TextView) itemView.findViewById(R.id.recycle_repeat_info);
                mActiveImage = (ImageView) itemView.findViewById(R.id.active_image);
                mThumbnailImage = (ImageView) itemView.findViewById(R.id.thumbnail_image);
            }

            // On clicking a reminder item
            @Override
            public void onClick(View v) {
                if (!mMultiSelector.tapSelection(this)) {
                    mTempPost = mList.getChildAdapterPosition(v);

                    int mReminderClickID = IDmap.get(mTempPost);
                    selectReminder(mReminderClickID);

                } else if (mMultiSelector.getSelectedPositions().isEmpty()) {
                    mAdapter.setItemCount(getDefaultItemCount());
                }
            }

            // On long press enter action mode with context menu
            @Override
            public boolean onLongClick(View v) {
                AppCompatActivity activity = MainActivity.this;
                activity.startSupportActionMode(mDeleteMode);
                mMultiSelector.setSelected(this, true);
                return true;
            }

            // Set reminder title view
            public void setReminderTitle(String title) {
                mTitleText.setText(title);
                String letter = "A";

                if (title != null && !title.isEmpty()) {
                    letter = title.substring(0, 1);
                }

                int color = mColorGenerator.getRandomColor();

                // Tạo icon hình tròn bao gồm
                // màu nền random và chữ cái đầu tiên trong title
                mDrawableBuilder = TextDrawable.builder()
                        .buildRound(letter, color);
                mThumbnailImage.setImageDrawable(mDrawableBuilder);
            }

            // Set date and time views
            public void setReminderDateTime(String datetime) {
                mDateAndTimeText.setText(datetime);
            }

            // Set repeat views
            public void setReminderRepeatInfo(String repeat, String repeatNo, String repeatType) {
                if (repeat.equals("true")) {
                    mRepeatInfoText.setText("Mỗi " + repeatNo + " " + repeatType + " ");
                } else if (repeat.equals("false")) {
                    mRepeatInfoText.setText("Không lặp");
                }
            }

            // Set active image as on or off
            public void setActiveImage(String active) {
                if (active.equals("true")) {
                    mActiveImage.setImageResource(R.drawable.ic_notifications_on);
                } else if (active.equals("false")) {
                    mActiveImage.setImageResource(R.drawable.ic_notifications_off);
                }
            }
        }


        // Đổ dữ liệu của db vào từng item
        public List<ReminderItem> generateData(int count) {
            ArrayList<ReminderItem> items = new ArrayList<>();

            // Get all reminders from the database
            List<Reminder> reminders = rb.getAllReminders();

            // Initialize lists
            List<String> Titles = new ArrayList<>();
            List<String> Repeats = new ArrayList<>();
            List<String> RepeatNos = new ArrayList<>();
            List<String> RepeatTypes = new ArrayList<>();
            List<String> Actives = new ArrayList<>();
            List<String> DateAndTime = new ArrayList<>();
            List<Integer> IDList = new ArrayList<>();
            List<DateTimeSorter> DateTimeSortList = new ArrayList<>();

            // Add details of all reminders in their respective lists
            for (Reminder r : reminders) {
                Titles.add(r.getTitle());
                DateAndTime.add(r.getDate() + " " + r.getTime());
                Repeats.add(r.getRepeat());
                RepeatNos.add(r.getRepeatNo());
                RepeatTypes.add(r.getRepeatType());
                Actives.add(r.getActive());
                IDList.add(r.getID());
            }

            int key = 0;

            // Add date and time as DateTimeSorter objects
            for (int k = 0; k < Titles.size(); k++) {
                DateTimeSortList.add(new DateTimeSorter(key, DateAndTime.get(k)));
                key++;
            }

            // Sắp xếp các item theo ngày giờ tăng dần
            Collections.sort(DateTimeSortList, new DateTimeComparator());

            int k = 0;

            // Thêm data vào mỗi recycler view item
            for (DateTimeSorter item : DateTimeSortList) {
                int i = item.getIndex();

                items.add(new ReminderItem(Titles.get(i), DateAndTime.get(i), Repeats.get(i),
                        RepeatNos.get(i), RepeatTypes.get(i), Actives.get(i)));
                IDmap.put(k, IDList.get(i));
                k++;
            }
            return items;
        }
    }
}






