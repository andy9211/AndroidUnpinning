import android.util.Log;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import static de.robv.android.xposed.XposedHelpers.findAndHookMethod;
import static de.robv.android.xposed.XposedHelpers.findClass;

public class Unpinning {

    private static final String SSL_CLASS_NAME = "com.android.org.conscrypt.TrustManagerImpl";
    private static final String SSL_METHOD_NAME = "checkTrustedRecursive";
    private static final Class<?> SSL_RETURN_TYPE = List.class;
    private static final Class<?> SSL_RETURN_PARAM_TYPE = X509Certificate.class;

    @Override
    public void initZygote(IXposedHookZygoteInit.StartupParam startupParam) throws Throwable {
        XposedBridge.log("[.] TrustMeAlready loading...");
        int hookedMethods = 0;

        for (Method method : findClass(SSL_CLASS_NAME, null).getDeclaredMethods()) {
            if (!checkSSLMethod(method)) {
                continue;
            }

            List<Object> params = new ArrayList<>();
            params.addAll(Arrays.asList(method.getParameterTypes()));
            params.add(new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    return new ArrayList<X509Certificate>();
                }
            });

            XposedBridge.log("[+] Hooking method:");
            XposedBridge.log(method.toString());
            findAndHookMethod(SSL_CLASS_NAME, null, SSL_METHOD_NAME, params.toArray());
            hookedMethods++;
        }

        XposedBridge.log(String.format(Locale.ENGLISH, "[+] TrustMeAlready loaded! Hooked %d methods", hookedMethods));
    }

    private boolean checkSSLMethod(Method method) {
        if (!method.getName().equals(SSL_METHOD_NAME)) {
            return false;
        }

        // check return type
        if (!SSL_RETURN_TYPE.isAssignableFrom(method.getReturnType())) {
            return false;
        }

        // check if parameterized return type
        Type returnType = method.getGenericReturnType();
        if (!(returnType instanceof ParameterizedType)) {
            return false;
        }

        // check parameter type
        Type[] args = ((ParameterizedType) returnType).getActualTypeArguments();
        if (args.length != 1 || !(args[0].equals(SSL_RETURN_PARAM_TYPE))) {
            return false;
        }

        return true;
    }
    public static void doing(final XC_LoadPackage.LoadPackageParam loadPackageParam)
    {
        XposedBridge.log("[.] Cert Pinning Bypass/Re-Pinning");
        XposedBridge.log("[+] loading our CA...");
        FileInputStream fileInputStream=null;
        CertificateFactory cf = null;
        try {
            cf=CertificateFactory.getInstance("X.509");
            fileInputStream = new FileInputStream("/sdcard/cert.cer");
        }catch (Exception e)
        {
            XposedBridge.log("[o] "+e.getMessage());
            return ;
        }
        TrustManagerFactory tmp =null;
        try {
            BufferedInputStream bufferedInputStream=new BufferedInputStream(fileInputStream);
            Certificate ca=null;
            ca=cf.generateCertificate(bufferedInputStream);
            bufferedInputStream.close();

            X509Certificate certInfo=(X509Certificate) ca;
            XposedBridge.log("[o] Our CA Info: "+certInfo.getSubjectDN());
            XposedBridge.log("[+] Creating a KeyStore for our CA...");

            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);

            XposedBridge.log("[+] Creating a TrustManager that trusts the CA in our KeyStore...");
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            tmp = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmp.init(keyStore);
        }catch (Exception e)
        {
            XposedBridge.log("[o] "+e.getMessage());
            return;
        }
        final  TrustManagerFactory tmf =tmp;
        XposedBridge.log("[+] Our TrustManager is ready...");
        XposedBridge.log("[+] Hijacking SSLContext methods now...");
        XposedBridge.log("[-] Waiting for the app to invoke SSLContext.init()...");

        findAndHookMethod(SSLContext.class, "init",javax.net.ssl.KeyManager[].class, javax.net.ssl.TrustManager[].class, java.security.SecureRandom.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.args[1]=tmf.getTrustManagers();
                XposedBridge.log("[o] App invoked javax.net.ssl.SSLContext.init...");
                super.beforeHookedMethod(param);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                XposedBridge.log("[+] SSLContext initialized with our custom TrustManager!");
                //SSLContext(param.thisObject,param.args[0],tmf.getTrustManagers(),param.args[2]);
            }
        });

    }
}
