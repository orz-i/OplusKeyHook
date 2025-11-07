package me.siowu.OplusKeyHook;

import android.os.Bundle;
import android.view.View;
import android.widget.*;

import androidx.appcompat.app.AppCompatActivity;


public class MainActivity extends AppCompatActivity {

    private Spinner spinnerType, spinnerCommon;
    private EditText editPackage, editActivity, editUrlScheme;
    private LinearLayout layoutCommon, layoutCustomActivity, layoutUrlScheme;
    private Button btnSave;
    private CheckBox checkboxVibrate, checkboxExecuteWhenScreenOff; // ✅ 新增

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        try {
            // ✅ 初始化工具类
            SPUtils.init(this);
        } catch (SecurityException e) {
            runOnUiThread(() -> {
                Toast.makeText(
                        MainActivity.this,
                        "请先激活模块",
                        Toast.LENGTH_LONG
                ).show();
            });
        }


        spinnerType = findViewById(R.id.spinnerType);
        spinnerCommon = findViewById(R.id.spinnerCommon);
        layoutCommon = findViewById(R.id.layoutCommon);
        layoutCustomActivity = findViewById(R.id.layoutCustomActivity);
        layoutUrlScheme = findViewById(R.id.layoutUrlScheme);
        editPackage = findViewById(R.id.editPackage);
        editActivity = findViewById(R.id.editActivity);
        editUrlScheme = findViewById(R.id.editUrlScheme);
        btnSave = findViewById(R.id.btnSave);
        // ✅ 绑定 CheckBox
        checkboxVibrate = findViewById(R.id.checkboxVibrate);
        checkboxExecuteWhenScreenOff = findViewById(R.id.checkboxExecuteWhenScreenOff);

        // 模式下拉框
        ArrayAdapter<String> adapterType = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"无", "常用", "自定义Activity", "自定义UrlScheme"});
        adapterType.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerType.setAdapter(adapterType);

        // 常用功能下拉框
        ArrayAdapter<String> adapterCommon = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"微信付款码", "微信扫一扫", "支付宝付款码", "支付宝扫一扫"});
        adapterCommon.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCommon.setAdapter(adapterCommon);

        // ✅ 读取保存状态（通过工具类）
        String type = SPUtils.getString("type", "无");
        spinnerType.setSelection(adapterType.getPosition(type));
        spinnerCommon.setSelection(SPUtils.getInt("common_index", 0));
        editPackage.setText(SPUtils.getString("package", ""));
        editActivity.setText(SPUtils.getString("activity", ""));
        editUrlScheme.setText(SPUtils.getString("url", ""));
        checkboxVibrate.setChecked(SPUtils.getBoolean("vibrate_on_press", true));
        checkboxExecuteWhenScreenOff.setChecked(SPUtils.getBoolean("execute_when_screen_off", true));

        // 根据选择动态显示
        spinnerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateLayout(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        btnSave.setOnClickListener(v -> {
            String selectedType = (String) spinnerType.getSelectedItem();
            // ✅ 保存数据（通过工具类）
            SPUtils.putString("type", selectedType);
            SPUtils.putInt("common_index", spinnerCommon.getSelectedItemPosition());
            SPUtils.putString("package", editPackage.getText().toString().trim());
            SPUtils.putString("activity", editActivity.getText().toString().trim());
            SPUtils.putString("url", editUrlScheme.getText().toString().trim());
            SPUtils.putBoolean("vibrate_on_press", checkboxVibrate.isChecked());
            SPUtils.putBoolean("execute_when_screen_off", checkboxExecuteWhenScreenOff.isChecked());

            Toast.makeText(MainActivity.this, "保存成功 3秒后生效~", Toast.LENGTH_SHORT).show();
        });

        updateLayout(spinnerType.getSelectedItemPosition());
    }


    private void updateLayout(int position) {
        layoutCommon.setVisibility(View.GONE);
        layoutCustomActivity.setVisibility(View.GONE);
        layoutUrlScheme.setVisibility(View.GONE);

        switch (position) {
            case 1: // 常用
                layoutCommon.setVisibility(View.VISIBLE);
                break;
            case 2: // 自定义 Activity
                layoutCustomActivity.setVisibility(View.VISIBLE);
                break;
            case 3: // 自定义 UrlScheme
                layoutUrlScheme.setVisibility(View.VISIBLE);
                break;
        }
    }

}
