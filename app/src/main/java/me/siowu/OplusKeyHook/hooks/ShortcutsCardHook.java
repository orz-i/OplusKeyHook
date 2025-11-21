package me.siowu.OplusKeyHook.hooks;

import android.content.Context;
import android.os.Bundle;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class ShortcutsCardHook implements IXposedHookLoadPackage {

    private static final String TAG = "CardRobustHook";

    // 用于存储当前正在处理的 widgetCode
    private static String currentWidgetCode = null;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!"com.coloros.shortcuts".equals(lpparam.packageName)) return;

        // Hook subscribed(...) — 记录当前正在添加的 widgetCode
        try {
            Class<?> providerClazz = XposedHelpers.findClass(
                    "com.coloros.shortcuts.shortcutcard.OneShortcutCardWidgetProvider",
                    lpparam.classLoader
            );

            XposedHelpers.findAndHookMethod(
                    providerClazz,
                    "subscribed",
                    Context.class,
                    String.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String widgetCode = (String) param.args[1];
                            currentWidgetCode = widgetCode; // 记录下来，用于后续关联
                            XposedBridge.log(TAG + ": [ADD START] subscribed() widgetCode=" + widgetCode);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            // 添加完成后，清空记录，避免干扰后续操作
                            currentWidgetCode = null;
                        }
                    }
            );
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed hook OneShortcutCardWidgetProvider.subscribed: " + t);
        }

        // Hook u3.e0.b(...) —— 捕获对 CardDataProvider 的请求
        try {
            Class<?> helper = XposedHelpers.findClass("u3.e0", lpparam.classLoader);

            // 尝试 hook 常见签名
            try {
                XposedHelpers.findAndHookMethod(helper,
                        "b",
                        Context.class,               // context
                        String.class,                // authority (provider)
                        String.class,                // method
                        String.class,                // optional arg (可能为 null)
                        Bundle.class,                // extras bundle
                        new XC_MethodHook() {
                            @Override
                            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                String authority = (String) param.args[1];
                                String method = (String) param.args[2];
                                Bundle extras = (Bundle) param.args[4];

                                // 我们只关心对 CardDataProvider 的 bindWidgetAndShortcutCard 调用
                                if ("com.coloros.shortcuts.carddata.CardDataProvider".equals(authority) &&
                                        "bindWidgetAndShortcutCard".equals(method)) {

                                    // 获取 call 方法的返回结果
                                    Object result = param.getResult();
                                    if (result instanceof Bundle) {
                                        Bundle resultBundle = (Bundle) result;

                                        // 检查返回结果是否成功
                                        if (resultBundle.containsKey("bundleResult") && resultBundle.getBoolean("bundleResult")) {
                                            // 成功绑定！现在开始查找对应的快捷方式信息

                                            // 1. 从 extras 中获取 widgetCode 和 shortcutId
                                            String widgetCode = extras.getString("widgetCode");
                                            int shortcutId = extras.getInt("shortcutId");

                                            // 2. 使用当前记录的 widgetCode 进行验证（可选，增加准确性）
                                            if (currentWidgetCode != null && !currentWidgetCode.equals(widgetCode)) {
                                                XposedBridge.log(TAG + ": [WARN] widgetCode mismatch! Expected: " + currentWidgetCode + ", Got: " + widgetCode);
                                                // 可以选择不打印，或者打印警告
                                                return;
                                            }

                                            // 3. 打印关键信息
                                            XposedBridge.log(TAG + ": [ADD SUCCESS] Widget added! widgetCode=" + widgetCode + ", shortcutId=" + shortcutId);

                                            // 4. 关键步骤：查询刚刚绑定的 shortcutId 对应的详细信息
                                            // 通常，在 bind 成功后，系统会立即调用 queryShortcutLayoutArguments 来获取布局数据
                                            // 但我们也可以直接构造一个查询来获取它
                                            Bundle queryExtras = new Bundle();
                                            queryExtras.putString("widgetCode", widgetCode); // 或者使用 shortcutId 查询，取决于接口

                                            // 调用 e0.b(...) 来查询 layout arguments
                                            Object queryResult = XposedHelpers.callStaticMethod(helper, "b",
                                                    param.args[0], // context
                                                    authority,
                                                    "queryShortcutLayoutArguments", // method name
                                                    null, // arg
                                                    queryExtras);

                                            if (queryResult instanceof Bundle) {
                                                Bundle layoutBundle = (Bundle) queryResult;
                                                String shortcutTag = layoutBundle.getString("key_shortcut_tag");
                                                String shortcutTitle = layoutBundle.getString("key_shortcut_title");
                                                String shortcutDes = layoutBundle.getString("key_shortcut_des");

                                                // 打印最终目标：key_shortcut_tag
                                                XposedBridge.log(TAG + ": [KEY INFO] key_shortcut_tag=" + shortcutTag +
                                                        ", title=" + shortcutTitle +
                                                        ", des=" + shortcutDes);
                                            } else {
                                                XposedBridge.log(TAG + ": [ERROR] Failed to query layout for widgetCode=" );
                                            }
                                        } else {
                                            XposedBridge.log(TAG + ": [ERROR] bindWidgetAndShortcutCard failed for widgetCode=");
                                        }
                                    }
                                }
                            }
                        }
                );
                XposedBridge.log(TAG + ": hooked u3.e0.b(Context,String,String,String,Bundle)");
            } catch (Throwable ex1) {
                XposedBridge.log(TAG + ": failed hook u3.e0.b variants: " + ex1);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": cannot find class u3.e0: " + t);
        }
    }
}