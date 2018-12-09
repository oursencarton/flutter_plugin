package com.example.infosys;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import static android.content.Context.ACTIVITY_SERVICE;
import android.os.Build;
import android.os.Debug;
import android.os.Environment;
import android.os.Process;
import android.os.StatFs;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

/** InfoSysPlugin */
public class InfoSysPlugin implements MethodCallHandler {
    private final Activity activity;

  /** Plugin registration. */
  public static void registerWith(Registrar registrar) {
    final MethodChannel channel = new MethodChannel(registrar.messenger(), "info_sys");
    channel.setMethodCallHandler(new InfoSysPlugin(registrar.activity()));
  }

    private InfoSysPlugin(Activity activity) {
        this.activity = activity;
    }

  @Override
  public void onMethodCall(MethodCall call, Result result) {
    if (call.method.equals("infoRam")) {
        //On récupère les différentes infos
        Debug.MemoryInfo[] memInfo = infoRam(activity);
        long maxRam = maxRam(activity);
        int totalSize, cache, data;
        double romLibre;
        totalSize = cache = data = 0;
        infoRom(activity);
        romLibre = infoRomLibre();
        //Lecture du fichier data, qui nous sert à récupérer les infos liés à la ROM
        FileInputStream inputStream = null;
        try {
            inputStream = activity.openFileInput("data");
            Scanner scanner = new Scanner(inputStream);
            totalSize = scanner.nextInt();
            cache = scanner.nextInt();
            data = scanner.nextInt();
            scanner.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        String cpu = cpuUse();
        //On créer une HashMap qui nous servira à renvoyer les infos
        Map<String, Object> donnee1 = new HashMap<>();
        donnee1.put("PSS", memInfo[0].nativePss);
        donnee1.put("Native", memInfo[0].nativePrivateDirty);
        donnee1.put("MaxRam", maxRam);
        donnee1.put("Rom", totalSize + data);
        donnee1.put("RomLibre", romLibre);
        donnee1.put("Cache", cache);
        donnee1.put("CPU", cpu);
        //On les renvoit
        result.success(donnee1);
    } //Si la méthode appelé n'est pas "infoRam', result renvoie une erreur
    else {
        result.notImplemented();
    }
  }

  // Appelle diverses fonctions permettant de récupèrer les informations Mémoires de notre Application
  private Debug.MemoryInfo[] infoRam(Activity activity) {
    //Récupère un tableau d'1 pid. Obligation de passer par un tableau puisque getProcessMemoryInfo prend un pid[]
    int pid[] = {android.os.Process.myPid()};
      /*instancier un ActivityManager (comme avec un new build()).
      Sauf que là ça récupère un SystemService correspondant à notre MainActivity.*/
    ActivityManager manager = (ActivityManager) activity.getSystemService(ACTIVITY_SERVICE);
    Debug.MemoryInfo[] mem;
    if (manager != null) {
      /*On récupère le MemoryInfo[] lié au pid de notre application*/
      mem = manager.getProcessMemoryInfo(pid);
    } else {
      mem = null;
    }
    return mem;
  }

    //On récupère ici la RAM totale du Système
    private long maxRam(Activity activity) {

        //cf infoRam()
        ActivityManager manager = (ActivityManager) activity.getSystemService(ACTIVITY_SERVICE);
        //On instancie une classe ActivityManager.MemoryInfo (Ne pas confondre avec le MemoryInfo[] de Debug.)
        ActivityManager.MemoryInfo mem = new ActivityManager.MemoryInfo();
        //on récupère les infos mémoires
        if (manager != null) {
            manager.getMemoryInfo(mem);
        }
        //on garde la RAM totale.
        return mem.totalMem;
    }

    private void infoRom(final Activity activity) {
      /* https://stackoverflow.com/questions/1806286/getting-installed-app-size?noredirect=1&lq=1
      Il n'y a pas de manière de récupèrer la taille d'une application via la simple API android
      jusqu'à la version 26. */
        if (Build.VERSION.SDK_INT < 26) {
            //On instancie un PackageManager
            PackageManager pm = activity.getPackageManager();
            Method getPackageSizeInfo = null;
            try {
                /*On spécifie la méthode que l'on va utiliser.
                 * Basiquement, On va surchager une méthode cachée (getPackageSizeInfo) dans le package IPackageStatsObserver
                 * Cela nous oblige à rajouter des fichiers AIDL afin de pouvoir voir la méthode et l'invoquer.
                 * */
                getPackageSizeInfo = pm.getClass().getMethod(
                        "getPackageSizeInfo", String.class, IPackageStatsObserver.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            try {
                if (getPackageSizeInfo != null) {
                    getPackageSizeInfo.invoke(pm, activity.getApplicationContext().getPackageName(),
                            new IPackageStatsObserver.Stub() {
                                /*La méthode que l'on surchage, onGetStatsCompleted, est un 'void'. On va donc appeller une
                                fonction d'écriture dans un fichier pour récupérer les infos.
                                */
                                @Override
                                public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) {
                                    /* On divise par 1024² pour récupérer les valeurs en MégaOctets*/
                                    int totalSize = (int) pStats.codeSize / 1048576;
                                    int cache = (int) pStats.cacheSize / 1048576;
                                    int data = (int) pStats.dataSize / 1048576;
                                    ecritureFichier(totalSize, cache, data, activity);
                                }
                            });
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
      /*
      Si la version de notre API est supérieur à 26, il existe un moyen plus simple de récupérer ses infos.
       */
        else {
            // On instancie d'abord un objet StorageManager
            StorageManager storageManager = activity.getSystemService(StorageManager.class);
            //On instance ensuite un StorageStatsManager. Il va nous permettre de récuperer les informations voulus
            //Sous la forme d'un StorageStats.
            StorageStatsManager stats = activity.getSystemService(StorageStatsManager.class);
            //On récupére l'UserHandle.
            UserHandle user = Process.myUserHandle();
            UUID uuid = null;
            StorageStats st = null;
            try {
                if (storageManager != null) {
                    //le StorageManager nous permet de récupérer l'UUID (l'identifiant) de notre App.
                    uuid = storageManager.getUuidForPath(activity.getFilesDir());
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (stats != null) {
                try {
                    if (uuid != null) {
                      /* Les éléments récupérer plus haut (UUID et UserHandle) nous permette d'appeler
                     la méthode queryStatsForPackage de la classe StorageStatsManager.
                     On a également besoin du nom du package, mais récupérer ce dernier est trivial.
                       */
                        st = stats.queryStatsForPackage(uuid, activity.getApplicationContext().getPackageName(), user);
                    }
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (st != null) {
                //Si tout a bien marché, on peut désormais appeler écritureFichier pour écrire nos infos.
                ecritureFichier((int) st.getAppBytes(), (int) st.getCacheBytes(), (int) st.getDataBytes(),activity);
            }
        }
    }

    /*
    Fonction servant à écrire dans un fichier les infos lié à la ROM récupérer dans la méthode ci-dessus.
     */
    private void ecritureFichier(int totalSize, int cache, int data,Activity activity) {
        //On convertit nos int en String
        String totalSizeS = Integer.toString(totalSize);
        String cacheS = Integer.toString(cache);
        String dataS = Integer.toString(data);
        FileOutputStream outputStream;
        try {
            //On ouvre un OutputStream,
            outputStream = activity.openFileOutput("data", Context.MODE_PRIVATE);
            //On écrit dedans,
            outputStream.write(totalSizeS.getBytes());
            outputStream.write(" ".getBytes());
            outputStream.write(cacheS.getBytes());
            outputStream.write(" ".getBytes());
            outputStream.write(dataS.getBytes());
            //Et on le ferme.
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private double infoRomLibre() {
        long romLibre;
        //On instancie un objet StatFs.
        //StatFs stats = new StatFs(Environment.getExternalStorageDirectory().getPath());
        StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        //Si l'on est dans une version de l'API supérieur à 18
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            //Il existe une fonction permettant de récupérer directement le nombre d'octet libre
            //On divise par 1024^3 pour récupérer un chiffre en GB.
            romLibre = stats.getAvailableBytes() / (1024*1024);

        } else {
            //Sinon, on récupére la taille d'un block et on multiplie par le nb de block dispos.
            romLibre = stats.getBlockSize() * stats.getAvailableBlocks() / (1024*1024);
        }
        return (double) romLibre;
    }

    private String cpuUse() {
        String cpu = "";
        if (Build.VERSION.SDK_INT < 24) {
            java.lang.Process p;
            int aAppPID = android.os.Process.myPid();
          /*
          https://stackoverflow.com/questions/39796707/is-there-a-way-to-get-application-cpu-usage-with-android-7-nougat?noredirect=1&lq=1
           Suite à des modifications de sécurité à partir de Android 7 (Nougat), Il n'est plus possible d'accéder au dossier proc/stat
           et donc de récupérer les infos. On renverra alors -1 dans ce cas là.
           */
          /* Command TOP :
          -d : temps entre chaque MAJ (en Sec)
          -m : Nb Max de Processus à afficher
          -n : MAJ à montrer avant de terminer
            Command GREP : le filtre nous permettant de ne récupérer que notre processus.
          * */
            String[] cmd = {"sh", "-c", "top -d 0 -m 1000 -n 1 | grep \"" + aAppPID + "\""};
            try {
                p = Runtime.getRuntime().exec(cmd);
                Scanner s = new Scanner(p.getInputStream());
                //On saute les 2e premières valeurs (le PID et le niveau de priorité) qui ne nous intéresse pas
                s.nextInt();
                s.nextInt();
                //On récupére la prochaine valeur (celle qui nous intéresse, le % d'usage du CPU)
                cpu = s.next();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            // et on la retourne.
            return cpu;
            //Sinon on retourne -1
        } else {
            return "-1%";
        }
    }
}