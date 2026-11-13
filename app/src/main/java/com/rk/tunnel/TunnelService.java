package com.rk.tunnel;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public class TunnelService extends Service {
    public static final String ACTION_START = "com.rk.tunnel.START";
    public static final String ACTION_STOP = "com.rk.tunnel.STOP";
    public static final String ACTION_QUERY = "com.rk.tunnel.QUERY";
    public static final String ACTION_STATUS = "com.rk.tunnel.STATUS";
    public static final String EXTRA_ORIGIN = "origin";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_LOG = "log";
    public static final String EXTRA_ACTIVE = "active";

    private static final String PREFS = "ohli_tunel_settings";
    private static final String PREF_ORIGIN = "origin";
    private static final String PREF_HOSTNAME = "hostname";
    private static final String PREF_RUN_REQUESTED = "run_requested";
    private static final int NOTIFICATION_ID = 7302;
    private static final String CHANNEL_ID = "ohli_tunel_service";

    private final AtomicBoolean loopRunning = new AtomicBoolean(false);
    private volatile Process tunnelProcess;
    private volatile Thread workerThread;
    private volatile boolean stopping = false;
    private volatile boolean active = false;
    private volatile String publicUrl = "";
    private volatile String lastStatus = "Siap";
    private volatile String lastLog = "Simpan Named Tunnel Token Cloudflare untuk memulai.";
    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private SharedPreferences prefs;

    @Override
    public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_QUERY.equals(action)) {
            if (!active && !loopRunning.get() && prefs.getBoolean(PREF_RUN_REQUESTED, false)) {
                stopping = false;
                active = true;
                String configuredHost = prefs.getString(PREF_HOSTNAME, "").trim();
                publicUrl = configuredHost.isEmpty() ? "" : (configuredHost.startsWith("http://") || configuredHost.startsWith("https://") ? configuredHost : "https://" + configuredHost);
                lastStatus = "MEMULIHKAN LAYANAN TUNNEL";
                startForegroundCompat(makeNotification("Memulihkan koneksi…"));
                startWorkerIfNeeded();
            }
            broadcastStatus();
            if (!active && !loopRunning.get()) stopSelf(startId);
            return active ? START_STICKY : START_NOT_STICKY;
        }
        if (ACTION_STOP.equals(action)) {
            stopTunnel(true);
            return START_NOT_STICKY;
        }
        if (ACTION_START.equals(action) || (intent == null && prefs.getBoolean(PREF_RUN_REQUESTED, false))) {
            if (intent != null) {
                String origin = intent.getStringExtra(EXTRA_ORIGIN);
                if (origin != null && !origin.trim().isEmpty()) {
                    prefs.edit().putString(PREF_ORIGIN, origin.trim()).apply();
                }
                prefs.edit().putBoolean(PREF_RUN_REQUESTED, true).apply();
            }
            stopping = false;
            active = true;
            String configuredHost = prefs.getString(PREF_HOSTNAME, "").trim();
            publicUrl = configuredHost.isEmpty() ? "" : (configuredHost.startsWith("http://") || configuredHost.startsWith("https://") ? configuredHost : "https://" + configuredHost);
            lastStatus = "MENYIAPKAN TUNNEL";
            lastLog = "Memulai Named Cloudflare Tunnel. Alamat publik adalah domain yang dikonfigurasi; status koneksi ditampilkan terpisah.";
            startForegroundCompat(makeNotification("Menyiapkan koneksi aman…"));
            broadcastStatus();
            startWorkerIfNeeded();
            return START_STICKY;
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    private void startWorkerIfNeeded() {
        if (loopRunning.compareAndSet(false, true)) {
            workerThread = new Thread(new Runnable() {
                @Override public void run() {
                    runReconnectLoop();
                }
            }, "OhliTunelWorker");
            workerThread.start();
        } else {
            // Recycle the current process so newly saved credentials are picked up.
            Process current = tunnelProcess;
            if (current != null) {
                try { current.destroy(); } catch (Exception ignored) { }
            } else if (workerThread != null) {
                workerThread.interrupt();
            }
        }
    }

    private void runReconnectLoop() {
        long retryDelay = 2000L;
        try {
            acquireLocks();
            while (!stopping && prefs.getBoolean(PREF_RUN_REQUESTED, false)) {
                Process process = null;
                try {
                    File binary = prepareCloudflared();
                    File tokenFile = new File(getFilesDir(), "ohli-tunnel-token");
                    if (!tokenFile.exists() || tokenFile.length() < 20) {
                        throw new IllegalStateException("Token Named Tunnel belum ditemukan. Buka OHLI TUNEL dan simpan token Cloudflare.");
                    }

                    File home = new File(getFilesDir(), "cloudflared-home");
                    if (!home.exists() && !home.mkdirs()) throw new IllegalStateException("Folder konfigurasi tidak dapat dibuat.");
                    File resolver = new File(getFilesDir(), "ohli-resolv.conf");
                    writeText(resolver, "nameserver 1.1.1.1\nnameserver 8.8.8.8\noptions timeout:2 attempts:2\n");
                    File caBundle = new File(getFilesDir(), "ohli-cacert.pem");
                    copyAsset("cacert.pem", caBundle, false);

                    String[] command = new String[]{
                            binary.getAbsolutePath(), "tunnel", "--no-autoupdate", "--protocol", "auto",
                            "run", "--token-file", tokenFile.getAbsolutePath()
                    };
                    ProcessBuilder pb = new ProcessBuilder(command);
                    pb.redirectErrorStream(true);
                    pb.redirectInput(resolver);
                    pb.environment().put("HOME", home.getAbsolutePath());
                    pb.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
                    pb.environment().put("SSL_CERT_FILE", caBundle.getAbsolutePath());
                    pb.environment().put("GODEBUG", "netdns=go");

                    String host = prefs.getString(PREF_HOSTNAME, "").trim();
                    String origin = prefs.getString(PREF_ORIGIN, "http://127.0.0.1:8080/").trim();
                    lastStatus = "MENGHUBUNGKAN KE CLOUDFLARE";
                    lastLog = "Menjalankan Named Tunnel. Domain: " + (host.isEmpty() ? "lihat dashboard Cloudflare" : host)
                            + "\nOrigin yang harus diatur di dashboard: " + origin;
                    updateNotification("Menghubungkan ke Cloudflare…");
                    broadcastStatus();
                    if (stopping) break;

                    process = pb.start();
                    tunnelProcess = process;
                    lastStatus = "PROSES TUNNEL BERJALAN";
                    updateNotification("Proses tunnel berjalan");
                    broadcastStatus();

                    BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                    String line;
                    StringBuilder tail = new StringBuilder();
                    while (!stopping && (line = reader.readLine()) != null) {
                        if (tail.length() > 3600) tail.delete(0, tail.length() - 1800);
                        tail.append(line).append('\n');
                        String lower = line.toLowerCase(Locale.US);
                        if (lower.contains("registered tunnel connection") || lower.contains("connection registered")) {
                            retryDelay = 2000L;
                            lastStatus = "TUNNEL TERHUBUNG";
                            updateNotification(host.isEmpty() ? "Tunnel terhubung" : "Aktif: " + host);
                            broadcastStatus();
                        } else if (lower.contains("error") || lower.contains("failed") || lower.contains("unable")) {
                            lastLog = tail.toString().trim();
                            broadcastStatus();
                        }
                    }
                    int exitCode = -1;
                    try { exitCode = process.waitFor(); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                    tunnelProcess = null;
                    if (!stopping) {
                        lastStatus = "KONEKSI TERPUTUS — MENCOBA ULANG";
                        lastLog = "cloudflared berhenti dengan kode " + exitCode + ".\n" + tail.toString().trim();
                        updateNotification("Koneksi terputus — mencoba ulang");
                        broadcastStatus();
                    }
                } catch (Throwable error) {
                    if (error instanceof ThreadDeath) throw (ThreadDeath) error;
                    if (!stopping) {
                        lastStatus = "KONEKSI GAGAL — MENCOBA ULANG";
                        String message = error.getMessage() == null ? "tanpa detail" : error.getMessage();
                        lastLog = error.getClass().getSimpleName() + ": " + message;
                        updateNotification("Koneksi bermasalah — mencoba ulang");
                        broadcastStatus();
                    }
                } finally {
                    tunnelProcess = null;
                    if (process != null) {
                        try { process.destroy(); } catch (Exception ignored) { }
                    }
                }

                if (stopping || !prefs.getBoolean(PREF_RUN_REQUESTED, false)) break;
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException e) {
                    if (stopping) break;
                }
                retryDelay = Math.min(retryDelay * 2L, 60000L);
            }
        } finally {
            loopRunning.set(false);
            tunnelProcess = null;
            releaseLocks();
            if (stopping || !prefs.getBoolean(PREF_RUN_REQUESTED, false)) {
                active = false;
                if (stopping) lastStatus = "TUNNEL DIMATIKAN";
                broadcastStatus();
                stopForeground(true);
                stopSelf();
            } else if (prefs.getBoolean(PREF_RUN_REQUESTED, false)) {
                // If the worker ends unexpectedly while the service process survives,
                // start a fresh worker. START_STICKY also covers full process reclamation.
                active = true;
                lastStatus = "MEMULIHKAN KONEKSI TUNNEL";
                broadcastStatus();
                startWorkerIfNeeded();
            }
        }
    }

    private File prepareCloudflared() throws Exception {
        // The executable is packaged inside the APK as an ABI-specific native payload.
        // Android 10+ restricts exec() from writable app-home directories; nativeLibraryDir
        // is extracted by PackageManager and is the supported read-only app code location.
        File binary = new File(getApplicationInfo().nativeLibraryDir, "libcloudflared.so");
        if (!binary.exists() || binary.length() < 1024 * 1024) {
            throw new IllegalStateException("Binary cloudflared tidak ditemukan pada nativeLibraryDir. Pastikan APK dibangun melalui GitHub Actions.");
        }
        if (!binary.canExecute()) {
            throw new IllegalStateException("Android tidak memberi izin eksekusi pada binary cloudflared yang tertanam di APK.");
        }
        return binary;
    }

    private void copyAsset(String assetName, File destination, boolean executable) throws Exception {
        InputStream in = getAssets().open(assetName);
        try (FileOutputStream out = new FileOutputStream(destination, false)) {
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
            out.flush();
        } finally {
            try { in.close(); } catch (Exception ignored) { }
        }
        destination.setReadable(true, true);
        if (executable) destination.setExecutable(true, true);
    }

    private void writeText(File file, String value) throws Exception {
        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(value.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
        file.setReadable(true, true);
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && (wakeLock == null || !wakeLock.isHeld())) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OHLI_TUNEL:Cpu");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire();
            }
        } catch (Exception e) {
            lastLog = "Wake lock tidak dapat diaktifkan: " + e.getMessage();
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && (wifiLock == null || !wifiLock.isHeld())) {
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL, "OHLI_TUNEL:Wifi");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
            }
        } catch (Exception e) {
            // Mobile-data tunnel can continue without a Wi-Fi lock.
        }
    }

    private void releaseLocks() {
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) { }
        try { if (wifiLock != null && wifiLock.isHeld()) wifiLock.release(); } catch (Exception ignored) { }
    }

    private void stopTunnel(boolean userInitiated) {
        stopping = true;
        prefs.edit().putBoolean(PREF_RUN_REQUESTED, false).apply();
        active = false;
        Process p = tunnelProcess;
        tunnelProcess = null;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) { }
        }
        Thread t = workerThread;
        if (t != null) t.interrupt();
        if (userInitiated) lastStatus = "TUNNEL DIMATIKAN";
        broadcastStatus();
        stopForeground(true);
        releaseLocks();
        stopSelf();
    }

    private void startForegroundCompat(Notification notification) {
        // API 26-35 compatibility: ordinary foreground service, no API 34 special-use type.
        startForeground(NOTIFICATION_ID, notification);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "OHLI TUNEL", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Status layanan tunnel OHLI TUNEL yang berjalan di latar belakang");
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification makeNotification(String content) {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent openPending = PendingIntent.getActivity(this, 0, open, pendingFlags());
        Intent stop = new Intent(this, TunnelService.class);
        stop.setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(this, 1, stop, pendingFlags());
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_tunnel)
                .setContentTitle("OHLI TUNEL")
                .setContentText(content)
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(R.drawable.ic_stat_tunnel, "Hentikan", stopPending).build());
        return builder.build();
    }

    private int pendingFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private void updateNotification(String content) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, makeNotification(content));
    }

    private void broadcastStatus() {
        Intent intent = new Intent(ACTION_STATUS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_STATUS, lastStatus == null ? "" : lastStatus);
        intent.putExtra(EXTRA_URL, publicUrl == null ? "" : publicUrl);
        intent.putExtra(EXTRA_LOG, lastLog == null ? "" : lastLog);
        intent.putExtra(EXTRA_ACTIVE, active);
        sendBroadcast(intent);
    }

    @Override
    public void onDestroy() {
        stopping = true;
        Process p = tunnelProcess;
        tunnelProcess = null;
        if (p != null) {
            try { p.destroy(); } catch (Exception ignored) { }
        }
        releaseLocks();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
