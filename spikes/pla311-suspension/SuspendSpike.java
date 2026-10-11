// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike311;
import android.app.Instrumentation;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import java.lang.reflect.*;
import java.util.Arrays;

/**
 * THROWAWAY spike for PLA-311 (emulator only, never a physical device, never places calls).
 * Runs in org.plainphone's process (the Device Owner). Based on the PLA-307 Verifier FailureProbe:
 * wraps the runtime's port so only setLockTaskFeatures is ignored, sets features 16 with the list kept,
 * then suspends the packages named in -e pkgs (comma list) through DevicePolicyManager and reads them
 * back. Holds for -e hold seconds, then lifts exactly that set and restores the full features.
 */
public final class SuspendSpike extends Instrumentation {
  Bundle args;
  public void onCreate(Bundle a) { args = a; super.onCreate(a); start(); }
  static Object field(Object obj, String name) throws Exception {
    Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(obj);
  }
  static void replace(Object obj, String name, Object value) throws Exception {
    Field f=obj.getClass().getDeclaredField(name); f.setAccessible(true); f.set(obj,value);
  }
  public void onStart() {
    final String T="PLA311Spike";
    try {
      final Context c=getTargetContext(); ClassLoader cl=c.getClassLoader();
      String spec=args.getString("pkgs","com.android.settings,com.android.chrome");
      final String[] pkgs;
      if (spec.equals("ALL")) {
        // Every installed package except the lock-task list and the -e keep list.
        java.util.Set<String> keep=new java.util.HashSet<>(Arrays.asList(args.getString("keep","").split(",")));
        DevicePolicyManager dd=c.getSystemService(DevicePolicyManager.class);
        keep.addAll(Arrays.asList(dd.getLockTaskPackages(new ComponentName("org.plainphone","org.plainphone.PlainphoneAdminReceiver"))));
        java.util.List<String> l=new java.util.ArrayList<>();
        for (android.content.pm.PackageInfo i: c.getPackageManager().getInstalledPackages(0)) if (!keep.contains(i.packageName)) l.add(i.packageName);
        pkgs=l.toArray(new String[0]);
        Log.i(T,"ALL resolved to "+pkgs.length+" packages; kept="+keep);
      } else pkgs=spec.split(",");
      int hold=Integer.parseInt(args.getString("hold","150"));
      Class<?> rt=cl.loadClass("org.plainphone.boot.PolicyRuntime");
      Object companion=rt.getField("Companion").get(null);
      Object runtime=companion.getClass().getMethod("get",Context.class).invoke(companion,c);
      final Object boot=field(runtime,"boot"); Object dispatcher=field(runtime,"dispatcher");
      Class<?> fn=cl.loadClass("kotlin.jvm.functions.Function0");
      final Object unit=cl.loadClass("kotlin.Unit").getField("INSTANCE").get(null);
      final Object original=field(boot,"port"); Class<?> port=cl.loadClass("org.plainphone.device.DevicePolicyPort");
      final Object fault=Proxy.newProxyInstance(cl,new Class<?>[]{port},(proxy,m,a)->{
        if (m.getName().equals("setLockTaskFeatures")) { Log.i(T,"IGNORED feature write"); return null; }
        try { return m.invoke(original,a); } catch(InvocationTargetException e) { throw e.getCause(); }
      });
      final DevicePolicyManager d=c.getSystemService(DevicePolicyManager.class);
      final ComponentName admin=new ComponentName("org.plainphone","org.plainphone.PlainphoneAdminReceiver");
      Object injection=Proxy.newProxyInstance(cl,new Class<?>[]{fn},(proxy,m,a)->{
        replace(boot,"port",fault); replace(field(boot,"applier"),"port",fault);
        String[] kept=d.getLockTaskPackages(admin);
        d.setLockTaskPackages(admin,new String[0]);
        d.setLockTaskFeatures(admin,DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS);
        d.setLockTaskPackages(admin,kept);
        Log.i(T,"fault installed; membership retained; flags="+d.getLockTaskFeatures(admin));
        String[] refused=d.setPackagesSuspended(admin,pkgs,true);
        Log.i(T,"suspend requested="+Arrays.toString(pkgs)+" refused="+Arrays.toString(refused));
        for (String p: pkgs) if (d.isPackageSuspended(admin,p)==false) Log.i(T,"readback-not-suspended "+p);
        return unit;
      });
      dispatcher.getClass().getMethod("run",fn).invoke(dispatcher,injection);
      rt.getMethod("afterTrustedPersonAction").invoke(runtime);
      Thread.sleep(2000);
      c.startActivity(new Intent().setComponent(new ComponentName("org.plainphone","org.plainphone.MainActivity")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      Log.i(T,"production MainActivity launched; inspect now for "+hold+" s");
      Thread.sleep(hold*1000L);
      String[] refused=d.setPackagesSuspended(admin,pkgs,false);
      Log.i(T,"lifted "+Arrays.toString(pkgs)+" refused="+Arrays.toString(refused));
      replace(boot,"port",original); replace(field(boot,"applier"),"port",original);
      Log.i(T,"port restored");
      finish(0,new Bundle());
    } catch(Throwable e) { Log.e(T,"spike failed",e); Bundle b=new Bundle();b.putString("error",e.toString());finish(1,b); }
  }
}
