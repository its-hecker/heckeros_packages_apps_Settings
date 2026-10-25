/*
 * SPDX-FileCopyrightText: crDroid Android Project
 * SPDX-FileCopyrightText: Evolution X
 * SPDX-FileCopyrightText: 2026 HeckerOS
 * SPDX-License-Identifier: Apache-2.0
 * Adapted from Evolution X TrickyStore.kt at 84b7ff421f5ce2c0dcdc3f573597f5d4d658f9ba.
 */
package com.android.settings.hecker;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.*;
import java.util.*;
import java.util.regex.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.json.*;

/** Public certificate diagnostics. VALID requires a live revocation response and trusted chains. */
final class KeyboxStatus {
    enum State { MISSING, UNKNOWN, VALID, EXPIRING, EXPIRED, REVOKED, SUSPENDED, INVALID, UNTRUSTED }
    final State state;
    final long checkedAt,expiresAt;
    final int chains;
    KeyboxStatus(State state,long checkedAt,long expiresAt,int chains){this.state=state;this.checkedAt=checkedAt;this.expiresAt=expiresAt;this.chains=chains;}
    private static final Set<String> ROOT_HASHES=Set.of(
        "1ef1a04b8ba58ab94589ac498c8982a783f24ea7307e0159a0c3a73b377d87cc",
        "cedb1cb6dc896ae5ec797348bce9286753c2b38ee71ce0fbe34a9a1248800dfc",
        "6d9db4ce6c5c0b293166d08986e05774a8776ceb525d9e4329520de12ba4bcc0");
    static String xml(String raw){
        if(raw==null||raw.isBlank())return "";
        if(raw.getBytes(StandardCharsets.UTF_8).length>256*1024)throw new IllegalArgumentException("Keybox is too large");
        String value=raw.trim();if(!value.startsWith("<"))value=new String(Base64.getMimeDecoder().decode(value),StandardCharsets.UTF_8).trim();
        String upper=value.toUpperCase(Locale.ROOT);
        if(value.getBytes(StandardCharsets.UTF_8).length>256*1024||!value.startsWith("<")||upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new IllegalArgumentException("Invalid keybox XML");
        return value;
    }
    static List<List<X509Certificate>> chains(String xml) throws Exception{
        DocumentBuilderFactory factory=DocumentBuilderFactory.newInstance();factory.setExpandEntityReferences(false);
        try{factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);}catch(ParserConfigurationException unsupported){}
        DocumentBuilder builder=factory.newDocumentBuilder();
        builder.setEntityResolver((publicId,systemId)->{throw new org.xml.sax.SAXException("External entities are disabled");});
        Document document=builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList keys=document.getElementsByTagName("Key");if(keys.getLength()==0||keys.getLength()>16)throw new IllegalArgumentException("No supported keybox chains");
        List<List<X509Certificate>> result=new ArrayList<>();
        for(int i=0;i<keys.getLength();i++){
            Element key=(Element)keys.item(i);NodeList certs=key.getElementsByTagName("Certificate");
            if(certs.getLength()<2||certs.getLength()>10)throw new IllegalArgumentException("Invalid certificate chain length");
            List<X509Certificate> chain=new ArrayList<>();
            for(int j=0;j<certs.getLength();j++)chain.add(certificate(certs.item(j).getTextContent()));
            result.add(chain);
        }
        return result;
    }
    private static X509Certificate certificate(String content) throws Exception{
        String pem=content.trim();
        byte[] bytes=pem.contains("BEGIN CERTIFICATE")?pem.getBytes(StandardCharsets.US_ASCII):Base64.getMimeDecoder().decode(pem);
        return (X509Certificate)CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(bytes));
    }
    private static List<X509Certificate> roots(String raw) throws Exception{
        List<X509Certificate> result=new ArrayList<>();
        if(raw.trim().startsWith("[")){
            // Decode JSON first: PEM base64 can contain escaped slashes as well as newlines.
            JSONArray array=new JSONArray(raw);
            for(int i=0;i<array.length()&&i<20;i++)result.add(certificate(array.getString(i)));
            return result;
        }
        Matcher certificates=Pattern.compile("-----BEGIN CERTIFICATE-----[\\s\\S]*?-----END CERTIFICATE-----").matcher(raw);
        while(certificates.find()&&result.size()<20)result.add(certificate(certificates.group()));
        return result;
    }
    static KeyboxStatus check(String raw,boolean network){
        if(raw==null||raw.isBlank())return new KeyboxStatus(State.MISSING,0,0,0);
        List<List<X509Certificate>> chains;
        try{chains=chains(xml(raw));}catch(Exception bad){return new KeyboxStatus(State.INVALID,0,0,0);}
        String revocation=null,anchors=null;
        if(network){
            try{anchors=SpoofingHttp.get(SpoofingHttp.ROOTS,256*1024);}catch(IOException unavailable){}
            try{revocation=SpoofingHttp.get(SpoofingHttp.REVOCATION,4*1024*1024);}catch(IOException unavailable){}
        }
        return evaluate(chains,anchors,revocation,System.currentTimeMillis());
    }
    static KeyboxStatus evaluate(List<List<X509Certificate>> chains,String anchors,String revocation,long now){
        long expiry=Long.MAX_VALUE;List<X509Certificate> trusted=new ArrayList<>();
        try{if(anchors!=null)trusted=roots(anchors);}catch(Exception unavailable){}
        Set<String> serials=new HashSet<>();
        try{
            if(chains.isEmpty())return new KeyboxStatus(State.INVALID,now,0,0);
            for(List<X509Certificate> chain:chains){
                if(chain.size()<2)return new KeyboxStatus(State.INVALID,now,0,chains.size());
                for(int i=0;i<chain.size();i++){
                    X509Certificate cert=chain.get(i);cert.checkValidity(new Date(now));
                    expiry=Math.min(expiry,cert.getNotAfter().getTime());
                    cert.verify(chain.get(Math.min(i+1,chain.size()-1)).getPublicKey());
                    serials.add(cert.getSerialNumber().toString(16));serials.add(cert.getSerialNumber().toString());
                }
                X509Certificate root=chain.get(chain.size()-1);boolean matches=false;
                if(trusted.isEmpty())matches=ROOT_HASHES.contains(hash(root.getEncoded()));
                else for(X509Certificate anchor:trusted)matches|=MessageDigest.isEqual(root.getPublicKey().getEncoded(),anchor.getPublicKey().getEncoded());
                if(!matches)return new KeyboxStatus(State.UNTRUSTED,now,expiry,chains.size());
            }
            if(revocation==null)return new KeyboxStatus(State.UNKNOWN,now,expiry,chains.size());
            JSONObject response=new JSONObject(revocation);JSONObject entries=response.optJSONObject("entries");
            if(entries==null)return new KeyboxStatus(State.UNKNOWN,now,expiry,chains.size());
            for(String serial:serials){JSONObject entry=entries.optJSONObject(serial);if(entry==null)continue;
                if("REVOKED".equalsIgnoreCase(entry.optString("status")))return new KeyboxStatus(State.REVOKED,now,expiry,chains.size());
                if("SUSPENDED".equalsIgnoreCase(entry.optString("status")))return new KeyboxStatus(State.SUSPENDED,now,expiry,chains.size());
                return new KeyboxStatus(State.UNKNOWN,now,expiry,chains.size());
            }
            return new KeyboxStatus(expiry-now<14L*86400000?State.EXPIRING:State.VALID,now,expiry,chains.size());
        }catch(CertificateExpiredException expired){return new KeyboxStatus(State.EXPIRED,now,expiry,chains.size());}
        catch(Exception invalid){return new KeyboxStatus(State.INVALID,now,expiry,chains.size());}
    }
    static String hash(byte[] bytes){
        try{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(Locale.ROOT,"%02x",b&255));return out.toString();}
        catch(GeneralSecurityException impossible){throw new IllegalStateException(impossible);}
    }
}
