package com.xiaoxiao.pet;

import android.app.*;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import java.io.File;
import java.net.URL;
import java.util.Collections;

/** Explicit opt-in live HTTPS check, using only the independent test app's cache. */
public class UpdateNetworkInstrumentation extends Instrumentation {
    Bundle arguments;
    public void onCreate(Bundle args){super.onCreate(args);arguments=args;start();}
    public void onStart(){Bundle result=new Bundle();int status=Activity.RESULT_CANCELED;File downloaded=null;
        try {
            String endpoint=arguments.getString("updateUrl","");String expectedSha=arguments.getString("expectedSha","");String expectedSigner=arguments.getString("expectedSigner","");
            if(endpoint.isEmpty()||!expectedSha.matches("[a-f0-9]{64}")||!expectedSigner.matches("[a-f0-9]{64}"))throw new IllegalArgumentException("Explicit URL and public release digests are required");
            URL url=AppUpdate.https(endpoint);UpdateTransport.Token token=new UpdateTransport.Token();
            AppUpdate release=new AppUpdate(UpdateTransport.manifest(url,token),"com.dafeiyu.companion",url);
            if(!expectedSha.equals(release.sha))throw new AssertionError("Unexpected public release digest");
            downloaded=UpdateTransport.download(release,new File(getTargetContext().getCacheDir(),"live-update-check"),token,(n,total)->{});
            PackageInfo archive=getTargetContext().getPackageManager().getPackageArchiveInfo(downloaded.getAbsolutePath(),UpdateApk.signatureFlags());
            if(archive==null||!release.packageName.equals(archive.packageName)||UpdateApk.code(archive)!=release.code)throw new AssertionError("APK identity differs from manifest");
            if(!UpdateApk.signers(archive).equals(Collections.singleton(expectedSigner)))throw new AssertionError("Unexpected release signer");
            result.putString("stream","PASS: real Android HTTPS IP certificate, manifest, full APK download, size/SHA-256, package/version and release signer; version="+release.name+" bytes="+downloaded.length()+"\n");status=Activity.RESULT_OK;
        }catch(Throwable e){result.putString("stream","FAIL: "+e.toString()+"\n");}
        finally{if(downloaded!=null)downloaded.delete();finish(status,result);}
    }
}
