package br.edu.utfpr.inventariomaquinas.service;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.data.remote.BrandDeletionSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.BrandUpdateSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.RemoteDataSource;
import br.edu.utfpr.inventariomaquinas.data.remote.RemoteImporter;
import br.edu.utfpr.inventariomaquinas.data.remote.TypeDeletionSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.TypeUpdateSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.dto.RemoteSnapshot;
import br.edu.utfpr.inventariomaquinas.data.remote.MachineSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.CreationSynchronizer;
import br.edu.utfpr.inventariomaquinas.data.remote.TransferCoordinator;

@SuppressWarnings("deprecation")
public class DownloadDataService extends IntentService {

    public static final String ACTION_SYNC_CATALOGS =
            "br.edu.utfpr.inventariomaquinas.SYNC_CATALOGS";

    private static final String TAG =
            "DownloadDataService";

    private static final String CHANNEL_ID =
            "server_data_download";

    private static final int NOTIFICATION_ID = 1001;

    public DownloadDataService() {
        super("DownloadDataService");

        setIntentRedelivery(false);
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();

        Notification notification =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(
                                android.R.drawable.stat_sys_upload
                        )
                        .setContentTitle(getString(
                                R.string.transfer_notification_title
                        ))
                        .setContentText(getString(
                                R.string.transfer_notification_message
                        ))
                        .setPriority(
                                NotificationCompat.PRIORITY_LOW
                        )
                        .setOngoing(true)
                        .setProgress(0, 0, true)
                        .build();

        startForeground(
                NOTIFICATION_ID,
                notification
        );
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) {
            return;
        }

        String action = intent.getAction();

        boolean syncing =
                ACTION_SYNC_CATALOGS.equals(action);

        try {
            synchronized (TransferCoordinator.LOCK) {
            AppDatabase database =
                    AppDatabase.getInstance(
                            getApplicationContext()
                    );

            CreationSynchronizer creations = new CreationSynchronizer(this, database);
            if (syncing) {
                int created = creations.synchronize();
                int updatedTypes =
                        new TypeUpdateSynchronizer(database)
                                .synchronize();

                int updatedBrands =
                        new BrandUpdateSynchronizer(database)
                                .synchronize();

                MachineSynchronizer.Result machines =
                        new MachineSynchronizer(database)
                                .synchronize();

                int deletedTypes =
                        new TypeDeletionSynchronizer(database)
                                .synchronize();

                int deletedBrands =
                        new BrandDeletionSynchronizer(database)
                                .synchronize();

                int updated = updatedTypes
                        + updatedBrands
                        + machines.updated;

                int deleted = machines.deleted
                        + deletedTypes
                        + deletedBrands;

                showMessage(getString(
                        R.string.sync_all_success,
                        created,
                        updated,
                        deleted
                ));

            } else if (action == null) {
                creations.ensureDownloadAllowed();
                RemoteSnapshot snapshot =
                        new RemoteDataSource()
                                .downloadAll();

                new RemoteImporter(database)
                        .importSnapshot(snapshot);

                showMessage(getString(
                        R.string.download_success
                ));

            } else {
                throw new IllegalArgumentException(
                        "Unknown transfer action: " + action
                );
            }

            }
        } catch (Exception exception) {
            Log.e(
                    TAG,
                    "Data transfer failed",
                    exception
            );

            String detail = exception.getMessage();

            if (detail == null || detail.trim().isEmpty()) {
                detail = exception.getClass().getSimpleName();
            }

            int messageId = syncing
                    ? R.string.sync_catalogs_error
                    : R.string.download_error;

            showMessage(getString(
                    messageId,
                    detail
            ));
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            CHANNEL_ID,
                            getString(
                                    R.string.download_channel_name
                            ),
                            NotificationManager.IMPORTANCE_LOW
                    );

            NotificationManager manager =
                    getSystemService(NotificationManager.class);

            manager.createNotificationChannel(channel);
        }
    }

    private void showMessage(String message) {
        new Handler(Looper.getMainLooper()).post(() ->
                Toast.makeText(
                        getApplicationContext(),
                        message,
                        Toast.LENGTH_LONG
                ).show()
        );
    }

    @Override
    public void onDestroy() {
        stopForeground(true);

        super.onDestroy();
    }
}
