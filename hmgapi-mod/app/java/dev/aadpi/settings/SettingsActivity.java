package dev.aadpi.settings;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONObject;

import hmgapi.client.HmgApi;

/**
 * The AA DPI app (its tile in the car's menu): Android Auto's DPI, real density
 * and tap/scroll limit, saved as the aa-dpi mod's settings through HMG-API
 * (hmgapi-client.jar). HMG-API hands a saved change to the mod's hooks in the
 * Android Auto app at once; the phone takes it at its next connection.
 *
 * The same settings are on HMG-API's own settings page for the mod. Closed by
 * the car while driving (mod.json not_while_driving).
 */
public final class SettingsActivity extends Activity {
    static final int MIN = 60, MAX = 480, STEP = 5, STOCK = 240;
    static final int[] PRESETS = {240, 200, 180, 160, 130};
    static final int TEXT = Color.rgb(235, 235, 235), GREY = Color.rgb(150, 150, 150),
            ACCENT = Color.rgb(90, 170, 255), ERROR = Color.rgb(255, 110, 100);

    TextView value, scale, status;
    SeekBar bar;
    RadioButton same, keep;
    Switch tapLimit;
    Button save;
    int dpi = 200;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.rgb(18, 20, 24));
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(48), dp(24), dp(48), dp(24));
        scroll.addView(col);

        text(col, "Android Auto DPI", 30, true, TEXT);
        text(col, "Lower is smaller text and buttons and more map. The car's own is 240.", 18, false, GREY);

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(16), 0, 0);
        col.addView(row);
        row.addView(button("-", new View.OnClickListener() {
            public void onClick(View v) { set(dpi - STEP); }
        }), new LinearLayout.LayoutParams(dp(72), dp(64)));
        value = new TextView(this);
        value.setTextSize(40);
        value.setTypeface(Typeface.DEFAULT_BOLD);
        value.setTextColor(ACCENT);
        value.setGravity(Gravity.CENTER);
        row.addView(value, new LinearLayout.LayoutParams(dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(button("+", new View.OnClickListener() {
            public void onClick(View v) { set(dpi + STEP); }
        }), new LinearLayout.LayoutParams(dp(72), dp(64)));
        scale = new TextView(this);
        scale.setTextSize(18);
        scale.setTextColor(GREY);
        scale.setPadding(dp(24), 0, 0, 0);
        row.addView(scale);

        bar = new SeekBar(this);
        bar.setMax((MAX - MIN) / STEP);
        bar.setPadding(dp(24), dp(16), dp(24), dp(16));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean user) { if (user) set(MIN + p * STEP); }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)));

        LinearLayout presets = new LinearLayout(this);
        col.addView(presets);
        for (final int p : PRESETS) {
            presets.addView(button(p == STOCK ? p + " (stock)" : String.valueOf(p), new View.OnClickListener() {
                public void onClick(View v) { set(p); }
            }), new LinearLayout.LayoutParams(0, dp(60), 1f));
        }

        text(col, "Real density", 22, true, TEXT).setPadding(0, dp(24), 0, 0);
        RadioGroup real = new RadioGroup(this);
        same = radio(real, "Same as the DPI (like aa-proxy-rs)");
        keep = radio(real, "Keep the head unit's");
        col.addView(real);

        tapLimit = new Switch(this);
        tapLimit.setText("Tap and scroll limit while driving (stock)");
        tapLimit.setTextSize(22);
        tapLimit.setTextColor(TEXT);
        tapLimit.setPadding(0, dp(24), 0, dp(8));
        col.addView(tapLimit, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        text(col, "Off: the car doesn't offer the phone its speed sensor, so Android Auto doesn't lock taps and scrolling on the move. "
                + "Its other driving restrictions stay.", 16, false, GREY);

        save = button("Save", new View.OnClickListener() {
            public void onClick(View v) { save(); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(260), dp(72));
        lp.topMargin = dp(24);
        col.addView(save, lp);
        status = text(col, "Reading the settings...", 18, false, GREY);
        status.setPadding(0, dp(12), 0, 0);

        setContentView(scroll);
        set(dpi);
        enable(false);
        load();
    }

    /** the mod's current settings (the owner's values over the defaults) */
    void load() {
        new AsyncTask<Void, Void, Object>() {
            protected Object doInBackground(Void... v) {
                try {
                    HmgApi api = HmgApi.connect();
                    if (!api.available()) return "HMG-API isn't running.";
                    if (api.mod() == null) return "The aa-dpi mod is off: turn it on in the HMG-API app.";
                    return api.settings();
                } catch (Exception e) {
                    return "Can't read the settings: " + e.getMessage();
                }
            }

            protected void onPostExecute(Object r) {
                if (r instanceof String) {
                    show((String) r, ERROR);
                    return;
                }
                JSONObject s = (JSONObject) r;
                set(s.optInt("dpi", 200));
                ("keep".equals(s.optString("real_dpi", "same")) ? keep : same).setChecked(true);
                tapLimit.setChecked(s.optBoolean("tap_limit", true));
                enable(true);
                show("Changes apply at the next Android Auto connection.", GREY);
            }
        }.execute();
    }

    void save() {
        final JSONObject out = new JSONObject();
        try {
            out.put("dpi", dpi);
            out.put("real_dpi", keep.isChecked() ? "keep" : "same");
            out.put("tap_limit", tapLimit.isChecked());
        } catch (Exception e) {
            return;
        }
        enable(false);
        new AsyncTask<Void, Void, String>() {
            protected String doInBackground(Void... v) {
                try {
                    HmgApi.connect().saveSettings(out);
                    return null;
                } catch (Exception e) {
                    return e.getMessage();
                }
            }

            protected void onPostExecute(String err) {
                enable(true);
                if (err != null) {
                    show("Not saved: " + err, ERROR);
                } else {
                    show("Saved. Reconnect the phone (unplug and plug in, or turn its Wi-Fi off and on) to see it.", ACCENT);
                    HmgApi.toast(SettingsActivity.this, "AA DPI saved: reconnect the phone");
                }
            }
        }.execute();
    }

    void set(int d) {
        d = Math.max(MIN, Math.min(MAX, Math.round(d / (float) STEP) * STEP));
        dpi = d;
        value.setText(String.valueOf(d));
        bar.setProgress((d - MIN) / STEP);
        scale.setText(String.format("1 dp = %.2f px, %d%% of stock size", d / 160f, Math.round(100f * d / STOCK)));
    }

    void enable(boolean on) {
        save.setEnabled(on);
        bar.setEnabled(on);
        tapLimit.setEnabled(on);
        same.setEnabled(on);
        keep.setEnabled(on);
    }

    void show(String s, int color) {
        status.setText(s);
        status.setTextColor(color);
    }

    TextView text(LinearLayout parent, String s, int sp, boolean bold, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        parent.addView(t);
        return t;
    }

    Button button(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(22);
        b.setOnClickListener(l);
        return b;
    }

    RadioButton radio(RadioGroup g, String s) {
        RadioButton r = new RadioButton(this);
        r.setText(s);
        r.setTextSize(20);
        r.setTextColor(TEXT);
        r.setId(View.generateViewId());
        g.addView(r);
        return r;
    }

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
