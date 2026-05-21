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
    private boolean ledgerShowSupplier = false;
    private boolean homeShowSupplier  = false;
    private boolean historyShowSupplier = false;
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

        if ("home".equals(tab))     dashboard(content);
        else if ("ledger".equals(tab))   ledger(content);
        else if ("search".equals(tab))   search(content);
        else if ("products".equals(tab)) products(content);
        else if ("history".equals(tab))  history(content);
        else if ("stats".equals(tab))    statistics(content);
        else if ("export".equals(tab))   export(content);

        contentLayout.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        mainRoot.addView(contentLayout);

        FrameLayout.LayoutParams navParams = new FrameLayout.LayoutParams(-1, -2);
        navParams.gravity = Gravity.BOTTOM;
        mainRoot.addView(nav(), navParams);

        // FAB on Ledger tab
        if ("ledger".equals(tab)) {
            FloatingActionButton fab = new FloatingActionButton(this);
            fab.setImageResource(android.R.drawable.ic_input_add);
            fab.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY));
            fab.setColorFilter(Color.WHITE);
            fab.setElevation(dp(8));
            fab.setOnClickListener(v -> partyDialog(ledgerShowSupplier, null));
            FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
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
        // Show context-aware due: customers owed to us (green), suppliers we owe (blue)
        long custDue = store.customerDue();
        TextView due = text("Rs " + money(custDue), 14, Color.WHITE);
        due.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        due.setPadding(dp(16), dp(6), dp(16), dp(6));
        due.setBackground(modernBox(C_PRIMARY, 12));
        h.addView(due);
        return h;
    }

    private View nav() {
        BottomNavigationView bnv = new BottomNavigationView(this);
        bnv.setBackgroundColor(C_SURFACE);
        bnv.setElevation(dp(8));
        android.view.Menu m = bnv.getMenu();
        m.add(0, 0, 0, "Home").setIcon(R.drawable.ic_nav_home);
        m.add(0, 1, 1, "Ledger").setIcon(R.drawable.ic_nav_ledger);
        m.add(0, 2, 2, "Search").setIcon(R.drawable.ic_nav_search);
        m.add(0, 3, 3, "Products").setIcon(R.drawable.ic_nav_products);
        m.add(0, 4, 4, "History").setIcon(R.drawable.ic_nav_history);
        int activeId = "home".equals(screen)?0:"ledger".equals(screen)?1:"search".equals(screen)?2:"products".equals(screen)?3:"history".equals(screen)?4:0;
        bnv.setSelectedItemId(activeId);
        ColorStateList itemColor = new ColorStateList(
            new int[][]{ new int[]{android.R.attr.state_checked}, new int[]{}},
            new int[]{ C_PRIMARY, C_ON_SURF_VAR });
        bnv.setItemIconTintList(itemColor);
        bnv.setItemTextColor(itemColor);
        bnv.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id==0) showHome("home");
            else if (id==1) showHome("ledger");
            else if (id==2) showHome("search");
            else if (id==3) showHome("products");
            else showHome("history");
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

        // Unified Section Card
        int[] cardColors  = homeShowSupplier
            ? new int[]{Color.parseColor("#0D2D6B"), Color.parseColor("#1976D2")}
            : new int[]{Color.parseColor("#145C41"), Color.parseColor("#2BA876")};

        LinearLayout sectionLayout = column();
        sectionLayout.setPadding(dp(20), dp(20), dp(20), dp(20));
        sectionLayout.setBackground(gradientBox(cardColors, 24));
        sectionLayout.setElevation(dp(6));

        // Pill switcher inside card
        LinearLayout pillRow = row(); pillRow.setPadding(dp(4), dp(4), dp(4), dp(4));
        int pillBg = homeShowSupplier ? Color.parseColor("#091F4A") : Color.parseColor("#0B3D2A");
        pillRow.setBackground(modernBox(pillBg, 32));
        LinearLayout.LayoutParams pillWrapLp = new LinearLayout.LayoutParams(-1, -2);

        MaterialButton hBtnCust = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        hBtnCust.setText("Customers"); hBtnCust.setAllCaps(false); hBtnCust.setCornerRadius(dp(28)); hBtnCust.setSingleLine(true);
        MaterialButton hBtnSupp = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        hBtnSupp.setText("Suppliers"); hBtnSupp.setAllCaps(false); hBtnSupp.setCornerRadius(dp(28)); hBtnSupp.setSingleLine(true);

        if (!homeShowSupplier) {
            hBtnCust.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            hBtnCust.setTextColor(Color.parseColor("#145C41"));
            hBtnSupp.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            hBtnSupp.setTextColor(Color.parseColor("#A8D5C2"));
            hBtnSupp.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
            hBtnCust.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
        } else {
            hBtnSupp.setBackgroundTintList(ColorStateList.valueOf(Color.WHITE));
            hBtnSupp.setTextColor(Color.parseColor("#0D2D6B"));
            hBtnCust.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
            hBtnCust.setTextColor(Color.parseColor("#B3D4FF"));
            hBtnCust.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
            hBtnSupp.setStrokeColor(ColorStateList.valueOf(Color.TRANSPARENT));
        }

        hBtnCust.setOnClickListener(v -> { homeShowSupplier = false; showHome("home"); });
        hBtnSupp.setOnClickListener(v -> { homeShowSupplier = true;  showHome("home"); });
        LinearLayout.LayoutParams php = new LinearLayout.LayoutParams(0, dp(44), 1);
        pillRow.addView(hBtnCust, php); pillRow.addView(hBtnSupp, php);
        sectionLayout.addView(pillRow, pillWrapLp);

        // Balance details inside card
        sectionLayout.addView(space(16));
        long displayDue  = homeShowSupplier ? store.supplierDue() : store.customerDue();
        String cardLabel = homeShowSupplier ? "Suppliers Pending (You Owe)" : "Customers Pending (They Owe You)";
        sectionLayout.addView(text(cardLabel, 13, homeShowSupplier ? Color.parseColor("#B3D4FF") : Color.parseColor("#A8D5C2")));
        sectionLayout.addView(space(4));
        TextView bal = text("Rs " + money(displayDue), 32, Color.WHITE); bal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        sectionLayout.addView(bal);
        int count = homeShowSupplier ? store.suppliers.size() : store.customers.size();
        String who = homeShowSupplier ? "supplier" : "customer";
        sectionLayout.addView(text(count + " " + who + (count == 1 ? "" : "s"), 13, homeShowSupplier ? Color.parseColor("#B3D4FF") : Color.parseColor("#A8D5C2")));
        sectionLayout.addView(space(16));

        // Quick action row inside card
        LinearLayout actions = row(); actions.setPadding(0, 0, 0, dp(8));
        int activeColor = homeShowSupplier ? Color.parseColor("#0D2D6B") : C_PRIMARY;
        addAction(actions, "Add",  R.drawable.ic_action_add,     activeColor, Color.WHITE, v -> dashboardAddAction());
        addAction(actions, "Pay",  R.drawable.ic_action_pay,     activeColor, Color.WHITE, v -> dashboardPayAction());
        addAction(actions, "Hist", R.drawable.ic_action_history, activeColor, Color.WHITE, v -> {
            historyShowSupplier = homeShowSupplier;
            showHome("history");
        });
        sectionLayout.addView(actions);

        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2); slp.setMargins(dp(20), dp(4), dp(20), dp(16));
        c.addView(sectionLayout, slp);

        // Features grid
        c.addView(space(8));
        TextView featLabel = text("Features", 16, C_ON_SURFACE);
        featLabel.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        featLabel.setPadding(dp(20), dp(8), dp(20), dp(4)); c.addView(featLabel);
        LinearLayout grid = column();
        LinearLayout row1 = row();
        addGridItem(row1, "Customers", R.drawable.ic_feat_customers, C_PRIMARY, v -> { ledgerShowSupplier=false; showHome("ledger"); });
        addGridItem(row1, "Suppliers", R.drawable.ic_feat_suppliers, C_PRIMARY, v -> { ledgerShowSupplier=true;  showHome("ledger"); });
        grid.addView(row1);
        LinearLayout row2 = row();
        addGridItem(row2, "Export CSV", R.drawable.ic_feat_export,  Color.parseColor("#2E7D32"), v -> showHome("export"));
        addGridItem(row2, "Stats",      R.drawable.ic_feat_stats,   Color.parseColor("#E65100"), v -> showHome("stats"));
        grid.addView(row2);
        LinearLayout row3 = row();
        addGridItem(row3, "Bills",    R.drawable.ic_action_bill,   Color.parseColor("#1565C0"), v -> billsFeatureAction());
        addGridItem(row3, "Settings", R.drawable.ic_feat_settings, Color.parseColor("#546E7A"), v -> settingsDialog());
        grid.addView(row3);
        LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(-1, -2); gp.setMargins(dp(12), 0, dp(12), dp(16));
        c.addView(grid, gp);
    }

    private void addAction(LinearLayout parent, String label, int iconRes, int tintColor, int textColor, View.OnClickListener click) {
        LinearLayout col = column(); col.setGravity(Gravity.CENTER);
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(18)); card.setCardElevation(dp(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeWidth(0);
        FrameLayout frame = new FrameLayout(this);
        ImageView iv = new ImageView(this);
        iv.setImageResource(iconRes);
        iv.setColorFilter(tintColor);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        iv.setPadding(dp(14), dp(14), dp(14), dp(14));
        frame.addView(iv, new FrameLayout.LayoutParams(-1, -1));
        card.addView(frame);
        card.setOnClickListener(click);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(64), dp(64));
        col.addView(card, cp);
        TextView lbl = text(label, 12, textColor);
        lbl.setGravity(Gravity.CENTER); lbl.setPadding(0, dp(4), 0, 0);
        col.addView(lbl);
        parent.addView(col, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private void dashboardAddAction() {
        ArrayList<Party> list = homeShowSupplier ? store.suppliers : store.customers;
        if (list.isEmpty()) {
            partyDialog(homeShowSupplier, null);
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        labels.add(homeShowSupplier ? "+ Add New Supplier" : "+ Add New Customer");
        for (Party p : list) {
            labels.add(p.name);
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(homeShowSupplier ? "Select supplier to add item" : "Select customer to add item")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                if (which == 0) {
                    partyDialog(homeShowSupplier, null);
                } else {
                    Party chosen = list.get(which - 1);
                    addItemDialog(chosen);
                }
            }).show();
    }

    private void dashboardPayAction() {
        ArrayList<Party> list = homeShowSupplier ? store.suppliers : store.customers;
        if (list.isEmpty()) {
            toast(homeShowSupplier ? "Add a supplier first" : "Add a customer first");
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        for (Party p : list) {
            labels.add(p.name + " (Pending: Rs " + money(p.due()) + ")");
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle(homeShowSupplier ? "Record payment to supplier" : "Record payment from customer")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                Party chosen = list.get(which);
                paymentDialog(chosen);
            }).show();
    }

    private void billsFeatureAction() {
        if (store.suppliers.isEmpty()) {
            toast("Add a supplier first to attach bills");
            return;
        }
        ArrayList<String> labels = new ArrayList<>();
        for (Party p : store.suppliers) {
            labels.add(p.name);
        }
        new MaterialAlertDialogBuilder(this)
            .setTitle("Attach bill for supplier")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                Party selected = store.suppliers.get(which);
                billDialog(selected);
            }).show();
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
        String[] options = {"Manage products", "Export CSV", "Clear all data", "About"};
        new MaterialAlertDialogBuilder(this).setTitle("Settings").setItems(options, (d, which) -> {
            if (which == 0) showHome("products");
            if (which == 1) showHome("export");
            if (which == 2) new MaterialAlertDialogBuilder(this).setTitle("Clear all data?").setMessage("This will delete all customers, suppliers and entries.").setPositiveButton("Clear", (dd, ww) -> { store.customers.clear(); store.suppliers.clear(); store.save(); showHome("home"); }).setNegativeButton("Cancel", null).show();
            if (which == 3) new MaterialAlertDialogBuilder(this).setTitle("Transpent").setMessage("v1.0 – Local ledger tracking app").setPositiveButton("OK", null).show();
        }).show();
    }

    private void statistics(LinearLayout c) {
        TextView title = text("Statistics", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(8)); c.addView(title);

        // Compute real totals separately — customers and suppliers are independent
        long customerTotal = 0, customerPaid = 0, supplierTotal = 0, supplierPaid = 0;
        for (Party p : store.customers) { for (Entry e : p.items) { customerTotal += e.total(); customerPaid += e.paid; } customerPaid += p.miscPaid; }
        for (Party p : store.suppliers) { for (Entry e : p.items) { supplierTotal += e.total(); supplierPaid += e.paid; } supplierPaid += p.miscPaid; }
        long customerDue = Math.max(0, customerTotal - customerPaid);
        long supplierDue = Math.max(0, supplierTotal - supplierPaid);

        // ── Customers section ──
        LinearLayout custSection = column(); custSection.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView custHeader = text("Customers", 13, C_ON_SURF_VAR); custHeader.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        custHeader.setPadding(0, 0, 0, dp(8)); custSection.addView(custHeader);
        LinearLayout custCard = column(); custCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        custCard.setBackground(gradientBox(new int[]{Color.parseColor("#145C41"), Color.parseColor("#2BA876")}, 20));
        custCard.setElevation(dp(4));
        custCard.addView(text("Customers Pending (They Owe You)", 12, Color.parseColor("#A8D5C2")));
        custCard.addView(space(4));
        TextView custBal = text("Rs " + money(customerDue), 28, Color.WHITE); custBal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); custCard.addView(custBal);
        custCard.addView(space(4));
        custCard.addView(text(store.customers.size() + " customer" + (store.customers.size() == 1 ? "" : "s") + "  ·  Rs " + money(customerTotal) + " total", 12, Color.parseColor("#A8D5C2")));
        custSection.addView(custCard);
        c.addView(custSection);

        c.addView(space(4));

        // ── Suppliers section ──
        LinearLayout suppSection = column(); suppSection.setPadding(dp(20), dp(8), dp(20), dp(8));
        TextView suppHeader = text("Suppliers", 13, C_ON_SURF_VAR); suppHeader.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        suppHeader.setPadding(0, 0, 0, dp(8)); suppSection.addView(suppHeader);
        LinearLayout suppCard = column(); suppCard.setPadding(dp(20), dp(20), dp(20), dp(20));
        suppCard.setBackground(gradientBox(new int[]{Color.parseColor("#0D2D6B"), Color.parseColor("#1976D2")}, 20));
        suppCard.setElevation(dp(4));
        suppCard.addView(text("Suppliers Pending (You Owe Them)", 12, Color.parseColor("#B3D4FF")));
        suppCard.addView(space(4));
        TextView suppBal = text("Rs " + money(supplierDue), 28, Color.WHITE); suppBal.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); suppCard.addView(suppBal);
        suppCard.addView(space(4));
        suppCard.addView(text(store.suppliers.size() + " supplier" + (store.suppliers.size() == 1 ? "" : "s") + "  ·  Rs " + money(supplierTotal) + " total", 12, Color.parseColor("#B3D4FF")));
        suppSection.addView(suppCard);
        c.addView(suppSection);

        // Empty state if no data
        if (store.customers.isEmpty() && store.suppliers.isEmpty()) {
            TextView empty = text("Add customers or suppliers to see stats here.", 14, C_ON_SURF_VAR);
            empty.setGravity(android.view.Gravity.CENTER); empty.setPadding(dp(40), dp(40), dp(40), dp(40));
            c.addView(empty);
        }
    }

    // ── Ledger screen (customers + suppliers combined with pill toggle) ──────
    private void ledger(LinearLayout c) {
        // Header
        TextView title = text("Ledger", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(12)); c.addView(title);

        // Pill toggle row
        LinearLayout pills = row(); pills.setPadding(dp(20), 0, dp(20), dp(16));
        pills.setBackground(modernBox(C_SURF_VAR, 32));
        LinearLayout pillWrap = row(); pillWrap.setBackground(modernBox(C_SURF_VAR, 32));
        pillWrap.setPadding(dp(4), dp(4), dp(4), dp(4));

        MaterialButton btnCust = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCust.setText("Customers"); btnCust.setAllCaps(false); btnCust.setCornerRadius(dp(28));
        MaterialButton btnSupp = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnSupp.setText("Suppliers"); btnSupp.setAllCaps(false); btnSupp.setCornerRadius(dp(28));

        Runnable applyPillState = () -> {
            if (!ledgerShowSupplier) {
                btnCust.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY)); btnCust.setTextColor(Color.WHITE);
                btnSupp.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT)); btnSupp.setTextColor(C_ON_SURF_VAR);
            } else {
                btnSupp.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY)); btnSupp.setTextColor(Color.WHITE);
                btnCust.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT)); btnCust.setTextColor(C_ON_SURF_VAR);
            }
        };
        applyPillState.run();

        btnCust.setOnClickListener(v -> { ledgerShowSupplier = false; showHome("ledger"); });
        btnSupp.setOnClickListener(v -> { ledgerShowSupplier = true;  showHome("ledger"); });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(44), 1);
        pillWrap.addView(btnCust, pp); pillWrap.addView(btnSupp, pp);
        LinearLayout.LayoutParams pwp = new LinearLayout.LayoutParams(-1, -2); pwp.setMargins(dp(20), 0, dp(20), dp(16));
        c.addView(pillWrap, pwp);

        ArrayList<Party> list = ledgerShowSupplier ? store.suppliers : store.customers;
        summary(c, list);
        int i = 0; for (Party p : list) c.addView(partyCard(p, ledgerShowSupplier, i++));
        if (list.isEmpty()) {
            String who = ledgerShowSupplier ? "suppliers" : "customers";
            TextView empty = text("No " + who + " yet.\nTap + to add one.", 15, C_ON_SURF_VAR);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(40), dp(60), dp(40), dp(40));
            c.addView(empty);
        }
    }

    // ── Search screen ────────────────────────────────────────────────────────
    private void search(LinearLayout c) {
        c.setPadding(0, dp(48), 0, 0);
        TextView title = text("Search", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), 0, dp(24), dp(12)); c.addView(title);

        // Search bar
        TextInputEditText searchBar = input("Search customers, suppliers, products…");
        LinearLayout.LayoutParams sbp = new LinearLayout.LayoutParams(-1, -2); sbp.setMargins(dp(16), 0, dp(16), dp(8));
        c.addView((View) searchBar.getTag(), sbp);

        // Results container updated as user types
        LinearLayout results = column();
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, -2);
        c.addView(results, rp);

        // Build frequency map: partyId → entry count
        java.util.Map<String, Integer> freq = new java.util.HashMap<>();
        for (Party p : store.customers)  freq.put(p.id, p.items.size());
        for (Party p : store.suppliers)  freq.put(p.id, p.items.size());

        // Sort parties by frequency descending
        ArrayList<Party> allParties = new ArrayList<>();
        allParties.addAll(store.customers);
        allParties.addAll(store.suppliers);
        allParties.sort((a, b) -> Integer.compare(freq.getOrDefault(b.id, 0), freq.getOrDefault(a.id, 0)));

        Runnable renderResults = new Runnable() {
            @Override public void run() {
                results.removeAllViews();
                String q = val(searchBar).toLowerCase(Locale.ROOT);

                if (q.isEmpty()) {
                    // Suggestions header
                    TextView hdr = text("Frequent contacts", 13, C_ON_SURF_VAR);
                    hdr.setPadding(dp(24), dp(8), dp(24), dp(4)); results.addView(hdr);
                    int shown = 0;
                    for (Party p : allParties) {
                        if (shown++ >= 6) break;
                        boolean isSup = store.suppliers.contains(p);
                        results.addView(searchResultRow(p.name, isSup ? "Supplier" : "Customer",
                            "Rs " + money(p.due()), v -> { ledgerShowSupplier = isSup; showHome("ledger"); }));
                    }
                } else {
                    boolean found = false;
                    // Match customers
                    for (Party p : store.customers) {
                        if (p.name.toLowerCase(Locale.ROOT).contains(q)) {
                            found = true;
                            results.addView(searchResultRow(p.name, "Customer", "Rs " + money(p.due()), v -> { ledgerShowSupplier=false; showHome("ledger"); }));
                        }
                    }
                    // Match suppliers
                    for (Party p : store.suppliers) {
                        if (p.name.toLowerCase(Locale.ROOT).contains(q)) {
                            found = true;
                            results.addView(searchResultRow(p.name, "Supplier", "Rs " + money(p.due()), v -> { ledgerShowSupplier=true; showHome("ledger"); }));
                        }
                    }
                    // Match products
                    for (Product pr : store.products) {
                        if (pr.name.toLowerCase(Locale.ROOT).contains(q)) {
                            found = true;
                            results.addView(searchResultRow(pr.name, "Product", "Rs " + money(pr.price), v -> showHome("products")));
                        }
                    }
                    if (!found) {
                        TextView em = text("No results for \"" + val(searchBar) + "\"", 14, C_ON_SURF_VAR);
                        em.setPadding(dp(24), dp(24), dp(24), dp(24)); results.addView(em);
                    }
                }
            }
        };
        renderResults.run();
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int b,int c){ renderResults.run(); }
            public void afterTextChanged(android.text.Editable s){}
        });
    }

    private View searchResultRow(String name, String type, String amount, View.OnClickListener click) {
        MaterialCardView card = new MaterialCardView(this);
        card.setRadius(dp(14)); card.setCardElevation(dp(1));
        card.setCardBackgroundColor(Color.WHITE); card.setStrokeColor(C_SURF_VAR); card.setStrokeWidth(dp(1));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(dp(16), dp(4), dp(16), dp(4));
        LinearLayout row = row(); row.setPadding(dp(16), dp(14), dp(16), dp(14)); row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = column();
        TextView nameV = text(name, 15, C_ON_SURFACE); nameV.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); left.addView(nameV);
        int typeColor = "Customer".equals(type) ? C_PRIMARY : "Supplier".equals(type) ? Color.parseColor("#1565C0") : Color.parseColor("#E65100");
        left.addView(text(type, 12, typeColor));
        row.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(text(amount, 14, C_ON_SURF_VAR));
        card.addView(row); card.setLayoutParams(cp); card.setOnClickListener(click);
        return card;
    }

    // ── Products screen ──────────────────────────────────────────────────────
    private void products(LinearLayout c) {
        LinearLayout hRow = row(); hRow.setGravity(Gravity.CENTER_VERTICAL);
        hRow.setPadding(dp(24), dp(48), dp(24), dp(8));
        TextView title = text("Products", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        hRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton addBtn = modernBtn("+ Add", C_PRIMARY, Color.WHITE);
        addBtn.setCornerRadius(dp(20));
        addBtn.setOnClickListener(v -> productDialog(null));
        hRow.addView(addBtn, new LinearLayout.LayoutParams(-2, dp(40)));
        c.addView(hRow);

        if (store.products.isEmpty()) {
            TextView empty = text("No products yet. Tap + Add to create one.", 14, C_ON_SURF_VAR);
            empty.setPadding(dp(24), dp(24), dp(24), dp(24)); c.addView(empty);
        }
        for (Product p : store.products) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(16)); card.setCardElevation(dp(2));
            card.setCardBackgroundColor(Color.WHITE); card.setStrokeColor(C_SURF_VAR); card.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(dp(16), dp(6), dp(16), dp(6));
            LinearLayout inner = row(); inner.setPadding(dp(16), dp(14), dp(16), dp(14)); inner.setGravity(Gravity.CENTER_VERTICAL);
            // Icon circle
            TextView av = text(p.name.substring(0,1).toUpperCase(), 14, Color.WHITE);
            av.setGravity(Gravity.CENTER); av.setBackground(modernBox(C_PRIMARY, 20));
            inner.addView(av, new LinearLayout.LayoutParams(dp(36), dp(36))); inner.addView(spaceWidth(12));
            LinearLayout names = column();
            TextView nv = text(p.name, 15, C_ON_SURFACE); nv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); names.addView(nv);
            names.addView(text("Rs " + money(p.price) + " per unit", 13, C_ON_SURF_VAR));
            inner.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
            MaterialButton edit = modernBtn("Edit", C_SURF_VAR, C_PRIMARY);
            edit.setCornerRadius(dp(20));
            edit.setOnClickListener(v -> productDialog(p)); inner.addView(edit, new LinearLayout.LayoutParams(dp(72), dp(36)));
            MaterialButton del = modernBtn("", Color.parseColor("#FFEBEE"), C_ERROR);
            del.setIcon(androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_delete));
            del.setIconTint(ColorStateList.valueOf(C_ERROR));
            del.setIconGravity(com.google.android.material.button.MaterialButton.ICON_GRAVITY_TEXT_START);
            del.setIconPadding(0);
            del.setCornerRadius(dp(20));
            del.setOnClickListener(v -> new MaterialAlertDialogBuilder(this).setTitle("Delete " + p.name + "?").setPositiveButton("Delete", (d,w) -> { store.products.remove(p); store.save(); showHome("products"); }).setNegativeButton("Cancel", null).show());
            inner.addView(spaceWidth(8)); inner.addView(del, new LinearLayout.LayoutParams(dp(48), dp(36)));
            card.addView(inner); card.setLayoutParams(cp); c.addView(card);
        }
    }

    // ── History screen ───────────────────────────────────────────────────────
    private void history(LinearLayout c) {
        TextView title = text("History", 24, C_ON_SURFACE); title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        title.setPadding(dp(24), dp(48), dp(24), dp(8)); c.addView(title);

        // Switcher inside History
        LinearLayout pillWrap = row(); pillWrap.setBackground(modernBox(C_SURF_VAR, 32));
        pillWrap.setPadding(dp(4), dp(4), dp(4), dp(4));

        MaterialButton btnCust = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnCust.setText("Customers"); btnCust.setAllCaps(false); btnCust.setCornerRadius(dp(28));
        MaterialButton btnSupp = new MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
        btnSupp.setText("Suppliers"); btnSupp.setAllCaps(false); btnSupp.setCornerRadius(dp(28));

        Runnable applyPillState = () -> {
            if (!historyShowSupplier) {
                btnCust.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY)); btnCust.setTextColor(Color.WHITE);
                btnSupp.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT)); btnSupp.setTextColor(C_ON_SURF_VAR);
            } else {
                btnSupp.setBackgroundTintList(ColorStateList.valueOf(C_PRIMARY)); btnSupp.setTextColor(Color.WHITE);
                btnCust.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT)); btnCust.setTextColor(C_ON_SURF_VAR);
            }
        };
        applyPillState.run();

        btnCust.setOnClickListener(v -> { historyShowSupplier = false; showHome("history"); });
        btnSupp.setOnClickListener(v -> { historyShowSupplier = true;  showHome("history"); });
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(0, dp(44), 1);
        pillWrap.addView(btnCust, pp); pillWrap.addView(btnSupp, pp);
        LinearLayout.LayoutParams pwp = new LinearLayout.LayoutParams(-1, -2); pwp.setMargins(dp(20), 0, dp(20), dp(16));
        c.addView(pillWrap, pwp);

        // Filter and display history based on active tab
        ArrayList<Object[]> rows = new ArrayList<>(); // {partyName, isSupplier, entryName, amount, date}
        if (!historyShowSupplier) {
            for (Party p : store.customers) for (Entry e : p.items)
                rows.add(new Object[]{p.name, false, e.name, e.total(), e.date});
        } else {
            for (Party p : store.suppliers) for (Entry e : p.items)
                rows.add(new Object[]{p.name, true,  e.name, e.total(), e.date});
        }

        // Sort newest first by date string (lexicographic — works for "dd MMM yyyy, HH:mm")
        rows.sort((a, b) -> String.valueOf(b[4]).compareTo(String.valueOf(a[4])));

        if (rows.isEmpty()) {
            String who = historyShowSupplier ? "suppliers" : "customers";
            TextView empty = text("No entries yet. Add " + who + " and items to see history here.", 14, C_ON_SURF_VAR);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(40), dp(60), dp(40), dp(40)); c.addView(empty);
            return;
        }

        for (Object[] row : rows) {
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(14)); card.setCardElevation(dp(1));
            card.setCardBackgroundColor(Color.WHITE); card.setStrokeColor(C_SURF_VAR); card.setStrokeWidth(dp(1));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, -2); cp.setMargins(dp(16), dp(4), dp(16), dp(4));
            LinearLayout inner = row(); inner.setPadding(dp(14), dp(12), dp(14), dp(12)); inner.setGravity(Gravity.CENTER_VERTICAL);
            boolean isSup = (Boolean) row[1];
            int dotColor = isSup ? Color.parseColor("#1565C0") : C_PRIMARY;
            View dot = new View(this); dot.setBackground(modernBox(dotColor, 8));
            inner.addView(dot, new LinearLayout.LayoutParams(dp(8), dp(8))); inner.addView(spaceWidth(12));
            LinearLayout left = column();
            TextView nameV = text(String.valueOf(row[0]), 14, C_ON_SURFACE); nameV.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); left.addView(nameV);
            left.addView(text(String.valueOf(row[2]), 13, C_ON_SURF_VAR));
            if (!String.valueOf(row[4]).isEmpty()) left.addView(text(String.valueOf(row[4]), 11, C_OUTLINE));
            inner.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
            TextView amt = text("Rs " + money((Long) row[3]), 15, C_ON_SURFACE); amt.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            inner.addView(amt);
            card.addView(inner); card.setLayoutParams(cp); c.addView(card);
        }
    }

    // ── New Entry dialog (quick entry from home Add button) ──────────────────
    private void newEntryDialog() {
        if (store.customers.isEmpty() && store.suppliers.isEmpty()) {
            toast("Add a customer or supplier first"); return;
        }
        // Build list: customers then suppliers
        ArrayList<String> labels = new ArrayList<>();
        ArrayList<Party> parties = new ArrayList<>();
        ArrayList<Boolean> isSupList = new ArrayList<>();
        for (Party p : store.customers) { labels.add("👤 " + p.name); parties.add(p); isSupList.add(false); }
        for (Party p : store.suppliers) { labels.add("🚚 " + p.name); parties.add(p); isSupList.add(true); }

        // Step 1: pick party
        new MaterialAlertDialogBuilder(this).setTitle("Select customer / supplier")
            .setItems(labels.toArray(new String[0]), (d, which) -> {
                Party chosen = parties.get(which);
                // Step 2: add item to that party
                addItemDialog(chosen);
            }).show();
    }

    private void customers(LinearLayout c) { ledgerShowSupplier = false; ledger(c); }
    private void suppliers(LinearLayout c) { ledgerShowSupplier = true;  ledger(c); }

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
        long totalDue()    { long x = 0; for (Party p : customers) x += p.due(); for (Party p : suppliers) x += p.due(); return x; }
        long customerDue() { long x = 0; for (Party p : customers) x += p.due(); return x; }
        long supplierDue() { long x = 0; for (Party p : suppliers) x += p.due(); return x; }
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




