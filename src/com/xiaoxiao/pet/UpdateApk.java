package com.xiaoxiao.pet;

import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.Build;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;

final class UpdateApk {
    static long code(PackageInfo info){return Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}
    static int signatureFlags(){return Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;}
    static PackageInfo installed(Context c)throws Exception {return c.getPackageManager().getPackageInfo(c.getPackageName(),signatureFlags());}
    static Set<String> signers(PackageInfo info)throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        Set<String> result=new HashSet<>();if(signatures!=null)for(Signature signature:signatures)result.add(AppUpdate.hex(MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())));return result;
    }
    static void validateInfo(AppUpdate release,PackageInfo current,PackageInfo candidate,int sdk)throws Exception {
        if(candidate==null||!release.packageName.equals(candidate.packageName)||!current.packageName.equals(candidate.packageName))throw new IOException("安装包的应用标识不匹配");
        if(code(candidate)!=release.code||code(candidate)<=code(current))throw new IOException("安装包版本不匹配或不是新版本");
        if(release.minSdk>sdk||candidate.applicationInfo==null||candidate.applicationInfo.minSdkVersion>sdk)throw new IOException("此更新不支持当前 Android 版本");
        Set<String> expected=signers(current);if(expected.isEmpty()||!expected.equals(signers(candidate)))throw new IOException("安装包签名不一致，已保留当前版本");
    }
    static void verify(Context c,AppUpdate release,File file)throws Exception {
        if(file.length()!=release.size||!AppUpdate.digest(file).equals(release.sha))throw new IOException("安装包校验失败，请重新下载");
        validateInfo(release,installed(c),c.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),signatureFlags()),Build.VERSION.SDK_INT);
    }
    static File directory(Context c){return new File(c.getFilesDir(),"updates");}
    static Uri uri(Context c,AppUpdate release){return new Uri.Builder().scheme("content").authority(c.getPackageName()+".updates").appendPath(release.filename()).build();}
    static Intent installIntent(Context c,AppUpdate release){Uri uri=uri(c,release);Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("update",uri));return intent;}
    static File resolve(Context c,Uri uri)throws IOException {
        if(!"content".equals(uri.getScheme())||!(c.getPackageName()+".updates").equals(uri.getAuthority())||uri.getQuery()!=null||uri.getFragment()!=null||uri.getPath()==null||!uri.getPath().matches("/[0-9]+-[a-f0-9]{64}\\.apk"))throw new FileNotFoundException("无效更新文件");
        File root=directory(c).getCanonicalFile(),file=new File(root,uri.getLastPathSegment()).getCanonicalFile();if(!root.equals(file.getParentFile())||!file.isFile())throw new FileNotFoundException("更新文件不存在");return file;
    }
}
