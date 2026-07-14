package com.mytvlauncher.ui;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.role.RoleManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RenderEffect;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import com.mytvlauncher.R;
import com.mytvlauncher.service.HomeAccessibilityService;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {
    private static final int REQ_IMAGE = 40;
    private static final int REQ_VIDEO = 41;
    private static final int REQ_HOME_ROLE = 42;
    private static final int CURRENT_VERSION_CODE = 60;

    private SharedPreferences prefs;
    private GridLayout appsGrid;
    private FrameLayout launcherRoot;
    private ScrollView homeScroll;
    private View searchPill;
    private VideoView currentVideoBackground;
    private FrameLayout folderOverlay;
    private Dialog folderDialog;
    private View folderLastFocus;
    private boolean showingSettings;
    private boolean showingWeatherDetails;
    private String movingPackage;
    private String movingFolderName;
    private String movingFolderPackage;
    private String movingFolderOriginalOrder;
    private String pendingFolderFocusPackage;
    private String focusAfterLoadPackage;
    private int pendingSettingsScrollY = -1;
    private String pendingSettingsFocusTitle;
    private String pendingSettingsFocusSection;
    private List<AppEntry> cachedLaunchableApps;
    private String cachedHiddenKey = "";
    private long cachedLaunchableAppsAt;

    private TextView weatherCityView;
    private TextView weatherTempView;
    private ImageView weatherIconView;
    private long lastWeatherFetchTime;
    private boolean weatherFetching;
    private boolean showingAppDrawer;
    private FrameLayout appDrawerOverlay;
    private LinearLayout dockRow;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("launcher_settings", MODE_PRIVATE);
        applyUpgradeFixes();
        enterImmersiveMode();
        showHome();
    }

    @Override
    protected void onDestroy() {
        stopVideoBackground();
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        stopVideoBackground();
        super.onPause();
    }

    private void applyUpgradeFixes() {
        int lastVersion = prefs.getInt("last_version_code", 0);
        if (lastVersion < 15) {
            prefs.edit()
                    .putBoolean("minimal_status", false)
                    .putBoolean("hide_featured", false)
                    .putBoolean("override_current_launcher", true)
                    .putBoolean("start_on_boot", true)
                    .putBoolean("auto_open_on_wake", true)
                    .putInt("last_version_code", CURRENT_VERSION_CODE)
                    .apply();
            return;
        }
        if (lastVersion < CURRENT_VERSION_CODE) {
            SharedPreferences.Editor editor = prefs.edit().putInt("last_version_code", CURRENT_VERSION_CODE);
            if (lastVersion < 18) {
                editor.putBoolean("override_current_launcher", true)
                        .putBoolean("start_on_boot", true)
                        .putBoolean("auto_open_on_wake", true);
            }
            if (lastVersion < 36) {
                editor.putBoolean("override_current_launcher", true)
                        .putBoolean("start_on_boot", true)
                        .putBoolean("auto_open_on_wake", true);
            }
            editor.apply();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        updateServicePill();
        if (!showingSettings && !showingWeatherDetails && !showingAppDrawer && weatherCityView != null) {
            fetchWeather();
        }
        if (dockRow != null && !showingSettings && !showingWeatherDetails && !showingAppDrawer) {
            loadDockApps();
        }
    }

    @Override
    public void onBackPressed() {
        if (!TextUtils.isEmpty(movingFolderName) && !TextUtils.isEmpty(movingFolderPackage)) {
            cancelFolderMove(true);
            return;
        }
        if (folderOverlay != null) {
            dismissFolderOverlay();
            return;
        }
        if (showingAppDrawer) {
            closeAppDrawer();
            return;
        }
        if (showingSettings) {
            showHome();
            return;
        }
        if (showingWeatherDetails) {
            showHome();
            return;
        }
        if (prefs.getBoolean("override_back_exit", true)) {
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU || keyCode == KeyEvent.KEYCODE_SETTINGS) {
            showSettings("主题模式");
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_HOME_ROLE) {
            toast(isDefaultHomeLauncher() ? "星河桌面已设为默认桌面。" : "系统未授予桌面权限。");
            showSettings("控制");
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try {
            getContentResolver().takePersistableUriPermission(uri, flags);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_IMAGE) {
            prefs.edit().putString("bg_mode", "image").putString("bg_uri", uri.toString()).apply();
            toast("已选择自定义图片");
            showSettings("主题模式");
        } else if (requestCode == REQ_VIDEO) {
            prefs.edit().putString("bg_mode", "video").putString("bg_video_uri", uri.toString()).apply();
            toast("已选择自定义视频");
            showSettings("主题模式");
        }
    }

    private void showHome() {
        showingSettings = false;
        showingWeatherDetails = false;
        showingAppDrawer = false;
        currentVideoBackground = null;
        FrameLayout root = new FrameLayout(this);
        launcherRoot = root;
        root.setClipChildren(false);
        root.setClipToPadding(false);
        addBackground(root);

        View topWash = new View(this);
        topWash.setBackgroundResource(R.drawable.home_scrim);
        root.addView(topWash, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        if (!prefs.getBoolean("minimal_status", false)) {
            content.addView(buildHomeTopBar(), new LinearLayout.LayoutParams(-1, dp(64)));
        }

        FrameLayout mainArea = new FrameLayout(this);
        mainArea.setClipChildren(false);
        mainArea.setClipToPadding(false);
        LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(-1, 0);
        mainLp.weight = 1;
        content.addView(mainArea, mainLp);

        LinearLayout heroArea = new LinearLayout(this);
        heroArea.setOrientation(LinearLayout.HORIZONTAL);
        heroArea.setGravity(Gravity.TOP | Gravity.START);
        heroArea.setPadding(dp(54), dp(18), dp(54), 0);
        heroArea.setClipChildren(false);
        heroArea.setClipToPadding(false);
        mainArea.addView(heroArea, new FrameLayout.LayoutParams(-1, -2));

        if (!prefs.getBoolean("hide_featured", false)) {
            heroArea.addView(buildWeatherCard(), new LinearLayout.LayoutParams(dp(150), dp(140)));
        }

        LinearLayout bottomDockArea = new LinearLayout(this);
        bottomDockArea.setOrientation(LinearLayout.VERTICAL);
        bottomDockArea.setGravity(Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bottomDockArea.setClipChildren(false);
        bottomDockArea.setClipToPadding(false);
        mainArea.addView(bottomDockArea, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        dockRow = new LinearLayout(this);
        dockRow.setOrientation(LinearLayout.HORIZONTAL);
        dockRow.setGravity(Gravity.CENTER);
        dockRow.setPadding(dp(16), dp(8), dp(16), dp(10));
        dockRow.setClipChildren(false);
        dockRow.setClipToPadding(false);
        dockRow.setBackgroundColor(Color.TRANSPARENT);
        bottomDockArea.addView(dockRow, new LinearLayout.LayoutParams(-2, -2));

        loadDockApps();
        setContentView(root);
    }

    private GradientDrawable glassDockBackground() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(180, 255, 255, 255),
                        Color.argb(120, 200, 210, 220)
                });
        bg.setCornerRadius(dp(24));
        bg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        return bg;
    }

    private void loadDockApps() {
        if (dockRow == null) return;
        PackageManager pm = getPackageManager();
        List<AppEntry> allApps = queryLaunchableApps(pm);
        dockRow.removeAllViews();
        Set<String> dockApps = prefs.getStringSet("dock_apps", new HashSet<>());
        List<AppEntry> dockEntries = new ArrayList<>();

        if (dockApps.isEmpty()) {
            for (AppEntry app : allApps) {
                if (dockEntries.size() >= 5) break;
                dockEntries.add(app);
            }
        } else {
            List<String> dockList = new ArrayList<>(dockApps);
            for (String pkg : dockList) {
                for (AppEntry app : allApps) {
                    if (TextUtils.equals(app.packageName, pkg)) {
                        dockEntries.add(app);
                        break;
                    }
                }
            }
            while (dockEntries.size() < 5 && allApps.size() > dockEntries.size()) {
                for (AppEntry app : allApps) {
                    if (dockEntries.size() >= 5) break;
                    boolean found = false;
                    for (AppEntry de : dockEntries) {
                        if (TextUtils.equals(de.packageName, app.packageName)) {
                            found = true;
                            break;
                        }
                    }
                    if (!found && !app.isFolder) dockEntries.add(app);
                }
            }
        }

        int dockSize = Math.min(5, dockEntries.size());
        int cellWidth = dp(140);
        for (int i = 0; i < dockSize; i++) {
            AppEntry entry = dockEntries.get(i);
            View tile = createAppTile(pm, entry, cellWidth);
            LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(cellWidth, -2);
            tileLp.leftMargin = dp(6);
            tileLp.rightMargin = dp(6);
            dockRow.addView(tile, tileLp);

            if (tile instanceof LinearLayout && ((LinearLayout) tile).getChildCount() > 0) {
                View card = ((LinearLayout) tile).getChildAt(0);
                card.setOnKeyListener((v, keyCode, event) -> {
                    if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        openAppDrawer();
                        return true;
                    }
                    return false;
                });
            }
        }
    }

    private void openAppDrawer() {
        if (showingAppDrawer) return;
        showingAppDrawer = true;

        appDrawerOverlay = new FrameLayout(this);
        appDrawerOverlay.setBackgroundColor(Color.argb(0, 0, 0, 0));
        appDrawerOverlay.setClipChildren(false);
        appDrawerOverlay.setClipToPadding(false);

        FrameLayout drawerContainer = new FrameLayout(this);
        drawerContainer.setClipChildren(false);
        drawerContainer.setClipToPadding(false);
        FrameLayout.LayoutParams drawerLp = new FrameLayout.LayoutParams(-1, -2);
        drawerLp.gravity = Gravity.BOTTOM;
        appDrawerOverlay.addView(drawerContainer, drawerLp);

        LinearLayout drawer = new LinearLayout(this);
        drawer.setOrientation(LinearLayout.VERTICAL);
        drawer.setPadding(dp(40), dp(18), dp(40), dp(32));
        drawer.setClipChildren(false);
        drawer.setClipToPadding(false);
        drawer.setBackground(drawerBackground());
        drawerContainer.addView(drawer, new FrameLayout.LayoutParams(-1, -2));

        TextView drawerTitle = new TextView(this);
        drawerTitle.setText("所有应用");
        drawerTitle.setTextColor(Color.WHITE);
        drawerTitle.setTextSize(22);
        drawerTitle.setTypeface(Typeface.DEFAULT_BOLD);
        drawerTitle.setPadding(0, 0, 0, dp(10));
        drawer.addView(drawerTitle, new LinearLayout.LayoutParams(-1, -2));

        ScrollView drawerScroll = new ScrollView(this);
        drawerScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        drawerScroll.setClipChildren(false);
        drawerScroll.setClipToPadding(false);
        drawer.addView(drawerScroll, new LinearLayout.LayoutParams(-1, dp(380)));

        GridLayout drawerGrid = new GridLayout(this);
        drawerGrid.setColumnCount(6);
        drawerGrid.setClipChildren(false);
        drawerGrid.setClipToPadding(false);
        drawerScroll.addView(drawerGrid, new ScrollView.LayoutParams(-1, -2));

        PackageManager pm = getPackageManager();
        List<AppEntry> allApps = buildDisplayEntries(queryLaunchableApps(pm));
        int cellWidth = dp(150);
        List<View> tileViews = new ArrayList<>();
        for (AppEntry entry : allApps) {
            View tile = createAppTile(pm, entry, cellWidth);
            GridLayout.LayoutParams gridLp = new GridLayout.LayoutParams();
            gridLp.width = cellWidth;
            gridLp.height = dp(prefs.getBoolean("hide_app_titles", false) ? 116 : 150);
            gridLp.setMargins(dp(4), dp(6), dp(4), dp(6));
            tile.setLayoutParams(gridLp);
            tile.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                        int idx = drawerGrid.indexOfChild(v);
                        if (idx < 6) {
                            closeAppDrawer();
                            return true;
                        }
                    }
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        closeAppDrawer();
                        return true;
                    }
                }
                return false;
            });
            drawerGrid.addView(tile);
            tileViews.add(tile);
        }

        appDrawerOverlay.setOnClickListener(v -> closeAppDrawer());
        launcherRoot.addView(appDrawerOverlay, new FrameLayout.LayoutParams(-1, -1));

        drawer.setTranslationY(dp(600));
        drawer.setAlpha(0f);
        drawerTitle.setAlpha(0f);
        for (View tile : tileViews) {
            tile.setAlpha(0f);
            tile.setTranslationY(dp(120));
            tile.setScaleY(0.6f);
            tile.setPivotY(1f);
        }

        appDrawerOverlay.setBackgroundColor(Color.argb(0, 0, 0, 0));
        drawer.animate()
                .translationY(0)
                .alpha(1f)
                .setDuration(340)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(0.9f))
                .start();
        drawerTitle.animate()
                .setStartDelay(60)
                .alpha(1f)
                .setDuration(200)
                .start();

        for (int i = 0; i < tileViews.size(); i++) {
            View tile = tileViews.get(i);
            int row = i / 6;
            int col = i % 6;
            int delay = 70 + row * 35 + (5 - col) * 8;
            tile.animate()
                    .setStartDelay(delay)
                    .alpha(1f)
                    .translationY(0)
                    .scaleY(1f)
                    .setDuration(280)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator(1.3f))
                    .start();
        }

        appDrawerOverlay.animate()
                .setStartDelay(20)
                .setDuration(240)
                .setUpdateListener(animation -> {
                    float frac = animation.getAnimatedFraction();
                    appDrawerOverlay.setBackgroundColor(Color.argb((int) (frac * 130), 0, 0, 0));
                })
                .start();

        drawer.postDelayed(() -> {
            if (drawerGrid.getChildCount() > 0) {
                drawerGrid.getChildAt(0).requestFocus();
            }
        }, 260);
    }

    private void closeAppDrawer() {
        if (!showingAppDrawer || appDrawerOverlay == null) return;
        showingAppDrawer = false;

        View drawerContainer = appDrawerOverlay.getChildAt(0);
        View drawer = null;
        if (drawerContainer instanceof FrameLayout && ((FrameLayout) drawerContainer).getChildCount() > 0) {
            drawer = ((FrameLayout) drawerContainer).getChildAt(0);
        }

        if (drawer instanceof LinearLayout) {
            LinearLayout drawerLl = (LinearLayout) drawer;
            if (drawerLl.getChildCount() >= 2) {
                View scroll = drawerLl.getChildAt(1);
                if (scroll instanceof ScrollView && ((ScrollView) scroll).getChildCount() > 0) {
                    View grid = ((ScrollView) scroll).getChildAt(0);
                    if (grid instanceof GridLayout) {
                        GridLayout gridLayout = (GridLayout) grid;
                        for (int i = 0; i < gridLayout.getChildCount(); i++) {
                            View tile = gridLayout.getChildAt(i);
                            int row = i / 6;
                            int col = i % 6;
                            int delay = row * 20 + (5 - col) * 5;
                            tile.animate()
                                    .setStartDelay(delay)
                                    .alpha(0f)
                                    .translationY(dp(60))
                                    .scaleY(0.7f)
                                    .setDuration(180)
                                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.2f))
                                    .start();
                        }
                    }
                }
            }
            drawerLl.animate()
                    .setStartDelay(80)
                    .translationY(dp(500))
                    .alpha(0f)
                    .setDuration(240)
                    .setInterpolator(new android.view.animation.AccelerateInterpolator(1.1f))
                    .start();
        }

        appDrawerOverlay.animate()
                .setDuration(220)
                .setUpdateListener(animation -> {
                    float frac = 1f - animation.getAnimatedFraction();
                    appDrawerOverlay.setBackgroundColor(Color.argb((int) (frac * 130), 0, 0, 0));
                })
                .withEndAction(() -> {
                    if (launcherRoot != null && appDrawerOverlay != null) {
                        launcherRoot.removeView(appDrawerOverlay);
                        appDrawerOverlay = null;
                    }
                    if (dockRow != null && dockRow.getChildCount() > 0) {
                        LinearLayout firstTile = (LinearLayout) dockRow.getChildAt(0);
                        if (firstTile != null && firstTile.getChildCount() > 0) {
                            firstTile.getChildAt(0).requestFocus();
                        }
                    }
                })
                .start();
    }

    private GradientDrawable drawerBackground() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(230, 40, 45, 55),
                        Color.argb(245, 25, 28, 36)
                });
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        return bg;
    }

    private void addBackground(FrameLayout root) {
        String mode = prefs.getString("bg_mode", "light");
        if ("dark".equals(mode)) {
            root.setBackgroundResource(R.drawable.bg_launcher);
            return;
        }
        if ("gradient".equals(mode)) {
            root.setBackground(currentGradientDrawable());
            return;
        }
        if ("image".equals(mode)) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            try {
                image.setImageURI(Uri.parse(prefs.getString("bg_uri", "")));
            } catch (Exception e) {
                image.setImageResource(R.drawable.wallpaper_mountain);
            }
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if ("url".equals(mode)) {
            ImageView image = new ImageView(this);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            File f = new File(getFilesDir(), "url_wallpaper.img");
            if (f.exists()) image.setImageURI(Uri.fromFile(f));
            else image.setImageResource(R.drawable.wallpaper_mountain);
            root.addView(image, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        if ("video".equals(mode)) {
            VideoView video = new VideoView(this);
            try {
                currentVideoBackground = video;
                video.setVideoURI(Uri.parse(prefs.getString("bg_video_uri", "")));
                video.setOnPreparedListener(mp -> {
                    mp.setLooping(true);
                    mp.setVolume(0f, 0f);
                    mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING);
                    video.start();
                });
                root.addView(video, new FrameLayout.LayoutParams(-1, -1));
                return;
            } catch (Exception ignored) {
            }
        }
        ImageView wallpaper = new ImageView(this);
        wallpaper.setImageResource(R.drawable.wallpaper_mountain);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallpaper, new FrameLayout.LayoutParams(-1, -1));
    }

    private void stopVideoBackground() {
        if (currentVideoBackground == null) return;
        try {
            currentVideoBackground.stopPlayback();
        } catch (Exception ignored) {
        }
        currentVideoBackground = null;
    }

    private void startExternalActivity(Intent intent) {
        if (intent == null) return;
        stopVideoBackground();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private View buildHomeTopBar() {
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView filler = new TextView(this);
        top.addView(filler, new LinearLayout.LayoutParams(0, 1, 1));

        View googleSettingsButton = imageIconChip(R.drawable.ic_google_settings, 18);
        googleSettingsButton.setOnClickListener(v -> openGoogleSettings());
        LinearLayout.LayoutParams googleLp = new LinearLayout.LayoutParams(dp(52), dp(38));
        googleLp.rightMargin = dp(10);
        top.addView(googleSettingsButton, googleLp);

        View mytvSettings = imageIconChip(R.drawable.ic_mytv_settings, 18);
        mytvSettings.setOnClickListener(v -> showSettings("主题模式"));
        LinearLayout.LayoutParams settingsLp = new LinearLayout.LayoutParams(dp(60), dp(38));
        settingsLp.rightMargin = dp(10);
        top.addView(mytvSettings, settingsLp);

        return top;
    }

    private View buildWeatherCard() {
        FrameLayout shell = new FrameLayout(this);
        shell.setFocusable(true);
        shell.setClickable(true);
        shell.setBackgroundResource(R.drawable.weather_card);
        shell.setClipToOutline(false);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));
        shell.addView(card, new FrameLayout.LayoutParams(-1, -1));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour(prefs.getBoolean("use_24h", true) ? "HH:mm" : "h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(20);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setIncludeFontPadding(false);
        clock.setGravity(Gravity.CENTER);
        card.addView(clock, new LinearLayout.LayoutParams(-1, -2));

        weatherIconView = new ImageView(this);
        weatherIconView.setImageResource(R.drawable.ic_weather_clouds);
        weatherIconView.setScaleType(ImageView.ScaleType.FIT_XY);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(96), dp(48));
        iconLp.topMargin = dp(2);
        iconLp.bottomMargin = dp(2);
        card.addView(weatherIconView, iconLp);

        weatherTempView = new TextView(this);
        weatherTempView.setText("--°");
        weatherTempView.setTextColor(Color.WHITE);
        weatherTempView.setTextSize(18);
        weatherTempView.setTypeface(Typeface.DEFAULT_BOLD);
        weatherTempView.setSingleLine(true);
        weatherTempView.setGravity(Gravity.CENTER);
        card.addView(weatherTempView, new LinearLayout.LayoutParams(-1, -2));

        weatherCityView = new TextView(this);
        weatherCityView.setText("定位中...");
        weatherCityView.setTextColor(Color.argb(200, 255, 255, 255));
        weatherCityView.setTextSize(12);
        weatherCityView.setSingleLine(true);
        weatherCityView.setEllipsize(TextUtils.TruncateAt.END);
        weatherCityView.setGravity(Gravity.CENTER);
        card.addView(weatherCityView, new LinearLayout.LayoutParams(-1, -2));

        shell.setOnClickListener(v -> showWeatherDetails());
        shell.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.06f));

        fetchWeather();
        return shell;
    }

    private void showWeatherDetails() {
        showingWeatherDetails = true;
        showingSettings = false;

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);
        root.setClipToPadding(false);

        ImageView weatherBackground = new ImageView(this);
        weatherBackground.setImageResource(R.drawable.weather_scene);
        weatherBackground.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(weatherBackground, new FrameLayout.LayoutParams(-1, -1));

        View weatherWash = new View(this);
        weatherWash.setBackground(weatherDetailWash());
        root.addView(weatherWash, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(64), dp(42), dp(64), dp(34));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(top, new LinearLayout.LayoutParams(-1, dp(58)));

        TextView back = chip("返回", false);
        back.setOnClickListener(v -> showHome());
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(dp(112), dp(46));
        backLp.rightMargin = dp(18);
        top.addView(back, backLp);

        TextView title = new TextView(this);
        title.setText("天气");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        top.addView(title, new LinearLayout.LayoutParams(0, -1, 1));

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour(prefs.getBoolean("use_24h", true) ? "HH:mm" : "h:mm");
        clock.setGravity(Gravity.CENTER);
        clock.setTextColor(Color.WHITE);
        clock.setTextSize(24);
        clock.setTypeface(Typeface.DEFAULT_BOLD);
        clock.setBackgroundResource(R.drawable.glass_chip);
        top.addView(clock, new LinearLayout.LayoutParams(dp(118), dp(46)));

        FrameLayout hero = new FrameLayout(this);
        hero.setClipChildren(false);
        hero.setClipToPadding(false);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(-1, 0, 1);
        heroLp.topMargin = dp(18);
        page.addView(hero, heroLp);

        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        summary.setGravity(Gravity.LEFT);
        summary.setPadding(dp(46), dp(24), dp(46), dp(166));
        summary.setClipChildren(false);
        summary.setClipToPadding(false);
        hero.addView(summary, new FrameLayout.LayoutParams(-1, -1));

        String savedCity = prefs.getString("weather_city", "北京");
        int savedTemp = prefs.getInt("weather_temp", 25);
        int savedCode = prefs.getInt("weather_code", 3);
        String conditionText = wmoCodeToCondition(savedCode);

        TextView city = new TextView(this);
        city.setText(savedCity);
        city.setTextColor(Color.argb(220, 255, 255, 255));
        city.setTextSize(19);
        city.setIncludeFontPadding(false);
        city.setShadowLayer(dp(2), 0, dp(1), Color.argb(120, 0, 0, 0));
        summary.addView(city, new LinearLayout.LayoutParams(-1, dp(30)));

        LinearLayout headline = new LinearLayout(this);
        headline.setGravity(Gravity.CENTER_VERTICAL);
        headline.setClipChildren(false);
        headline.setClipToPadding(false);
        summary.addView(headline, new LinearLayout.LayoutParams(-1, dp(118)));

        TextView temp = new TextView(this);
        temp.setText(savedTemp + "°C");
        temp.setTextColor(Color.WHITE);
        temp.setTextSize(78);
        temp.setTypeface(Typeface.DEFAULT_BOLD);
        temp.setIncludeFontPadding(false);
        temp.setShadowLayer(dp(4), 0, dp(3), Color.argb(120, 0, 0, 0));
        headline.addView(temp, new LinearLayout.LayoutParams(-2, -1));

        ImageView detailIcon = new ImageView(this);
        detailIcon.setImageResource(wmoCodeToIcon(savedCode));
        detailIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams detailIconLp = new LinearLayout.LayoutParams(dp(118), dp(118));
        detailIconLp.leftMargin = dp(28);
        detailIconLp.rightMargin = dp(18);
        headline.addView(detailIcon, detailIconLp);

        TextView condition = new TextView(this);
        condition.setText(conditionText);
        condition.setTextColor(Color.WHITE);
        condition.setTextSize(30);
        condition.setTypeface(Typeface.DEFAULT_BOLD);
        condition.setSingleLine(true);
        condition.setIncludeFontPadding(false);
        condition.setShadowLayer(dp(3), 0, dp(2), Color.argb(120, 0, 0, 0));
        headline.addView(condition, new LinearLayout.LayoutParams(0, -1, 1));

        LinearLayout metricLine1 = new LinearLayout(this);
        metricLine1.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(metricLine1, new LinearLayout.LayoutParams(dp(620), dp(36)));

        String humidityStr = "湿度 : --%";
        String windStr = "风速 : -- km/h";
        String feelsStr = "体感 : --°C";
        try {
            String savedJson = prefs.getString("weather_json", "");
            if (!TextUtils.isEmpty(savedJson)) {
                org.json.JSONObject wObj = new org.json.JSONObject(savedJson);
                org.json.JSONObject cur = wObj.optJSONObject("current");
                if (cur != null) {
                    int humidity = cur.optInt("relative_humidity_2m", 0);
                    double wind = cur.optDouble("wind_speed_10m", 0);
                    double curTemp = cur.optDouble("temperature_2m", 0);
                    humidityStr = "湿度 : " + humidity + "%";
                    windStr = "风速 : " + Math.round(wind) + " km/h";
                    feelsStr = "体感 : " + Math.round(curTemp) + "°C";
                }
            }
        } catch (Exception ignored) {
        }

        addWeatherInfo(metricLine1, feelsStr);
        addWeatherInfo(metricLine1, humidityStr);

        LinearLayout metricLine2 = new LinearLayout(this);
        metricLine2.setGravity(Gravity.CENTER_VERTICAL);
        summary.addView(metricLine2, new LinearLayout.LayoutParams(dp(620), dp(36)));
        addWeatherInfo(metricLine2, windStr);
        addWeatherInfo(metricLine2, "能见度 : -- km");

        LinearLayout forecast = new LinearLayout(this);
        forecast.setGravity(Gravity.CENTER);
        forecast.setPadding(dp(18), dp(8), dp(18), dp(10));
        forecast.setClipChildren(false);
        forecast.setClipToPadding(false);
        forecast.setBackground(weatherForecastBackground());
        FrameLayout.LayoutParams forecastLp = new FrameLayout.LayoutParams(-1, dp(150), Gravity.BOTTOM);
        forecastLp.leftMargin = dp(28);
        forecastLp.rightMargin = dp(28);
        forecastLp.bottomMargin = dp(4);
        hero.addView(forecast, forecastLp);

        String[] dayNames = {"今天", "明天", "后天", "周四", "周五", "周六", "周日"};
        try {
            String savedJson = prefs.getString("weather_json", "");
            if (!TextUtils.isEmpty(savedJson)) {
                org.json.JSONObject wObj = new org.json.JSONObject(savedJson);
                org.json.JSONObject daily = wObj.optJSONObject("daily");
                if (daily != null) {
                    org.json.JSONArray codes = daily.optJSONArray("weather_code");
                    org.json.JSONArray maxTemps = daily.optJSONArray("temperature_2m_max");
                    org.json.JSONArray minTemps = daily.optJSONArray("temperature_2m_min");
                    org.json.JSONArray times = daily.optJSONArray("time");
                    if (codes != null && maxTemps != null && minTemps != null) {
                        int count = Math.min(7, codes.length());
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("EE", Locale.CHINA);
                        for (int i = 0; i < count; i++) {
                            int code = codes.optInt(i, 3);
                            double maxT = maxTemps.optDouble(i, 0);
                            double minT = minTemps.optDouble(i, 0);
                            String dayLabel = dayNames[i];
                            if (i >= 3 && times != null) {
                                try {
                                    String dateStr = times.optString(i, "");
                                    java.text.SimpleDateFormat parseSdf = new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.CHINA);
                                    java.util.Date date = parseSdf.parse(dateStr);
                                    dayLabel = sdf.format(date);
                                } catch (Exception ignored) {
                                }
                            }
                            addForecastTile(forecast, dayLabel, wmoCodeToCondition(code),
                                    Math.round(maxT) + "° / " + Math.round(minT) + "°", code);
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        if (forecast.getChildCount() == 0) {
            addForecastTile(forecast, "今天", "多云", "31° / 25°", 3);
            addForecastTile(forecast, "明天", "雨", "30° / 24°", 61);
            addForecastTile(forecast, "后天", "局部多云", "32° / 25°", 2);
            addForecastTile(forecast, "周四", "多云", "31° / 25°", 3);
            addForecastTile(forecast, "周五", "晴", "33° / 26°", 0);
            addForecastTile(forecast, "周六", "晴", "34° / 27°", 0);
            addForecastTile(forecast, "周日", "多云", "32° / 26°", 2);
        }

        setContentView(root);
        back.requestFocus();
    }

    private GradientDrawable weatherDetailWash() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.argb(165, 94, 146, 160),
                        Color.argb(120, 113, 158, 172),
                        Color.argb(75, 44, 72, 82)
                });
        bg.setCornerRadius(dp(0));
        return bg;
    }

    private GradientDrawable weatherForecastBackground() {
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{
                        Color.argb(128, 130, 174, 188),
                        Color.argb(116, 72, 119, 136)
                });
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(32, 255, 255, 255));
        return bg;
    }

    private void addWeatherInfo(LinearLayout parent, String text) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setTextColor(Color.argb(230, 255, 255, 255));
        item.setTextSize(18);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setSingleLine(true);
        item.setIncludeFontPadding(false);
        item.setShadowLayer(dp(2), 0, dp(1), Color.argb(110, 0, 0, 0));
        parent.addView(item, new LinearLayout.LayoutParams(0, -1, 1));
    }

    private void addWeatherMetric(LinearLayout parent, String label, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        row.setBackground(actionButtonBackground(false, false));
        TextView name = new TextView(this);
        name.setText(label);
        name.setTextColor(Color.argb(180, 255, 255, 255));
        name.setTextSize(18);
        row.addView(name, new LinearLayout.LayoutParams(0, -1, 1));
        TextView val = new TextView(this);
        val.setText(value);
        val.setTextColor(Color.WHITE);
        val.setTextSize(22);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        val.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        row.addView(val, new LinearLayout.LayoutParams(dp(150), -1));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(68));
        lp.bottomMargin = dp(12);
        parent.addView(row, lp);
    }

    private void addForecastTile(LinearLayout parent, String day, String condition, String temp, int wmoCode) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setPadding(dp(4), dp(6), dp(4), dp(6));
        tile.setClipChildren(false);
        tile.setClipToPadding(false);
        TextView dayView = new TextView(this);
        dayView.setText(day);
        dayView.setTextColor(Color.WHITE);
        dayView.setTextSize(15);
        dayView.setTypeface(Typeface.DEFAULT_BOLD);
        dayView.setGravity(Gravity.CENTER);
        dayView.setIncludeFontPadding(false);
        dayView.setShadowLayer(dp(2), 0, dp(1), Color.argb(115, 0, 0, 0));
        tile.addView(dayView, new LinearLayout.LayoutParams(-1, dp(24)));
        ImageView icon = new ImageView(this);
        icon.setImageResource(wmoCodeToIcon(wmoCode));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        tile.addView(icon, new LinearLayout.LayoutParams(-1, dp(64)));
        TextView tempView = new TextView(this);
        tempView.setText(temp);
        tempView.setTextColor(Color.WHITE);
        tempView.setTextSize(14);
        tempView.setTypeface(Typeface.DEFAULT_BOLD);
        tempView.setGravity(Gravity.CENTER);
        tempView.setSingleLine(true);
        tempView.setIncludeFontPadding(false);
        tempView.setShadowLayer(dp(2), 0, dp(1), Color.argb(115, 0, 0, 0));
        tile.addView(tempView, new LinearLayout.LayoutParams(-1, dp(24)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -1, 1);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        parent.addView(tile, lp);
    }

    private int weatherIconResource(String condition) {
        String c = condition == null ? "" : condition.toLowerCase(Locale.ROOT);
        if (c.contains("storm") || c.contains("thunder")) return R.drawable.ic_weather_storm;
        if (c.contains("night") && c.contains("rain")) return R.drawable.ic_weather_night_rain;
        if (c.contains("night") || c.contains("moon")) return R.drawable.ic_weather_night;
        if (c.contains("heavy") && c.contains("rain")) return R.drawable.ic_weather_heavy_rain;
        if (c.contains("rain")) return R.drawable.ic_weather_rain;
        if (c.contains("wind") || c.contains("fog") || c.contains("mist")) return R.drawable.ic_weather_wind;
        if (c.contains("sun") || c.contains("clear")) return R.drawable.ic_weather_sun;
        if (c.contains("part")) return R.drawable.ic_weather_partly;
        if (c.contains("broken") || c.contains("clouds")) return R.drawable.ic_weather_clouds;
        if (c.contains("cloud")) return R.drawable.ic_weather_cloud;
        return R.drawable.ic_weather_partly;
    }

    private int wmoCodeToIcon(int code) {
        if (code == 0) return R.drawable.ic_weather_sun;
        if (code == 1 || code == 2) return R.drawable.ic_weather_partly;
        if (code == 3) return R.drawable.ic_weather_clouds;
        if (code == 45 || code == 48) return R.drawable.ic_weather_wind;
        if (code >= 51 && code <= 57) return R.drawable.ic_weather_rain;
        if (code == 61 || code == 63 || code == 66 || code == 80) return R.drawable.ic_weather_rain;
        if (code == 65 || code == 67 || code == 81 || code == 82) return R.drawable.ic_weather_heavy_rain;
        if (code >= 71 && code <= 77) return R.drawable.ic_weather_wind;
        if (code >= 95 && code <= 99) return R.drawable.ic_weather_storm;
        return R.drawable.ic_weather_clouds;
    }

    private String wmoCodeToCondition(int code) {
        if (code == 0) return "晴";
        if (code == 1) return "大部晴";
        if (code == 2) return "多云";
        if (code == 3) return "阴";
        if (code == 45 || code == 48) return "雾";
        if (code == 51 || code == 53 || code == 55) return "毛毛雨";
        if (code == 56 || code == 57) return "冻毛毛雨";
        if (code == 61) return "小雨";
        if (code == 63) return "中雨";
        if (code == 65) return "大雨";
        if (code == 66 || code == 67) return "冻雨";
        if (code == 71 || code == 73 || code == 75) return "雪";
        if (code == 77) return "雪粒";
        if (code == 80) return "阵雨";
        if (code == 81 || code == 82) return "强阵雨";
        if (code == 85 || code == 86) return "阵雪";
        if (code == 95) return "雷暴";
        if (code == 96 || code == 99) return "雷暴冰雹";
        return "未知";
    }

    private void fetchWeather() {
        long now = System.currentTimeMillis();
        if (weatherFetching || (now - lastWeatherFetchTime < 30 * 60 * 1000L && lastWeatherFetchTime > 0)) {
            return;
        }
        weatherFetching = true;
        new Thread(() -> {
            try {
                String cityName = "";
                double lat = 0, lon = 0;

                URL ipUrl = new URL("http://ip-api.com/json/?lang=zh-CN");
                InputStream ipIs = ipUrl.openStream();
                java.util.Scanner ipScanner = new java.util.Scanner(ipIs).useDelimiter("\\A");
                String ipJson = ipScanner.hasNext() ? ipScanner.next() : "";
                ipScanner.close();
                ipIs.close();

                org.json.JSONObject ipObj = new org.json.JSONObject(ipJson);
                if ("success".equals(ipObj.optString("status"))) {
                    cityName = ipObj.optString("city", "");
                    if (TextUtils.isEmpty(cityName)) {
                        cityName = ipObj.optString("regionName", "");
                    }
                    if (TextUtils.isEmpty(cityName)) {
                        cityName = ipObj.optString("country", "");
                    }
                    lat = ipObj.optDouble("lat", 39.9);
                    lon = ipObj.optDouble("lon", 116.4);
                }

                if (lat == 0 && lon == 0) {
                    lat = 39.9042;
                    lon = 116.4074;
                    cityName = "北京";
                }

                String weatherUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + lat
                        + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,relative_humidity_2m,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&timezone=auto";
                URL wUrl = new URL(weatherUrl);
                InputStream wIs = wUrl.openStream();
                java.util.Scanner wScanner = new java.util.Scanner(wIs).useDelimiter("\\A");
                String wJson = wScanner.hasNext() ? wScanner.next() : "";
                wScanner.close();
                wIs.close();

                final String finalCityName = cityName;
                final org.json.JSONObject weatherObj = new org.json.JSONObject(wJson);
                final org.json.JSONObject current = weatherObj.optJSONObject("current");

                runOnUiThread(() -> {
                    try {
                        if (current != null) {
                            double temp = current.optDouble("temperature_2m", 0);
                            int wmoCode = current.optInt("weather_code", 3);
                            weatherTempView.setText(Math.round(temp) + "°");
                            weatherCityView.setText(finalCityName);
                            weatherIconView.setImageResource(wmoCodeToIcon(wmoCode));

                            prefs.edit()
                                    .putString("weather_city", finalCityName)
                                    .putInt("weather_temp", (int) Math.round(temp))
                                    .putInt("weather_code", wmoCode)
                                    .putString("weather_json", wJson)
                                    .apply();
                        }
                    } catch (Exception ignored) {
                    }
                    lastWeatherFetchTime = System.currentTimeMillis();
                    weatherFetching = false;
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    String savedCity = prefs.getString("weather_city", "");
                    int savedTemp = prefs.getInt("weather_temp", Integer.MIN_VALUE);
                    if (!TextUtils.isEmpty(savedCity) && savedTemp != Integer.MIN_VALUE) {
                        int savedCode = prefs.getInt("weather_code", 3);
                        weatherTempView.setText(savedTemp + "°");
                        weatherCityView.setText(savedCity);
                        weatherIconView.setImageResource(wmoCodeToIcon(savedCode));
                    } else {
                        weatherCityView.setText("获取失败");
                    }
                    weatherFetching = false;
                });
            }
        }).start();
    }

    private void showSettings(String selected) {
        showingSettings = true;
        showingWeatherDetails = false;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(52, 52, 52));
        root.setClipChildren(false);
        root.setClipToPadding(false);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setPadding(dp(58), dp(58), dp(62), dp(34));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout menu = new LinearLayout(this);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setClipChildren(false);
        menu.setClipToPadding(false);
        page.addView(menu, new LinearLayout.LayoutParams(dp(396), -1));

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextColor(Color.argb(120, 255, 255, 255));
        title.setTextSize(36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.bottomMargin = dp(28);
        menu.addView(title, titleLp);

        ScrollView menuScroll = new ScrollView(this);
        menuScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        menuScroll.setClipChildren(false);
        menuScroll.setClipToPadding(false);
        menuScroll.setPadding(0, 0, 0, dp(24));
        menu.addView(menuScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout menuItems = new LinearLayout(this);
        menuItems.setOrientation(LinearLayout.VERTICAL);
        menuItems.setClipChildren(false);
        menuItems.setClipToPadding(false);
        menuScroll.addView(menuItems, new ScrollView.LayoutParams(-1, -2));

        String[] sections = {"主题模式", "控制", "外观", "关于", "帮助"};
        View selectedMenuItem = null;
        for (String section : sections) {
            TextView item = settingsMenuItem(section, section.equals(selected));
            item.setOnClickListener(v -> showSettings(section));
            item.setOnFocusChangeListener((v, hasFocus) -> {
                animateFocus(v, hasFocus, 1.0f);
                if (hasFocus) {
                    menuScroll.post(() -> menuScroll.requestChildRectangleOnScreen(menuItems,
                            new Rect(v.getLeft(), v.getTop() - dp(12), v.getRight(), v.getBottom() + dp(12)), false));
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(56));
            lp.bottomMargin = dp(12);
            menuItems.addView(item, lp);
            if (section.equals(selected)) selectedMenuItem = item;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(4), dp(12), dp(40));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(0, -1, 1);
        scrollLp.leftMargin = dp(72);
        page.addView(scroll, scrollLp);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.VERTICAL);
        options.setClipChildren(false);
        options.setClipToPadding(false);
        options.setPadding(0, 0, dp(34), dp(32));
        scroll.addView(options, new ScrollView.LayoutParams(-1, -2));
        boolean restoreOptionFocus = !TextUtils.isEmpty(pendingSettingsFocusTitle);
        fillSettingsOptions(options, selected);
        setContentView(root);
        if (pendingSettingsScrollY >= 0) {
            final int y = pendingSettingsScrollY;
            pendingSettingsScrollY = -1;
            scroll.post(() -> scroll.scrollTo(0, y));
        }
        if ((restoreOptionFocus && !TextUtils.isEmpty(pendingSettingsFocusTitle)) && selectedMenuItem != null) {
            pendingSettingsFocusTitle = null;
            View target = selectedMenuItem;
            target.post(target::requestFocus);
        } else if (!restoreOptionFocus && selectedMenuItem != null) {
            View target = selectedMenuItem;
            target.post(target::requestFocus);
        }
    }

    private void fillSettingsOptions(LinearLayout options, String selected) {
        if ("主题模式".equals(selected)) {
            addHeading(options, "壁纸主题");
            addModeOption(options, "浅色", "内置山脉壁纸", "light");
            addModeOption(options, "深色", "AT4K风格深色渐变", "dark");
            addOption(options, "自定义图片", "选择本地图片文件", "image".equals(bgMode()), v -> openPicker("image/*", REQ_IMAGE));
            addOption(options, "网络图片", prefs.getString("bg_url", "下载并使用远程图片"), "url".equals(bgMode()), v -> askWallpaperUrl());
            addOption(options, "自定义视频", "选择本地视频文件，静音循环播放", "video".equals(bgMode()), v -> openPicker("video/*", REQ_VIDEO));
            addOption(options, "自定义渐变", "使用流畅的渐变效果", "gradient".equals(bgMode()), v -> {
                rememberSettingsState(v, "自定义渐变");
                showGradientPicker();
            });
            return;
        }
        if ("控制".equals(selected)) {
            addHeading(options, "每行应用数");
            addAppsOption(options, 5);
            addAppsOption(options, 6);
            addAppsOption(options, 7);
            addHeading(options, "桌面启动器");
            addOption(options, "设为默认桌面", homeStatusText(), isDefaultHomeLauncher(), v -> requestDefaultHomeLauncher());
            addOption(options, "测试桌面", "如果系统允许，打开Android桌面选择器", false, v -> testHomeLauncher());
            addHeading(options, "遥控器与开机控制");
            addToggle(options, "禁用返回键退出", "防止按返回键退出桌面", "override_back_exit", "控制");
            addToggle(options, "开机自动启动", "开机或更新后几秒自动启动", "start_on_boot", "控制");
            addToggle(options, "唤醒后自动打开", "屏保或休眠结束后自动返回", "auto_open_on_wake", "控制");
            addHeading(options, "无障碍服务备用方案");
            addOption(options, "1. 允许受限设置", "打开应用信息，如果Google TV阻止访问请使用右上角菜单", false, v -> {
                toast("如果显示，请选择允许受限设置，然后返回此处。");
                openAppInfo(getPackageName());
            });
            addOption(options, "2. 打开无障碍设置", isAccessibilityServiceEnabled() ? "已启用" : "启用桌面无障碍服务", isAccessibilityServiceEnabled(), v -> openAccessibilitySettings());
            addToggle(options, "备用：拦截系统桌面", "可选功能；Android 13+可能阻止侧载应用", "override_current_launcher", "控制");
            addOption(options, "系统设置", "打开Android/Google TV系统设置", false, v -> openGoogleSettings());
            addOption(options, "打开应用信息", "如果Android阻止受限设置请使用此选项", false, v -> openAppInfo(getPackageName()));
            return;
        }
        if ("外观".equals(selected)) {
            addHeading(options, "外观");
            addToggle(options, "隐藏精选卡片", "隐藏首页天气卡片", "hide_featured", "外观");
            addToggle(options, "隐藏应用标题", "隐藏\"我的应用\"标题", "hide_app_titles", "外观");
            addToggle(options, "收藏保持在底部", "保存用于下次收藏行更新", "favorites_bottom", "外观");
            addToggle(options, "使用24小时制", "切换时钟格式", "use_24h", "外观");
            addToggle(options, "极简状态栏", "隐藏顶部控制栏，更清爽的壁纸视图", "minimal_status", "外观");
            addOption(options, "图标包", "占位：图标包功能即将推出", false, v -> toast("图标包支持将在下一个版本中添加。"));
            return;
        }
        if ("关于".equals(selected)) {
            addHeading(options, "星河桌面");
            addNote(options, "灵感来自ATK和艾蒙顿布局的开源Android TV桌面。");
            addOption(options, "重置桌面设置", "恢复默认设置", false, v -> {
                prefs.edit().clear().apply();
                toast("设置已重置");
                showHome();
            });
            return;
        }
        addHeading(options, selected);
        addNote(options, "此开源版本专注于本地自定义和电视友好的导航体验。");
    }

    private void loadApps() {
        if (appsGrid == null) return;
        PackageManager pm = getPackageManager();
        List<AppEntry> entries = buildDisplayEntries(queryLaunchableApps(pm));
        applySavedDisplayOrder(entries);
        appsGrid.removeAllViews();

        int columns = appsPerRow();
        int gridWidth = getResources().getDisplayMetrics().widthPixels - dp(108);
        int cellWidth = Math.max(dp(150), gridWidth / columns);
        appsGrid.setColumnCount(columns);

        for (AppEntry entry : entries) {
            appsGrid.addView(createAppTile(pm, entry, cellWidth));
        }
        if (!TextUtils.isEmpty(focusAfterLoadPackage)) {
            String focusPackage = focusAfterLoadPackage;
            focusAfterLoadPackage = null;
            appsGrid.post(() -> focusAppTile(focusPackage));
        }
    }

    private void showAppSearch() {
        PackageManager pm = getPackageManager();
        List<AppEntry> allApps = new ArrayList<>(queryLaunchableApps(pm));
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.argb(215, 8, 10, 14));
        root.setPadding(dp(56), dp(48), dp(56), dp(42));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(actionPanelBackground());
        root.addView(panel, new FrameLayout.LayoutParams(-1, -1));

        TextView title = new TextView(this);
        title.setText("搜索应用商店");
        title.setTextColor(Color.WHITE);
        title.setTextSize(30);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(46)));

        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setFocusable(true);
        input.setFocusableInTouchMode(true);
        input.setHint("搜索要下载的TV应用");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.argb(145, 255, 255, 255));
        input.setTextSize(20);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        input.setBackground(actionButtonBackground(false, false));
        input.setPadding(dp(18), 0, dp(18), 0);
        input.setOnClickListener(v -> showKeyboard(input));
        input.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) input.postDelayed(() -> showKeyboard(input), 80);
        });
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(-1, dp(62));
        inputLp.bottomMargin = dp(22);
        panel.addView(input, inputLp);

        TextView playSearch = createActionButton(
                new AppAction("搜索Google Play", "打开应用商店结果", () ->
                        openPlayStoreSearch(input.getText().toString().trim()), false), dialog);
        LinearLayout.LayoutParams playLp = new LinearLayout.LayoutParams(-1, dp(64));
        playLp.bottomMargin = dp(18);
        panel.addView(playSearch, playLp);

        TextView installedHeading = new TextView(this);
        installedHeading.setText("已安装应用");
        installedHeading.setTextColor(Color.argb(190, 255, 255, 255));
        installedHeading.setTextSize(18);
        installedHeading.setTypeface(Typeface.DEFAULT_BOLD);
        panel.addView(installedHeading, new LinearLayout.LayoutParams(-1, dp(34)));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        GridLayout results = new GridLayout(this);
        results.setColumnCount(4);
        results.setClipChildren(false);
        results.setClipToPadding(false);
        scroll.addView(results, new ScrollView.LayoutParams(-1, -2));

        Runnable[] populate = new Runnable[1];
        populate[0] = () -> {
            String q = input.getText().toString().trim().toLowerCase(Locale.ROOT);
            results.removeAllViews();
            int shown = 0;
            for (AppEntry app : allApps) {
                String haystack = (app.label + " " + app.packageName).toLowerCase(Locale.ROOT);
                if (TextUtils.isEmpty(q) || !haystack.contains(q)) continue;
                View tile = createSearchResultTile(pm, app, dialog);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = dp(255);
                lp.height = dp(92);
                lp.setMargins(dp(8), dp(8), dp(8), dp(8));
                results.addView(tile, lp);
                shown++;
                if (shown >= 48) break;
            }
            if (shown == 0) {
                TextView empty = new TextView(this);
                empty.setText(TextUtils.isEmpty(q)
                        ? "输入名称，然后按搜索Google Play。已安装的匹配项显示在此处。"
                        : "未找到已安装应用");
                empty.setTextColor(Color.argb(180, 255, 255, 255));
                empty.setTextSize(22);
                results.addView(empty, new ViewGroup.LayoutParams(-1, dp(80)));
            }
        };
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (event == null || event.getAction() == KeyEvent.ACTION_DOWN) {
                openPlayStoreSearch(input.getText().toString().trim());
                return true;
            }
            return false;
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { populate[0].run(); }
            @Override public void afterTextChanged(Editable s) {}
        });
        populate[0].run();

        dialog.setContentView(root);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        dialog.setOnShowListener(d -> input.postDelayed(() -> {
            input.requestFocus();
            input.performClick();
            showKeyboard(input);
        }, 120));
        dialog.setOnDismissListener(d -> {
            if (searchPill != null) searchPill.postDelayed(searchPill::requestFocus, 50);
        });
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                    | WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
    }

    private void showKeyboard(EditText input) {
        if (input == null) return;
        input.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(input, InputMethodManager.SHOW_FORCED);
            imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
        }
    }

    private void openPlayStoreSearch(String query) {
        if (TextUtils.isEmpty(query)) {
            toast("请先输入应用名称。");
            return;
        }
        PackageManager pm = getPackageManager();
        Intent market = new Intent(Intent.ACTION_VIEW,
                Uri.parse("market://search?q=" + Uri.encode(query) + "&c=apps"));
        market.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (market.resolveActivity(pm) != null) {
            try {
                startActivity(market);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        Intent web = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(query) + "&c=apps"));
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (web.resolveActivity(pm) != null) {
            try {
                startActivity(web);
                return;
            } catch (ActivityNotFoundException ignored) {
            }
        }
        toast("此设备上没有Google Play商店。");
    }

    private View createSearchResultTile(PackageManager pm, AppEntry entry, Dialog searchDialog) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(14), 0);
        row.setBackground(actionButtonBackground(false, false));
        row.setFocusable(true);
        row.setClickable(true);

        ImageView icon = new ImageView(this);
        icon.setImageDrawable(loadAppIcon(pm, entry));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(52), dp(52));
        iconLp.rightMargin = dp(14);
        row.addView(icon, iconLp);

        TextView label = new TextView(this);
        label.setText(entry.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(17);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(label, new LinearLayout.LayoutParams(0, -2, 1));

        row.setOnFocusChangeListener((v, hasFocus) -> {
            row.setBackground(actionButtonBackground(hasFocus, false));
            label.setTextColor(hasFocus ? Color.BLACK : Color.WHITE);
            row.animate().scaleX(hasFocus ? 1.035f : 1f).scaleY(hasFocus ? 1.035f : 1f).setDuration(130).start();
        });
        row.setOnClickListener(v -> {
            searchDialog.dismiss();
            launchEntry(entry, true);
        });
        row.setOnLongClickListener(v -> {
            showAppActionDialog(entry, buildSearchActions(entry, searchDialog), row);
            return true;
        });
        return row;
    }

    private List<AppAction> buildSearchActions(AppEntry entry, Dialog searchDialog) {
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction("打开", "启动应用", () -> {
            searchDialog.dismiss();
            launchEntry(entry, true);
        }, false));
        actions.add(new AppAction("文件夹", "添加到文件夹", () -> promptAddToFolder(entry), false));
        actions.add(new AppAction("隐藏", "从桌面隐藏", () -> {
            searchDialog.dismiss();
            hideApp(entry.packageName);
        }, true));
        actions.add(new AppAction("图标", "修改图标", () -> toast("自定义图标选择器即将推出。"), false));
        actions.add(new AppAction("信息", "应用信息", () -> openAppInfo(entry.packageName), false));
        return actions;
    }

    private void focusAppTile(String packageName) {
        if (appsGrid == null || TextUtils.isEmpty(packageName)) return;
        for (int i = 0; i < appsGrid.getChildCount(); i++) {
            View cell = appsGrid.getChildAt(i);
            Object tag = cell.getTag();
            if (TextUtils.equals(packageName, tag == null ? null : tag.toString()) && cell instanceof LinearLayout) {
                LinearLayout layout = (LinearLayout) cell;
                if (layout.getChildCount() > 0) layout.getChildAt(0).requestFocus();
                return;
            }
        }
    }

    private List<AppEntry> queryLaunchableApps(PackageManager pm) {
        String hiddenKey = hiddenAppsKey();
        long now = System.currentTimeMillis();
        if (cachedLaunchableApps != null
                && TextUtils.equals(cachedHiddenKey, hiddenKey)
                && now - cachedLaunchableAppsAt < 120000L) {
            List<AppEntry> cached = new ArrayList<>(cachedLaunchableApps);
            applySavedOrder(cached);
            return cached;
        }
        Intent leanback = new Intent(Intent.ACTION_MAIN);
        leanback.addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> resolves = new ArrayList<>(pm.queryIntentActivities(leanback, 0));
        Intent normal = new Intent(Intent.ACTION_MAIN);
        normal.addCategory(Intent.CATEGORY_LAUNCHER);
        resolves.addAll(pm.queryIntentActivities(normal, 0));

        Set<String> seen = new HashSet<>();
        Set<String> hidden = prefs.getStringSet("hidden_apps", new HashSet<>());
        List<AppEntry> apps = new ArrayList<>();
        for (ResolveInfo info : resolves) {
            if (info.activityInfo == null) continue;
            String pkg = info.activityInfo.packageName;
            if (getPackageName().equals(pkg) || seen.contains(pkg)) continue;
            if (hidden.contains(pkg)) continue;
            seen.add(pkg);

            Intent launch = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = getPackageManager().getLaunchIntentForPackage(pkg);
            if (launch == null) {
                launch = new Intent(Intent.ACTION_MAIN);
                launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            Drawable banner = null;
            try {
                banner = info.activityInfo.loadBanner(pm);
                if (banner == null) banner = info.activityInfo.applicationInfo.loadBanner(pm);
            } catch (Exception ignored) {
            }
            apps.add(new AppEntry(pkg, String.valueOf(info.loadLabel(pm)), launch, info.activityInfo.applicationInfo, banner));
        }

        String[] extraIntentActions = {
                Intent.ACTION_VIEW,
                Intent.ACTION_GET_CONTENT,
                Intent.ACTION_PICK
        };
        String[] extraMimeTypes = {
                "file/*",
                "vnd.android.cursor.dir/*",
                "*/*"
        };
        for (String action : extraIntentActions) {
            for (String mimeType : extraMimeTypes) {
                try {
                    Intent fileIntent = new Intent(action);
                    fileIntent.setType(mimeType);
                    fileIntent.addCategory(Intent.CATEGORY_DEFAULT);
                    List<ResolveInfo> fileResolves = pm.queryIntentActivities(fileIntent, 0);
                    for (ResolveInfo info : fileResolves) {
                        if (info.activityInfo == null) continue;
                        String pkg = info.activityInfo.packageName;
                        if (getPackageName().equals(pkg) || seen.contains(pkg)) continue;
                        if (hidden.contains(pkg)) continue;
                        seen.add(pkg);
                        Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                        if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                        if (launch == null) {
                            launch = new Intent(action);
                            launch.setComponent(new ComponentName(pkg, info.activityInfo.name));
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                        Drawable banner = null;
                        try {
                            banner = info.activityInfo.loadBanner(pm);
                            if (banner == null) banner = info.activityInfo.applicationInfo.loadBanner(pm);
                        } catch (Exception ignored) {
                        }
                        apps.add(new AppEntry(pkg, String.valueOf(info.loadLabel(pm)), launch, info.activityInfo.applicationInfo, banner));
                    }
                } catch (Exception ignored) {
                }
            }
        }

        String[] knownFileManagers = {
                "com.android.files",
                "com.google.android.files",
                "com.google.android.documentsui",
                "com.amazon.tv.files",
                "com.filetv.explorer",
                "com.estrongs.android.pop",
                "com.rhmsoft.fm",
                "com.rhmsoft.fm.hd",
                "com.mobisystems.fileman",
                "pl.solidexplorer2",
                "pl.solidexplorer",
                "com.speedsoftware.explorer",
                "com.speedsoftware.rootexplorer",
                "com.dasmoticz.files",
                "com.alphainventor.filemanager"
        };
        try {
            for (ApplicationInfo appInfo : pm.getInstalledApplications(0)) {
                String pkg = appInfo.packageName;
                if (getPackageName().equals(pkg) || seen.contains(pkg) || hidden.contains(pkg)) continue;
                boolean isKnownFileManager = false;
                for (String knownPkg : knownFileManagers) {
                    if (pkg.toLowerCase(Locale.ROOT).contains(knownPkg.toLowerCase(Locale.ROOT))
                            || knownPkg.toLowerCase(Locale.ROOT).contains(pkg.toLowerCase(Locale.ROOT))) {
                        isKnownFileManager = true;
                        break;
                    }
                }
                String appLabel = String.valueOf(appInfo.loadLabel(pm));
                if (appLabel.toLowerCase(Locale.ROOT).contains("文件")
                        || appLabel.toLowerCase(Locale.ROOT).contains("file")
                        || appLabel.toLowerCase(Locale.ROOT).contains("explorer")
                        || appLabel.toLowerCase(Locale.ROOT).contains("管理器")) {
                    isKnownFileManager = true;
                }
                if (!isKnownFileManager) continue;
                Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
                if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
                if (launch == null) {
                    try {
                        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
                        mainIntent.setPackage(pkg);
                        List<ResolveInfo> mainResolves = pm.queryIntentActivities(mainIntent, 0);
                        if (mainResolves.size() > 0 && mainResolves.get(0).activityInfo != null) {
                            launch = new Intent(Intent.ACTION_MAIN);
                            launch.setComponent(new ComponentName(pkg, mainResolves.get(0).activityInfo.name));
                            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (launch == null) continue;
                seen.add(pkg);
                Drawable banner = null;
                try {
                    banner = appInfo.loadBanner(pm);
                } catch (Exception ignored) {
                }
                apps.add(new AppEntry(pkg, appLabel, launch, appInfo, banner));
            }
        } catch (Exception ignored) {
        }

        Collator collator = Collator.getInstance(Locale.getDefault());
        Collections.sort(apps, (a, b) -> collator.compare(a.label, b.label));
        cachedLaunchableApps = new ArrayList<>(apps);
        cachedHiddenKey = hiddenKey;
        cachedLaunchableAppsAt = now;
        applySavedOrder(apps);
        return apps;
    }

    private String hiddenAppsKey() {
        List<String> hidden = new ArrayList<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
        Collections.sort(hidden);
        return TextUtils.join("|", hidden);
    }

    private void invalidateAppCache() {
        cachedLaunchableApps = null;
        cachedLaunchableAppsAt = 0L;
    }

    private List<AppEntry> buildDisplayEntries(List<AppEntry> apps) {
        Map<String, String> folderByPackage = readPackageFolders();
        Map<String, List<AppEntry>> folderChildren = new LinkedHashMap<>();
        List<AppEntry> display = new ArrayList<>();
        Set<String> addedFolders = new HashSet<>();
        for (AppEntry app : apps) {
            String folder = folderByPackage.get(app.packageName);
            if (TextUtils.isEmpty(folder)) {
                display.add(app);
                continue;
            }
            if (!folderChildren.containsKey(folder)) folderChildren.put(folder, new ArrayList<>());
            folderChildren.get(folder).add(app);
            if (!addedFolders.contains(folder)) {
                display.add(AppEntry.folder(folder, folderChildren.get(folder)));
                addedFolders.add(folder);
            }
        }
        for (Map.Entry<String, List<AppEntry>> entry : folderChildren.entrySet()) {
            applySavedFolderOrder(entry.getKey(), entry.getValue());
        }
        return display;
    }

    private void applySavedOrder(List<AppEntry> apps) {
        List<String> order = readOrder();
        if (order.isEmpty()) return;
        Map<String, Integer> indexByPackage = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) indexByPackage.put(order.get(i), i);
        Collections.sort(apps, (a, b) -> {
            int ai = indexByPackage.containsKey(a.packageName) ? indexByPackage.get(a.packageName) : 100000;
            int bi = indexByPackage.containsKey(b.packageName) ? indexByPackage.get(b.packageName) : 100000;
            if (ai != bi) return ai - bi;
            return Collator.getInstance(Locale.getDefault()).compare(a.label, b.label);
        });
    }

    private void applySavedDisplayOrder(List<AppEntry> entries) {
        List<String> order = readOrder();
        if (order.isEmpty()) return;
        Map<String, Integer> indexByPackage = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) indexByPackage.put(order.get(i), i);
        Collections.sort(entries, (a, b) -> {
            int ai = indexByPackage.containsKey(a.packageName) ? indexByPackage.get(a.packageName) : 100000;
            int bi = indexByPackage.containsKey(b.packageName) ? indexByPackage.get(b.packageName) : 100000;
            if (ai != bi) return ai - bi;
            return Collator.getInstance(Locale.getDefault()).compare(a.label, b.label);
        });
    }

    private void applySavedFolderOrder(String folderName, List<AppEntry> entries) {
        List<String> order = readFolderOrder(folderName);
        if (order.isEmpty()) return;
        Map<String, Integer> indexByPackage = new LinkedHashMap<>();
        for (int i = 0; i < order.size(); i++) indexByPackage.put(order.get(i), i);
        Collections.sort(entries, (a, b) -> {
            int ai = indexByPackage.containsKey(a.packageName) ? indexByPackage.get(a.packageName) : 100000;
            int bi = indexByPackage.containsKey(b.packageName) ? indexByPackage.get(b.packageName) : 100000;
            if (ai != bi) return ai - bi;
            return Collator.getInstance(Locale.getDefault()).compare(a.label, b.label);
        });
    }

    private View createAppTile(PackageManager pm, AppEntry entry, int cellWidth) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(8), dp(2), dp(8), dp(10));
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        cell.setTag(entry.packageName);

        GridLayout.LayoutParams gridLp = new GridLayout.LayoutParams();
        gridLp.width = cellWidth;
        gridLp.height = dp(prefs.getBoolean("hide_app_titles", false) ? 116 : 150);
        cell.setLayoutParams(gridLp);

        boolean hasBanner = !entry.isFolder && entry.banner != null;
        boolean colorCard = !entry.isFolder && !hasBanner;
        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setClipToOutline(true);
        card.setBackgroundResource(hasBanner ? R.drawable.app_card_banner : R.drawable.app_card);
        if (hasBanner) {
            card.setForeground(getResources().getDrawable(R.drawable.app_card_banner_foreground));
        }
        int cardWidth = cellWidth - dp(16);
        int cardHeight = Math.max(dp(78), Math.round(cardWidth * 9f / 16f));
        card.setPadding(colorCard ? dp(18) : 0, colorCard ? dp(8) : 0,
                colorCard ? dp(16) : 0, colorCard ? dp(8) : 0);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cell.addView(card, cardLp);

        ImageView icon = new ImageView(this);
        if (entry.isFolder) {
            addFolderPreview(pm, card, entry, cardWidth, cardHeight);
        } else if (hasBanner) {
            icon.setImageDrawable(entry.banner);
            icon.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(icon, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = loadAppIcon(pm, entry);
            int baseColor = dominantColor(appIcon);
            card.setBackground(buildIconCardBackground(baseColor));
            icon.setImageDrawable(appIcon);
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Math.min(dp(62), cardHeight - dp(20));
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.addView(icon, iconLp);

            TextView cardLabel = new TextView(this);
            cardLabel.setText(entry.label);
            cardLabel.setTextColor(readableTextColor(baseColor));
            cardLabel.setTextSize(15);
            cardLabel.setTypeface(Typeface.DEFAULT_BOLD);
            cardLabel.setGravity(Gravity.CENTER_VERTICAL);
            cardLabel.setSingleLine(true);
            cardLabel.setMaxLines(1);
            cardLabel.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = iconSize + dp(18);
            card.addView(cardLabel, textLp);
        }

        TextView label = new TextView(this);
        boolean showTitleBelow = !prefs.getBoolean("hide_app_titles", false);
        if (showTitleBelow) {
            label.setText(entry.label);
            label.setTextColor(Color.argb(222, 255, 255, 255));
            label.setTextSize(16);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(cardWidth, dp(25));
            labelLp.topMargin = dp(4);
            cell.addView(label, labelLp);
        }

        card.setOnClickListener(v -> {
            if (!TextUtils.isEmpty(movingPackage)) {
                toast("位置已保存");
                movingPackage = null;
                loadApps();
                return;
            }
            if (entry.isFolder) {
                showFolder(entry);
                return;
            }
            try {
                startExternalActivity(entry.launchIntent);
            } catch (ActivityNotFoundException ex) {
                toast("无法打开 " + entry.label);
            }
        });
        card.setOnLongClickListener(v -> {
            showAppMenu(pm, entry);
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || !TextUtils.equals(movingPackage, entry.packageName)) {
                return false;
            }
            int delta = 0;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) delta = -1;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) delta = 1;
            else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) delta = -appsPerRow();
            else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) delta = appsPerRow();
            else if (keyCode == KeyEvent.KEYCODE_BACK) {
                movingPackage = null;
                loadApps();
                toast("Move cancelled");
                return true;
            }
            if (delta == 0) return false;
            return moveFocusedTileByDelta(v, entry.packageName, delta);
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) keepFocusedCardFullyVisible(v);
            animateFocus(v, hasFocus, 1.08f);
            if (showTitleBelow) {
                label.setTextColor(hasFocus ? Color.WHITE : Color.argb(222, 255, 255, 255));
                label.setShadowLayer(hasFocus ? dp(4) : 0, 0, hasFocus ? dp(2) : 0, Color.argb(180, 0, 0, 0));
            }
        });
        if (TextUtils.equals(movingPackage, entry.packageName)) {
            startHomeMoveWiggle(card, entry.packageName);
        }
        return cell;
    }

    private Drawable loadAppIcon(PackageManager pm, AppEntry entry) {
        try {
            if (entry.info != null) return entry.info.loadIcon(pm);
        } catch (Exception ignored) {
        }
        return getResources().getDrawable(R.drawable.ic_launcher);
    }

    private GradientDrawable buildIconCardBackground(int baseColor) {
        int start = adjustColor(baseColor, 1.22f);
        int end = adjustColor(baseColor, 0.92f);
        if (isLowSaturation(baseColor)) {
            start = Color.rgb(238, 244, 251);
            end = Color.rgb(213, 228, 246);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        bg.setCornerRadius(dp(12));
        return bg;
    }

    private int dominantColor(Drawable drawable) {
        try {
            int size = dp(56);
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, size, size);
            drawable.draw(canvas);
            long r = 0, g = 0, b = 0, count = 0;
            for (int y = 0; y < size; y += 4) {
                for (int x = 0; x < size; x += 4) {
                    int color = bitmap.getPixel(x, y);
                    if (Color.alpha(color) < 80) continue;
                    int cr = Color.red(color);
                    int cg = Color.green(color);
                    int cb = Color.blue(color);
                    int max = Math.max(cr, Math.max(cg, cb));
                    int min = Math.min(cr, Math.min(cg, cb));
                    if (max > 235 && min > 220) continue;
                    if (max - min < 16 && max > 170) continue;
                    r += cr;
                    g += cg;
                    b += cb;
                    count++;
                }
            }
            bitmap.recycle();
            if (count == 0) return Color.rgb(82, 145, 236);
            return Color.rgb((int) (r / count), (int) (g / count), (int) (b / count));
        } catch (Exception ignored) {
            return Color.rgb(82, 145, 236);
        }
    }

    private boolean isLowSaturation(int color) {
        int max = Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)));
        int min = Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
        return max - min < 24;
    }

    private int adjustColor(int color, float factor) {
        return Color.rgb(
                Math.max(0, Math.min(255, Math.round(Color.red(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.green(color) * factor))),
                Math.max(0, Math.min(255, Math.round(Color.blue(color) * factor))));
    }

    private int readableTextColor(int color) {
        double luminance = Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114;
        return luminance > 145 ? Color.BLACK : Color.WHITE;
    }

    private void keepFocusedCardFullyVisible(View v) {
        int pad = dp(34);
        Rect rect = new Rect(-pad, -pad, v.getWidth() + pad, v.getHeight() + pad);
        v.post(() -> v.requestRectangleOnScreen(rect, false));
    }

    private void addFolderPreview(PackageManager pm, FrameLayout card, AppEntry folder, int cardWidth, int cardHeight) {
        int count = folder.children == null ? 0 : Math.min(9, folder.children.size());
        if (cardWidth <= 0) {
            int measured = card.getWidth();
            int lpWidth = card.getLayoutParams() == null ? 0 : card.getLayoutParams().width;
            cardWidth = measured > 0 ? measured : (lpWidth > 0 ? lpWidth : dp(220));
        }
        int gapX = dp(10);
        int gapY = dp(4);
        int safeWidth = Math.max(dp(90), cardWidth - dp(58));
        int iconWidth = Math.min(dp(58), Math.max(dp(38), (safeWidth - gapX * 2) / 3));
        int maxHeightForNine = Math.max(dp(24), (cardHeight - dp(20) - gapY * 2) / 3);
        int iconHeight = Math.min(maxHeightForNine, Math.min(dp(42), Math.max(dp(30), Math.round(iconWidth * 0.66f))));
        iconWidth = Math.min(iconWidth, Math.max(dp(38), Math.round(iconHeight * 1.55f)));
        int gridWidth = iconWidth * 3 + gapX * 2;
        int left = Math.max(dp(12), (cardWidth - gridWidth) / 2);
        int top = Math.max(dp(12), (cardHeight - iconHeight * 3 - gapY * 2) / 2);
        for (int i = 0; i < count; i++) {
            AppEntry child = folder.children.get(i);
            FrameLayout mini = createFolderMiniCard(pm, child, iconWidth, iconHeight);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(iconWidth, iconHeight);
            lp.leftMargin = left + (i % 3) * (iconWidth + gapX);
            lp.topMargin = top + (i / 3) * (iconHeight + gapY);
            card.addView(mini, lp);
        }

    }

    private void showAppMenu(PackageManager pm, AppEntry entry) {
        if (entry.isFolder) {
            showFolderMenu(entry);
            return;
        }
        View restoreFocus = getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction("打开", "启动应用", () -> launchEntry(entry, true), false));
        actions.add(new AppAction("Move", "Reorder on home", () -> {
            movingPackage = entry.packageName;
            focusAfterLoadPackage = entry.packageName;
            loadApps();
        }, false));
        actions.add(new AppAction("文件夹", "添加到文件夹", () -> promptAddToFolder(entry), false));
        actions.add(new AppAction("隐藏", "从桌面隐藏", () -> hideApp(entry.packageName), true));
        actions.add(new AppAction("信息", "应用信息", () -> openAppInfo(entry.packageName), false));
        actions.add(new AppAction("图标", "修改图标", () -> toast("自定义图标选择器即将推出。"), false));
        showAppActionDialog(entry, actions, restoreFocus);
    }

    private void showFolderMenu(AppEntry folder) {
        View restoreFocus = getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction("打开", "Open folder", () -> showFolder(folder), false));
        actions.add(new AppAction("Move", "Reorder on home", () -> {
            movingPackage = folder.packageName;
            focusAfterLoadPackage = folder.packageName;
            loadApps();
        }, false));
        actions.add(new AppAction("Rename", "Rename folder", () -> toast("Rename folder comes next."), false));
        actions.add(new AppAction("Delete", "Remove folder", () -> removeFolder(folder.label), true));
        showAppActionDialog(folder, actions, restoreFocus);
    }

    private void showAppActionDialog(AppEntry entry, List<AppAction> actions, View restoreFocus) {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);

        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(Color.argb(170, 0, 0, 0));
        dim.setPadding(dp(24), dp(24), dp(24), dp(24));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(28), dp(24), dp(28), dp(24));
        panel.setBackground(actionPanelBackground());
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(dp(560), -2, Gravity.CENTER);
        dim.addView(panel, panelLp);

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(0, 0, 0, dp(12));
        ImageView icon = new ImageView(this);
        if (entry.isFolder) {
            icon.setImageResource(R.drawable.ic_launcher);
        } else {
            icon.setImageDrawable(loadAppIcon(getPackageManager(), entry));
        }
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(54), dp(54));
        iconLp.rightMargin = dp(16);
        header.addView(icon, iconLp);

        LinearLayout titleBox = new LinearLayout(this);
        titleBox.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(entry.label);
        title.setTextColor(Color.WHITE);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(title);
        TextView subtitle = new TextView(this);
        subtitle.setText(entry.packageName);
        subtitle.setTextColor(Color.argb(150, 255, 255, 255));
        subtitle.setTextSize(12);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        titleBox.addView(subtitle);
        header.addView(titleBox, new LinearLayout.LayoutParams(0, -2, 1));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.argb(55, 255, 255, 255));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.bottomMargin = dp(18);
        panel.addView(divider, dividerLp);

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        panel.addView(grid, new LinearLayout.LayoutParams(-1, -2));
        List<View> buttons = new ArrayList<>();
        for (AppAction action : actions) {
            TextView button = createActionButton(action, dialog);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(240);
            lp.height = dp(72);
            lp.setMargins(dp(6), dp(6), dp(6), dp(6));
            grid.addView(button, lp);
            buttons.add(button);
        }

        TextView cancel = createActionButton(new AppAction("Cancel", "Close menu", dialog::dismiss, false), dialog);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(-1, dp(58));
        cancelLp.topMargin = dp(12);
        panel.addView(cancel, cancelLp);

        dialog.setContentView(dim);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        dialog.setOnDismissListener(d -> {
            if (restoreFocus != null) restoreFocus.postDelayed(restoreFocus::requestFocus, 40);
        });
        dialog.setOnShowListener(d -> {
            panel.setAlpha(0f);
            panel.setScaleX(0.96f);
            panel.setScaleY(0.96f);
            panel.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(180).start();
            if (!buttons.isEmpty()) buttons.get(0).requestFocus();
        });
        dialog.show();
    }

    private TextView createActionButton(AppAction action, Dialog dialog) {
        TextView button = new TextView(this);
        button.setText(action.title + "\n" + action.subtitle);
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(18), 0, dp(14), 0);
        button.setTextColor(action.danger ? Color.rgb(255, 176, 176) : Color.WHITE);
        button.setBackground(actionButtonBackground(false, action.danger));
        button.setFocusable(true);
        button.setClickable(true);
        button.setOnFocusChangeListener((v, hasFocus) -> {
            v.setBackground(actionButtonBackground(hasFocus, action.danger));
            button.setTextColor(hasFocus ? Color.BLACK : (action.danger ? Color.rgb(255, 176, 176) : Color.WHITE));
            v.animate().scaleX(hasFocus ? 1.04f : 1f).scaleY(hasFocus ? 1.04f : 1f)
                    .translationZ(hasFocus ? dp(10) : 0).setDuration(130).start();
        });
        button.setOnClickListener(v -> {
            dialog.dismiss();
            action.action.run();
        });
        return button;
    }

    private GradientDrawable actionPanelBackground() {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(235, 28, 28, 28));
        bg.setCornerRadius(dp(28));
        bg.setStroke(dp(1), Color.argb(90, 255, 255, 255));
        return bg;
    }

    private GradientDrawable actionButtonBackground(boolean focused, boolean danger) {
        GradientDrawable bg = new GradientDrawable();
        int color = focused ? Color.argb(245, 255, 255, 255)
                : (danger ? Color.argb(155, 86, 38, 38) : Color.argb(175, 68, 68, 68));
        bg.setColor(color);
        bg.setCornerRadius(dp(16));
        bg.setStroke(focused ? dp(3) : dp(1),
                focused ? Color.WHITE : Color.argb(42, 255, 255, 255));
        return bg;
    }

    private void launchEntry(AppEntry entry, boolean dismissFolder) {
        if (dismissFolder) dismissFolderOverlay();
        try {
            startExternalActivity(entry.launchIntent);
        } catch (ActivityNotFoundException ex) {
            toast("无法打开 " + entry.label);
        }
    }

    private void showFolder(AppEntry folder) {
        if (folder.children == null || folder.children.isEmpty()) {
            toast("Folder is empty.");
            return;
        }
        dismissFolderOverlay();
        folderLastFocus = getCurrentFocus();
        setFolderBackgroundBlur(true);
        PackageManager pm = getPackageManager();

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        dialog.setOnDismissListener(d -> {
            folderOverlay = null;
            folderDialog = null;
            setFolderBackgroundBlur(false);
            if (folderLastFocus != null) {
                folderLastFocus.requestFocus();
                folderLastFocus = null;
            }
        });
        folderDialog = dialog;

        FrameLayout overlay = new FrameLayout(this);
        folderOverlay = overlay;
        overlay.setFocusable(true);
        overlay.setFocusableInTouchMode(true);
        overlay.setClickable(true);
        overlay.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        overlay.setBackgroundColor(Color.argb(170, 226, 238, 236));
        overlay.setOnClickListener(v -> dialog.dismiss());
        overlay.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            return false;
        });

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(48), dp(32), dp(48), dp(40));
        panel.setClickable(true);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        GradientDrawable panelBg = new GradientDrawable();
        panelBg.setColor(Color.argb(184, 246, 248, 247));
        panelBg.setCornerRadius(dp(22));
        panelBg.setStroke(dp(1), Color.argb(170, 255, 255, 255));
        panel.setBackground(panelBg);
        panel.setElevation(dp(10));

        TextView title = new TextView(this);
        title.setText(folder.label);
        title.setTextColor(Color.rgb(76, 72, 84));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, dp(46));
        titleLp.bottomMargin = dp(18);
        panel.addView(title, titleLp);

        int panelWidth = Math.min(dp(760), getResources().getDisplayMetrics().widthPixels - dp(140));
        int panelHeight = Math.min(dp(540), getResources().getDisplayMetrics().heightPixels - dp(120));
        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFillViewport(false);

        FrameLayout grid = new FrameLayout(this);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, dp(8));
        int panelHorizontalPadding = dp(48) * 2;
        int tileWidth = Math.min(dp(172), Math.max(dp(146), (panelWidth - panelHorizontalPadding) / 3));
        int tileHeight = dp(130);
        int columns = 3;
        int rows = Math.max(1, (int) Math.ceil(folder.children.size() / 3f));
        int gridWidth = tileWidth * columns;
        int gridHeight = rows * tileHeight;
        List<View> folderCards = new ArrayList<>();
        for (int i = 0; i < folder.children.size(); i++) {
            AppEntry child = folder.children.get(i);
            View cell = createFolderAppTile(pm, child, tileWidth, folder.label);
            FrameLayout.LayoutParams cellLp = new FrameLayout.LayoutParams(tileWidth, tileHeight);
            cellLp.leftMargin = (i % columns) * tileWidth;
            cellLp.topMargin = (i / columns) * tileHeight;
            grid.addView(cell, cellLp);
            if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
                folderCards.add(((LinearLayout) cell).getChildAt(0));
            }
        }
        installFolderKeyNavigation(folderCards, scroll, columns, folder.label);
        FrameLayout gridHolder = new FrameLayout(this);
        gridHolder.setClipChildren(false);
        gridHolder.setClipToPadding(false);
        gridHolder.addView(grid, new FrameLayout.LayoutParams(gridWidth, gridHeight, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        scroll.addView(gridHolder, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.CENTER);
        overlay.addView(panel, panelLp);
        dialog.setContentView(overlay, new ViewGroup.LayoutParams(-1, -1));
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams attrs = window.getAttributes();
            attrs.dimAmount = 0f;
            window.setAttributes(attrs);
        }
        dialog.show();
        Window shownWindow = dialog.getWindow();
        if (shownWindow != null) {
            shownWindow.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        overlay.requestFocus();
        panel.setScaleX(0.96f);
        panel.setScaleY(0.96f);
        panel.setAlpha(0f);
        panel.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(130).start();
        grid.post(() -> {
            if (!folderCards.isEmpty()) {
                View target = null;
                if (!TextUtils.isEmpty(pendingFolderFocusPackage)) {
                    for (View card : folderCards) {
                        Object tag = card.getTag();
                        if (TextUtils.equals(pendingFolderFocusPackage, tag == null ? null : tag.toString())) {
                            target = card;
                            break;
                        }
                    }
                    pendingFolderFocusPackage = null;
                }
                if (target == null) target = folderCards.get(0);
                target.requestFocus();
            }
        });
    }

    private View createFolderAppTile(PackageManager pm, AppEntry entry, int tileWidth, String folderName) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setClipChildren(false);
        cell.setClipToPadding(false);
        GridLayout.LayoutParams gridLp = new GridLayout.LayoutParams();
        gridLp.width = tileWidth;
        gridLp.height = dp(130);
        gridLp.setMargins(dp(9), dp(0), dp(9), dp(8));
        cell.setLayoutParams(gridLp);

        FrameLayout card = new FrameLayout(this);
        card.setFocusable(true);
        card.setClickable(true);
        card.setTag(entry.packageName);
        card.setClipToOutline(true);
        card.setElevation(dp(5));
        card.setTranslationZ(dp(2));
        boolean hasBanner = entry.banner != null;
        card.setBackgroundResource(hasBanner ? R.drawable.app_card_banner : R.drawable.app_card);
        if (hasBanner) {
            card.setForeground(getResources().getDrawable(R.drawable.app_card_banner_foreground));
        }
        int cardWidth = tileWidth - dp(22);
        int cardHeight = Math.round(cardWidth * 9f / 16f);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(cardWidth, cardHeight);
        cell.addView(card, cardLp);

        ImageView image = new ImageView(this);
        if (hasBanner) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.FIT_XY);
            card.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = loadAppIcon(pm, entry);
            int baseColor = dominantColor(appIcon);
            card.setBackground(buildIconCardBackground(baseColor));
            card.setPadding(dp(14), dp(6), dp(12), dp(6));
            image.setImageDrawable(appIcon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int iconSize = Math.min(dp(50), cardHeight - dp(18));
            FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(iconSize, iconSize, Gravity.LEFT | Gravity.CENTER_VERTICAL);
            card.addView(image, iconLp);

            TextView cardLabel = new TextView(this);
            cardLabel.setText(entry.label);
            cardLabel.setTextColor(readableTextColor(baseColor));
            cardLabel.setTextSize(13);
            cardLabel.setTypeface(Typeface.DEFAULT_BOLD);
            cardLabel.setGravity(Gravity.CENTER_VERTICAL);
            cardLabel.setSingleLine(true);
            cardLabel.setMaxLines(1);
            cardLabel.setEllipsize(TextUtils.TruncateAt.END);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = iconSize + dp(14);
            card.addView(cardLabel, textLp);
        }

        TextView label = new TextView(this);
        label.setText(entry.label);
        label.setTextColor(Color.WHITE);
        label.setTextSize(16);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        label.setGravity(Gravity.CENTER);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(cardWidth, dp(24));
        labelLp.topMargin = dp(3);
        cell.addView(label, labelLp);

        card.setOnClickListener(v -> {
            if (TextUtils.equals(movingFolderName, folderName)
                    && TextUtils.equals(movingFolderPackage, entry.packageName)) {
                finishFolderMove(folderName, entry.packageName);
                return;
            }
            dismissFolderOverlay();
            try {
                startExternalActivity(entry.launchIntent);
            } catch (ActivityNotFoundException ex) {
                toast("无法打开 " + entry.label);
            }
        });
        card.setOnLongClickListener(v -> {
            showFolderChildMenu(pm, entry, folderName);
            return true;
        });
        card.setOnFocusChangeListener((v, hasFocus) -> {
            animateFocus(v, hasFocus, 1.07f);
            v.setElevation(hasFocus ? dp(12) : dp(5));
            v.setTranslationZ(hasFocus ? dp(8) : dp(2));
            label.setShadowLayer(hasFocus ? dp(4) : 0, 0, hasFocus ? dp(2) : 0, Color.argb(180, 0, 0, 0));
            if (hasFocus && TextUtils.equals(movingFolderName, folderName)
                    && TextUtils.equals(movingFolderPackage, entry.packageName)) {
                v.postDelayed(() -> startFolderMoveWiggle(v, folderName, entry.packageName), 150);
            }
        });
        if (TextUtils.equals(movingFolderName, folderName)
                && TextUtils.equals(movingFolderPackage, entry.packageName)) {
            startFolderMoveWiggle(card, folderName, entry.packageName);
        }
        return cell;
    }

    private void installFolderKeyNavigation(List<View> cards, ScrollView scroll, int columns, String folderName) {
        for (int i = 0; i < cards.size(); i++) {
            final int index = i;
            View card = cards.get(i);
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                Object tag = v.getTag();
                String packageName = tag == null ? "" : tag.toString();
                boolean movingInsideFolder = TextUtils.equals(movingFolderName, folderName)
                        && TextUtils.equals(movingFolderPackage, packageName);
                if (movingInsideFolder) {
                    int delta = 0;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) delta = -1;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) delta = 1;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) delta = -columns;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) delta = columns;
                    else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                            || keyCode == KeyEvent.KEYCODE_ENTER
                            || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                        finishFolderMove(folderName, packageName);
                        return true;
                    } else if (keyCode == KeyEvent.KEYCODE_BACK) {
                        cancelFolderMove(true);
                        return true;
                    } else {
                        return false;
                    }
                    if (delta != 0) {
                        moveFolderChildByDelta(folderName, packageName, delta);
                        moveFolderCardInPlace(cards, v, delta, scroll, columns, folderName);
                    }
                    return true;
                }
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    dismissFolderOverlay();
                    return true;
                }
                int target = index;
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    target = index % columns == 0 ? index : index - 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    target = (index % columns == columns - 1 || index + 1 >= cards.size()) ? index : index + 1;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                    target = index - columns >= 0 ? index - columns : index;
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                    if (index + columns < cards.size()) {
                        target = index + columns;
                    } else {
                        int nextRowStart = ((index / columns) + 1) * columns;
                        target = nextRowStart < cards.size() ? nextRowStart : index;
                    }
                } else {
                    return false;
                }
                View next = cards.get(target);
                next.requestFocus();
                scroll.post(() -> next.requestRectangleOnScreen(new Rect(0, 0, next.getWidth(), next.getHeight()), false));
                return true;
            });
        }
    }

    private void moveFolderCardInPlace(List<View> cards, View focusedCard, int delta,
                                       ScrollView scroll, int columns, String folderName) {
        int from = cards.indexOf(focusedCard);
        if (from < 0) return;
        int to = Math.max(0, Math.min(cards.size() - 1, from + delta));
        if (from == to) return;

        View cell = (View) focusedCard.getParent();
        if (!(cell.getParent() instanceof FrameLayout)) return;
        FrameLayout grid = (FrameLayout) cell.getParent();
        grid.removeView(cell);
        grid.addView(cell, to);
        cards.remove(from);
        cards.add(to, focusedCard);

        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            ViewGroup.LayoutParams rawLp = child.getLayoutParams();
            if (!(rawLp instanceof FrameLayout.LayoutParams)) continue;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) rawLp;
            lp.leftMargin = (i % columns) * lp.width;
            lp.topMargin = (i / columns) * lp.height;
            child.setLayoutParams(lp);
        }

        installFolderKeyNavigation(cards, scroll, columns, folderName);
        focusedCard.requestFocus();
        focusedCard.animate().scaleX(1.11f).scaleY(1.11f).setDuration(70)
                .withEndAction(() -> focusedCard.animate().scaleX(1.07f).scaleY(1.07f).setDuration(90)
                        .withEndAction(() -> startFolderMoveWiggle(focusedCard, folderName,
                                focusedCard.getTag() == null ? "" : focusedCard.getTag().toString()))
                        .start())
                .start();
        scroll.post(() -> focusedCard.requestRectangleOnScreen(
                new Rect(0, 0, focusedCard.getWidth(), focusedCard.getHeight()), false));
    }

    private FrameLayout createFolderMiniCard(PackageManager pm, AppEntry entry, int width, int height) {
        FrameLayout mini = new FrameLayout(this);
        mini.setClipToOutline(true);
        mini.setBackgroundColor(Color.TRANSPARENT);

        ImageView image = new ImageView(this);
        if (entry.banner != null) {
            image.setImageDrawable(entry.banner);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            mini.addView(image, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));
        } else {
            Drawable appIcon = loadAppIcon(pm, entry);
            int baseColor = dominantColor(appIcon);
            mini.setBackground(buildMiniIconCardBackground(baseColor));
            mini.setPadding(dp(4), dp(2), dp(3), dp(2));
            image.setImageDrawable(appIcon);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int icon = Math.max(dp(12), Math.min(dp(20), height - dp(8)));
            mini.addView(image, new FrameLayout.LayoutParams(icon, icon, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            TextView name = new TextView(this);
            name.setText(entry.label);
            name.setTextColor(readableTextColor(baseColor));
            name.setTextSize(5);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER_VERTICAL);
            FrameLayout.LayoutParams textLp = new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER_VERTICAL);
            textLp.leftMargin = icon + dp(4);
            mini.addView(name, textLp);
        }
        return mini;
    }

    private GradientDrawable buildMiniIconCardBackground(int baseColor) {
        int start = adjustColor(baseColor, 1.22f);
        int end = adjustColor(baseColor, 0.92f);
        if (isLowSaturation(baseColor)) {
            start = Color.rgb(238, 244, 251);
            end = Color.rgb(213, 228, 246);
        }
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{start, end});
        bg.setCornerRadius(dp(4));
        return bg;
    }

    private void showFolderChildMenu(PackageManager pm, AppEntry entry, String folderName) {
        View restoreFocus = getCurrentFocus();
        List<AppAction> actions = new ArrayList<>();
        actions.add(new AppAction("打开", "启动应用", () -> launchEntry(entry, true), false));
        actions.add(new AppAction("Move", "Reorder in folder", () -> {
            movingFolderName = folderName;
            movingFolderPackage = entry.packageName;
            movingFolderOriginalOrder = prefs.getString(folderOrderKey(folderName), "");
            if (restoreFocus != null) {
                restoreFocus.postDelayed(() -> {
                    restoreFocus.requestFocus();
                    startFolderMoveWiggle(restoreFocus, folderName, entry.packageName);
                }, 90);
            }
        }, false));
        actions.add(new AppAction("Remove", "Remove from folder", () -> removeAppFromFolder(entry.packageName), true));
        actions.add(new AppAction("隐藏", "从桌面隐藏", () -> hideApp(entry.packageName), true));
        actions.add(new AppAction("信息", "应用信息", () -> openAppInfo(entry.packageName), false));
        actions.add(new AppAction("图标", "修改图标", () -> toast("自定义图标选择器即将推出。"), false));
        showAppActionDialog(entry, actions, restoreFocus);
    }

    private void cancelFolderMove(boolean keepFolderOpen) {
        String folderName = movingFolderName;
        String packageName = movingFolderPackage;
        if (!TextUtils.isEmpty(folderName) && movingFolderOriginalOrder != null) {
            prefs.edit().putString(folderOrderKey(folderName), movingFolderOriginalOrder).apply();
        }
        movingFolderName = null;
        movingFolderPackage = null;
        movingFolderOriginalOrder = null;
        if (keepFolderOpen && !TextUtils.isEmpty(folderName)) {
            refreshFolder(folderName, packageName);
        }
        toast("Move cancelled");
    }

    private void finishFolderMove(String folderName, String packageName) {
        View focused = getCurrentFocus();
        movingFolderName = null;
        movingFolderPackage = null;
        movingFolderOriginalOrder = null;
        pendingFolderFocusPackage = packageName;
        if (focused != null) {
            focused.animate().cancel();
            focused.setRotation(0f);
            focused.setScaleX(1.07f);
            focused.setScaleY(1.07f);
            focused.requestFocus();
        }
        toast("位置已保存");
    }

    private void requestFolderCellFocus(View cell) {
        if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
            ((LinearLayout) cell).getChildAt(0).requestFocus();
        } else if (cell != null) {
            cell.requestFocus();
        }
    }

    private void dismissFolderOverlay() {
        if (folderDialog != null) {
            folderDialog.dismiss();
            return;
        }
        if (folderOverlay == null) return;
        folderOverlay = null;
        setFolderBackgroundBlur(false);
        if (folderLastFocus != null) {
            folderLastFocus.requestFocus();
            folderLastFocus = null;
        }
    }

    private void setFolderBackgroundBlur(boolean enabled) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return;
        try {
            View target = launcherRoot != null ? launcherRoot : homeScroll;
            if (target == null) return;
            if (enabled) {
                target.setRenderEffect(RenderEffect.createBlurEffect(dp(32), dp(32), Shader.TileMode.CLAMP));
            } else {
                target.setRenderEffect(null);
            }
        } catch (Exception ignored) {
        }
    }

    private void promptAddToFolder(AppEntry entry) {
        Map<String, String> existingAssignments = readPackageFolders();
        List<String> folders = new ArrayList<>();
        for (String folder : existingAssignments.values()) {
            if (!TextUtils.isEmpty(folder) && !folders.contains(folder)) folders.add(folder);
        }
        Collections.sort(folders, Collator.getInstance(Locale.getDefault()));
        folders.add("+ Create New Folder");
        String[] items = folders.toArray(new String[0]);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Add to Folder")
                .setItems(items, (d, which) -> {
                    String selected = items[which];
                    if ("+ Create New Folder".equals(selected)) {
                        promptCreateFolder(entry);
                    } else {
                        addAppToFolder(entry, selected);
                    }
                })
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            try {
                dialog.getListView().requestFocus();
            } catch (Exception ignored) {
            }
        });
        dialog.show();
    }

    private void promptCreateFolder(AppEntry entry) {
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("Folder name");
        input.setText(prefs.getString("last_folder_name", "TV Apps"));
        input.setSelectAllOnFocus(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        new AlertDialog.Builder(this)
                .setTitle("Add to Folder")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (d, which) -> {
                    String folder = input.getText().toString().trim();
                    if (TextUtils.isEmpty(folder)) {
                        toast("Folder name is empty.");
                        return;
                    }
                    addAppToFolder(entry, folder);
                })
                .show();
    }

    private void addAppToFolder(AppEntry entry, String folder) {
        Map<String, String> folders = readPackageFolders();
        folders.put(entry.packageName, folder);
        savePackageFolders(folders);
        prefs.edit().putString("last_folder_name", folder).apply();
        loadApps();
    }

    private void removeAppFromFolder(String packageName) {
        Map<String, String> folders = readPackageFolders();
        folders.remove(packageName);
        savePackageFolders(folders);
        focusAfterLoadPackage = packageName;
        loadApps();
        toast("Removed from folder");
    }

    private void removeFolder(String folderName) {
        Map<String, String> folders = readPackageFolders();
        List<String> removedPackages = new ArrayList<>();
        for (Map.Entry<String, String> entry : new ArrayList<>(folders.entrySet())) {
            if (TextUtils.equals(entry.getValue(), folderName)) {
                removedPackages.add(entry.getKey());
                folders.remove(entry.getKey());
            }
        }
        savePackageFolders(folders);
        if (!removedPackages.isEmpty()) focusAfterLoadPackage = removedPackages.get(0);
        loadApps();
        toast("Folder removed");
    }

    private void hideApp(String packageName) {
        Set<String> hidden = new HashSet<>(prefs.getStringSet("hidden_apps", new HashSet<>()));
        hidden.add(packageName);
        prefs.edit().putStringSet("hidden_apps", hidden).apply();
        invalidateAppCache();
        loadApps();
    }

    private void moveAppBefore(String moving, String target) {
        if (TextUtils.equals(moving, target)) return;
        List<AppEntry> current = buildDisplayEntries(queryLaunchableApps(getPackageManager()));
        applySavedDisplayOrder(current);
        List<String> order = new ArrayList<>();
        for (AppEntry entry : current) {
            if (!order.contains(entry.packageName)) order.add(entry.packageName);
        }
        order.remove(moving);
        int targetIndex = order.indexOf(target);
        if (targetIndex < 0) order.add(moving);
        else order.add(targetIndex, moving);
        saveOrder(order);
    }

    private void moveAppByDelta(String packageName, int delta) {
        List<AppEntry> current = buildDisplayEntries(queryLaunchableApps(getPackageManager()));
        applySavedDisplayOrder(current);
        List<String> order = new ArrayList<>();
        for (AppEntry entry : current) {
            if (!order.contains(entry.packageName)) order.add(entry.packageName);
        }
        int from = order.indexOf(packageName);
        if (from < 0) return;
        int to = Math.max(0, Math.min(order.size() - 1, from + delta));
        if (from == to) return;
        order.remove(from);
        order.add(to, packageName);
        saveOrder(order);
    }

    private void moveFolderChildByDelta(String folderName, String packageName, int delta) {
        List<String> order = currentFolderPackageOrder(folderName);
        int from = order.indexOf(packageName);
        if (from < 0) return;
        int to = Math.max(0, Math.min(order.size() - 1, from + delta));
        if (from == to) return;
        order.remove(from);
        order.add(to, packageName);
        saveFolderOrder(folderName, order);
    }

    private List<String> currentFolderPackageOrder(String folderName) {
        Map<String, String> folders = readPackageFolders();
        List<AppEntry> apps = queryLaunchableApps(getPackageManager());
        List<AppEntry> children = new ArrayList<>();
        for (AppEntry app : apps) {
            if (TextUtils.equals(folderName, folders.get(app.packageName))) {
                children.add(app);
            }
        }
        applySavedFolderOrder(folderName, children);
        List<String> order = new ArrayList<>();
        for (AppEntry child : children) {
            if (!order.contains(child.packageName)) order.add(child.packageName);
        }
        return order;
    }

    private void refreshFolder(String folderName, String focusPackage) {
        pendingFolderFocusPackage = focusPackage;
        AppEntry folder = findFolderEntry(folderName);
        if (folder == null || folder.children == null || folder.children.isEmpty()) {
            dismissFolderOverlay();
            loadApps();
            return;
        }
        showFolder(folder);
    }

    private AppEntry findFolderEntry(String folderName) {
        List<AppEntry> entries = buildDisplayEntries(queryLaunchableApps(getPackageManager()));
        applySavedDisplayOrder(entries);
        String target = "folder:" + folderName;
        for (AppEntry entry : entries) {
            if (entry.isFolder && TextUtils.equals(entry.packageName, target)) return entry;
        }
        return null;
    }

    private boolean moveFocusedTileByDelta(View focusedCard, String packageName, int delta) {
        if (appsGrid == null) return true;
        View cell = (View) focusedCard.getParent();
        int from = appsGrid.indexOfChild(cell);
        int to = Math.max(0, Math.min(appsGrid.getChildCount() - 1, from + delta));
        if (from < 0 || from == to) return true;

        moveAppByDelta(packageName, delta);
        appsGrid.removeViewAt(from);
        appsGrid.addView(cell, to);
        cell.post(() -> {
            if (cell instanceof LinearLayout && ((LinearLayout) cell).getChildCount() > 0) {
                View card = ((LinearLayout) cell).getChildAt(0);
                card.requestFocus();
                card.animate().scaleX(1.12f).scaleY(1.12f).setDuration(80)
                        .withEndAction(() -> card.animate().scaleX(1.08f).scaleY(1.08f).setDuration(90).start())
                        .start();
            }
        });
        return true;
    }

    private List<String> readOrder() {
        String raw = prefs.getString("app_order", "");
        List<String> order = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return order;
        for (String part : raw.split(",")) {
            if (!TextUtils.isEmpty(part)) order.add(part);
        }
        return order;
    }

    private void saveOrder(List<String> order) {
        prefs.edit().putString("app_order", TextUtils.join(",", order)).apply();
    }

    private List<String> readFolderOrder(String folderName) {
        String raw = prefs.getString(folderOrderKey(folderName), "");
        List<String> order = new ArrayList<>();
        if (TextUtils.isEmpty(raw)) return order;
        for (String part : raw.split(",")) {
            if (!TextUtils.isEmpty(part)) order.add(part);
        }
        return order;
    }

    private void saveFolderOrder(String folderName, List<String> order) {
        prefs.edit().putString(folderOrderKey(folderName), TextUtils.join(",", order)).apply();
    }

    private String folderOrderKey(String folderName) {
        return "folder_order_" + Uri.encode(folderName == null ? "" : folderName);
    }

    private Map<String, String> readPackageFolders() {
        Map<String, String> result = new LinkedHashMap<>();
        String raw = prefs.getString("app_folders", "");
        if (TextUtils.isEmpty(raw)) return result;
        for (String item : raw.split("\\|")) {
            int split = item.indexOf('=');
            if (split <= 0) continue;
            result.put(Uri.decode(item.substring(0, split)), Uri.decode(item.substring(split + 1)));
        }
        return result;
    }

    private void savePackageFolders(Map<String, String> folders) {
        List<String> parts = new ArrayList<>();
        for (Map.Entry<String, String> entry : folders.entrySet()) {
            parts.add(Uri.encode(entry.getKey()) + "=" + Uri.encode(entry.getValue()));
        }
        prefs.edit().putString("app_folders", TextUtils.join("|", parts)).apply();
    }

    private TextView chip(String text, boolean primary) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setGravity(Gravity.CENTER);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setTextSize(primary ? 19 : 16);
        chip.setTextColor(primary ? Color.rgb(18, 18, 18) : Color.WHITE);
        chip.setTypeface(primary ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        chip.setBackgroundResource(primary ? R.drawable.light_chip : R.drawable.glass_chip);
        chip.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.06f));
        return chip;
    }

    private View iconChip(String icon) {
        IconChipView chip = new IconChipView(this, icon);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setBackgroundResource(R.drawable.glass_chip);
        chip.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.06f));
        return chip;
    }

    private View imageIconChip(int resId, int iconDp) {
        FrameLayout chip = new FrameLayout(this);
        chip.setFocusable(true);
        chip.setClickable(true);
        chip.setBackgroundColor(Color.TRANSPARENT);
        chip.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.15f));
        ImageView icon = new ImageView(this);
        icon.setImageResource(resId);
        icon.setColorFilter(Color.argb(220, 255, 255, 255));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        chip.addView(icon, new FrameLayout.LayoutParams(dp(iconDp), dp(iconDp), Gravity.CENTER));
        return chip;
    }

    private TextView settingsMenuItem(String text, boolean selected) {
        TextView item = new TextView(this);
        item.setText(text);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(18), 0, dp(18), 0);
        item.setTextSize(24);
        item.setSingleLine(true);
        item.setTextColor(selected ? Color.BLACK : Color.argb(215, 255, 255, 255));
        item.setTypeface(selected ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        item.setBackgroundResource(selected ? R.drawable.settings_menu_selected : R.drawable.settings_menu_item);
        item.setFocusable(true);
        item.setClickable(true);
        item.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.0f));
        return item;
    }

    private void addModeOption(LinearLayout options, String title, String subtitle, String mode) {
        addOption(options, title, subtitle, mode.equals(bgMode()), v -> {
            rememberSettingsState(v, title);
            setBgMode(mode);
        });
    }

    private void addAppsOption(LinearLayout options, int count) {
        String title = count + " Apps";
        addOption(options, title, "", appsPerRow() == count, v -> {
            rememberSettingsState(v, title);
            prefs.edit().putInt("apps_per_row", count).apply();
            showSettings("控制");
        });
    }

    private void addToggle(LinearLayout options, String title, String subtitle, String key, String section) {
        addOption(options, title, subtitle, prefBool(key), v -> {
            rememberSettingsState(v, title);
            prefs.edit().putBoolean(key, !prefBool(key)).apply();
            showSettings(section);
        });
    }

    private void rememberSettingsState(View view, String title) {
        pendingSettingsFocusTitle = title;
        View current = view;
        while (current != null) {
            if (current instanceof ScrollView) {
                pendingSettingsScrollY = ((ScrollView) current).getScrollY();
                return;
            }
            if (!(current.getParent() instanceof View)) break;
            current = (View) current.getParent();
        }
        pendingSettingsScrollY = -1;
    }

    private boolean prefBool(String key) {
        if ("override_back_exit".equals(key)
                || "start_on_boot".equals(key)
                || "auto_open_on_wake".equals(key)
                || "override_current_launcher".equals(key)
                || "use_24h".equals(key)) {
            return prefs.getBoolean(key, true);
        }
        return prefs.getBoolean(key, false);
    }

    private void addHeading(LinearLayout options, String text) {
        TextView heading = new TextView(this);
        heading.setText(text);
        heading.setTextColor(Color.WHITE);
        heading.setTextSize(24);
        heading.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(22);
        lp.bottomMargin = dp(14);
        options.addView(heading, lp);
    }

    private void addOption(LinearLayout options, String title, String subtitle, boolean checked, View.OnClickListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(22), 0, dp(18), 0);
        row.setBackgroundResource(R.drawable.settings_option);
        row.setFocusable(true);
        row.setClickable(true);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setOnFocusChangeListener((v, hasFocus) -> animateFocus(v, hasFocus, 1.0f));
        if (listener != null) row.setOnClickListener(listener);

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(21);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(titleView);

        if (!TextUtils.isEmpty(subtitle)) {
            TextView sub = new TextView(this);
            sub.setText(subtitle);
            sub.setTextColor(Color.argb(170, 255, 255, 255));
            sub.setTextSize(13);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(sub);
        }

        TextView check = new TextView(this);
        check.setText(checked ? String.valueOf((char) 0x2713) : "");
        check.setTextColor(Color.WHITE);
        check.setTextSize(26);
        check.setGravity(Gravity.CENTER);
        row.addView(check, new LinearLayout.LayoutParams(dp(58), -1));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(TextUtils.isEmpty(subtitle) ? 58 : 72));
        lp.bottomMargin = dp(12);
        options.addView(row, lp);
        if (title.equals(pendingSettingsFocusTitle)) {
            pendingSettingsFocusTitle = null;
            row.post(row::requestFocus);
        }
    }

    private void addNote(LinearLayout options, String text) {
        TextView note = new TextView(this);
        note.setText(text);
        note.setTextColor(Color.argb(145, 255, 255, 255));
        note.setTextSize(15);
        note.setPadding(dp(6), dp(6), dp(6), dp(18));
        options.addView(note, new LinearLayout.LayoutParams(-1, -2));
    }

    private void openPicker(String type, int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType(type);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, requestCode);
    }

    private void askWallpaperUrl() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        input.setSingleLine(true);
        input.setHint("https://example.com/wallpaper.jpg");
        input.setText(prefs.getString("bg_url", ""));
        new AlertDialog.Builder(this)
                .setTitle("网络图片")
                .setView(input)
                .setPositiveButton("Download", (dialog, which) -> downloadWallpaper(input.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadWallpaper(String url) {
        if (TextUtils.isEmpty(url)) return;
        toast("Downloading wallpaper...");
        new Thread(() -> {
            try (InputStream in = new URL(url).openStream();
                 FileOutputStream out = new FileOutputStream(new File(getFilesDir(), "url_wallpaper.img"))) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
                prefs.edit().putString("bg_mode", "url").putString("bg_url", url).apply();
                runOnUiThread(() -> {
                    toast("URL wallpaper applied");
                    showSettings("主题模式");
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Could not download wallpaper"));
            }
        }).start();
    }

    private void setBgMode(String mode) {
        prefs.edit().putString("bg_mode", mode).apply();
        showSettings("主题模式");
    }

    private void showGradientPicker() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        FrameLayout dim = new FrameLayout(this);
        dim.setBackgroundColor(Color.argb(165, 0, 0, 0));
        dim.setPadding(dp(54), dp(38), dp(54), dp(38));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(30), dp(24), dp(30), dp(24));
        panel.setBackground(actionPanelBackground());
        int screenW = getResources().getDisplayMetrics().widthPixels;
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int panelWidth = Math.min(dp(1080), screenW - dp(108));
        int panelHeight = Math.min(dp(616), screenH - dp(64));
        FrameLayout.LayoutParams panelLp = new FrameLayout.LayoutParams(panelWidth, panelHeight, Gravity.CENTER);
        dim.addView(panel, panelLp);

        TextView title = new TextView(this);
        title.setText("自定义渐变");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView hint = new TextView(this);
        hint.setText("D-pad to preview - OK to apply - Back to cancel");
        hint.setTextColor(Color.argb(150, 255, 255, 255));
        hint.setTextSize(14);
        hint.setGravity(Gravity.CENTER);
        panel.addView(hint, new LinearLayout.LayoutParams(-1, dp(28)));

        ScrollView scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipToPadding(false);
        scroll.setPadding(0, dp(10), 0, dp(18));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        GridLayout grid = new GridLayout(this);
        int columns = panelWidth >= dp(980) ? 5 : 4;
        grid.setColumnCount(columns);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        grid.setPadding(0, 0, 0, dp(28));
        scroll.addView(grid, new ScrollView.LayoutParams(-1, -2));

        List<View> previews = new ArrayList<>();
        String selectedId = prefs.getString("gradient_id", "emerald_night");
        String originalMode = prefs.getString("bg_mode", "light");
        String originalId = selectedId;
        boolean[] applied = new boolean[]{false};
        int cardWidth = Math.max(dp(160), (panelWidth - dp(60) - dp(16) * columns) / columns);
        int cardHeight = Math.round(cardWidth * 0.52f);
        View[] selectedView = new View[1];
        for (GradientPreset preset : gradientPresets()) {
            boolean selected = preset.id.equals(selectedId);
            FrameLayout preview = new FrameLayout(this);
            preview.setBackground(gradientDrawable(preset, selected, false));
            preview.setFocusable(true);
            preview.setClickable(true);
            preview.setClipChildren(false);
            preview.setClipToPadding(false);

            TextView name = new TextView(this);
            name.setText(preset.name);
            name.setTextColor(Color.WHITE);
            name.setTextSize(13);
            name.setTypeface(Typeface.DEFAULT_BOLD);
            name.setGravity(Gravity.BOTTOM | Gravity.LEFT);
            name.setPadding(dp(12), 0, dp(38), dp(10));
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            preview.addView(name, new FrameLayout.LayoutParams(-1, -1));

            TextView check = new TextView(this);
            check.setText(selected ? String.valueOf((char) 0x2713) : "");
            check.setTextColor(Color.WHITE);
            check.setTextSize(22);
            check.setTypeface(Typeface.DEFAULT_BOLD);
            check.setGravity(Gravity.CENTER);
            FrameLayout.LayoutParams checkLp = new FrameLayout.LayoutParams(dp(34), dp(34), Gravity.RIGHT | Gravity.TOP);
            checkLp.setMargins(0, dp(5), dp(6), 0);
            preview.addView(check, checkLp);
            if (selected) selectedView[0] = preview;

            preview.setOnFocusChangeListener((v, hasFocus) -> {
                v.setBackground(gradientDrawable(preset, selected, hasFocus));
                v.animate().scaleX(hasFocus ? 1.05f : 1f).scaleY(hasFocus ? 1.05f : 1f).setDuration(130).start();
                if (hasFocus) {
                    scroll.post(() -> scroll.requestChildRectangleOnScreen(grid,
                            new Rect(v.getLeft() - dp(12), v.getTop() - dp(12),
                                    v.getRight() + dp(12), v.getBottom() + dp(28)), false));
                    if (launcherRoot != null) launcherRoot.setBackground(gradientDrawable(preset, false, false));
                }
            });
            preview.setOnClickListener(v -> {
                applied[0] = true;
                prefs.edit().putString("bg_mode", "gradient").putString("gradient_id", preset.id).apply();
                dialog.dismiss();
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = cardWidth;
            lp.height = cardHeight;
            lp.setMargins(dp(8), dp(8), dp(8), dp(8));
            grid.addView(preview, lp);
            previews.add(preview);
        }

        dialog.setContentView(dim);
        dialog.setOnShowListener(d -> {
            View first = selectedView[0] != null ? selectedView[0] : (previews.isEmpty() ? null : previews.get(0));
            if (first != null) first.requestFocus();
        });
        dialog.setOnDismissListener(d -> {
            if (!applied[0]) {
                prefs.edit().putString("bg_mode", originalMode).putString("gradient_id", originalId).apply();
            }
            showSettings("主题模式");
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
    }

    private GradientDrawable currentGradientDrawable() {
        String id = prefs.getString("gradient_id", "emerald_night");
        for (GradientPreset preset : gradientPresets()) {
            if (preset.id.equals(id)) return gradientDrawable(preset, false, false);
        }
        return gradientDrawable(gradientPresets().get(0), false, false);
    }

    private GradientDrawable gradientDrawable(GradientPreset preset, boolean selected, boolean focused) {
        GradientDrawable.Orientation orientation = preset.vertical
                ? GradientDrawable.Orientation.TOP_BOTTOM
                : GradientDrawable.Orientation.TL_BR;
        GradientDrawable bg = new GradientDrawable(orientation, preset.colors);
        bg.setCornerRadius(dp(16));
        bg.setStroke(focused ? dp(3) : (selected ? dp(2) : dp(1)),
                focused ? Color.WHITE : (selected ? Color.argb(220, 255, 255, 255) : Color.argb(55, 255, 255, 255)));
        return bg;
    }

    private List<GradientPreset> gradientPresets() {
        List<GradientPreset> items = new ArrayList<>();
        items.add(new GradientPreset("emerald_night", "Emerald Night", false, "#102B24", "#185E4C", "#6BD08D"));
        items.add(new GradientPreset("ocean_blue", "Ocean Blue", false, "#0C2B5E", "#1D79D8", "#7FD9FF"));
        items.add(new GradientPreset("midnight_purple", "Midnight Purple", false, "#17132F", "#4B2B84", "#A765FF"));
        items.add(new GradientPreset("sunset_orange", "Sunset Orange", false, "#32170E", "#E75E35", "#FFD37A"));
        items.add(new GradientPreset("aurora_green", "Aurora Green", false, "#062819", "#35B978", "#B5F37D"));
        items.add(new GradientPreset("deep_space", "Deep Space", false, "#050816", "#132B54", "#6D4BD8"));
        items.add(new GradientPreset("rose_night", "Rose Night", false, "#27101A", "#8D3159", "#F08AB5"));
        items.add(new GradientPreset("cyber_blue", "Cyber Blue", false, "#061B2F", "#0877B8", "#4FF3E8"));
        items.add(new GradientPreset("forest_mist", "Forest Mist", true, "#173522", "#5B8C63", "#C7E5C2"));
        items.add(new GradientPreset("black_gold", "Black Gold", false, "#090806", "#53431C", "#F2C64E"));
        items.add(new GradientPreset("arctic_blue", "Arctic Blue", true, "#0D3556", "#83C8E8", "#F4FCFF"));
        items.add(new GradientPreset("lavender_dream", "Lavender Dream", false, "#282045", "#8B79D9", "#F0D6FF"));
        items.add(new GradientPreset("teal_shadow", "Teal Shadow", false, "#08272B", "#1E8085", "#85E0D8"));
        items.add(new GradientPreset("crimson_night", "Crimson Night", false, "#240909", "#8F1C28", "#F06666"));
        items.add(new GradientPreset("indigo_glow", "Indigo Glow", false, "#10163D", "#395BE7", "#86C4FF"));
        items.add(new GradientPreset("bronze_dark", "Bronze Dark", false, "#20120B", "#8C5732", "#DFA66B"));
        items.add(new GradientPreset("sky_horizon", "Sky Horizon", true, "#215A93", "#B9E4FF", "#F5D497"));
        items.add(new GradientPreset("mint_glass", "Mint Glass", false, "#163B39", "#80D5BB", "#E8FFF5"));
        items.add(new GradientPreset("volcano", "Volcano", false, "#1B0804", "#B52A19", "#FFAB40"));
        items.add(new GradientPreset("moonlight", "Moonlight", true, "#0B1020", "#354C6F", "#C6D4EF"));
        items.add(new GradientPreset("tropical_sea", "Tropical Sea", false, "#063E4B", "#13B5A7", "#B6F78E"));
        items.add(new GradientPreset("graphite", "Graphite", false, "#111111", "#3B4650", "#9BA8B4"));
        items.add(new GradientPreset("neon_purple", "Neon Purple", false, "#16072C", "#9C27FF", "#00D5FF"));
        items.add(new GradientPreset("deep_emerald", "Deep Emerald", false, "#041B16", "#0E5A49", "#2DE0A5"));
        return items;
    }

    private String bgMode() {
        return prefs.getString("bg_mode", "light");
    }

    private int appsPerRow() {
        return prefs.getInt("apps_per_row", 5);
    }

    private void updateServicePill() {
        if (searchPill == null) return;
        searchPill.invalidate();
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager) getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (manager == null) return false;
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        String target = getPackageName() + "/" + HomeAccessibilityService.class.getName();
        for (AccessibilityServiceInfo service : services) {
            if (service.getId() != null && service.getId().equalsIgnoreCase(target)) return true;
        }
        return false;
    }

    private void openAccessibilitySettings() {
        ComponentName component = new ComponentName(this, HomeAccessibilityService.class);
        Intent details = new Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, component.flattenToString())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(details);
            toast("Enable MyTV Launcher Home Service. If blocked, first use Allow restricted settings.");
            return;
        } catch (Exception ignored) {
        }
        try {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            toast("If blocked, open App info and choose Allow restricted settings.");
        } catch (Exception e) {
            openAppInfo(getPackageName());
        }
    }

    private void requestDefaultHomeLauncher() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = (RoleManager) getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    toast("MyTV Launcher is already the Home app.");
                    return;
                }
                try {
                    startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME), REQ_HOME_ROLE);
                    return;
                } catch (Exception ignored) {
                }
            }
        }
        openHomeSettings();
    }

    private boolean isDefaultHomeLauncher() {
        Intent home = new Intent(Intent.ACTION_MAIN);
        home.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager().resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY);
        return info != null && info.activityInfo != null && getPackageName().equals(info.activityInfo.packageName);
    }

    private String homeStatusText() {
        if (isDefaultHomeLauncher()) return "MyTV Launcher is currently the default Home app";
        return "Request Android Home role, then choose MyTV Launcher";
    }

    private void openHomeSettings() {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_HOME_SETTINGS),
                new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : intents) {
            try {
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                toast("Choose MyTV Launcher as the Home app if it appears.");
                return;
            } catch (Exception ignored) {
            }
        }
        toast("Could not open Home settings on this device.");
    }

    private void openWifiSettings() {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_WIFI_SETTINGS),
                new Intent("android.settings.WIFI_SETTINGS"),
                new Intent(Settings.ACTION_SETTINGS)
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        toast("Unable to open Wi-Fi settings");
    }

    private void testHomeLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(Intent.createChooser(intent, "Select Home launcher"));
        } catch (Exception e) {
            try {
                startActivity(intent);
            } catch (Exception ignored) {
                openHomeSettings();
            }
        }
    }

    private void openGoogleSettings() {
        Intent[] intents = new Intent[] {
                new Intent(Settings.ACTION_SETTINGS),
                new Intent("com.google.android.tv.settings.action.SETTINGS"),
                new Intent("android.settings.SETTINGS")
        };
        for (Intent intent : intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return;
            } catch (Exception ignored) {
            }
        }
        toast("Unable to open Google settings");
    }

    private void openAppInfo(String packageName) {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse("package:" + packageName));
        startActivity(intent);
    }

    private static class AppAction {
        final String title;
        final String subtitle;
        final Runnable action;
        final boolean danger;

        AppAction(String title, String subtitle, Runnable action, boolean danger) {
            this.title = title;
            this.subtitle = subtitle;
            this.action = action;
            this.danger = danger;
        }
    }

    private static class GradientPreset {
        final String id;
        final String name;
        final boolean vertical;
        final int[] colors;

        GradientPreset(String id, String name, boolean vertical, String... colors) {
            this.id = id;
            this.name = name;
            this.vertical = vertical;
            this.colors = new int[colors.length];
            for (int i = 0; i < colors.length; i++) {
                this.colors[i] = Color.parseColor(colors[i]);
            }
        }
    }

    private static class IconChipView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String icon;
        private final float density;

        IconChipView(Context context, String icon) {
            super(context);
            this.icon = icon == null ? "" : icon;
            this.density = context.getResources().getDisplayMetrics().density;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            float cx = w / 2f;
            float cy = h / 2f;
            paint.setColor(Color.WHITE);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            if ("search".equals(icon)) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3.4f * density);
                canvas.drawCircle(cx - 4f * density, cy - 3f * density, 9f * density, paint);
                canvas.drawLine(cx + 4f * density, cy + 5f * density, cx + 15f * density, cy + 16f * density, paint);
                return;
            }
            if ("mytv".equals(icon)) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setTypeface(Typeface.DEFAULT_BOLD);
                paint.setTextSize(17f * density);
                canvas.drawText("M", cx, cy + 6f * density, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(2.2f * density);
                canvas.drawRoundRect(cx - 19f * density, cy - 13f * density,
                        cx + 19f * density, cy + 13f * density,
                        7f * density, 7f * density, paint);
            }
        }
    }

    private static class WeatherAnimationView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String condition;
        private long startTime;
        private boolean running;

        WeatherAnimationView(Context context, String condition) {
            super(context);
            this.condition = condition == null ? "" : condition.toLowerCase(Locale.ROOT);
            setWillNotDraw(false);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            running = true;
            startTime = System.currentTimeMillis();
            postInvalidateOnAnimation();
        }

        @Override
        protected void onDetachedFromWindow() {
            running = false;
            super.onDetachedFromWindow();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            float t = (System.currentTimeMillis() - startTime) / 1000f;

            paint.setShader(new android.graphics.LinearGradient(0, 0, w, h,
                    Color.argb(135, 82, 198, 230),
                    Color.argb(118, 255, 221, 170),
                    Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);

            paint.setShader(new android.graphics.RadialGradient(w * 0.78f, h * 0.2f, h * 0.62f,
                    Color.argb(86, 255, 255, 255),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(w * 0.78f, h * 0.2f, h * 0.62f, paint);
            paint.setShader(null);

            if (condition.contains("rain")) {
                drawClouds(canvas, w, h, t);
                drawRain(canvas, w, h, t);
            } else if (condition.contains("snow")) {
                drawClouds(canvas, w, h, t);
                drawSnow(canvas, w, h, t);
            } else if (condition.contains("storm") || condition.contains("thunder")) {
                drawClouds(canvas, w, h, t);
                drawRain(canvas, w, h, t);
                if (((int) (t * 2)) % 9 == 0) drawLightning(canvas, w, h);
            } else if (condition.contains("fog") || condition.contains("mist")) {
                drawFog(canvas, w, h, t);
            } else if (condition.contains("sun") || condition.contains("clear")) {
                drawSun(canvas, w, h, t);
            } else {
                drawClouds(canvas, w, h, t);
            }

            if (running) postInvalidateOnAnimation();
        }

        private void drawSun(Canvas canvas, int w, int h, float t) {
            float cx = w * 0.74f;
            float cy = h * 0.34f;
            float pulse = (float) Math.sin(t * 1.6f) * 7f;
            paint.setShader(new android.graphics.RadialGradient(cx, cy, h * 0.34f + pulse,
                    Color.argb(125, 255, 244, 175),
                    Color.TRANSPARENT,
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, h * 0.34f + pulse, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(55, 255, 255, 255));
            canvas.drawCircle(cx - h * 0.035f, cy - h * 0.055f, h * 0.18f, paint);
            paint.setShader(new android.graphics.RadialGradient(cx - h * 0.04f, cy - h * 0.05f, h * 0.2f,
                    Color.rgb(255, 250, 186),
                    Color.rgb(255, 186, 70),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, h * 0.17f, paint);
            paint.setShader(null);
        }

        private void drawClouds(Canvas canvas, int w, int h, float t) {
            if (condition.contains("clear") || condition.contains("part")) {
                drawSun(canvas, w, h, t);
            }
            drawCloud(canvas, (w * 0.58f + (t * 18f) % (w * 0.34f)) % (w + 120) - 60, h * 0.38f, h * 0.13f, 205);
            drawCloud(canvas, (w * 0.2f + (t * 10f) % (w * 0.44f)) % (w + 140) - 70, h * 0.62f, h * 0.09f, 118);
        }

        private void drawCloud(Canvas canvas, float x, float y, float r, int alpha) {
            paint.setColor(Color.argb(Math.min(95, alpha / 2), 0, 0, 0));
            canvas.drawRoundRect(x - r * 0.35f, y + r * 0.33f, x + r * 2.38f, y + r * 1.02f, r, r, paint);
            paint.setShader(new android.graphics.RadialGradient(x + r * 0.7f, y - r * 0.18f, r * 2.1f,
                    Color.argb(alpha, 255, 255, 255),
                    Color.argb(Math.max(72, alpha - 62), 222, 232, 238),
                    Shader.TileMode.CLAMP));
            canvas.drawCircle(x, y, r, paint);
            canvas.drawCircle(x + r * 0.9f, y - r * 0.28f, r * 1.12f, paint);
            canvas.drawCircle(x + r * 1.9f, y, r * 0.86f, paint);
            canvas.drawRoundRect(x - r * 0.45f, y, x + r * 2.4f, y + r * 0.9f, r, r, paint);
            paint.setShader(null);
            paint.setColor(Color.argb(Math.min(115, alpha / 2), 255, 255, 255));
            canvas.drawCircle(x + r * 0.48f, y - r * 0.18f, r * 0.34f, paint);
        }

        private void drawRain(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(150, 190, 225, 255));
            paint.setStrokeWidth(3f);
            for (int i = 0; i < 18; i++) {
                float x = (i * w / 17f + (t * 34f)) % w;
                float y = ((i * 37f) + (t * 130f)) % h;
                canvas.drawLine(x, y, x - 10, y + 28, paint);
            }
        }

        private void drawSnow(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(175, 255, 255, 255));
            for (int i = 0; i < 18; i++) {
                float x = (i * w / 17f + (float) Math.sin(t + i) * 12f) % w;
                float y = ((i * 43f) + (t * 42f)) % h;
                canvas.drawCircle(x, y, 3f + (i % 3), paint);
            }
        }

        private void drawFog(Canvas canvas, int w, int h, float t) {
            paint.setColor(Color.argb(72, 255, 255, 255));
            for (int i = 0; i < 5; i++) {
                float y = h * (0.22f + i * 0.14f);
                float x = ((t * 26f + i * 80f) % (w + 180)) - 90;
                canvas.drawRoundRect(x - 180, y, x + 260, y + h * 0.08f, 40, 40, paint);
            }
        }

        private void drawLightning(Canvas canvas, int w, int h) {
            paint.setColor(Color.argb(185, 255, 246, 138));
            paint.setStrokeWidth(6f);
            float x = w * 0.72f;
            canvas.drawLine(x, h * 0.18f, x - 24, h * 0.44f, paint);
            canvas.drawLine(x - 24, h * 0.44f, x + 8, h * 0.42f, paint);
            canvas.drawLine(x + 8, h * 0.42f, x - 18, h * 0.7f, paint);
        }
    }

    private void animateFocus(View v, boolean hasFocus, float scale) {
        v.animate().scaleX(hasFocus ? scale : 1f).scaleY(hasFocus ? scale : 1f).translationZ(hasFocus ? dp(12) : 0).setDuration(100).start();
    }

    private void startHomeMoveWiggle(View card, String packageName) {
        card.post(new Runnable() {
            boolean flip;

            @Override
            public void run() {
                if (!TextUtils.equals(movingPackage, packageName) || !card.isAttachedToWindow()) {
                    card.animate().cancel();
                    card.setRotation(0f);
                    card.setScaleX(1f);
                    card.setScaleY(1f);
                    return;
                }
                flip = !flip;
                card.animate()
                        .rotation(flip ? 1.8f : -1.8f)
                        .scaleX(flip ? 1.025f : 0.995f)
                        .scaleY(flip ? 1.025f : 0.995f)
                        .setDuration(145)
                        .withEndAction(this)
                        .start();
            }
        });
    }

    private void startFolderMoveWiggle(View card, String folderName, String packageName) {
        card.post(new Runnable() {
            boolean flip;

            @Override
            public void run() {
                boolean stillMoving = TextUtils.equals(movingFolderName, folderName)
                        && TextUtils.equals(movingFolderPackage, packageName);
                if (!stillMoving || !card.isAttachedToWindow()) {
                    card.animate().cancel();
                    card.setRotation(0f);
                    card.setScaleX(1f);
                    card.setScaleY(1f);
                    return;
                }
                flip = !flip;
                card.animate()
                        .rotation(flip ? 1.8f : -1.8f)
                        .scaleX(flip ? 1.025f : 0.995f)
                        .scaleY(flip ? 1.025f : 0.995f)
                        .setDuration(145)
                        .withEndAction(this)
                        .start();
            }
        });
    }

    private void enterImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class AppEntry {
        final String packageName;
        final String label;
        final Intent launchIntent;
        final ApplicationInfo info;
        final Drawable banner;
        final boolean isFolder;
        final List<AppEntry> children;

        AppEntry(String packageName, String label, Intent launchIntent, ApplicationInfo info, Drawable banner) {
            this.packageName = packageName;
            this.label = label;
            this.launchIntent = launchIntent;
            this.info = info;
            this.banner = banner;
            this.isFolder = false;
            this.children = null;
        }

        private AppEntry(String folderName, List<AppEntry> children) {
            this.packageName = "folder:" + folderName;
            this.label = folderName;
            this.launchIntent = null;
            this.info = children.isEmpty() ? null : children.get(0).info;
            this.banner = children.isEmpty() ? null : children.get(0).banner;
            this.isFolder = true;
            this.children = children;
        }

        static AppEntry folder(String folderName, List<AppEntry> children) {
            return new AppEntry(folderName, children);
        }
    }
}
