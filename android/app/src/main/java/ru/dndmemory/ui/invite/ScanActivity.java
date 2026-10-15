package ru.dndmemory.ui.invite;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.ResultPoint;
import com.journeyapps.barcodescanner.BarcodeCallback;
import com.journeyapps.barcodescanner.BarcodeResult;

import java.util.List;

import ru.dndmemory.R;
import ru.dndmemory.databinding.ActivityScanBinding;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.PermissionHelper;
import ru.dndmemory.util.QrUtils;

/**
 * Сканирование QR-приглашения (ZXing embedded). Камера нужна только здесь,
 * поэтому разрешение запрашивается на входе в этот экран.
 */
public class ScanActivity extends AppCompatActivity {

    private ActivityScanBinding binding;
    private boolean decoding;

    private final BarcodeCallback callback = new BarcodeCallback() {
        @Override
        public void barcodeResult(BarcodeResult result) {
            if (result == null || result.getText() == null || decoding) {
                return;
            }
            decoding = true;
            binding.scanPreview.pause();
            String token = QrUtils.extractToken(result.getText());
            if (token == null || token.isEmpty()) {
                decoding = false;
                ViewUtils.toast(ScanActivity.this, R.string.error_invite_invalid);
                binding.scanPreview.resume();
                return;
            }
            Intent data = new Intent();
            data.putExtra(Extras.INVITE_TOKEN, token);
            setResult(RESULT_OK, data);
            finish();
        }

        @Override
        public void possibleResultPoints(List<ResultPoint> resultPoints) {
            // точки распознавания не показываем
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityScanBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (PermissionHelper.hasCameraPermission(this)) {
            startDecoding();
        } else {
            PermissionHelper.requestCamera(this);
        }
    }

    private void startDecoding() {
        binding.scanPreview.setStatusText(getString(R.string.invite_scan_hint));
        binding.scanPreview.decodeContinuous(callback);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PermissionHelper.REQUEST_CAMERA) {
            return;
        }
        if (PermissionHelper.granted(requestCode, grantResults)) {
            startDecoding();
            return;
        }
        // Отказали: объясняем и предлагаем настройки.
        ViewUtils.toast(this, R.string.invite_camera_denied);
        ViewUtils.show(binding.scanStatus, true);
        binding.scanStatus.setText(R.string.invite_camera_rationale);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (PermissionHelper.hasCameraPermission(this)) {
            binding.scanPreview.resume();
            decoding = false;
        }
    }

    @Override
    protected void onPause() {
        binding.scanPreview.pause();
        super.onPause();
    }

    /** Ручная отмена — системная кнопка «назад». */
    @Override
    public void onBackPressed() {
        setResult(RESULT_CANCELED);
        super.onBackPressed();
    }
}
