package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.accessibility.AccessibilityNodeInfo;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BadPasscodeAttempt;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.nio.channels.FileChannel;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BadPasscodeCell extends FrameLayout {

    private final TextView typeView;
    private final TextView fakePasscodeView;
    private final TextView dateView;
    private final List<ImageView> photos = new ArrayList<>();
    private final LinearLayout layout;

    public BadPasscodeCell(Context context) {
        super(context);

        layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);

        typeView = createTextView(context);
        typeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        layout.addView(typeView, lp);

        fakePasscodeView = createTextView(context);
        fakePasscodeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        layout.addView(fakePasscodeView, lp);

        dateView = createTextView(context);
        dateView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteValueText));
        layout.addView(dateView, lp);

        layout.addView(createPhotoLayout(context));

        addView(layout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 2, 21, 2));
    }

    private static TextView createTextView(Context context) {
        TextView textView = new TextView(context);
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        return textView;
    }

    private LinearLayout createPhotoLayout(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setHorizontalGravity(Gravity.CENTER_HORIZONTAL);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, AndroidUtilities.dp(200));
        lp.leftMargin = AndroidUtilities.dp(5);
        lp.rightMargin = AndroidUtilities.dp(5);

        for (int i = 0; i < 2; i++) {
            ImageView photo = createImageView(context);
            layout.addView(photo, lp);
            photos.add(photo);
        }
        return layout;
    }

    private static ImageView createImageView(Context context) {
        ImageView image = new ImageView(context);
        image.setAdjustViewBounds(true);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        return image;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(50));
        int availableWidth = getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - AndroidUtilities.dp(34);
        layout.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec( getMeasuredHeight(), MeasureSpec.UNSPECIFIED));
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), layout.getMeasuredHeight() + AndroidUtilities.dp(10));
    }

    public void setBadPasscodeAttempt(BadPasscodeAttempt badPasscodeAttempt) {
        typeView.setVisibility(VISIBLE);
        typeView.setText(badPasscodeAttempt.getTypeString());
        if (badPasscodeAttempt.isFakePasscode) {
            fakePasscodeView.setVisibility(VISIBLE);
            fakePasscodeView.setText(LocaleController.getString(R.string.FakePasscode));
        } else {
            fakePasscodeView.setVisibility(GONE);
        }
        dateView.setVisibility(VISIBLE);
        dateView.setText(badPasscodeAttempt.date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        for (int i = 0; i < photos.size(); i++) {
            String path = i < badPasscodeAttempt.photoPaths.size()
                    ? badPasscodeAttempt.photoPaths.get(i)
                    : null;
            bindPhoto(photos.get(i), path);
        }
        setWillNotDraw(false);
    }

    private void bindPhoto(ImageView image, String path) {
        if (path == null) {
            image.setVisibility(GONE);
            image.setOnClickListener(null);
            return;
        }
        File file = new File(ApplicationLoader.getFilesDirFixed(), path);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.submit(() -> {
            Bitmap bitmap = lessResolution(file.getAbsolutePath());
            AndroidUtilities.runOnUIThread(() -> image.setImageBitmap(bitmap));
        });
        image.setVisibility(VISIBLE);
        image.setOnClickListener(view -> {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setTitle(LocaleController.getString(R.string.AppName));
            builder.setMessage(LocaleController.getString(R.string.SaveToGallery) + "?");
            builder.setNegativeButton(LocaleController.getString(R.string.Cancel), null);
            builder.setPositiveButton(LocaleController.getString(R.string.OK), (dialogInterface, i) -> {
                saveFile(file);
            });
            builder.create().show();
        });
    }

    public static Bitmap lessResolution(String filePath) {
        int reqHeight = 320;
        int reqWidth = 320;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(filePath, options);
        Matrix matrix = new Matrix();
        matrix.postRotate(getExifOrientation(filePath));
        bitmap = Bitmap.createBitmap(bitmap, 0, 0,bitmap.getWidth(), bitmap.getHeight(), matrix, true);
        return bitmap;
    }

    private static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = Math.min(heightRatio, widthRatio);
        }
        return inSampleSize;
    }

    public static int getExifOrientation(String filepath) {
        int degree = 0;
        ExifInterface exif = null;
        try {
            exif = new ExifInterface(filepath);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        if (exif != null) {
            int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, -1);
            if (orientation != -1) {
                // We only recognise a subset of orientation tag values.
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        degree = 90;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_180:
                        degree = 180;
                        break;
                    case ExifInterface.ORIENTATION_ROTATE_270:
                        degree = 270;
                        break;
                }

            }
        }

        return degree;
    }

    public static void saveFile(File file) {
        if (file == null || !file.exists()) {
            return;
        }

        final File sourceFile = file;
        if (sourceFile.exists()) {
            new Thread(() -> {
                try {
                    if (Build.VERSION.SDK_INT >= 29) {
                        Uri uriToInsert = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
                        File dirDest = new File(Environment.DIRECTORY_PICTURES, "Telegram");
                        ContentValues contentValues = new ContentValues();
                        contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, dirDest + File.separator);
                        contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, sourceFile.getName());
                        String extension = MimeTypeMap.getFileExtensionFromUrl(sourceFile.getAbsolutePath());
                        String mimeType = null;
                        if (extension != null) {
                            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
                        }
                        contentValues.put(MediaStore.Images.Media.MIME_TYPE, mimeType);
                        Uri dstUri = ApplicationLoader.applicationContext.getContentResolver().insert(uriToInsert, contentValues);
                        if (dstUri != null) {
                            FileInputStream fileInputStream = new FileInputStream(sourceFile);
                            OutputStream outputStream = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(dstUri);
                            AndroidUtilities.copyFile(fileInputStream, outputStream);
                            fileInputStream.close();
                            AndroidUtilities.addMediaToGallery(dstUri.getPath());
                        }
                    } else {
                        File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
                        String fileName = AndroidUtilities.generateFileName(0, FileLoader.getFileExtension(sourceFile));
                        File destFile = new File(new File(dir, "Telegram"), fileName);
                        if (!destFile.exists()) {
                            destFile.createNewFile();
                        }
                        boolean result = true;
                        try (FileInputStream inputStream = new FileInputStream(sourceFile);
                             FileChannel source = inputStream.getChannel();
                             FileChannel destination = new FileOutputStream(destFile).getChannel()) {
                            long size = source.size();
                            try {
                                @SuppressLint("DiscouragedPrivateApi") Method getInt = FileDescriptor.class.getDeclaredMethod("getInt$");
                                int fdint = (Integer) getInt.invoke(inputStream.getFD());
                                if (AndroidUtilities.isInternalUri(fdint)) {
                                    return;
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                            for (long a = 0; a < size; a += 4096) {
                                destination.transferFrom(source, a, Math.min(4096, size - a));
                            }
                        } catch (Exception e) {
                            FileLog.e(e);
                            result = false;
                        }
                        if (result) {
                            AndroidUtilities.addMediaToGallery(destFile);
                        }
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }).start();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setEnabled(isEnabled());
    }
}
