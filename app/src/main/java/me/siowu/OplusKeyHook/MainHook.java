package me.siowu.OplusKeyHook;


import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import me.siowu.OplusKeyHook.hooks.KeyHook;
import me.siowu.OplusKeyHook.hooks.ShortcutsHook;

public class MainHook implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        String packageName = lpparam.packageName;
        if ("android".equals(packageName)) {
            new KeyHook().handleLoadPackage(lpparam);
        } else if ("com.coloros.shortcuts".equals(packageName)) {
            new ShortcutsHook().handleLoadPackage(lpparam);
//            暂时只启用捕获点开一键指令之后添加到桌面
//            new ShortcutsCardHook().handleLoadPackage(lpparam);
        }
    }
}

