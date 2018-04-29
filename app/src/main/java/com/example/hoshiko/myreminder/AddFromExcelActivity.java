package com.example.hoshiko.myreminder;

import android.Manifest;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.example.hoshiko.myreminder.object.Reminder;
import com.example.hoshiko.myreminder.database.ReminderDatabase;
import com.example.hoshiko.myreminder.receiver.AlarmReceiver;

import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellValue;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;

public class AddFromExcelActivity extends AppCompatActivity {

    private static final String TAG = "AddFromExcelAct";

    // Constant values in milliseconds
    private static final long milMinute = 60000L;   // ( = 1 phút)
    private static final long milHour = 3600000L;   // ( = 60 phút)
    private static final long milDay = 86400000L;   // ( = 24 giờ)
    private static final long milWeek = 604800000L;
    private static final long milMonth = 2592000000L;

    // Bất kỳ số nào cũng đc
    // Xin phép permission từ ng dùng thành công
    public static final int GRANTED = 0;

    // Declare variables
    private String[] FilePathStrings;
    private String[] FileNameStrings;
    private File[] listFile;
    File file;

    Button btnUpDirectory, btnSDCard;

    ArrayList<String> pathHistory;
    String lastDirectory;
    int count = 0;


    ListView lvInternalStorage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_from_excel);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        lvInternalStorage = (ListView) findViewById(R.id.lvInternalStorage);

        // Initialize
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(R.string.title_activity_add_from_excel);

        // Kiểm tra permission có được phép từ người dùng hay chưa
        checkPermission();

        //Hiển thị thư mục gốc trong SD Card
        openSdCard();

        // Khi chọn vào mõi item trong list view
        lvInternalStorage.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                lastDirectory = pathHistory.get(count);

                final File file = new File(adapterView.getItemAtPosition(i).toString());

                if (file.isFile()) {
                    // Kiểm tra xem liệu file này có phải là Excel ?
                    if (file.getName().endsWith(".xlsx")) {
                        // Đọc dữ liệu rồi quay về activity trước
                        readExcelData(file);

                    } else {
                        toastMessage("File không hợp lệ!");
                    }
                } else if (file.isDirectory()) {
                    count++;
                    pathHistory.add(count, (String) adapterView.getItemAtPosition(i));
                    checkInternalStorage();
                    Log.d(TAG, "lvInternalStorage: " + pathHistory.get(count));
                }
            }
        });
    }

    // Kiểm tra permission có được phép từ người dùng hay chưa
    // Nếu người dùng đã từng từ chối thì giải thích rồi hỏi quyền truy cập lại
    // Nếu ng dùng lần đầu mở app thì hỏi ngay quyền luôn
    public void checkPermission() {

        if (ContextCompat.checkSelfPermission(AddFromExcelActivity.this,
                Manifest.permission.READ_EXTERNAL_STORAGE) +

                ContextCompat.checkSelfPermission(AddFromExcelActivity.this,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            // Permission is not granted
            //            // Should we show an explanation?
            if (ActivityCompat.shouldShowRequestPermissionRationale(AddFromExcelActivity.this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) ||
                    ActivityCompat.shouldShowRequestPermissionRationale(AddFromExcelActivity.this,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    ) {

                // Giải thích cho người dùng biết cần đồng ý permission
                // để được thêm reminder từ Excel
                AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
                alertBuilder.setCancelable(true);
                alertBuilder.setTitle("Truy cập vào bộ nhớ ngoài?");
                alertBuilder.setMessage("Ứng dụng cần truy cập vào bộ nhớ ngoài để đọc và ghi dữ liệu từ file Excel");
                alertBuilder.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        ActivityCompat.requestPermissions(AddFromExcelActivity.this,
                                new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE},
                                GRANTED);
                    }
                });

                alertBuilder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onBackPressed();
                    }
                });
                AlertDialog alert = alertBuilder.create();
                alert.show();

            } else {
                // No explanation needed, we can request the permission.
                // Lần đầu launch app thì nó hỏi ngay permission mà không cần phải giải thích.
                ActivityCompat.requestPermissions(AddFromExcelActivity.this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        GRANTED);

            }
        }
    }


    // Luôn lắng nghe xem permission có được thay đổi hay không ?
    // Nếu đc cấp phép đọc & ghi lên bộ nhớ ngoài mới có thể add new reminders from Excel file
    // Nếu không đc cấp phép sẽ trở về màn hình chủ
    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == GRANTED) {
            // If request is cancelled, the result arrays are empty.
            if (grantResults.length > 0 &&
                    (grantResults[0] == PackageManager.PERMISSION_GRANTED ||
                            grantResults[1] == PackageManager.PERMISSION_GRANTED)) {
                // permission denied, boo!
                // Trở lại màn hình chủ
                openSdCard();
            } else {

                toastMessage("Bạn chưa dùng tính năng thêm từ file Excel!");
                onBackPressed();
            }
        }
    }

    // Hiển thị thư mục gốc trong SD card
    private void openSdCard() {
        count = 0;
        pathHistory = new ArrayList<String>();
        pathHistory.add(count, System.getenv("EXTERNAL_STORAGE"));
        Log.d(TAG, "btnSDCard PATH HISTORY: " + pathHistory.get(count));
        Log.d(TAG, "btnSDCard PATH HISTORY COUNT: " + count);
        checkInternalStorage();
    }

    // Hiển thị các folder/file có trong Sd card
    private void checkInternalStorage() {

        Log.d(TAG, "checkInternalStorage: Started.");
        try {
            if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                toastMessage("Không có SD card !");
            } else {
                // Locate the image folder in your SD Car;d
                file = new File(pathHistory.get(count));
                Log.d(TAG, "checkInternalStorage: directory path: " + pathHistory.get(count));
                Log.d(TAG, "checkInternalStorage: directory path: COUNT: " + count);
            }

            listFile = file.listFiles();

            // Create a String array for FilePathStrings
            FilePathStrings = new String[listFile.length];

            // Create a String array for FileNameStrings
            FileNameStrings = new String[listFile.length];

            for (int i = 0; i < listFile.length; i++) {
                // Get the path of the image file
                FilePathStrings[i] = listFile[i].getAbsolutePath();
                // Get the name image file
                FileNameStrings[i] = listFile[i].getName();
            }

            for (int i = 0; i < listFile.length; i++) {
                Log.d("Files", "FileName:" + listFile[i].getName());
            }

            ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, FilePathStrings);
            lvInternalStorage.setAdapter(adapter);

        } catch (NullPointerException e) {
            Log.e(TAG, "checkInternalStorage: NULLPOINTEREXCEPTION " + e.getMessage());
            toastMessage("Khong hop le !");
        }
    }


    // Đọc file excel theo từng hàng rồi đến từng cột.
    private void readExcelData(File inputFile) {

        try {
            InputStream inputStream = new FileInputStream(inputFile);
            XSSFWorkbook workbook = new XSSFWorkbook(inputStream);
            XSSFSheet sheet = workbook.getSheetAt(0);
            int rowsCount = sheet.getPhysicalNumberOfRows();
            FormulaEvaluator formulaEvaluator = workbook.getCreationHelper().createFormulaEvaluator();
            StringBuilder sb = new StringBuilder();

            //outter loop, loops through rows
            for (int r = 0; r < rowsCount; r++) {
                Row row = sheet.getRow(r);
                int cellsCount = row.getPhysicalNumberOfCells();
                //inner loop, loops through columns
                for (int c = 0; c < cellsCount; c++) {
                    //handles if there are to many columns on the excel sheet.
                    if (c > 7) {
                        toastMessage("Lỗi:Định dạng của file Excel không chính xác!");
                        break;

                    } else {

                        String value = getCellAsString(row, c, formulaEvaluator);
                        Log.d(TAG, "VALUE IS : " + value);
                        sb.append(value + "_");
                    }
                }
                // Sau khi đọc xong 1 reminder (1 hàng)
                // Cách biệt bởi dấu %
                sb.append("%");
                Log.d(TAG, "STRING BULDER" + sb);
            }

            // Đưa chuỗi vừa nhận được tách thành các Reminder riêng biệt
            // Ghi vào db
            parseStringBuilder(sb);

        } catch (FileNotFoundException e) {
            Log.e(TAG, "readExcelData: FileNotFoundException. " + e.getMessage());
        } catch (IOException e) {
            Log.e(TAG, "readExcelData: Error reading inputstream. " + e.getMessage());
        }
    }


    /**
     * Method for parsing imported data and storing in ArrayList<XYValue>
     */
    public void parseStringBuilder(StringBuilder mStringBuilder) {
        Log.d(TAG, "parseStringBuilder: Started parsing.");

        // Tách riêng từng đối tượng Reminder bằng dấu "_"
        String[] rows = mStringBuilder.toString().split("%");

        // Đọc theo mỗi hàng (Row by row)
        for (int i = 0; i < rows.length; i++) {
            //Split the columns of the rows
            String[] columns = rows[i].split("_");

            try {
                String title = columns[0].trim();
                String date = columns[1].trim();
                String time = columns[2].trim();
                String repeat = columns[3].trim();
                String repeatNo = columns[4].trim();
                repeatNo = repeatNo.replace(".0", "");
                Log.d(TAG, "REPEAT NO=" + repeatNo);

                String repeatType = columns[5].trim();
                Log.d(TAG, "REPEAT type=" + repeatType);
                String active = columns[6].trim();

                // Lưu dữ liệu và database
                Log.d(TAG, "string BULDER: " + title + date + time + repeat + repeatNo + repeatType + active);
                saveReminder(new Reminder(title, date, time, repeat, repeatNo, repeatType, active));

            } catch (NumberFormatException e) {

                Log.e(TAG, "parseStringBuilder: NumberFormatException: " + e.getMessage());

            }
        }
    }


    // Returns the cell as a string from the excel file

    private String getCellAsString(Row row, int c, FormulaEvaluator formulaEvaluator) {
        String value = "";
        try {
            Cell cell = row.getCell(c);
            CellValue cellValue = formulaEvaluator.evaluate(cell);
            switch (cellValue.getCellTypeEnum()) {
                case BOOLEAN:
                    value = "" + cellValue.getBooleanValue();
                    break;

                case NUMERIC:
                    double numericValue = cellValue.getNumberValue();
                    if (HSSFDateUtil.isCellDateFormatted(cell)) {
                        double date = cellValue.getNumberValue();
                        SimpleDateFormat formatDate = new SimpleDateFormat("dd/MM/yyyy");
                        value = formatDate.format(HSSFDateUtil.getJavaDate(date));

                        if (value.equals("31/12/1899")) {
                            SimpleDateFormat formatTime = new SimpleDateFormat("HH:mm");
                            value = formatTime.format(HSSFDateUtil.getJavaDate(date));
                        }
                    } else {
                        value = "" + numericValue;
                    }
                    break;

                case STRING:
                    value = "" + cellValue.getStringValue();
                    break;

                default:
            }
        } catch (NullPointerException e) {

            Log.e(TAG, "getCellAsString: NullPointerException: " + e.getMessage());
        }
        return value;
    }

    // Lưu dữ liệu vừa nhận được vào db
    public void saveReminder(Reminder reminder) {

        ReminderDatabase rb = new ReminderDatabase(this);

        // Creating Reminder
        int ID = rb.addReminder(reminder);

        // Set up calender for creating the notification
        Calendar mCalendar;
        mCalendar = Calendar.getInstance();

        // Chia date & time string thành MM, dd, yyyy, ....
        String[] mDateSplit;
        String[] mTimeSplit;
        mDateSplit = reminder.getDate().split("/");
        mTimeSplit = reminder.getTime().split(":");

        int mDay = Integer.parseInt(mDateSplit[0]);
        int mMonth = Integer.parseInt(mDateSplit[1]);
        int mYear = Integer.parseInt(mDateSplit[2]);
        int mHour = Integer.parseInt(mTimeSplit[0]);
        int mMinute = Integer.parseInt(mTimeSplit[1]);


        mCalendar.set(Calendar.MONTH, --mMonth);
        mCalendar.set(Calendar.YEAR, mYear);
        mCalendar.set(Calendar.DAY_OF_MONTH, mDay);
        mCalendar.set(Calendar.HOUR_OF_DAY, mHour);
        mCalendar.set(Calendar.MINUTE, mMinute);
        mCalendar.set(Calendar.SECOND, 0);

        long mRepeatTime = 0;

        // Check repeat type
        if (reminder.getRepeatType().equals("Phút")) {
            mRepeatTime = Integer.parseInt(reminder.getRepeatNo()) * milMinute;

        } else if (reminder.getRepeatType().equals("Giờ")) {
            mRepeatTime = Integer.parseInt(reminder.getRepeatNo()) * milHour;

        } else if (reminder.getRepeatType().equals("Ngày")) {
            mRepeatTime = Integer.parseInt(reminder.getRepeatNo()) * milDay;

        } else if (reminder.getRepeatType().equals("Tuần")) {
            mRepeatTime = Integer.parseInt(reminder.getRepeatNo()) * milWeek;

        } else if (reminder.getRepeatType().equals("Tháng")) {
            mRepeatTime = Integer.parseInt(reminder.getRepeatNo()) * milMonth;
        }
        String mRepeat = "true";

        // Create a new notification
        if (reminder.getActive().equals("true")) {
            if (mRepeat.equals("true")) {
                new AlarmReceiver().setRepeatAlarm(getApplicationContext(), mCalendar, ID, mRepeatTime);
            } else if (mRepeat.equals("false")) {
                new AlarmReceiver().setAlarm(getApplicationContext(), mCalendar, ID);
            }
        }
        // Create toast to confirm new reminder
        Toast.makeText(getApplicationContext(), "Đã lưu", Toast.LENGTH_SHORT).show();

        // Quay trở lại màn hình chủ
        onBackPressed();
    }


    // Tạo menu quay lại folder trước.
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_add_from_excel, menu);
        return true;
    }

    // Setup menu
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

            // Nhấn left_arrow để trở về thư mục trước đó
            case R.id.action_back_previous_folder:
                if (count == 0) {
                    toastMessage("Bạn đã đến thư mục ngoài cùng !");
                } else {
                    pathHistory.remove(count);
                    count--;
                    checkInternalStorage();
                }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    // Customize my toast
    private void toastMessage(String message) {
        Toast.makeText(AddFromExcelActivity.this, message, Toast.LENGTH_SHORT).show();
    }

}
