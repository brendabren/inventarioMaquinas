package br.edu.utfpr.inventariomaquinas;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.AppDatabase;
import br.edu.utfpr.inventariomaquinas.databinding.ActivityMainBinding;
import br.edu.utfpr.inventariomaquinas.service.DownloadDataService;
import br.edu.utfpr.inventariomaquinas.ui.marca.MarcaListActivity;
import br.edu.utfpr.inventariomaquinas.ui.tipo.TipoListActivity;
import br.edu.utfpr.inventariomaquinas.ui.maquina.MaquinaListActivity;


public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        int padding = Math.round(
                24 * getResources().getDisplayMetrics().density
        );

        ViewCompat.setOnApplyWindowInsetsListener(
                binding.main,
                (view, windowInsets) -> {
                    Insets bars = windowInsets.getInsets(
                            WindowInsetsCompat.Type.systemBars()
                    );

                    view.setPadding(
                            bars.left + padding,
                            bars.top + padding,
                            bars.right + padding,
                            bars.bottom + padding
                    );

                    return windowInsets;
                }
        );

        ViewCompat.requestApplyInsets(binding.main);

        binding.buttonTipos.setOnClickListener(view -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    TipoListActivity.class
            );

            startActivity(intent);
        });

        binding.buttonMarcas.setOnClickListener(view -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MarcaListActivity.class
            );

            startActivity(intent);
        });

        binding.buttonMaquinas.setOnClickListener(view -> {
            Intent intent = new Intent(
                    MainActivity.this,
                    MaquinaListActivity.class
            );

            startActivity(intent);
        });

        if (savedInstanceState == null) {
            downloadIfDatabaseEmpty();
        }
    }

    private void downloadIfDatabaseEmpty() {
        AppDatabase.databaseExecutor.execute(() -> {
            AppDatabase database = AppDatabase.getInstance(
                    getApplicationContext()
            );

            boolean empty = database.tipoDao().countAll() == 0
                    && database.marcaDao().countAll() == 0
                    && database.maquinaDao().countAll() == 0;

            if (!empty) {
                return;
            }

            runOnUiThread(() -> {
                try {
                    ContextCompat.startForegroundService(
                            MainActivity.this,
                            new Intent(
                                    MainActivity.this,
                                    DownloadDataService.class
                            )
                    );
                } catch (RuntimeException exception) {
                    Toast.makeText(
                            MainActivity.this,
                            R.string.download_start_error,
                            Toast.LENGTH_LONG
                    ).show();
                }
            });
        });
    }
}
