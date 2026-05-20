package com.transpent.app;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.transpent.app.R;



import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String LOCAL_ACCOUNT = "Local mode";
    private static final int REQ_ACCOUNT = 10;
    private static final int REQ_PICK_BILL = 11;
    private static final int REQ_CAMERA_BILL = 12;
    private static final int REQ_EXPORT = 13;
    // MD3 Color tokens – Transpent Green
    private static final int C_PRIMARY       = Color.parseColor("#1E7C5A");
    private static final int C_ON_PRIMARY     = Color.WHITE;
    private static final int C_PRIMARY_CTR    = Color.parseColor("#B7F0D9");
    private static final int C_ON_PRI_CTR     = Color.parseColor("#00201A");
    private static final int C_SECONDARY_CTR  = Color.parseColor("#D0E8DC");
    private static final int C_SURFACE        = Color.parseColor("#F8FBF9");
    private static final int C_ON_SURFACE     = Color.parseColor("#191C1A");
    private static final int C_SURF_VAR       = Color.parseColor("#DBE5DE");
    private static final int C_ON_SURF_VAR    = Color.parseColor("#404943");
    private static final int C_OUTLINE        = Color.parseColor("#707973");
    private static final int C_ERROR          = Color.parseColor("#B3261E");
    private static final int C_SUCCESS        = Color.parseColor("#1E7C5A");
    private final LedgerStore store = new LedgerStore();
    private String screen = "home";
    private String selectedAccount;
    private Party selectedSupplierForBill;
    private Uri pendingCameraUri;
    private boolean exportSplit;
    private View rootView;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store.load();
        selectedAccount = getPreferences(0).getString("googleAccount", null);
        // Auto-continue locally on fresh install – Google sign-in available later
        if (selectedAccount == null) {
            selectedAccount = LOCAL_ACCOUNT;
            getPreferences(0).edit().putString("googleAccount", selectedAccount).apply();
        }
        showHome("home");
    }

    private void showLogin() {
        LinearLayout l = base(); l.setGravity(Gravity.CENTER);

        ImageView logo = new ImageView(this);
        logo.setImageResource(getResources().getIdentifier("logo_simple", "drawable", getPackageName()));
        logo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(120), dp(120));
        lp.gravity = Gravity.CENTER; l.addView(logo, lp);

        l.addView(space(24));
        TextView title = text("TRANSPENT", 32, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); title.setGravity(Gravity.CENTER); l.addView(title);
        TextView sub = text("Store ledger tracking", 15, C_ON_SURF_VAR); sub.setGravity(Gravity.CENTER); l.addView(sub);
        l.addView(space(48));

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-1, dp(56)); bp.setMargins(dp(32), dp(8), dp(32), dp(8));

        // Google sign-in hidden for now – will be re-enabled later
        MaterialButton google = (MaterialButton) modernBtn("Sign in with Google", C_PRIMARY, Color.WHITE);
        google.setOnClickListener(v -> chooseGoogleAccount());
        google.setVisibility(View.GONE);
        l.addView(google, bp);

        MaterialButton local = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        local.setText("Continue"); local.setAllCaps(false); local.setTextColor(C_PRIMARY);
        local.setStrokeColor(ColorStateList.valueOf(C_OUTLINE));
        local.setOnClickListener(v -> continueLocally()); l.addView(local, bp);

        TextView note = text("All data saved on device.", 13, C_ON_SURF_VAR);
        note.setGravity(Gravity.CENTER); note.setPadding(dp(40), dp(24), dp(40), 0); l.addView(note);
        rootView = l; setContentView(l);
    }

    private void continueLocally() {
        selectedAccount = LOCAL_ACCOUNT;
        getPreferences(0).edit().putString("googleAccount", selectedAccount).apply();
        showHome("home");
    }

    private void chooseGoogleAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"}, null, null, null, null);
        startActivityForResult(intent, REQ_ACCOUNT);
    }

    private void showHome(String tab) {
        screen = tab;
        FrameLayout mainRoot = new FrameLayout(this);
        rootView = mainRoot;
        mainRoot.setBackgroundColor(C_SURFACE);

        LinearLayout contentLayout = column();
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        LinearLayout content = column(); content.setPadding(0, 0, 0, dp(100));
        scroll.addView(content);

        if ("home".equals(tab)) dashboard(content);
        else if ("stats".equals(tab)) statistics(content);
        else if ("customers".equals(tab)) customers(content);
        else if ("suppliers".equals(tab)) suppliers(content);
        else if ("export".equals(tab)) export(content);

        contentLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        mainRoot.addView(contentLayout);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1, -2);
        navParams.gravity = Gravity.BOTTOM;
        mainRoot.addView(nav(), navParams);

        // FAB for Customers and Suppliers
        if ("customers".equals(tab) || "suppliers".equals(tab)) {
            boolean isSupplier = "suppliers".equals(tab);
            FloatingActionButton fab = new FloatingActionButton(this);
            fab.setImageResource(android.R.drawable.ic_input_add);
            fab.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY));
            fab.setColorFilter(Color.WHITE);
            fab.setElevation(dp(8));
            fab.setOnClickListener(v -> partyDialog(isSupplier, null));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
            fp.gravity = Gravity.BOTTOM | Gravity.END;
            fp.setMargins(0, 0, dp(24), dp(90));
            mainRoot.addView(fab, fp);
        }

        setContentView(mainRoot);
    }

    private View header() {
        LinearLayout h = row(); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(dp(24), dp(48), dp(24), dp(16));
        LinearLayout copy = column();
        TextView t = text("Transpent", 20, C_ON_SURFACE); t.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); copy.addView(t);
        copy.addView(text("User: " + selectedAccount, 12, Color.GRAY));
        h.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView due = text("Rs " + money(store.totalDue()), 14, Color.WHITE); due.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); due.setPadding(dp(16), dp(6), dp(16), dp(6)); due.setBackground(modernBox(C_PRIMARY, 12)); h.addView(due);
        return h;
    }

    private View nav() {
        BottomNavigationView bnv = new BottomNavigationView(this);
        bnv.setBackgroundColor(C_SURFACE);
        bnv.setElevation(dp(8));
        android.view.Menu m = bnv.getMenu();
        m.add(0, 0, 0, "Home").setIcon(R.drawable.ic_nav_home);
        m.add(0, 1, 1, "Stats").setIcon(R.drawable.ic_nav_stats);
        m.add(0, 2, 2, "Customers").setIcon(R.drawable.ic_nav_customers);
        m.add(0, 3, 3, "Suppliers").setIcon(R.drawable.ic_nav_suppliers);
        m.add(0, 4, 4, "Export").setIcon(R.drawable.ic_nav_export);
        int activeId = "home".equals(screen)?0:"stats".equals(screen)?1:"customers".equals(screen)?2:"suppliers".equals(screen)?3:4;
        bnv.setSelectedItemId(activeId);
        ColorStateList itemColor = new ColorStateList(
            new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{ C_PRIMARY, C_ON_SURF_VAR });
        bnv.setItemIconTintList(itemColor);
        bnv.setItemTextColor(itemColor);
        bnv.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id==0) showHome("home");
            else if (id==1) showHome("stats");
            else if (id==2) showHome("customers");
            else if (id==3) showHome("suppliers");
            else showHome("export");
            return true;
        });
        return bnv;
    }

    private void addTab(LinearLayout n, String tab, String icon) {
        boolean act = screen.equals(tab);
        TextView v = text(icon, 24, act ? C_PRIMARY : Color.LTGRAY);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(12), dp(12), dp(12), dp(12));
        v.setOnClickListener(x -> showHome(tab));
        n.addView(v, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void dashboard(LinearLayout c) {
        // Top bar: TP logo mark + store name + account label
        LinearLayout h = row(); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(dp(20), dp(48), dp(20), dp(12));
        ImageView navLogo = new ImageView(this);
        navLogo.setImageResource(getResources().getIdentifier("logo_nav", "drawable", getPackageName()));
        navLogo.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        h.addView(navLogo, new LinearLayout.LayoutParams(dp(40), dp(40)));
        h.addView(spaceWidth(12));
        LinearLayout copy = column();
        TextView storeName = text("Transpent", 18, C_ON_SURFACE); storeName.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); copy.addView(storeName);
        copy.addView(text(selectedAccount.equals(LOCAL_ACCOUNT) ? "Local mode" : selectedAccount, 12, C_ON_SURF_VAR));
        h.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        c.addView(h);

        // Balance card – green gradient
        LinearLayout card = column(); card.setPadding(dp(24), dp(32), dp(24), dp(32));
        card.setBackground(gradientBox(new int[]{Color.parseColor("#145C41"), Color.parseColor("#2BA876")}, 24));
        card.setElevation(dp(8));
        card.addView(text("Total Pending", 14, Color.parseColor("#A8D5C2")));
        TextView bal = text("Rs " + money(store.totalDue()), 32, Color.WHITE); bal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); card.addView(bal);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(dp(20), dp(8), dp(20), dp(16)); c.addView(card, cp);

        // Quick action row
        LinearLayout actions = row(); actions.setPadding(dp(12), 0, dp(12), dp(16));
        addAction(actions, "Add",  R.drawable.ic_action_add,     v -> partyDialog(false, null));
        addAction(actions, "Pay",  R.drawable.ic_action_pay,     v -> toast("Select a customer to record payment"));
        addAction(actions, "Bill", R.drawable.ic_action_bill,    v -> pickBill());
        addAction(actions, "Hist", R.drawable.ic_action_history, v -> showHome("history"));
        c.addView(actions);

        // Features grid
        c.addView(space(8));
        TextView featLabel = text("Features", 16, C_ON_SURFACE);
        featLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        featLabel.setPadding(dp(20), dp(8), dp(20), dp(4)); c.addView(featLabel);
        LinearLayout grid = column();
        LinearLayout row1 = row();
        addGridItem(row1, "Customers", R.drawable.ic_feat_customers, C_PRIMARY, v -> showHome("customers"));
        addGridItem(row1, "Suppliers", R.drawable.ic_feat_suppliers, C_PRIMARY, v -> showHome("suppliers"));
        grid.addView(row1);
        LinearLayout row2 = row();
        addGridItem(row2, "Export CSV", R.drawable.ic_feat_export,  Color.parseColor("#2E7D32"), v -> showHome("export"));
        addGridItem(row2, "Stats",      R.drawable.ic_feat_stats,   Color.parseColor("#E65100"), v -> showHome("stats"));
        grid.addView(row2);
        LinearLayout row3 = row();
        addGridItem(row3, "History",  R.drawable.ic_feat_history,  Color.parseColor("#1565C0"), v -> showHome("history"));
        addGridItem(row3, "Settings", R.drawable.ic_feat_settings, Color.parseColor("#546E7A"), v -> settingsDialog());
        grid.addView(row3);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.setMargins(dp(12), 0, dp(12), dp(16));
        c.addView(grid, gp);
    }

    private void addAction(LinearLayout parent, String label, int iconRes, View.OnClickListener click) {
        LinearLayout col = column(); col.setGravity(Gravity.CENTER);
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18)); card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(C_SURF_VAR); card.setStrokeWidth(dp(1));
        FrameLayout frame = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(C_PRIMARY);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(14), dp(14), dp(14), dp(14));
        frame.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        card.addView(frame);
        card.setOnClickListener(click);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(64), dp(64));
        col.addView(card, cp);
        TextView lbl = text(label, 12, C_ON_SURF_VAR);
        lbl.setGravity(Gravity.CENTER); lbl.setPadding(0, dp(4), 0, 0);
        col.addView(lbl);
        parent.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void addGridItem(LinearLayout parent, String label, int iconRes, int color, View.OnClickListener click) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(20)); card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(C_SURF_VAR); card.setStrokeWidth(dp(1));
        LinearLayout content = column(); content.setPadding(dp(18), dp(18), dp(18), dp(14));
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(color);
        content.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
        content.addView(space(10));
        content.addView(text(label, 14, C_ON_SURFACE));
        card.addView(content);
        card.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        parent.addView(card, lp);
    }

    private void settingsDialog() {
        String[] options = {"Manage products", "Clear all data", "About"};
        new MaterialAlertDialogBuilder(this).setTitle("Settings").setItems(options, (d, which) -> {
            if (which == 0) toast("Product management coming soon");
            if (which == 1) new MaterialAlertDialogBuilder(this).setTitle("Clear all data?").setMessage("This will delete all customers, suppliers and entries. This cannot be undone.").setPositiveButton("Clear", (dd, ww) -> { store.customers.clear(); store.suppliers.clear(); store.save(); showHome("home"); }).setNegativeButton("Cancel", null).show();
            if (which == 2) new MaterialAlertDialogBuilder(this).setTitle("Transpent").setMessage("v1.0 – Local ledger tracking app").setPositiveButton("OK", null).show();
        }).show();
    }

    private void statistics(LinearLayout c) {
        TextView title = text("Statistics", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(8)); c.addView(title);

        // Compute real totals from ledger
        long customerTotal = 0, customerPaid = 0, supplierTotal = 0, supplierPaid = 0;
        for (Party p : store.customers) { for (Entry e : p.items) { customerTotal += e.total(); customerPaid += e.paid; } customerPaid += p.miscPaid; }
        for (Party p : store.suppliers) { for (Entry e : p.items) { supplierTotal += e.total(); supplierPaid += e.paid; } supplierPaid += p.miscPaid; }
        long customerDue = customerTotal - customerPaid;
        long supplierDue = supplierTotal - supplierPaid;

        // Summary card
        LinearLayout card = column(); card.setPadding(dp(24), dp(24), dp(24), dp(24));
        card.setBackground(modernBox(Color.WHITE, 24)); card.setElevation(dp(6));
        card.addView(text("Total Outstanding", 13, C_ON_SURF_VAR));
        TextView bal = text("Rs " + money(store.totalDue()), 30, C_ON_SURFACE); bal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        card.addView(bal);
        card.addView(space(8));
        card.addView(text(store.customers.size() + " customers  ·  " + store.suppliers.size() + " suppliers", 13, C_ON_SURF_VAR));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-1, -2); clp.setMargins(dp(20), dp(8), dp(20), dp(16)); c.addView(card, clp);

        // Customer vs Supplier due row
        LinearLayout row = row(); row.setPadding(dp(20), 0, dp(20), 0);
        LinearLayout custCard = column(); custCard.setPadding(dp(16), dp(18), dp(16), dp(18));
        custCard.setBackground(modernBox(Color.parseColor("#145C41"), 16));
        custCard.addView(text("Customers Due", 12, Color.parseColor("#A8D5C2")));
        custCard.addView(text("Rs " + money(Math.max(0, customerDue)), 20, Color.WHITE));
        row.addView(custCard, new LinearLayout.LayoutParams(0, -2, 1)); row.addView(spaceWidth(12));

        LinearLayout suppCard = column(); suppCard.setPadding(dp(16), dp(18), dp(16), dp(18));
        suppCard.setBackground(modernBox(C_PRIMARY, 16));
        suppCard.addView(text("Suppliers Due", 12, Color.parseColor("#A8D5C2")));
        suppCard.addView(text("Rs " + money(Math.max(0, supplierDue)), 20, Color.WHITE));
        row.addView(suppCard, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-1, -2); rlp.setMargins(0, 0, 0, dp(16)); c.addView(row, rlp);

        // Empty state if no data
        if (store.customers.isEmpty() && store.suppliers.isEmpty()) {
            TextView empty = text("Add customers or suppliers to see stats here.", 14, C_ON_SURF_VAR);
            empty.setGravity(android.view.Gravity.CENTER); empty.setPadding(dp(40), dp(40), dp(40), dp(40));
            c.addView(empty);
        }
    }

    private void customers(LinearLayout c) {
        TextView title = text("Customers", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(8)); c.addView(title);
        summary(c, store.customers);
        int i = 0; for (Party p : store.customers) c.addView(partyCard(p, false, i++));
        if (store.customers.isEmpty()) {
            TextView empty = text("No customers yet.\nTap + to add one.", 15, C_ON_SURF_VAR);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(40), dp(60), dp(40), dp(40));
            c.addView(empty);
        }
    }

    private void suppliers(LinearLayout c) {
        TextView title = text("Suppliers", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(8)); c.addView(title);
        summary(c, store.suppliers);
        int i = 0; for (Party p : store.suppliers) c.addView(partyCard(p, true, i++));
        if (store.suppliers.isEmpty()) {
            TextView empty = text("No suppliers yet.\nTap + to add one.", 15, C_ON_SURF_VAR);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(40), dp(60), dp(40), dp(40));
            c.addView(empty);
        }
    }

    private void summary(LinearLayout c, ArrayList<Party> parties) {
        int active = 0; long due = 0; for (Party p : parties) { if (p.due() > 0) active++; due += p.due(); }
        TextView sum = text(active + " active parties | Rs " + money(due) + " pending", 14, Color.GRAY); sum.setPadding(dp(24), 0, dp(24), dp(16)); c.addView(sum);
    }

    private View partyCard(Party p, boolean supplier, int colorIdx) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(16));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(C_SURFACE);
        card.setStrokeColor(C_SURF_VAR);
        card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2);
        cp.setMargins(dp(16), dp(6), dp(16), dp(6));
        card.setLayoutParams(cp);

        LinearLayout inner = column(); inner.setPadding(dp(16), dp(16), dp(16), dp(16));
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);

        TextView av = text(p.name.substring(0, Math.min(1, p.name.length())).toUpperCase(), 16, C_ON_PRIMARY);
        av.setGravity(Gravity.CENTER); av.setBackground(modernBox(C_PRIMARY, 20));
        top.addView(av, new LinearLayout.LayoutParams(dp(40), dp(40)));
        top.addView(spaceWidth(12));

        LinearLayout names = column();
        TextView nameView = text(p.name, 16, C_ON_SURFACE); nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); names.addView(nameView);
        names.addView(text(p.phone.isEmpty() ? (supplier ? "Supplier" : "Customer") : p.phone, 13, C_ON_SURF_VAR));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1));

        TextView due = text("Rs " + money(p.due()), 15, p.due() > 0 ? C_ERROR : C_SUCCESS);
        due.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); top.addView(due);
        inner.addView(top);
        card.addView(inner);
        card.setOnClickListener(v -> addItemDialog(p));
        return card;
    }

    class BarChart extends View {
        public BarChart(android.content.Context c) { super(c); }
        @Override protected void onDraw(android.graphics.Canvas canvas) {
            super.onDraw(canvas);
            android.graphics.Paint p = new android.graphics.Paint(); p.setAntiAlias(true);
            float w = getWidth(), h = getHeight();
            float bw = w / 14f;
            for (int i = 0; i < 7; i++) {
                float val1 = (float)Math.random() * h * 0.8f;
                float val2 = (float)Math.random() * h * 0.6f;
                p.setColor(Color.parseColor("#DBE5DE")); canvas.drawRoundRect(i*bw*2 + bw/2, h - val1, i*bw*2 + bw*1.5f, h, 8, 8, p);
                p.setColor(Color.parseColor("#1E7C5A")); canvas.drawRoundRect(i*bw*2 + bw/2, h - val2, i*bw*2 + bw*1.5f, h, 8, 8, p);
            }
        }
    }

    private void products(LinearLayout c) {
        TextView title = text("Statistics", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), 24, dp(16)); c.addView(title);
        
        Button add = modernBtn("Add Product Price", C_PRIMARY, Color.WHITE); add.setOnClickListener(v -> productDialog(null));
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(56)); ap.setMargins(dp(24), 0, dp(24), dp(16)); c.addView(add, ap);

        int i = 0; for (Product p : store.products) {
            LinearLayout card = column(); card.setBackground(modernBox(Color.WHITE, 16)); card.setPadding(dp(20), dp(16), dp(24), dp(16));
            card.setElevation(dp(4));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(dp(24), dp(8), dp(24), dp(8)); card.setLayoutParams(cp);
            LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout copy = column(); TextView nameView = text(p.name, 16, C_ON_SURFACE); nameView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); copy.addView(nameView); 
            copy.addView(text("Rs " + money(p.price), 14, Color.GRAY)); r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = modernBtn("Edit", Color.WHITE, C_PRIMARY); edit.setOnClickListener(v -> productDialog(p)); r.addView(edit, new LinearLayout.LayoutParams(dp(80), dp(40))); card.addView(r); c.addView(card);
        }
    }

    private void export(LinearLayout c) {
        TextView title = text("Statistics", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), 24, dp(16)); c.addView(title);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(56)); p.setMargins(dp(24), dp(8), dp(24), dp(8));
        
        Button all = modernBtn("Export Single CSV", C_PRIMARY, Color.WHITE); all.setOnClickListener(v -> exportCsv(false)); c.addView(all, p);
        Button split = modernBtn("Export Separate CSVs", Color.parseColor("#43A047"), Color.WHITE); split.setOnClickListener(v -> exportCsv(true)); c.addView(split, p);
        Button backup = modernBtn("Backup to Drive", Color.parseColor("#FB8C00"), Color.WHITE); backup.setOnClickListener(v -> driveBackup()); c.addView(backup, p);
        Button restore = modernBtn("Restore from Drive", Color.GRAY, Color.WHITE); restore.setOnClickListener(v -> driveRestore()); c.addView(restore, p);
        
        c.addView(space(40));
        ImageView wm = new ImageView(this); wm.setImageResource(getResources().getIdentifier("logo_wordmark", "drawable", getPackageName()));
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(dp(200), dp(60)); wp.gravity = Gravity.CENTER; c.addView(wm, wp);
    }

    private void partyDialog(boolean supplier, Party edit) {
        LinearLayout f = form(); TextInputEditText name = input("Name"); TextInputEditText phone = input("Phone / note"); f.addView((android.view.View)name.getTag()); f.addView((android.view.View)phone.getTag());
        new MaterialAlertDialogBuilder(this).setTitle(supplier ? "Supplier business" : "Customer").setView(f).setPositiveButton("Save", (d,w) -> {
            Party p = new Party(); p.id = id(); p.name = val(name); p.phone = val(phone); if (!p.name.isEmpty()) { (supplier ? store.suppliers : store.customers).add(p); saveRefresh(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void productDialog(Product edit) {
        LinearLayout f = form(); TextInputEditText name = input("Product name"); TextInputEditText price = input("Price"); price.setInputType(InputType.TYPE_CLASS_NUMBER); if (edit != null) { name.setText(edit.name); price.setText(String.valueOf(edit.price)); } f.addView((android.view.View)name.getTag()); f.addView((android.view.View)price.getTag());
        new MaterialAlertDialogBuilder(this).setTitle("Product price").setView(f).setPositiveButton("Save", (d,w) -> {
            Product p = edit == null ? new Product() : edit; if (edit == null) { p.id = id(); store.products.add(p); } p.name = val(name); p.price = parse(price); saveRefresh();
        }).setNegativeButton("Cancel", null).show();
    }

    private void addItemDialog(Party party) {
        LinearLayout f = form(); Spinner spinner = new Spinner(this); ArrayList<String> labels = new ArrayList<>(); labels.add("Manual item"); for (Product p : store.products) labels.add(p.name + " - Rs " + money(p.price)); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        TextInputEditText name = input("Item name"); TextInputEditText price = input("Price"); TextInputEditText qty = input("Qty"); qty.setText("1"); price.setInputType(InputType.TYPE_CLASS_NUMBER); qty.setInputType(InputType.TYPE_CLASS_NUMBER); f.addView(spinner); f.addView((android.view.View)name.getTag()); f.addView((android.view.View)price.getTag()); f.addView((android.view.View)qty.getTag());
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) { if (pos > 0) { Product p = store.products.get(pos - 1); name.setText(p.name); price.setText(String.valueOf(p.price)); }} public void onNothingSelected(android.widget.AdapterView<?> a) {} });
        new MaterialAlertDialogBuilder(this).setTitle("Add bought item").setView(f).setPositiveButton("Add", (d,w) -> {
            Entry e = new Entry(); e.id = id(); e.name = val(name); e.price = parse(price); e.qty = Math.max(1, parse(qty)); e.date = now(); if (!e.name.isEmpty()) { party.items.add(e); saveRefresh(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void paymentDialog(Party party) {
        String[] options = {"Pay per item", "Misc part payment", "Paid in full"};
        new MaterialAlertDialogBuilder(this).setTitle("Record payment").setItems(options, (d, which) -> { if (which == 0) itemPaymentDialog(party); if (which == 1) miscPaymentDialog(party); if (which == 2) { party.miscPaid += party.due(); saveRefresh(); } }).show();
    }

    private void itemPaymentDialog(Party party) {
        LinearLayout f = form(); ArrayList<EditText> fields = new ArrayList<>();
        for (Entry e : party.items) if (e.due() > 0) { TextView label = text(e.name + " pending Rs " + money(e.due()), 13, Color.GRAY); TextInputEditText paid = input("Paid amount"); paid.setInputType(InputType.TYPE_CLASS_NUMBER); f.addView(label); f.addView((android.view.View)paid.getTag()); fields.add(paid); }
        new MaterialAlertDialogBuilder(this).setTitle("Item-wise payment").setView(f).setPositiveButton("Save", (d,w) -> { int x = 0; for (Entry e : party.items) if (e.due() > 0) e.paid += Math.min(e.due(), parse(fields.get(x++))); saveRefresh(); }).setNegativeButton("Cancel", null).show();
    }

    private void miscPaymentDialog(Party party) { TextInputEditText paid = input("Amount paid"); paid.setInputType(InputType.TYPE_CLASS_NUMBER); new MaterialAlertDialogBuilder(this).setTitle("Misc payment").setView((android.view.View)paid.getTag()).setPositiveButton("Save", (d,w) -> { party.miscPaid += Math.min(party.due(), parse(paid)); saveRefresh(); }).setNegativeButton("Cancel", null).show(); }

    private void billDialog(Party party) {
        selectedSupplierForBill = party; String[] opts = {"Click bill photo", "Upload bill image"};
        new MaterialAlertDialogBuilder(this).setTitle("Attach bill reference").setItems(opts, (d, which) -> { if (which == 0) openCamera(); else pickBill(); }).show();
    }

    private void openCamera() {
        try {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, "transpent_bill_" + System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            pendingCameraUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            i.putExtra(MediaStore.EXTRA_OUTPUT, pendingCameraUri);
            i.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivityForResult(i, REQ_CAMERA_BILL);
        } catch (Exception e) { toast("Camera unavailable: " + e.getMessage()); }
    }

    private void pickBill() { Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT); i.setType("image/*"); i.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(i, REQ_PICK_BILL); }

    private void exportCsv(boolean split) {
        try {
            String name = split ? "transpent-report.zip" : "transpent-ledger.csv";
            exportSplit = split; Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT); i.setType(split ? "application/zip" : "text/csv"); i.putExtra(Intent.EXTRA_TITLE, name); startActivityForResult(i, REQ_EXPORT);
        } catch (Exception e) { toast(e.getMessage()); }
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data); if (res != RESULT_OK) return;
        if (req == REQ_ACCOUNT) { selectedAccount = data.getStringExtra(AccountManager.KEY_ACCOUNT_NAME); getPreferences(0).edit().putString("googleAccount", selectedAccount).apply(); showHome("customers"); }
        if ((req == REQ_PICK_BILL || req == REQ_CAMERA_BILL) && selectedSupplierForBill != null) { Uri u = req == REQ_PICK_BILL ? data.getData() : pendingCameraUri; selectedSupplierForBill.billUris.add(u.toString()); store.save(); toast("Bill attached"); showHome("suppliers"); }
        if (req == REQ_EXPORT && data != null) writeExport(data.getData());
    }

    private void writeExport(Uri uri) {
        try (OutputStream out = getContentResolver().openOutputStream(uri)) { out.write(store.csv().getBytes(StandardCharsets.UTF_8)); toast("Export saved"); } catch (Exception e) { toast("Export failed: " + e.getMessage()); }
    }

    private void driveBackup() { new Thread(() -> { try { String token = googleToken(); uploadDrive(token, store.toJson().toString(2)); ui("Drive backup complete"); } catch (Exception e) { ui("Drive backup failed: " + e.getMessage()); } }).start(); }
    private void driveRestore() { new Thread(() -> { try { String token = googleToken(); String json = downloadDrive(token); if (json == null) { ui("No Drive backup found"); return; } store.fromJson(new JSONObject(json)); store.save(); runOnUiThread(() -> showHome(screen)); ui("Drive restore complete"); } catch (Exception e) { ui("Drive restore failed: " + e.getMessage()); } }).start(); }

    private String googleToken() throws Exception {
        if (LOCAL_ACCOUNT.equals(selectedAccount)) throw new IllegalStateException("Sign in with Google to use Drive backup");
        AccountManager am = AccountManager.get(this); Account[] accounts = am.getAccountsByType("com.google"); Account selected = null; for (Account a : accounts) if (a.name.equals(selectedAccount)) selected = a; if (selected == null) throw new IllegalStateException("Google account not available on this phone");
        return am.blockingGetAuthToken(selected, "oauth2:https://www.googleapis.com/auth/drive.file", true);
    }

    private void uploadDrive(String token, String json) throws Exception {
        String boundary = "transpent" + System.currentTimeMillis(); URL url = new URL("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart"); HttpURLConnection c = (HttpURLConnection) url.openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Content-Type", "multipart/related; boundary=" + boundary);
        String meta = "{\"name\":\"transpent-backup.json\",\"mimeType\":\"application/json\"}";
        String body = "--" + boundary + "\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n" + meta + "\r\n--" + boundary + "\r\nContent-Type: application/json\r\n\r\n" + json + "\r\n--" + boundary + "--";
        c.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8)); if (c.getResponseCode() >= 300) throw new IOException(read(c.getErrorStream()));
    }

    private String downloadDrive(String token) throws Exception {
        URL list = new URL("https://www.googleapis.com/drive/v3/files?q=name='transpent-backup.json'&orderBy=createdTime%20desc&pageSize=1&fields=files(id)"); HttpURLConnection c = (HttpURLConnection) list.openConnection(); c.setRequestProperty("Authorization", "Bearer " + token); String r = read(c.getInputStream()); JSONArray files = new JSONObject(r).getJSONArray("files"); if (files.length() == 0) return null;
        String id = files.getJSONObject(0).getString("id"); URL dl = new URL("https://www.googleapis.com/drive/v3/files/" + id + "?alt=media"); HttpURLConnection d = (HttpURLConnection) dl.openConnection(); d.setRequestProperty("Authorization", "Bearer " + token); return read(d.getInputStream());
    }

    private LinearLayout base() { LinearLayout l = column(); l.setBackgroundColor(C_SURFACE); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout form() { LinearLayout l = column(); l.setPadding(dp(24), dp(16), dp(24), 0); return l; }
    private TextView text(String s, int sp, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); return v; }
    private MaterialButton modernBtn(String s, int bgColor, int textColor) { MaterialButton b = new MaterialButton(this); b.setText(s); b.setTextColor(textColor); b.setBackgroundTintList(ColorStateList.valueOf(bgColor)); b.setAllCaps(false); b.setCornerRadius(dp(16)); b.setTextSize(14); b.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return b; }
    private TextInputEditText input(String hint) {
        TextInputLayout til = new TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle);
        til.setHint(hint);
        TextInputEditText et = new TextInputEditText(til.getContext());
        et.setSingleLine(true);
        til.addView(et, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, dp(8), 0, dp(8));
        til.setLayoutParams(p);
        et.setTag(til);
        return et;
    }
    private Space space(int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return s; }
    private Space spaceWidth(int w) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(dp(w), 1)); return s; }
    private android.graphics.drawable.Drawable modernBox(int color, int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE); g.setCornerRadius(dp(radius)); g.setColor(color); return g;
    }
    private android.graphics.drawable.Drawable gradientBox(int[] colors, int radius) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BR_TL, colors); g.setCornerRadius(dp(radius)); return g;
    }
    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }
    private String val(EditText e) { return e.getText().toString().trim(); }
    private long parse(EditText e) { try { return Long.parseLong(val(e)); } catch (Exception ex) { return 0; } }
    private String id() { return UUID.randomUUID().toString(); }
    private String now() { return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date()); }
    private String money(long x) { return String.valueOf(x); }
    private void saveRefresh() { store.save(); showHome(screen); }
    private void toast(String s) { if (rootView != null) Snackbar.make(rootView, s, Snackbar.LENGTH_LONG).show(); else Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private void ui(String s) { runOnUiThread(() -> toast(s)); }
    private String read(InputStream in) throws IOException { if (in == null) return ""; ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] b = new byte[4096]; for (int n; (n = in.read(b)) > 0;) out.write(b,0,n); return out.toString("UTF-8"); }

    class LedgerStore {
        ArrayList<Product> products = new ArrayList<>(); ArrayList<Party> customers = new ArrayList<>(); ArrayList<Party> suppliers = new ArrayList<>();
        File file() { return new File(getFilesDir(), "ledger.json"); }
        void load() { try { if (file().exists()) fromJson(new JSONObject(read(new FileInputStream(file())))); else seed(); } catch (Exception e) { seed(); } }
        void seed() { products.add(new Product("Rice 1kg", 60)); products.add(new Product("Sugar 1kg", 45)); products.add(new Product("Oil pouch", 130)); save(); }
        void save() { try (FileOutputStream f = new FileOutputStream(file())) { f.write(toJson().toString(2).getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) {} }
        long totalDue() { long x = 0; for (Party p : customers) x += p.due(); for (Party p : suppliers) x += p.due(); return x; }
        JSONObject toJson() throws Exception { JSONObject o = new JSONObject(); o.put("products", Product.arr(products)); o.put("customers", Party.arr(customers)); o.put("suppliers", Party.arr(suppliers)); return o; }
        void fromJson(JSONObject o) throws Exception { products = Product.list(o.optJSONArray("products")); customers = Party.list(o.optJSONArray("customers")); suppliers = Party.list(o.optJSONArray("suppliers")); }
        String csv() { StringBuilder s = new StringBuilder("type,name,phone,item,qty,price,total,paid,due,date,bill_refs\n"); rows(s,"customer",customers); rows(s,"supplier",suppliers); return s.toString(); }
        String partyCsv(String type, ArrayList<Party> ps) { StringBuilder s = new StringBuilder("type,name,phone,item,qty,price,total,paid,due,date,bill_refs\n"); rows(s,type,ps); return s.toString(); }
        String productsCsv() { StringBuilder s = new StringBuilder("name,price\n"); for (Product p : products) s.append(q(p.name)).append(',').append(p.price).append('\n'); return s.toString(); }
        void rows(StringBuilder s, String type, ArrayList<Party> ps) { for (Party p : ps) { if (p.items.isEmpty()) line(s,type,p,"",0,0,0,0,p.due(),"",p.billUris.toString()); for (Entry e : p.items) line(s,type,p,e.name,e.qty,e.price,e.total(),e.paid,e.due(),e.date,p.billUris.toString()); if (p.miscPaid > 0) line(s,type,p,"Misc paid",1,0,0,p.miscPaid,0,"",""); } }
        void line(StringBuilder s, String type, Party p, String item, long qty, long price, long total, long paid, long due, String date, String bills) { s.append(type).append(',').append(q(p.name)).append(',').append(q(p.phone)).append(',').append(q(item)).append(',').append(qty).append(',').append(price).append(',').append(total).append(',').append(paid).append(',').append(due).append(',').append(q(date)).append(',').append(q(bills)).append('\n'); }
        String q(String v) { return '"' + v.replace("\"", "\"\"") + '"'; }
    }

    static class Product { String id = UUID.randomUUID().toString(), name; long price; Product() {} Product(String n, long p) { name=n; price=p; }
        JSONObject json() throws Exception { JSONObject o = new JSONObject(); o.put("id",id); o.put("name",name); o.put("price",price); return o; }
        static Product from(JSONObject o) { Product p = new Product(); p.id=o.optString("id"); p.name=o.optString("name"); p.price=o.optLong("price"); return p; }
        static JSONArray arr(ArrayList<Product> list) throws Exception { JSONArray a = new JSONArray(); for (Product p:list) a.put(p.json()); return a; }
        static ArrayList<Product> list(JSONArray a) { ArrayList<Product> l = new ArrayList<>(); if (a!=null) for(int i=0;i<a.length();i++) l.add(from(a.optJSONObject(i))); return l; }}
    static class Party { String id = UUID.randomUUID().toString(), name="", phone=""; long miscPaid; ArrayList<Entry> items = new ArrayList<>(); ArrayList<String> billUris = new ArrayList<>(); long due(){ long t=0; for(Entry e:items)t+=e.due(); return Math.max(0,t-miscPaid); }
        JSONObject json() throws Exception { JSONObject o=new JSONObject(); o.put("id",id); o.put("name",name); o.put("phone",phone); o.put("miscPaid",miscPaid); JSONArray a=new JSONArray(); for(Entry e:items)a.put(e.json()); o.put("items",a); o.put("billUris",new JSONArray(billUris)); return o; }
        static Party from(JSONObject o) { Party p=new Party(); p.id=o.optString("id"); p.name=o.optString("name"); p.phone=o.optString("phone"); p.miscPaid=o.optLong("miscPaid"); JSONArray a=o.optJSONArray("items"); if(a!=null)for(int i=0;i<a.length();i++)p.items.add(Entry.from(a.optJSONObject(i))); JSONArray b=o.optJSONArray("billUris"); if(b!=null)for(int i=0;i<b.length();i++)p.billUris.add(b.optString(i)); return p; }
        static JSONArray arr(ArrayList<Party> list) throws Exception { JSONArray a=new JSONArray(); for(Party p:list)a.put(p.json()); return a; }
        static ArrayList<Party> list(JSONArray a) { ArrayList<Party> l=new ArrayList<>(); if(a!=null)for(int i=0;i<a.length();i++)l.add(from(a.optJSONObject(i))); return l; }}
    static class Entry { String id=UUID.randomUUID().toString(), name="", date=""; long price, qty=1, paid; long total(){ return price*qty; } long due(){ return Math.max(0,total()-paid); }
        JSONObject json() throws Exception { JSONObject o=new JSONObject(); o.put("id",id); o.put("name",name); o.put("price",price); o.put("qty",qty); o.put("paid",paid); o.put("date",date); return o; }
        static Entry from(JSONObject o){ Entry e=new Entry(); e.id=o.optString("id"); e.name=o.optString("name"); e.price=o.optLong("price"); e.qty=o.optLong("qty",1); e.paid=o.optLong("paid"); e.date=o.optString("date"); return e; }}
}




