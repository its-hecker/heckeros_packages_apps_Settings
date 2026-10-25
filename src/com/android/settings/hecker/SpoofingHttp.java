/* SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0 */
package com.android.settings.hecker;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Bounded HTTPS access to the public sources used by Evolution X; never logs payloads/keys. */
final class SpoofingHttp {
    static final String ROOTS="https://android.googleapis.com/attestation/root";
    static final String REVOCATION="https://android.googleapis.com/attestation/status?encrypted=0";
    static final String KEYBOX="https://gitea.com/Evolution-X/keybox/raw/branch/main/keybox.xml";
    private static final Set<String> HOSTS=Set.of("developer.android.com","flash.android.com",
            "content-flashstation-pa.googleapis.com","android.googleapis.com","dl.google.com","gitea.com");
    static boolean allowed(URL url){
        if(!"https".equals(url.getProtocol())||url.getUserInfo()!=null||url.getPort()!=-1&&url.getPort()!=443||!HOSTS.contains(url.getHost()))return false;
        if(url.getHost().equals("gitea.com"))return url.getPath().equals("/Evolution-X/keybox/raw/branch/main/keybox.xml");
        if(url.getHost().equals("android.googleapis.com"))return url.getPath().equals("/attestation/root")||url.getPath().equals("/attestation/status");
        if(url.getHost().equals("content-flashstation-pa.googleapis.com"))return url.getPath().equals("/v1/builds");
        return true;
    }
    static String get(String address,int limit) throws IOException{return get(address,limit,Map.of());}
    static String get(String address,int limit,Map<String,String> headers) throws IOException{
        if(limit<1||limit>4*1024*1024)throw new IOException("Invalid response limit");
        URL url=new URL(address);
        for(int redirects=0;redirects<=3;redirects++){
            if(!allowed(url))throw new IOException("Unexpected compatibility source");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(10000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept","application/json,text/plain,text/html,*/*");
            for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
            try{
                int status=connection.getResponseCode();
                if(status==301||status==302||status==303||status==307||status==308){
                    String location=connection.getHeaderField("Location");if(location==null)throw new IOException("Empty source redirect");
                    URL next=new URL(url,location);
                    // Browser API keys are never forwarded across hosts.
                    if(!url.getHost().equals(next.getHost())&&!headers.isEmpty())throw new IOException("Cross-host API redirect refused");
                    url=next;continue;
                }
                if(status!=200)throw new IOException("Compatibility source returned HTTP "+status);
                try(InputStream stream=connection.getInputStream()){
                    byte[] bytes=stream.readNBytes(limit+1);
                    if(bytes.length>limit)throw new IOException("Compatibility response is too large");
                    return new String(bytes,StandardCharsets.UTF_8);
                }
            }finally{connection.disconnect();}
        }
        throw new IOException("Too many compatibility source redirects");
    }
    private SpoofingHttp(){}
}
