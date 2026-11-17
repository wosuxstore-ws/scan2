# Wosux QR Scan (Android)

A scanner page and a form page for the codes your QR Maker makes.

1. Open the app, tap **Scan QR code**.
2. Point the camera at a sticker. It reads the code, unlocks the data inside it, and the form fills in:
   product name, SKU, item no., bill no., buying price, min/max selling price, date, category ID, store ID.
3. Check or correct the fields, then **Save**.

Anything that is not one of your QR codes (a shop barcode, someone else's QR, an edited or damaged link)
shows a short message on the camera screen and the camera keeps looking. Nothing needs internet.

## Build the APK with GitHub Actions

1. Put this whole folder in a GitHub repository (the `.github/workflows/build-apk.yml` file must stay where it is).
2. Open the repository **Actions** tab. The build runs on every push to `main`/`master`,
   or press **Run workflow** on "Build APK".
3. When it turns green, open the run and download **wosux-qr-scanner-apk** at the bottom. It holds `app-debug.apk`.

## The secret key

The app needs the same `secret_key_hex` as `qr/config.php` on your server. It is already filled in
`app/build.gradle` (the `qrKey` fallback), so the first build works as it is.

If the repository is **public**, anyone could read the key there. In that case: delete the key from the fallback
line, then in GitHub go to *Settings > Secrets and variables > Actions > New repository secret*, name it
`QR_SECRET_KEY_HEX`, and paste the 64 characters. The workflow already passes it to the build.

Never change the key on the server after you have printed stickers, or old stickers stop opening.

## Put the page into your existing app instead

Copy these into your app (change the `package` line at the top of each Java file):

- `QrCrypto.java`, `QrItem.java`, `QrScanActivity.java` (and `QrFormActivity.java` if you want my form as it is)
- `res/layout/activity_scan.xml` (and `activity_form.xml`), the `bg_*.xml` and `ic_*.xml` files in `res/drawable/`
- the strings from `res/values/strings.xml`
- in `AndroidManifest.xml`: the `CAMERA` permission and the `QrScanActivity` entry
- in `app/build.gradle`: the CameraX, ML Kit and guava lines, `buildFeatures { buildConfig true }`,
  and the `buildConfigField` for `QR_SECRET_KEY_HEX`

Then, from any of your own screens:

```java
ActivityResultLauncher<Intent> scan = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                QrItem it = QrScanActivity.itemFrom(result.getData());
                // it.name, it.buyPrice, it.minPrice, it.maxPrice, it.date, it.categoryId, it.storeId,
                // it.sku, it.number, it.billId, it.id
            }
        });
scan.launch(new Intent(this, QrScanActivity.class));
```

## Where the Save button needs your code

`QrFormActivity.save()` checks the fields and shows what would be saved. Replace the `TODO` there with the call
to your API or database.
