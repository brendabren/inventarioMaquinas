package br.edu.utfpr.inventariomaquinas.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.edu.utfpr.inventariomaquinas.data.local.dao.MarcaDao;
import br.edu.utfpr.inventariomaquinas.data.local.dao.MaquinaDao;
import br.edu.utfpr.inventariomaquinas.data.local.dao.TipoDao;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;

@Database(
        entities = {
                TipoEntity.class,
                MarcaEntity.class,
                MaquinaEntity.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    public static final ExecutorService databaseExecutor =
            Executors.newSingleThreadExecutor();

    public abstract TipoDao tipoDao();

    public abstract MarcaDao marcaDao();

    public abstract MaquinaDao maquinaDao();

    public static AppDatabase getInstance(Context context) {

        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "inventario_maquinas.db"
                    ).build();
                }
            }
        }

        return INSTANCE;
    }
}