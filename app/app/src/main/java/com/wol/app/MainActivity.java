package com.wol.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * WOL 原生管理界面（不依赖 WebView）
 */
public class MainActivity extends Activity {

    private ApiClient api;
    private ExecutorService executor;
    private ListView listView;
    private TextView statusBar;
    private DeviceAdapter adapter;
    private final List<Device> devices = new ArrayList<>();
    private final Map<String, DeviceState> states = new HashMap<>();

    private static class Device {
        int idx;
        String name;
        String mac;
    }

    private static class DeviceState {
        boolean online;
        boolean waking;
        long wakeStart;
        String ip = "";
        int[] history = new int[0];
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        api = new ApiClient(this);
        executor = Executors.newSingleThreadExecutor();

        listView = findViewById(R.id.list);
        statusBar = findViewById(R.id.status_bar);
        adapter = new DeviceAdapter();
        listView.setAdapter(adapter);

        findViewById(R.id.btn_refresh).setOnClickListener(v -> refreshAll());
        findViewById(R.id.btn_scan).setOnClickListener(v -> scanNetwork());
        findViewById(R.id.btn_settings).setOnClickListener(v -> showSettingsDialog());

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < devices.size()) {
                showDeviceMenu(devices.get(position));
            }
            return true;
        });

        if (api.getUsername().isEmpty()) {
            showSettingsDialog();
        } else {
            checkAuth();
        }
    }

    // ==================== 认证流程 ====================

    private void checkAuth() {
        setStatus("检查登录状态...");
        executor.execute(() -> {
            try {
                JSONObject r = api.check2fa();
                boolean ok = r.optBoolean("ok", false);
                if (ok) {
                    loadDevices();
                } else {
                    runOnUiThread(this::show2FADialog);
                }
            } catch (Exception e) {
                runOnUiThread(() -> {
                    setStatus("连接失败: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "连接失败，请检查设置", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void show2FADialog() {
        EditText input = new EditText(this);
        input.setHint("6 位验证码");
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        LinearLayout wrap = new LinearLayout(this);
        int pad = dp(16);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("双因素验证")
                .setMessage("请输入认证器 App 中的 6 位动态验证码")
                .setView(wrap)
                .setCancelable(false)
                .setPositiveButton("验证", (d, w) -> {
                    String code = input.getText().toString().trim();
                    if (code.length() != 6) {
                        Toast.makeText(this, "请输入 6 位数字", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    setStatus("验证中...");
                    executor.execute(() -> {
                        try {
                            JSONObject r = api.verify2fa(code);
                            if (r.optBoolean("ok", false)) {
                                runOnUiThread(() -> {
                                    setStatus("验证成功，加载设备...");
                                    loadDevices();
                                });
                            } else {
                                String err = r.optString("err", "验证失败");
                                runOnUiThread(() -> {
                                    setStatus("验证失败: " + err);
                                    show2FADialog();
                                });
                            }
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                setStatus("验证失败: " + e.getMessage());
                                show2FADialog();
                            });
                        }
                    });
                })
                .setNegativeButton("退出", (d, w) -> finish())
                .show();
    }

    // ==================== 设备列表 ====================

    private void loadDevices() {
        setStatus("加载设备列表...");
        executor.execute(() -> {
            try {
                JSONObject r = api.devices();
                if (!r.optBoolean("ok", false)) {
                    String err = r.optString("err", "加载失败");
                    if (r.optBoolean("need_2fa", false)) {
                        runOnUiThread(this::show2FADialog);
                    } else {
                        runOnUiThread(() -> setStatus("加载失败: " + err));
                    }
                    return;
                }
                JSONArray arr = r.optJSONArray("devices");
                List<Device> newList = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        Device d = new Device();
                        d.idx = o.optInt("idx", i);
                        d.name = o.optString("name", "");
                        d.mac = o.optString("mac", "");
                        newList.add(d);
                    }
                }
                runOnUiThread(() -> {
                    devices.clear();
                    devices.addAll(newList);
                    adapter.notifyDataSetChanged();
                    setStatus("共 " + devices.size() + " 台设备");
                });
                refreshStates();
                refreshHistories();
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("加载失败: " + e.getMessage()));
            }
        });
    }

    private void refreshStates() {
        List<Device> snapshot = new ArrayList<>(devices);
        executor.execute(() -> {
            for (Device d : snapshot) {
                try {
                    JSONObject r = api.status(d.mac);
                    DeviceState st = getState(d.mac);
                    st.online = r.optBoolean("online", false);
                    st.ip = r.optString("ip", "");
                    runOnUiThread(adapter::notifyDataSetChanged);
                } catch (Exception ignored) {
                }
            }
            runOnUiThread(this::updateOnlineCount);
        });
    }

    private void refreshHistories() {
        List<Device> snapshot = new ArrayList<>(devices);
        executor.execute(() -> {
            for (Device d : snapshot) {
                try {
                    JSONObject r = api.history(d.mac);
                    JSONArray h = r.optJSONArray("history");
                    if (h != null) {
                        int[] costs = new int[h.length()];
                        for (int i = 0; i < h.length(); i++) costs[i] = h.optInt(i, 0);
                        getState(d.mac).history = costs;
                        runOnUiThread(adapter::notifyDataSetChanged);
                    }
                } catch (Exception ignored) {
                }
            }
        });
    }

    private void updateOnlineCount() {
        int online = 0;
        for (DeviceState s : states.values()) if (s.online) online++;
        setStatus("在线 " + online + " / " + devices.size() + " 台");
    }

    private void refreshAll() {
        refreshStates();
        refreshHistories();
        Toast.makeText(this, "刷新中...", Toast.LENGTH_SHORT).show();
    }

    private DeviceState getState(String mac) {
        String key = mac.toUpperCase();
        DeviceState st = states.get(key);
        if (st == null) {
            st = new DeviceState();
            states.put(key, st);
        }
        return st;
    }

    // ==================== 唤醒 ====================

    private void wakeDevice(Device d, Button wakeBtn) {
        DeviceState st = getState(d.mac);
        if (st.waking) {
            Toast.makeText(this, "正在唤醒中...", Toast.LENGTH_SHORT).show();
            return;
        }
        st.waking = true;
        st.wakeStart = System.currentTimeMillis();
        adapter.notifyDataSetChanged();
        setStatus("正在唤醒 " + d.name + " ...");

        executor.execute(() -> {
            try {
                JSONObject pre = api.status(d.mac);
                if (pre.optBoolean("online", false)) {
                    st.waking = false;
                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        setStatus("✅ " + d.name + " 已在线，无需唤醒");
                    });
                    return;
                }
                JSONObject r = api.wake(d.mac);
                if (!r.optBoolean("ok", false)) {
                    st.waking = false;
                    String err = r.optString("err", "发送失败");
                    runOnUiThread(() -> {
                        adapter.notifyDataSetChanged();
                        setStatus("❌ 唤醒包发送失败: " + err);
                    });
                    return;
                }
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    setStatus("🟡 已发送唤醒包，等待开机...");
                });
                // 轮询最多 90 秒
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 90000) {
                    Thread.sleep(2000);
                    try {
                        JSONObject s = api.status(d.mac);
                        if (s.optBoolean("online", false)) {
                            int sec = (int) ((System.currentTimeMillis() - start) / 1000);
                            st.online = true;
                            st.waking = false;
                            // 记录唤醒历史
                            try {
                                api.get("cgi-bin/wakelog?mac=" + d.mac + "&cost=" + sec);
                            } catch (Exception ignored) {
                            }
                            runOnUiThread(() -> {
                                adapter.notifyDataSetChanged();
                                setStatus("✅ " + d.name + " 唤醒成功！耗时 " + sec + " 秒");
                            });
                            refreshHistories();
                            return;
                        }
                    } catch (Exception ignored) {
                    }
                }
                st.waking = false;
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    setStatus("❌ " + d.name + " 唤醒超时（90秒）");
                });
            } catch (Exception e) {
                st.waking = false;
                runOnUiThread(() -> {
                    adapter.notifyDataSetChanged();
                    setStatus("❌ 唤醒失败: " + e.getMessage());
                });
            }
        });
    }

    // ==================== 扫描 ====================

    private void scanNetwork() {
        setStatus("扫描网络中...");
        executor.execute(() -> {
            try {
                JSONObject r = api.scan();
                if (!r.optBoolean("ok", false)) {
                    runOnUiThread(() -> setStatus("扫描失败: " + r.optString("err", "")));
                    return;
                }
                JSONArray arr = r.optJSONArray("devices");
                List<JSONObject> found = new ArrayList<>();
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) found.add(arr.getJSONObject(i));
                }
                runOnUiThread(() -> showScanDialog(found));
            } catch (Exception e) {
                runOnUiThread(() -> setStatus("扫描失败: " + e.getMessage()));
            }
        });
    }

    private void showScanDialog(List<JSONObject> found) {
        if (found.isEmpty()) {
            Toast.makeText(this, "未扫描到设备", Toast.LENGTH_SHORT).show();
            return;
        }
        ScanAdapter scanAdapter = new ScanAdapter(found);
        ListView lv = new ListView(this);
        lv.setAdapter(scanAdapter);

        new AlertDialog.Builder(this)
                .setTitle("扫描到 " + found.size() + " 台在线设备")
                .setView(lv)
                .setPositiveButton("关闭", null)
                .show();
    }

    // ==================== 设置 ====================

    private void showSettingsDialog() {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        wrap.setPadding(pad, pad, pad, pad);

        EditText etUrl = new EditText(this);
        etUrl.setHint("服务器地址（如 http://路由器IP:8081/）");
        etUrl.setText(api.getBaseUrl());
        wrap.addView(etUrl);

        EditText etUser = new EditText(this);
        etUser.setHint("用户名（Basic Auth）");
        etUser.setText(api.getUsername());
        wrap.addView(etUser);

        EditText etPass = new EditText(this);
        etPass.setHint("密码（Basic Auth）");
        etPass.setText(api.getPassword());
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        wrap.addView(etPass);

        TextView tip = new TextView(this);
        tip.setTextSize(12);
        tip.setTextColor(Color.GRAY);
        tip.setText("2FA 验证码会在连接后提示输入。");
        tip.setPadding(0, dp(8), 0, 0);
        wrap.addView(tip);

        new AlertDialog.Builder(this)
                .setTitle("服务器设置")
                .setView(wrap)
                .setPositiveButton("保存并连接", (d, w) -> {
                    String url = etUrl.getText().toString().trim();
                    if (url.isEmpty()) {
                        Toast.makeText(this, "请填写服务器地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (!url.startsWith("http")) url = "http://" + url;
                    api.setBaseUrl(url);
                    api.setCredentials(etUser.getText().toString().trim(), etPass.getText().toString());
                    api.clearCookie();
                    states.clear();
                    checkAuth();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showDeviceMenu(Device d) {
        String[] items = {"唤醒", "编辑", "删除", "查看唤醒历史"};
        new AlertDialog.Builder(this)
                .setTitle(d.name)
                .setItems(items, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            wakeDevice(d, null);
                            break;
                        case 1:
                            showEditDialog(d);
                            break;
                        case 2:
                            confirmDelete(d);
                            break;
                        case 3:
                            showHistoryDialog(d);
                            break;
                    }
                })
                .show();
    }

    private void showEditDialog(Device d) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        wrap.setPadding(pad, pad, pad, pad);

        EditText etName = new EditText(this);
        etName.setHint("设备名称");
        etName.setText(d.name);
        wrap.addView(etName);

        EditText etMac = new EditText(this);
        etMac.setHint("MAC 地址");
        etMac.setText(d.mac);
        wrap.addView(etMac);

        new AlertDialog.Builder(this)
                .setTitle("编辑设备")
                .setView(wrap)
                .setPositiveButton("保存", (dialog, which) -> {
                    String name = etName.getText().toString().trim();
                    String mac = etMac.getText().toString().trim();
                    if (name.isEmpty() || mac.isEmpty()) {
                        Toast.makeText(this, "名称和 MAC 不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    executor.execute(() -> {
                        try {
                            JSONObject r = api.updateDevice(d.idx, name, mac);
                            runOnUiThread(() -> {
                                if (r.optBoolean("ok", false)) {
                                    Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show();
                                    loadDevices();
                                } else {
                                    Toast.makeText(this, "保存失败: " + r.optString("err"), Toast.LENGTH_SHORT).show();
                                }
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> Toast.makeText(this, "失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                        }
                    });
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmDelete(Device d) {
        new AlertDialog.Builder(this)
                .setTitle("删除设备")
                .setMessage("确定删除「" + d.name + "」？")
                .setPositiveButton("删除", (dialog, which) -> executor.execute(() -> {
                    try {
                        JSONObject r = api.deleteDevice(d.idx);
                        runOnUiThread(() -> {
                            if (r.optBoolean("ok", false)) {
                                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                                states.remove(d.mac.toUpperCase());
                                loadDevices();
                            } else {
                                Toast.makeText(this, "删除失败", Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> Toast.makeText(this, "失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                    }
                }))
                .setNegativeButton("取消", null)
                .show();
    }

    private void showHistoryDialog(Device d) {
        DeviceState st = getState(d.mac);
        StringBuilder sb = new StringBuilder();
        if (st.history.length == 0) {
            sb.append("暂无唤醒记录");
        } else {
            sb.append("最近 ").append(st.history.length).append(" 次唤醒耗时：\n");
            for (int i = 0; i < st.history.length; i++) {
                sb.append(String.format("%d. %d 秒", i + 1, st.history[i]));
                if (i == 0 && st.history.length > 1) {
                    int diff = st.history[0] - st.history[1];
                    if (diff > 0) sb.append("（+" + diff + "s 变慢）");
                    else if (diff < 0) sb.append("（" + diff + "s 变快）");
                    else sb.append("（持平）");
                }
                sb.append("\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(d.name + " 唤醒历史")
                .setMessage(sb.toString())
                .setPositiveButton("关闭", null)
                .show();
    }

    // ==================== 工具 ====================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void setStatus(String s) {
        statusBar.setText(s);
    }

    private String statusText(DeviceState st) {
        if (st.waking) {
            long sec = (System.currentTimeMillis() - st.wakeStart) / 1000;
            return "🟡 唤醒中 " + sec + "s";
        }
        if (st.online) return "🟢 已开机" + (st.ip.isEmpty() ? "" : " (" + st.ip + ")");
        return "⚪ 已关机";
    }

    private int statusColor(DeviceState st) {
        if (st.waking) return Color.rgb(217, 147, 10);
        if (st.online) return Color.rgb(46, 164, 79);
        return Color.GRAY;
    }

    private String historyText(DeviceState st) {
        if (st.history.length == 0) return "";
        StringBuilder sb = new StringBuilder("⏱ ").append(st.history[0]).append("s");
        if (st.history.length > 1) {
            int diff = st.history[0] - st.history[1];
            if (diff > 0) sb.append(" (+" + diff + "s)");
            else if (diff < 0) sb.append(" (" + diff + "s)");
        }
        return sb.toString();
    }

    // ==================== Adapters ====================

    private class DeviceAdapter extends BaseAdapter {
        @Override
        public int getCount() { return devices.size(); }

        @Override
        public Object getItem(int position) { return devices.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_device, parent, false);
            }
            Device d = devices.get(position);
            DeviceState st = getState(d.mac);

            TextView name = convertView.findViewById(R.id.dev_name);
            TextView mac = convertView.findViewById(R.id.dev_mac);
            TextView state = convertView.findViewById(R.id.dev_state);
            TextView hist = convertView.findViewById(R.id.dev_hist);
            Button wake = convertView.findViewById(R.id.btn_wake);

            name.setText(d.name);
            mac.setText(d.mac);
            state.setText(statusText(st));
            state.setTextColor(statusColor(st));

            String ht = historyText(st);
            if (ht.isEmpty()) {
                hist.setVisibility(View.GONE);
            } else {
                hist.setVisibility(View.VISIBLE);
                hist.setText(ht);
            }

            wake.setEnabled(!st.waking);
            wake.setText(st.waking ? "唤醒中" : "唤醒");
            wake.setOnClickListener(v -> wakeDevice(d, wake));

            return convertView;
        }
    }

    private class ScanAdapter extends BaseAdapter {
        private final List<JSONObject> items;
        private final Map<String, Boolean> added = new HashMap<>();

        ScanAdapter(List<JSONObject> items) { this.items = items; }

        @Override
        public int getCount() { return items.size(); }

        @Override
        public Object getItem(int position) { return items.get(position); }

        @Override
        public long getItemId(int position) { return position; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                        .inflate(R.layout.item_scan, parent, false);
            }
            JSONObject o = items.get(position);
            String ip = o.optString("ip", "");
            String mac = o.optString("mac", "");
            String name = o.optString("name", ip);

            TextView tvName = convertView.findViewById(R.id.scan_name);
            TextView tvMac = convertView.findViewById(R.id.scan_mac);
            Button add = convertView.findViewById(R.id.btn_add);

            tvName.setText(ip + "  " + (name.equals(ip) ? "" : name));
            tvMac.setText(mac);

            boolean isAdded = added.containsKey(mac.toUpperCase());
            add.setText(isAdded ? "已添加" : "添加");
            add.setEnabled(!isAdded);
            add.setOnClickListener(v -> {
                add.setEnabled(false);
                add.setText("添加中...");
                executor.execute(() -> {
                    try {
                        JSONObject r = api.addDevice(name.equals(ip) ? ip : name, mac);
                        runOnUiThread(() -> {
                            if (r.optBoolean("ok", false)) {
                                added.put(mac.toUpperCase(), true);
                                add.setText("已添加");
                                Toast.makeText(MainActivity.this, "已添加", Toast.LENGTH_SHORT).show();
                                loadDevices();
                            } else {
                                add.setEnabled(true);
                                add.setText("添加");
                                Toast.makeText(MainActivity.this, "添加失败: " + r.optString("err"), Toast.LENGTH_SHORT).show();
                            }
                        });
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            add.setEnabled(true);
                            add.setText("添加");
                            Toast.makeText(MainActivity.this, "失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                });
            });
            return convertView;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) executor.shutdownNow();
    }
}
