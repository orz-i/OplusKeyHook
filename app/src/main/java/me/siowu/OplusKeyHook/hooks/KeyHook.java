package me.siowu.OplusKeyHook.hooks;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;

import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;


public class KeyHook {

    XSharedPreferences sp = null;
    private long lastDownTime = 0;
    private long lastUpTime = 0;
    private int clickCount = 0;
    private boolean isLongPress = false;
    private boolean longPressHandled = false; // 标记长按是否已处理
    private Thread longPressThread = null; // 保存长按检测线程引用，用于取消
    private static final long DOUBLE_CLICK_DELAY = 300;
    private static final long LONG_PRESS_TIME = 495;
    private static Context systemContext;

    public void handleLoadPackage(LoadPackageParam lpparam) {

        sp = new XSharedPreferences("me.siowu.OplusKeyHook", "key_action");
        sp.makeWorldReadable();

        try {
            Class<?> clazz = XposedHelpers.findClass(
                    "com.android.server.policy.StrategyActionButtonKeyLaunchApp",
                    lpparam.classLoader
            );

            if (clazz == null) {
                XposedBridge.log("[Hook] Error: StrategyActionButtonKeyLaunchApp class not found");
            }

            XposedHelpers.findAndHookMethod(clazz,
                    "actionInterceptKeyBeforeQueueing",
                    KeyEvent.class, int.class, int.class, boolean.class, boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            KeyEvent event = (KeyEvent) param.args[0];
                            int keyCode = event.getKeyCode();
                            boolean down = (boolean) param.args[3];
                            boolean interactive = (boolean) param.args[4]; // 屏幕状态：true=亮屏，false=熄屏
                            Object currentStrategy = param.thisObject;

                            if (keyCode == 780) {
                                // 检查是否有任何手势配置了功能
                                sp.reload();
                                boolean hasConfig = hasAnyGestureConfig();
                                
                                // 如果没有任何配置，放行让系统处理
                                if (!hasConfig) {
                                    XposedBridge.log("未检测到任何手势配置，放行系统处理");
                                    return; // 不拦截，让系统继续处理
                                }
                                
                                long now = System.currentTimeMillis();
                                
                                // 🔽=== 按下事件 ACTION_DOWN ===🔽
                                if (event.getAction() == KeyEvent.ACTION_DOWN && down) {
                                    lastDownTime = now;
                                    isLongPress = false;
                                    longPressHandled = false;
                                    
                                    // 取消之前可能存在的长按检测线程
                                    if (longPressThread != null && longPressThread.isAlive()) {
                                        longPressThread.interrupt();
                                    }
                                    
                                    // 启动一个判定长按的线程
                                    longPressThread = new Thread(() -> {
                                        try {
                                            Thread.sleep(LONG_PRESS_TIME);
                                            // 若超过495ms仍未抬起，则判定为长按
                                            if (lastUpTime < lastDownTime && !isLongPress && !longPressHandled) {
                                                isLongPress = true;
                                                longPressHandled = true;
                                                // 检查长按是否有配置
                                                if (hasGestureConfig("long_")) {
                                                    XposedBridge.log("触发长按事件（按下超过495ms）");
                                                    handleClick("long_", interactive, currentStrategy);
                                                } else {
                                                    XposedBridge.log("长按未配置，不执行操作");
                                                }
                                            }
                                        } catch (InterruptedException e) {
                                            // 线程被中断，说明已经抬起，不处理长按
                                            XposedBridge.log("长按检测线程被中断（已抬起）");
                                        } catch (Exception ignored) {
                                        }
                                    });
                                    longPressThread.start();

                                    param.setResult(null);
                                    return;
                                }

                                // 🔼=== 抬起事件 ACTION_UP ===🔼
                                if (event.getAction() == KeyEvent.ACTION_UP && !down) {
                                    long pressDuration = now - lastDownTime;
                                    lastUpTime = now;
                                    
                                    // 如果已经被长按处理，不处理短按和双击
                                    if (longPressHandled) {
                                        XposedBridge.log("长按已处理，忽略抬起事件");
                                        param.setResult(null);
                                        return;
                                    }
                                    
                                    // 中断长按检测线程（如果还在运行）
                                    if (longPressThread != null && longPressThread.isAlive()) {
                                        longPressThread.interrupt();
                                    }
                                    
                                    // 如果按下时间超过长按阈值，判定为长按（在抬起时立即判断）
                                    if (pressDuration >= LONG_PRESS_TIME) {
                                        isLongPress = true;
                                        longPressHandled = true;
                                        // 检查长按是否有配置
                                        if (hasGestureConfig("long_")) {
                                            XposedBridge.log("触发长按事件（按下时间: " + pressDuration + "ms）");
                                            handleClick("long_", interactive, currentStrategy);
                                        } else {
                                            XposedBridge.log("长按未配置，不执行操作");
                                        }
                                        param.setResult(null);
                                        return;
                                    }
                                    
                                    // 按下时间小于长按阈值，可能是短按或双击
                                    clickCount++;

                                    // 判断双击
                                    if (clickCount == 2 && (now - lastDownTime) < DOUBLE_CLICK_DELAY) {
                                        // 检查双击是否有配置
                                        if (hasGestureConfig("double_")) {
                                            XposedBridge.log("触发双击事件");
                                            handleClick("double_", interactive, currentStrategy);
                                            clickCount = 0;
                                            param.setResult(null);
                                            return;
                                        } else {
                                            XposedBridge.log("双击未配置，不执行操作");
                                            clickCount = 0;
                                            param.setResult(null);
                                            return;
                                        }
                                    }

                                    // 如果 300ms 内没有第二次点击，判定为短按
                                    new Thread(() -> {
                                        try {
                                            Thread.sleep(DOUBLE_CLICK_DELAY);
                                            // 再次检查，确保长按没有被处理，且确实是单次点击
                                            if (clickCount == 1 && !longPressHandled && !isLongPress) {
                                                // 检查短按是否有配置
                                                if (hasGestureConfig("single_")) {
                                                    XposedBridge.log("触发短按事件");
                                                    handleClick("single_", interactive, currentStrategy);
                                                } else {
                                                    XposedBridge.log("短按未配置，不执行操作");
                                                }
                                            }
                                            clickCount = 0;
                                        } catch (Exception ignored) {
                                        }
                                    }).start();
                                    param.setResult(null);
                                }
                            }


                        }
                    });
        } catch (Throwable t) {
            XposedBridge.log("[Hook] Error: " + t.getMessage());
        }
    }


    public void handleClick(String prefix, boolean interactive, Object currentStrategy) {
        sp.reload();
        String gestureName = getGestureName(prefix);
        XposedBridge.log("检测到手势: " + gestureName + " (prefix: " + prefix + ")");
        
        if (interactive) {
            XposedBridge.log("当前屏幕是亮屏状态，执行 " + gestureName + " 操作");
            doAction(prefix, currentStrategy);
        } else {
            XposedBridge.log("当前屏幕是息屏状态");
            if (sp.getBoolean(prefix + "screen_off", true)) {
                XposedBridge.log("根据配置，息屏状态下允许执行 " + gestureName + " 操作，唤醒屏幕");
                XposedHelpers.callMethod(currentStrategy, "wakeup");
                doAction(prefix, currentStrategy);
            } else {
                XposedBridge.log("根据配置设定，息屏状态下不执行 " + gestureName + " 操作");
            }
        }
    }
    
    private String getGestureName(String prefix) {
        switch (prefix) {
            case "single_":
                return "短按";
            case "double_":
                return "双击";
            case "long_":
                return "长按";
            default:
                return prefix;
        }
    }
    
    /**
     * 检查是否有任何手势配置了功能
     * @return true 如果有至少一个手势配置了功能，false 如果都没有配置
     */
    private boolean hasAnyGestureConfig() {
        sp.reload();
        return hasGestureConfig("single_") || 
               hasGestureConfig("double_") || 
               hasGestureConfig("long_");
    }
    
    /**
     * 检查指定手势是否有有效配置
     * @param prefix 手势前缀 (single_/double_/long_)
     * @return true 如果有有效配置，false 如果未配置或配置为"无"
     */
    private boolean hasGestureConfig(String prefix) {
        sp.reload();
        String type = sp.getString(prefix + "type", "无");
        // 如果类型为空、null 或"无"，则认为没有配置
        return type != null && !type.isEmpty() && !"无".equals(type);
    }

    public void doAction(String prefix, Object currentStrategy) {
        XposedBridge.log("开始执行快捷键操作，手势类型: " + prefix);
        sp.reload();
        
        // 震动反馈
        if (sp.getBoolean(prefix + "vibrate", true)) {
            XposedBridge.log("根据配置需要震动反馈");
            XposedHelpers.callMethod(currentStrategy, "longPressStartVibrate");
        } else {
            XposedBridge.log("根据配置不需要震动反馈");
        }
        
        // 读取配置的操作类型
        String type = sp.getString(prefix + "type", "无");
        XposedBridge.log("当前快捷键类型 [" + prefix + "]: " + type);
        
        // 如果未配置或设置为"无"，则不执行任何操作
        if (type == null || type.isEmpty() || "无".equals(type)) {
            XposedBridge.log("手势 [" + prefix + "] 未配置或设置为无操作");
            return;
        }
        
        // 根据配置类型执行相应操作
        switch (type) {
            case "常用功能":
                doCommonAction(prefix);
                break;
            case "自定义Activity":
                doCustomActivity(prefix);
                break;
            case "自定义UrlScheme":
                doCustomUrlScheme(prefix);
                break;
            case "执行小布快捷指令":
                doXiaobuShortcuts(prefix);
                break;
            case "自定义Shell命令":
                doCustomShell(prefix);
                break;
            default:
                XposedBridge.log("未知的快捷键类型 [" + prefix + "]: " + type);
                break;
        }
    }

    public void doCommonAction(String prefix) {
        sp.reload();
        int index = sp.getInt(prefix + "common_index", 0);
        XposedBridge.log("当前常用操作索引: " + index);
        switch (index) {
            case 0:
                startWechatActivity("launch_type_offline_wallet");
                break;
            case 1:
                startWechatActivity("launch_type_scan_qrcode");
                break;
            case 2:
                startSchemeAsBrowser("alipays://platformapi/startapp?saId=20000056");
                break;
            case 3:
                startSchemeAsBrowser("alipays://platformapi/startapp?saId=10000007");
                break;
            case 4:
                startFlashMemoryService();
                break;
            case 5:
                startActivity("com.oplus.aimemory", "com.oplus.aimemory.MainActivity");
                break;
        }
    }

    public void doCustomActivity(String prefix) {
        sp.reload();
        String activity = sp.getString(prefix + "activity", "");
        String packageName = sp.getString(prefix + "package", "");
        if (activity.isEmpty() || packageName.isEmpty()) {
            XposedBridge.log("自定义Activity为空");
            return;
        }
        startActivity(packageName, activity);
    }

    public void doCustomUrlScheme(String prefix) {
        sp.reload();
        String scheme = sp.getString(prefix + "url", "");
        if (scheme.isEmpty()) {
            XposedBridge.log("自定义UrlScheme为空");
            return;
        }
        startSchemeAsBrowser(scheme);
    }

    public void doXiaobuShortcuts(String prefix) {
        sp.reload();
        String scheme = sp.getString(prefix + "xiaobu_shortcuts", "");
        if (scheme.isEmpty()) {
            XposedBridge.log("小布快捷指令ID为空");
            return;
        }
        XposedBridge.log("小布快捷指令ID: " + scheme);
        executeXiaoBuShortcut(scheme, "");
    }


    //启动自定义Activity
    private void startActivity(String pkgName, String targetActivity) {
        try {
            // 1. 获取系统上下文（Hook系统进程可直接拿到）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            // 2. 构造启动微信的Intent
            Intent intent = new Intent();
            // 设置微信包名和目标Activity
            intent.setComponent(new ComponentName(pkgName, targetActivity));
            // 关键Flag：新建任务栈，避免和其他页面冲突
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 3. 启动Activity（和负一屏的核心步骤完全一致）
            systemContext.startActivity(intent);
            XposedBridge.log("成功启动指定Activity: " + targetActivity);
        } catch (Throwable t) {
            XposedBridge.log("启动指定Activity失败: " + t.getMessage());
        }
    }

    //通过微信官方的分发接口打开微信的界面
    private void startWechatActivity(String targetActivity) {
        try {
            // 1. 获取系统上下文（ActivityThread.currentApplication() 返回 Application）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (systemContext == null) {
                XposedBridge.log("startWechatPayCode: systemContext == null");
                return;
            }

            // 2. 构造 Intent —— 与负一屏发送的一致：action + target ShortCutDispatchActivity + extras
            Intent intent = new Intent();
            intent.setAction("com.tencent.mm.ui.ShortCutDispatchAction"); // 与日志与源码相符
            intent.setComponent(new ComponentName("com.tencent.mm", "com.tencent.mm.ui.ShortCutDispatchActivity"));
            intent.setPackage("com.tencent.mm"); // 限定发给微信
            // 关键 extras（来源反编译代码表明微信读取这些字段来分发）
            intent.putExtra("LauncherUI.Shortcut.LaunchType", targetActivity); // 付款码
            intent.putExtra("LauncherUI.From.Scaner.Shortcut", false);
            // 模拟系统启动行为
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 3. 启动（以 systemContext 发起，确保调用者是系统）
            systemContext.startActivity(intent);
            XposedBridge.log("startWechatPayCode: started ShortCutDispatchAction -> offline wallet");
        } catch (Throwable t) {
            XposedBridge.log("startWechatPayCode: failed: " + t);
        }
    }

    //以系统上下文模拟浏览器打开任意 scheme
    private boolean startSchemeAsBrowser(String schemeUri) {
        try {
            // 获取系统 Application Context（必须在系统进程里调用才可靠）
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );
            if (systemContext == null) {
                XposedBridge.log("startSchemeAsBrowser: systemContext == null");
                return false;
            }

            // 构造 Intent：ACTION_VIEW + Uri + BROWSABLE 类别
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(android.net.Uri.parse(schemeUri));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            // 不设置 setPackage 或 setComponent，让系统解析哪个 app 处理 scheme
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // 启动
            systemContext.startActivity(intent);
            XposedBridge.log("startSchemeAsBrowser: started scheme -> " + schemeUri);
            return true;
        } catch (Throwable t) {
            XposedBridge.log("startSchemeAsBrowser: failed to start scheme: " + t);
            return false;
        }
    }

    public static void executeXiaoBuShortcut(String tag, String widgetCode) {

        Context systemContext = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", null),
                "currentApplication"
        );
        if (systemContext == null) {
            XposedBridge.log("startWechatPayCode: systemContext == null");
            return;
        }
        try {
            Uri uri = Uri.parse("content://com.coloros.shortcuts.basecard.provider.FunctionSpecProvider");

            Bundle params = new Bundle();
            params.putString("tag", tag);
            params.putString("widgetCode", widgetCode);

            // method = execute_one_shortcut
            Bundle result = systemContext.getContentResolver().call(
                    uri,
                    "execute_one_shortcut",
                    null,
                    params
            );

            Log.d("MyApp", "execute_one_shortcut result = " + result);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void startFlashMemoryService() {
        try {
            // 获取系统上下文
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );

            if (systemContext == null) {
                XposedBridge.log("triggerFlashMemoryService: systemContext is null");
                return;
            }

            // 构造Intent
            Intent intent = new Intent();
            intent.setPackage("com.coloros.colordirectservice");
            intent.putExtra("triggerType", 1);
            intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);

            // 兼容低版本系统
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                systemContext.startForegroundService(intent);
            } else {
                systemContext.startService(intent);
            }

            XposedBridge.log("成功触发一键闪记服务");
        } catch (Throwable t) {
            XposedBridge.log("triggerFlashMemoryService error: " + t.getMessage());
        }
    }

    private void doCustomShell(String prefix) {
        sp.reload();
        String cmd = sp.getString(prefix + "shell", "");
        if (cmd.isEmpty()) {
            XposedBridge.log("自定义Shell为空");
            return;
        }

        try {
            // 获取系统上下文
            Context systemContext = (Context) XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", null),
                    "currentApplication"
            );

            if (systemContext == null) {
                XposedBridge.log("systemContext 为 null，无法发送广播");
                return;
            }

            // *** 显式广播：直接指定组件 ***
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "me.siowu.OplusKeyHook",
                    "me.siowu.OplusKeyHook.utils.ShellReceiver"
            ));
            intent.putExtra("cmd", cmd);

            // 发送广播（不需要 action，不会被过滤）
            systemContext.sendBroadcast(intent);

            XposedBridge.log("已请求 APP 执行 Shell: " + cmd);

        } catch (Throwable t) {
            XposedBridge.log("发送广播失败: " + Log.getStackTraceString(t));
        }
    }


}