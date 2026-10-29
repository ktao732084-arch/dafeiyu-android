package com.xiaoxiao.pet;

import android.content.*;
import android.content.pm.*;
import org.json.*;
import java.io.*;

/** A tiny same-signer APK fixture is parsed and shared; it is never installed over any app. */
final class UpdateInstrumentationChecks {
    static void run(AgentInstrumentation test,Context context)throws Exception {
        test.check(UpdateRuntime.channel(context).isEmpty(),"unconfigured build does not contain an update host");
        File dir=UpdateApk.directory(context);dir.mkdirs();File downloaded=new File(dir,"fixture.apk");
        try(InputStream in=context.getAssets().open("update-fixture.apk");OutputStream out=new FileOutputStream(downloaded)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        PackageInfo candidate=context.getPackageManager().getPackageArchiveInfo(downloaded.getAbsolutePath(),UpdateApk.signatureFlags());
        test.check(candidate!=null&&UpdateApk.code(candidate)==1,"real Android package parser reads new APK version");
        test.check(!UpdateApk.signers(UpdateApk.installed(context)).isEmpty(),"real installed certificate available");
        JSONObject metadata=new JSONObject().put("schema",1).put("package_name",context.getPackageName()).put("version_code",1).put("version_name","test-next").put("min_sdk",26).put("size",downloaded.length()).put("sha256",AppUpdate.digest(downloaded)).put("apk_url","https://updates.example.test/releases/test.apk");
        AppUpdate release=new AppUpdate(metadata.toString(),context.getPackageName(),AppUpdate.https("https://updates.example.test/stable.json"));
        UpdateApk.verify(context,release,downloaded);test.check(true,"same-signer higher-version APK passes actual Android validation");
        File shared=new File(dir,release.filename());java.nio.file.Files.move(downloaded.toPath(),shared.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Intent install=UpdateApk.installIntent(context,release);test.check((install.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,"install intent grants read only");
        try(InputStream in=context.getContentResolver().openInputStream(install.getData())){test.check(in.read()>=0,"real content provider opens verified private APK");}
        try{context.getContentResolver().openOutputStream(install.getData());throw new AssertionError("provider allowed writing");}catch(Exception expected){test.check(true,"real provider rejects write access");}
        try(RandomAccessFile file=new RandomAccessFile(shared,"rw")){file.seek(0);file.writeByte(0);}
        boolean rejected=false;try{UpdateApk.verify(context,release,shared);}catch(Exception expected){rejected=true;}test.check(rejected,"tampered APK rejected before installer");
        shared.delete();
    }
}
