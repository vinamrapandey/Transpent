package com.transpent.app;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;


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

public class MainActivity extends Activity {
    private static final int REQ_ACCOUNT = 10;
    private static final int REQ_PICK_BILL = 11;
    private static final int REQ_CAMERA_BILL = 12;
    private static final int REQ_EXPORT = 13;
    private static final int GREEN = Color.rgb(29, 111, 66);
    private final LedgerStore store = new LedgerStore();
    private LinearLayout root;
    private String screen = "customers";
    private String selectedAccount;
    private Party selectedSupplierForBill;
    private Uri pendingCameraUri;
    private boolean exportSplit;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        store.load();
        selectedAccount = getPreferences(0).getString("googleAccount", null);
        if (selectedAccount == null) showLogin(); else showHome("customers");
    }

    private void showLogin() {
        root = base();
        Space top = new Space(this); root.addView(top, new LinearLayout.LayoutParams(1, dp(72)));
        TextView title = title("Transpent"); title.setTextSize(34); root.addView(title);
        TextView sub = text("Local finance monitoring for your store ledger", 15, Color.DKGRAY); sub.setGravity(Gravity.CENTER); root.addView(sub);
        root.addView(space(30));
        Button google = primary("Continue with Google");
        google.setOnClickListener(v -> chooseGoogleAccount());
        root.addView(google, matchWrap());
        TextView note = text("Data stays on this phone and can be backed up to your Google Drive after login.", 13, Color.GRAY);
        note.setGravity(Gravity.CENTER); note.setPadding(dp(22), dp(18), dp(22), 0); root.addView(note);
        setContentView(root);
    }

    private void chooseGoogleAccount() {
        Intent intent = AccountManager.newChooseAccountIntent(null, null, new String[]{"com.google"}, null, null, null, null);
        startActivityForResult(intent, REQ_ACCOUNT);
    }

    private void showHome(String tab) {
        screen = tab;
        root = base();
        root.addView(header());
        root.addView(nav());
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = column(); content.setPadding(0, dp(10), 0, dp(110));
        scroll.addView(content);
        if ("customers".equals(tab)) customers(content);
        if ("suppliers".equals(tab)) suppliers(content);
        if ("products".equals(tab)) products(content);
        if ("export".equals(tab)) export(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
    }

    private View header() {
        LinearLayout h = row(); h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(0, dp(14), 0, dp(10));
        LinearLayout copy = column();
        TextView t = title("Transpent"); copy.addView(t);
        copy.addView(text("Signed in: " + selectedAccount, 12, Color.GRAY));
        h.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        TextView due = pill("Due Rs " + money(store.totalDue())); h.addView(due);
        return h;
    }

    private View nav() {
        LinearLayout n = row(); n.setGravity(Gravity.CENTER); n.setPadding(0, 0, 0, dp(8));
        addTab(n, "Customers", "customers"); addTab(n, "Suppliers", "suppliers"); addTab(n, "Products", "products"); addTab(n, "Export", "export");
        return n;
    }

    private void addTab(LinearLayout n, String label, String tab) {
        TextView v = pill(label); v.setTextColor(screen.equals(tab) ? Color.WHITE : GREEN); v.setBackgroundResource(screen.equals(tab) ? R.drawable.primary_button : R.drawable.soft_button);
        v.setOnClickListener(x -> showHome(tab)); n.addView(v, new LinearLayout.LayoutParams(0, dp(42), 1));
    }

    private void customers(LinearLayout c) {
        Button add = primary("Add customer"); add.setOnClickListener(v -> partyDialog(false, null)); c.addView(add, matchWrap());
        summary(c, store.customers);
        for (Party p : store.customers) c.addView(partyCard(p, false));
    }

    private void suppliers(LinearLayout c) {
        Button add = primary("Add supplier business"); add.setOnClickListener(v -> partyDialog(true, null)); c.addView(add, matchWrap());
        summary(c, store.suppliers);
        for (Party p : store.suppliers) c.addView(partyCard(p, true));
    }

    private void summary(LinearLayout c, ArrayList<Party> parties) {
        int active = 0; long due = 0; for (Party p : parties) { if (p.due() > 0) active++; due += p.due(); }
        c.addView(text(active + " active ledgers | Rs " + money(due) + " pending", 14, Color.DKGRAY));
    }

    private View partyCard(Party p, boolean supplier) {
        LinearLayout card = card();
        LinearLayout top = row(); top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = column(); names.addView(title(p.name)); names.addView(text(p.phone.isEmpty() ? (supplier ? "Supplier" : "Customer") : p.phone, 12, Color.GRAY));
        top.addView(names, new LinearLayout.LayoutParams(0, -2, 1)); top.addView(pill("Rs " + money(p.due()))); card.addView(top);
        if (!p.items.isEmpty()) {
            card.addView(text("Last items", 12, Color.GRAY));
            for (int i = Math.max(0, p.items.size() - 4); i < p.items.size(); i++) {
                Entry e = p.items.get(i); card.addView(text(e.name + " x" + e.qty + " | Rs " + money(e.total()) + " | paid Rs " + money(e.paid), 13, Color.DKGRAY));
            }
        }
        LinearLayout actions = row(); actions.setPadding(0, dp(10), 0, 0);
        Button add = soft("Add items"); add.setOnClickListener(v -> addItemDialog(p)); actions.addView(add, new LinearLayout.LayoutParams(0, dp(44), 1));
        Button pay = soft("Record paid"); pay.setOnClickListener(v -> paymentDialog(p)); actions.addView(pay, new LinearLayout.LayoutParams(0, dp(44), 1));
        if (supplier) { Button bill = soft("Bill photo"); bill.setOnClickListener(v -> billDialog(p)); actions.addView(bill, new LinearLayout.LayoutParams(0, dp(44), 1)); }
        card.addView(actions);
        card.setOnClickListener(v -> addItemDialog(p));
        return card;
    }

    private void products(LinearLayout c) {
        Button add = primary("Add product price"); add.setOnClickListener(v -> productDialog(null)); c.addView(add, matchWrap());
        for (Product p : store.products) {
            LinearLayout card = card(); LinearLayout r = row(); r.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout copy = column(); copy.addView(title(p.name)); copy.addView(text("Rs " + money(p.price), 13, Color.GRAY)); r.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
            Button edit = soft("Edit"); edit.setOnClickListener(v -> productDialog(p)); r.addView(edit, new LinearLayout.LayoutParams(dp(92), dp(42))); card.addView(r); c.addView(card);
        }
    }

    private void export(LinearLayout c) {
        c.addView(text("Backup and reports", 20, Color.BLACK));
        Button all = primary("Export one Excel-compatible CSV"); all.setOnClickListener(v -> exportCsv(false)); c.addView(all, matchWrap());
        Button split = soft("Export separate CSV files"); split.setOnClickListener(v -> exportCsv(true)); c.addView(split, matchWrap());
        Button backup = soft("Backup JSON to Google Drive"); backup.setOnClickListener(v -> driveBackup()); c.addView(backup, matchWrap());
        Button restore = soft("Restore latest Drive backup"); restore.setOnClickListener(v -> driveRestore()); c.addView(restore, matchWrap());
        c.addView(text("Exports are CSV files that open directly in Excel. Drive backup stores the full local ledger JSON in your own Drive app data area.", 13, Color.GRAY));
    }

    private void partyDialog(boolean supplier, Party edit) {
        LinearLayout f = form(); EditText name = input("Name"); EditText phone = input("Phone / note"); f.addView(name); f.addView(phone);
        new AlertDialog.Builder(this).setTitle(supplier ? "Supplier business" : "Customer").setView(f).setPositiveButton("Save", (d,w) -> {
            Party p = new Party(); p.id = id(); p.name = val(name); p.phone = val(phone); if (!p.name.isEmpty()) { (supplier ? store.suppliers : store.customers).add(p); saveRefresh(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void productDialog(Product edit) {
        LinearLayout f = form(); EditText name = input("Product name"); EditText price = input("Price"); price.setInputType(InputType.TYPE_CLASS_NUMBER); if (edit != null) { name.setText(edit.name); price.setText(String.valueOf(edit.price)); } f.addView(name); f.addView(price);
        new AlertDialog.Builder(this).setTitle("Product price").setView(f).setPositiveButton("Save", (d,w) -> {
            Product p = edit == null ? new Product() : edit; if (edit == null) { p.id = id(); store.products.add(p); } p.name = val(name); p.price = parse(price); saveRefresh();
        }).setNegativeButton("Cancel", null).show();
    }

    private void addItemDialog(Party party) {
        LinearLayout f = form(); Spinner spinner = new Spinner(this); ArrayList<String> labels = new ArrayList<>(); labels.add("Manual item"); for (Product p : store.products) labels.add(p.name + " - Rs " + money(p.price)); spinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        EditText name = input("Item name"); EditText price = input("Price"); EditText qty = input("Qty"); qty.setText("1"); price.setInputType(InputType.TYPE_CLASS_NUMBER); qty.setInputType(InputType.TYPE_CLASS_NUMBER); f.addView(spinner); f.addView(name); f.addView(price); f.addView(qty);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() { public void onItemSelected(android.widget.AdapterView<?> a, View v, int pos, long id) { if (pos > 0) { Product p = store.products.get(pos - 1); name.setText(p.name); price.setText(String.valueOf(p.price)); }} public void onNothingSelected(android.widget.AdapterView<?> a) {} });
        new AlertDialog.Builder(this).setTitle("Add bought item").setView(f).setPositiveButton("Add", (d,w) -> {
            Entry e = new Entry(); e.id = id(); e.name = val(name); e.price = parse(price); e.qty = Math.max(1, parse(qty)); e.date = now(); if (!e.name.isEmpty()) { party.items.add(e); saveRefresh(); }
        }).setNegativeButton("Cancel", null).show();
    }

    private void paymentDialog(Party party) {
        String[] options = {"Pay per item", "Misc part payment", "Paid in full"};
        new AlertDialog.Builder(this).setTitle("Record payment").setItems(options, (d, which) -> { if (which == 0) itemPaymentDialog(party); if (which == 1) miscPaymentDialog(party); if (which == 2) { party.miscPaid += party.due(); saveRefresh(); } }).show();
    }

    private void itemPaymentDialog(Party party) {
        LinearLayout f = form(); ArrayList<EditText> fields = new ArrayList<>();
        for (Entry e : party.items) if (e.due() > 0) { TextView label = text(e.name + " pending Rs " + money(e.due()), 13, Color.DKGRAY); EditText paid = input("Paid for this item"); paid.setInputType(InputType.TYPE_CLASS_NUMBER); f.addView(label); f.addView(paid); fields.add(paid); }
        new AlertDialog.Builder(this).setTitle("Item-wise paid amount").setView(f).setPositiveButton("Save", (d,w) -> { int x = 0; for (Entry e : party.items) if (e.due() > 0) e.paid += Math.min(e.due(), parse(fields.get(x++))); saveRefresh(); }).setNegativeButton("Cancel", null).show();
    }

    private void miscPaymentDialog(Party party) { EditText paid = input("Amount paid"); paid.setInputType(InputType.TYPE_CLASS_NUMBER); new AlertDialog.Builder(this).setTitle("Misc payment").setView(paid).setPositiveButton("Save", (d,w) -> { party.miscPaid += Math.min(party.due(), parse(paid)); saveRefresh(); }).setNegativeButton("Cancel", null).show(); }

    private void billDialog(Party party) {
        selectedSupplierForBill = party; String[] opts = {"Click bill photo", "Upload bill image"};
        new AlertDialog.Builder(this).setTitle("Attach bill reference").setItems(opts, (d, which) -> { if (which == 0) openCamera(); else pickBill(); }).show();
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

    private LinearLayout base() { LinearLayout l = column(); l.setPadding(dp(18), dp(18), dp(18), 0); l.setBackgroundColor(Color.rgb(247,248,250)); return l; }
    private LinearLayout column() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); return l; }
    private LinearLayout card() { LinearLayout l = column(); l.setBackgroundResource(R.drawable.card); l.setPadding(dp(14), dp(12), dp(14), dp(12)); l.setClickable(true); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.setMargins(0, dp(8), 0, dp(8)); l.setLayoutParams(p); l.setMinimumHeight(dp(68)); return l; }
    private LinearLayout form() { LinearLayout l = column(); l.setPadding(dp(6), dp(4), dp(6), 0); return l; }
    private TextView title(String s) { TextView v = text(s, 19, Color.rgb(18, 24, 38)); v.setSingleLine(false); return v; }
    private TextView text(String s, int sp, int color) { TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color); v.setIncludeFontPadding(true); v.setPadding(0, dp(3), 0, dp(3)); return v; }
    private TextView pill(String s) { TextView v = text(s, 13, GREEN); v.setGravity(Gravity.CENTER); v.setPadding(dp(10), 0, dp(10), 0); v.setBackgroundResource(R.drawable.soft_button); return v; }
    private Button primary(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setBackgroundResource(R.drawable.primary_button); return b; }
    private Button soft(String s) { Button b = new Button(this); b.setText(s); b.setTextColor(GREEN); b.setAllCaps(false); b.setBackgroundResource(R.drawable.soft_button); return b; }
    private EditText input(String hint) { EditText e = new EditText(this); e.setHint(hint); e.setSingleLine(true); return e; }
    private Space space(int h) { Space s = new Space(this); s.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return s; }
    private LinearLayout.LayoutParams matchWrap() { LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(48)); p.setMargins(0, dp(8), 0, dp(8)); return p; }
    private int dp(int x) { return (int)(x * getResources().getDisplayMetrics().density + .5f); }
    private String val(EditText e) { return e.getText().toString().trim(); }
    private long parse(EditText e) { try { return Long.parseLong(val(e)); } catch (Exception ex) { return 0; } }
    private String id() { return UUID.randomUUID().toString(); }
    private String now() { return new SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US).format(new Date()); }
    private String money(long x) { return String.valueOf(x); }
    private void saveRefresh() { store.save(); showHome(screen); }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
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




