package com.rk.tunnel;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final String PREFS = "ohli_tunel_settings";
    private static final String PREF_ORIGIN = "origin";
    private static final String PREF_HOSTNAME = "hostname";
    private static final String PREF_AUTO_BOOT = "auto_boot";

    private EditText originInput;
    private EditText hostnameInput;
    private EditText tokenInput;
    private TextView statusText;
    private TextView publicUrlText;
    private TextView logText;
    private TextView tokenHint;
    private Button startButton;
    private Button stopButton;
    private Switch autoBootSwitch;
    private BroadcastReceiver receiver;
    private boolean receiverRegistered = false;

    private final int ink = Color.rgb(23, 43, 40);
    private final int muted = Color.rgb(103, 119, 114);
    private final int green = Color.rgb(20, 111, 91);
    private final int light = Color.rgb(243, 247, 245);

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(16, 54, 47));
        getWindow().setNavigationBarColor(Color.rgb(16, 54, 47));
        buildUi();
        loadSettings();
        registerStatusReceiver();
        requestNotificationPermission();
        sendCommand(TunnelService.ACTION_QUERY, null);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(light);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(18), dp(18), dp(28));
        if (Build.VERSION.SDK_INT >= 35) {
            scroll.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(android.view.WindowInsets.Type.systemBars());
                root.setPadding(dp(18) + bars.left, dp(18) + bars.top,
                        dp(18) + bars.right, dp(28) + bars.bottom);
                return insets;
            });
        }
        scroll.addView(root);

        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(22), dp(20), dp(22));
        hero.setBackground(rounded(Color.rgb(16, 54, 47), dp(22)));
        root.addView(hero, matchWrap());

        LinearLayout brand = new LinearLayout(this);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        hero.addView(brand, matchWrap());
        TextView mark = text("O", 27, Color.WHITE, true);
        mark.setGravity(Gravity.CENTER);
        mark.setBackground(rounded(Color.rgb(33, 135, 108), dp(14)));
        brand.addView(mark, new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout brandWords = new LinearLayout(this);
        brandWords.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wordsLp = new LinearLayout.LayoutParams(0, -2, 1f);
        wordsLp.leftMargin = dp(12);
        brand.addView(brandWords, wordsLp);
        brandWords.addView(text("OHLI TUNEL", 22, Color.WHITE, true));
        brandWords.addView(text("PERSONAL SERVER BRIDGE", 10, Color.rgb(190, 218, 208), true));

        TextView heroSub = text("Jaga layanan lokal tetap terhubung ke internet melalui Cloudflare Tunnel.", 14, Color.rgb(225, 237, 232), false);
        LinearLayout.LayoutParams heroSubLp = new LinearLayout.LayoutParams(-1, -2);
        heroSubLp.topMargin = dp(18);
        hero.addView(heroSub, heroSubLp);

        LinearLayout productionCard = card(root);
        productionCard.addView(sectionTitle("MODE PRODUKSI — DISARANKAN UNTUK OHLI SOCIAL"));
        productionCard.addView(text("Untuk situs sosial dan chat yang dipakai banyak orang, jalankan backend Node.js dan database di VPS yang selalu aktif. APK ini hanya meneruskan origin dari HP; layar mati, baterai, jaringan, dan pembatasan Android tetap dapat memutus koneksi. Paket VPS produksi tersedia di folder vps dalam ZIP sumber.", 13, ink, false));

        LinearLayout statusCard = card(root);
        LinearLayout statusRow = new LinearLayout(this);
        statusRow.setGravity(Gravity.CENTER_VERTICAL);
        statusCard.addView(statusRow, matchWrap());
        LinearLayout statusTexts = new LinearLayout(this);
        statusTexts.setOrientation(LinearLayout.VERTICAL);
        statusRow.addView(statusTexts, new LinearLayout.LayoutParams(0, -2, 1f));
        statusTexts.addView(text("STATUS LAYANAN", 10, muted, true));
        statusText = text("SIAP DIKONFIGURASI", 17, ink, true);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(5);
        statusTexts.addView(statusText, statusLp);
        TextView dot = text("●", 22, green, true);
        statusRow.addView(dot);
        publicUrlText = text("Alamat publik akan tampil di sini setelah tunnel tersambung.", 13, muted, false);
        publicUrlText.setTextIsSelectable(true);
        LinearLayout.LayoutParams publicLp = new LinearLayout.LayoutParams(-1, -2);
        publicLp.topMargin = dp(13);
        statusCard.addView(publicUrlText, publicLp);

        LinearLayout configCard = card(root);
        configCard.addView(sectionTitle("KONFIGURASI TUNNEL"));
        configCard.addView(label("Tunnel Token Cloudflare"));
        tokenInput = edit("Tempel token Named Tunnel dari Cloudflare", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        configCard.addView(tokenInput, matchWrap());
        tokenHint = text("Token disimpan di penyimpanan privat aplikasi. Jangan bagikan token ini kepada siapa pun.", 12, muted, false);
        LinearLayout.LayoutParams tokenHintLp = new LinearLayout.LayoutParams(-1, -2);
        tokenHintLp.topMargin = dp(7);
        configCard.addView(tokenHint, tokenHintLp);

        configCard.addView(label("Domain publik tetap"));
        hostnameInput = edit("contoh: tunnel.domainkamu.com", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        configCard.addView(hostnameInput, matchWrap());
        TextView hostHint = text("Domain harus sudah diarahkan ke Named Tunnel melalui dashboard Cloudflare.", 12, muted, false);
        LinearLayout.LayoutParams hostHintLp = new LinearLayout.LayoutParams(-1, -2);
        hostHintLp.topMargin = dp(6);
        configCard.addView(hostHint, hostHintLp);

        configCard.addView(label("Alamat server lokal / origin"));
        originInput = edit("http://127.0.0.1:8080/", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        configCard.addView(originInput, matchWrap());
        TextView originHint = text("Samakan alamat ini dengan Service URL pada Public Hostname di dashboard Cloudflare. Jika server ada di perangkat lain, gunakan IP LAN perangkat tersebut.", 12, muted, false);
        LinearLayout.LayoutParams originHintLp = new LinearLayout.LayoutParams(-1, -2);
        originHintLp.topMargin = dp(6);
        configCard.addView(originHint, originHintLp);

        LinearLayout bootRow = new LinearLayout(this);
        bootRow.setGravity(Gravity.CENTER_VERTICAL);
        bootRow.setPadding(0, dp(16), 0, 0);
        configCard.addView(bootRow, matchWrap());
        LinearLayout bootWords = new LinearLayout(this);
        bootWords.setOrientation(LinearLayout.VERTICAL);
        bootRow.addView(bootWords, new LinearLayout.LayoutParams(0, -2, 1f));
        bootWords.addView(text("Mulai otomatis saat perangkat menyala", 14, ink, true));
        bootWords.addView(text("Memulihkan tunnel setelah restart perangkat.", 12, muted, false));
        autoBootSwitch = new Switch(this);
        bootRow.addView(autoBootSwitch);
        autoBootSwitch.setOnCheckedChangeListener((button, checked) -> getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(PREF_AUTO_BOOT, checked).apply());

        Button saveButton = button("SIMPAN KONFIGURASI", Color.rgb(231, 239, 235), green);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(-1, dp(48));
        saveLp.topMargin = dp(18);
        configCard.addView(saveButton, saveLp);
        saveButton.setOnClickListener(v -> saveConfiguration(true));

        LinearLayout actionsCard = card(root);
        actionsCard.addView(sectionTitle("KENDALI SERVER"));
        startButton = button("MULAI OHLI TUNEL", green, Color.WHITE);
        actionsCard.addView(startButton, new LinearLayout.LayoutParams(-1, dp(52)));
        startButton.setOnClickListener(v -> startTunnel());
        stopButton = button("HENTIKAN TUNNEL", Color.rgb(238, 241, 240), Color.rgb(76, 88, 83));
        LinearLayout.LayoutParams stopLp = new LinearLayout.LayoutParams(-1, dp(48));
        stopLp.topMargin = dp(9);
        actionsCard.addView(stopButton, stopLp);
        stopButton.setEnabled(false);
        stopButton.setOnClickListener(v -> sendCommand(TunnelService.ACTION_STOP, null));

        Button copyButton = button("SALIN ALAMAT PUBLIK", Color.rgb(25, 48, 43), Color.WHITE);
        LinearLayout.LayoutParams copyLp = new LinearLayout.LayoutParams(-1, dp(46));
        copyLp.topMargin = dp(9);
        actionsCard.addView(copyButton, copyLp);
        copyButton.setOnClickListener(v -> copyPublicAddress());

        LinearLayout toolsCard = card(root);
        toolsCard.addView(sectionTitle("KEANDALAN PERANGKAT"));
        TextView batteryInfo = text("Agar layanan lebih tahan saat layar mati, izinkan aktivitas latar belakang dan kecualikan OHLI TUNEL dari optimasi baterai. Tunnel memakai CPU dan jaringan selama aktif sehingga baterai dapat lebih cepat habis.", 13, muted, false);
        toolsCard.addView(batteryInfo, matchWrap());
        Button batteryButton = button("ATUR IZIN LATAR BELAKANG", Color.rgb(231, 239, 235), green);
        LinearLayout.LayoutParams batteryLp = new LinearLayout.LayoutParams(-1, dp(46));
        batteryLp.topMargin = dp(13);
        toolsCard.addView(batteryButton, batteryLp);
        batteryButton.setOnClickListener(v -> openBatterySettings());

        Button guideButton = button("PANDUAN SETUP CLOUDFLARE", Color.rgb(25, 48, 43), Color.WHITE);
        LinearLayout.LayoutParams guideLp = new LinearLayout.LayoutParams(-1, dp(46));
        guideLp.topMargin = dp(8);
        toolsCard.addView(guideButton, guideLp);
        guideButton.setOnClickListener(v -> openCloudflareDashboard());

        LinearLayout logCard = card(root);
        logCard.addView(sectionTitle("LOG & DIAGNOSTIK"));
        logText = text("Belum ada aktivitas. Simpan Named Tunnel Token untuk memulai layanan.", 12, muted, false);
        logText.setTextIsSelectable(true);
        logCard.addView(logText, matchWrap());

        TextView footer = text("OHLI TUNEL  •  VERSION 2.1.0  •  ANDROID 8.0+", 10, muted, true);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(18);
        root.addView(footer, footerLp);
        setContentView(scroll);
    }

    private LinearLayout card(LinearLayout parent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(17), dp(17), dp(17));
        card.setBackground(rounded(Color.WHITE, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(13);
        parent.addView(card, lp);
        return card;
    }

    private TextView sectionTitle(String value) {
        TextView tv = text(value, 10, green, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = dp(15);
        tv.setLayoutParams(lp);
        return tv;
    }

    private TextView label(String value) {
        TextView tv = text(value, 13, ink, true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(13);
        lp.bottomMargin = dp(7);
        tv.setLayoutParams(lp);
        return tv;
    }

    private EditText edit(String hint, int type) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setTextSize(14);
        input.setTextColor(ink);
        input.setHintTextColor(Color.rgb(145, 158, 152));
        input.setHint(hint);
        input.setInputType(type);
        input.setPadding(dp(13), dp(11), dp(13), dp(11));
        input.setBackground(rounded(Color.rgb(247, 249, 248), dp(11)));
        return input;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(value);
        tv.setTextSize(size);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return tv;
    }

    private Button button(String value, int background, int foreground) {
        Button b = new Button(this);
        b.setText(value);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setTextColor(foreground);
        b.setAllCaps(false);
        b.setMinHeight(dp(44));
        b.setBackground(rounded(background, dp(12)));
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(color);
        bg.setCornerRadius(radius);
        return bg;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        originInput.setText(prefs.getString(PREF_ORIGIN, "http://127.0.0.1:8080/"));
        hostnameInput.setText(prefs.getString(PREF_HOSTNAME, ""));
        if (!prefs.getString(PREF_HOSTNAME, "").trim().isEmpty()) {
            publicUrlText.setText("Alamat terdaftar: https://" + prefs.getString(PREF_HOSTNAME, "").replaceFirst("^https?://", ""));
        }
        autoBootSwitch.setChecked(prefs.getBoolean(PREF_AUTO_BOOT, false));
        File tokenFile = new File(getFilesDir(), "ohli-tunnel-token");
        if (tokenFile.exists() && tokenFile.length() > 20) {
            tokenHint.setText("Token sudah tersimpan di penyimpanan privat aplikasi. Isi kolom hanya jika ingin menggantinya.");
        }
    }

    private boolean saveConfiguration(boolean showMessage) {
        String origin = originInput.getText().toString().trim();
        String hostname = hostnameInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();
        if (!origin.isEmpty()) {
            Uri uri = Uri.parse(origin);
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) {
                Toast.makeText(this, "Alamat server lokal tidak valid.", Toast.LENGTH_LONG).show();
                return false;
            }
        }
        SharedPreferences.Editor editor = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        editor.putString(PREF_ORIGIN, origin.isEmpty() ? "http://127.0.0.1:8080/" : origin);
        editor.putString(PREF_HOSTNAME, hostname);
        editor.putBoolean(PREF_AUTO_BOOT, autoBootSwitch.isChecked());
        editor.apply();
        if (!token.isEmpty()) {
            try {
                File tokenFile = new File(getFilesDir(), "ohli-tunnel-token");
                try (FileOutputStream out = new FileOutputStream(tokenFile, false)) {
                    out.write(token.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                tokenFile.setReadable(false, false);
                tokenFile.setReadable(true, true);
                tokenFile.setWritable(false, false);
                tokenFile.setWritable(true, true);
                tokenInput.setText("");
                tokenHint.setText("Token berhasil disimpan di penyimpanan privat aplikasi. Jangan bagikan token ini.");
            } catch (Exception e) {
                Toast.makeText(this, "Gagal menyimpan token: " + e.getMessage(), Toast.LENGTH_LONG).show();
                return false;
            }
        }
        if (showMessage) Toast.makeText(this, "Konfigurasi OHLI TUNEL disimpan.", Toast.LENGTH_SHORT).show();
        return true;
    }

    private void startTunnel() {
        if (!saveConfiguration(false)) return;
        File tokenFile = new File(getFilesDir(), "ohli-tunnel-token");
        if (!tokenFile.exists() || tokenFile.length() < 20) {
            Toast.makeText(this, "Masukkan Named Tunnel Token dari dashboard Cloudflare dahulu.", Toast.LENGTH_LONG).show();
            return;
        }
        String origin = originInput.getText().toString().trim();
        sendCommand(TunnelService.ACTION_START, origin);
        statusText.setText("MENYIAPKAN KONEKSI…");
        logText.setText("OHLI TUNEL sedang menjalankan cloudflared. Layanan akan mencoba tersambung ulang jika koneksi terputus.");
    }

    private void copyPublicAddress() {
        String hostname = hostnameInput.getText().toString().trim();
        if (hostname.isEmpty()) {
            Toast.makeText(this, "Isi domain publik yang sudah diatur di Cloudflare.", Toast.LENGTH_LONG).show();
            return;
        }
        String address = hostname.startsWith("http://") || hostname.startsWith("https://") ? hostname : "https://" + hostname;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("OHLI TUNEL public address", address));
        Toast.makeText(this, "Alamat publik disalin.", Toast.LENGTH_SHORT).show();
    }

    private void openBatterySettings() {
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (Build.VERSION.SDK_INT >= 23 && pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else {
                Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                startActivity(intent);
            }
        } catch (Exception e) {
            try { startActivity(new Intent(Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { Toast.makeText(this, "Buka pengaturan baterai perangkat secara manual.", Toast.LENGTH_LONG).show(); }
        }
    }

    private void openCloudflareDashboard() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://one.dash.cloudflare.com/")));
        } catch (Exception e) {
            Toast.makeText(this, "Tidak dapat membuka browser.", Toast.LENGTH_SHORT).show();
        }
    }

    private void sendCommand(String action, String origin) {
        Intent intent = new Intent(this, TunnelService.class);
        intent.setAction(action);
        if (origin != null) intent.putExtra(TunnelService.EXTRA_ORIGIN, origin);
        if (Build.VERSION.SDK_INT >= 26 && TunnelService.ACTION_START.equals(action)) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void registerStatusReceiver() {
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                String status = intent.getStringExtra(TunnelService.EXTRA_STATUS);
                String url = intent.getStringExtra(TunnelService.EXTRA_URL);
                String log = intent.getStringExtra(TunnelService.EXTRA_LOG);
                if (status != null) statusText.setText(status.toUpperCase());
                if (url != null && !url.isEmpty()) publicUrlText.setText(url);
                if (log != null && !log.isEmpty()) logText.setText(log);
                boolean active = intent.getBooleanExtra(TunnelService.EXTRA_ACTIVE, false);
                startButton.setEnabled(!active);
                stopButton.setEnabled(active);
            }
        };
        IntentFilter filter = new IntentFilter(TunnelService.ACTION_STATUS);
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
        receiverRegistered = true;
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered && receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        }
        super.onDestroy();
    }
}
