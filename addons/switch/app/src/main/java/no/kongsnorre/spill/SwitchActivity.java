package no.kongsnorre.spill;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * One press to the Nintendo Switch: opens the HDMI input the Switch sits on, through the TV's own input app as the
 * launcher's Inputs menu does. The input is the one remembered from last time, else the one whose HDMI-CEC name
 * says "Switch" or "Nintendo", else the owner picks it once from a list.
 */
public final class SwitchActivity extends Activity {

    static final class Input {
        final String id, name;

        Input(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        SharedPreferences prefs = getSharedPreferences("switch", MODE_PRIVATE);
        List<Input> inputs = inputs();
        String saved = prefs.getString("input", "");
        for (Input i : inputs) {
            if (i.id.equals(saved)) {
                open(i.id);
                return;
            }
        }
        Input found = find(inputs);
        if (found != null) {
            prefs.edit().putString("input", found.id).apply();
            open(found.id);
            return;
        }
        pick(inputs, prefs);
    }

    /** The input named like the Switch, or null. */
    static Input find(List<Input> inputs) {
        for (Input i : inputs) {
            String n = i.name.toLowerCase(Locale.ROOT);
            if (n.contains("switch") || n.contains("nintendo")) return i;
        }
        return null;
    }

    private List<Input> inputs() {
        List<Input> out = new ArrayList<>();
        TvInputManager tim = getSystemService(TvInputManager.class);
        if (tim == null) return out;
        for (TvInputInfo info : tim.getTvInputList()) {
            if (!info.isPassthroughInput()) continue;
            if (info.getType() != TvInputInfo.TYPE_HDMI && info.getParentId() == null) continue;
            CharSequence custom = info.loadCustomLabel(this);
            String name = custom != null && custom.length() > 0 ? custom.toString() : String.valueOf(info.loadLabel(this));
            out.add(new Input(info.getId(), name));
        }
        return out;
    }

    private void open(String id) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, TvContract.buildChannelUriForPassthroughInput(id))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            // no input app on this TV: nothing to open
        }
        finish();
    }

    /** First run with no name to go on: a list of the HDMI inputs; the choice is kept. */
    private void pick(List<Input> inputs, SharedPreferences prefs) {
        float d = getResources().getDisplayMetrics().density;
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        col.setBackgroundColor(0xFF101418);
        col.setPadding((int) (64 * d), (int) (56 * d), (int) (64 * d), (int) (40 * d));
        setContentView(col);
        line(col, getString(inputs.isEmpty() ? R.string.none : R.string.pick), 30, Color.WHITE, d);
        line(col, getString(R.string.pick_hint), 16, 0x99FFFFFF, d);
        TextView first = null;
        for (Input i : inputs) {
            TextView b = line(col, i.name, 22, Color.WHITE, d);
            b.setFocusable(true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(0, (int) (14 * d), 0, (int) (14 * d));
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, round(0xFFE60012, 14 * d));
            bg.addState(new int[]{}, round(0xFF1F262E, 14 * d));
            b.setBackground(bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (420 * d), -2);
            lp.topMargin = (int) (12 * d);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                prefs.edit().putString("input", i.id).apply();
                open(i.id);
            });
            if (first == null) first = b;
        }
        if (first != null) first.requestFocus();
    }

    private TextView line(LinearLayout parent, String s, int sp, int color, float d) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, (int) (6 * d), 0, (int) (6 * d));
        parent.addView(t, new LinearLayout.LayoutParams(-1, -2));
        return t;
    }

    private static GradientDrawable round(int color, float r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(r);
        return g;
    }
}
