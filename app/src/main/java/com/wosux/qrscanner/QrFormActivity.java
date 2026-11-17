package com.wosux.qrscanner;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * The form page. "Scan QR code" opens {@link QrScanActivity}; when it returns, every field is filled
 * from the QR. The person can still correct the editable fields before saving.
 */
public class QrFormActivity extends AppCompatActivity {

    private TextInputLayout tilName, tilBuy, tilMin, tilMax, tilDate;
    private TextInputEditText etName, etSku, etNumber, etBill, etBuy, etMin, etMax, etDate, etCategory, etStore;
    private TextView tvStatus;
    private MaterialButton btnScan;

    private final ActivityResultLauncher<Intent> scanLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    fill(QrScanActivity.itemFrom(result.getData()));
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_form);

        tilName = findViewById(R.id.tilName);
        tilBuy = findViewById(R.id.tilBuy);
        tilMin = findViewById(R.id.tilMin);
        tilMax = findViewById(R.id.tilMax);
        tilDate = findViewById(R.id.tilDate);
        etName = findViewById(R.id.etName);
        etSku = findViewById(R.id.etSku);
        etNumber = findViewById(R.id.etNumber);
        etBill = findViewById(R.id.etBill);
        etBuy = findViewById(R.id.etBuy);
        etMin = findViewById(R.id.etMin);
        etMax = findViewById(R.id.etMax);
        etDate = findViewById(R.id.etDate);
        etCategory = findViewById(R.id.etCategory);
        etStore = findViewById(R.id.etStore);
        tvStatus = findViewById(R.id.tvStatus);
        btnScan = findViewById(R.id.btnScan);

        btnScan.setOnClickListener(v -> scanLauncher.launch(new Intent(this, QrScanActivity.class)));
        findViewById(R.id.btnClear).setOnClickListener(v -> clearForm());
        findViewById(R.id.btnSave).setOnClickListener(v -> save());
        etDate.setOnClickListener(v -> pickDate());
    }

    /** Puts everything the QR carried into the form. */
    private void fill(QrItem it) {
        clearErrors();
        etName.setText(it.name);
        etSku.setText(it.sku);
        etNumber.setText(it.number);
        etBill.setText(it.billId);
        etBuy.setText(it.buyPrice);
        etMin.setText(it.minPrice);
        etMax.setText(it.maxPrice);
        etDate.setText(it.date);
        etCategory.setText(it.categoryId);
        etStore.setText(it.storeId);
        tvStatus.setText(getString(R.string.status_filled, it.id));
        btnScan.setText(R.string.btn_scan_again);
    }

    private void clearForm() {
        clearErrors();
        for (TextInputEditText e : new TextInputEditText[]{etName, etSku, etNumber, etBill, etBuy, etMin,
                etMax, etDate, etCategory, etStore}) {
            e.setText("");
        }
        tvStatus.setText(R.string.status_empty);
        btnScan.setText(R.string.btn_scan);
    }

    private void clearErrors() {
        tilName.setError(null);
        tilBuy.setError(null);
        tilMin.setError(null);
        tilMax.setError(null);
        tilDate.setError(null);
    }

    private void save() {
        clearErrors();
        boolean ok = true;
        if (text(etName).isEmpty()) { tilName.setError(getString(R.string.err_required)); ok = false; }
        if (!isPrice(text(etBuy))) { tilBuy.setError(getString(R.string.err_number)); ok = false; }
        if (!isPrice(text(etMin))) { tilMin.setError(getString(R.string.err_number)); ok = false; }
        if (!isPrice(text(etMax))) { tilMax.setError(getString(R.string.err_number)); ok = false; }
        if (!isDate(text(etDate))) { tilDate.setError(getString(R.string.err_date)); ok = false; }
        if (!ok) return;

        // ------------------------------------------------------------------------------------
        // TODO: send these values to your API / database here. Right now the page only shows
        // what would be saved, so you can check that the QR filled everything correctly.
        // ------------------------------------------------------------------------------------
        String summary =
                getString(R.string.f_name) + ": " + text(etName) + "\n"
                + getString(R.string.f_sku) + ": " + text(etSku) + "\n"
                + getString(R.string.f_number) + ": " + text(etNumber) + "\n"
                + getString(R.string.f_bill) + ": " + text(etBill) + "\n"
                + getString(R.string.f_buy) + ": " + text(etBuy) + "\n"
                + getString(R.string.f_min) + ": " + text(etMin) + "\n"
                + getString(R.string.f_max) + ": " + text(etMax) + "\n"
                + getString(R.string.f_date) + ": " + text(etDate) + "\n"
                + getString(R.string.f_category) + ": " + text(etCategory) + "\n"
                + getString(R.string.f_store) + ": " + text(etStore);
        new AlertDialog.Builder(this)
                .setTitle(R.string.dlg_title)
                .setMessage(summary)
                .setPositiveButton(R.string.dlg_ok, null)
                .show();
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        String cur = text(etDate);
        if (isDate(cur)) {
            c.set(Integer.parseInt(cur.substring(0, 4)), Integer.parseInt(cur.substring(5, 7)) - 1,
                    Integer.parseInt(cur.substring(8, 10)));
        }
        new DatePickerDialog(this,
                (view, y, m, d) -> etDate.setText(String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d)),
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show();
    }

    // ---- small helpers ---------------------------------------------------------------------

    private static String text(TextInputEditText e) {
        return e.getText() == null ? "" : e.getText().toString().trim();
    }

    /** Same rule the QR Maker uses for prices: digits, optionally up to 2 decimals. */
    private static boolean isPrice(String s) {
        return s.matches("\\d{1,10}(\\.\\d{1,2})?");
    }

    private static boolean isDate(String s) {
        if (!s.matches("\\d{4}-\\d{2}-\\d{2}")) return false;
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setLenient(false);
        try {
            f.parse(s);
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
