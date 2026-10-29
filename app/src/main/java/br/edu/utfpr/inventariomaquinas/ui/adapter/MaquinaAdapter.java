package br.edu.utfpr.inventariomaquinas.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MaquinaEntity;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.databinding.ItemMaquinaBinding;

public class MaquinaAdapter
        extends RecyclerView.Adapter<MaquinaAdapter.MaquinaViewHolder> {

    private final List<MaquinaEntity> items = new ArrayList<>();

    private final Map<Long, String> tipos = new HashMap<>();
    private final Map<Long, String> marcas = new HashMap<>();

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MaquinaEntity maquina);
    }

    public MaquinaAdapter(OnItemClickListener listener) {
        this.listener = listener;

        setStateRestorationPolicy(
                StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
    }

    public void submitList(List<MaquinaEntity> maquinas) {
        items.clear();

        if (maquinas != null) {
            items.addAll(maquinas);
        }

        notifyDataSetChanged();
    }

    public void submitTipos(List<TipoEntity> listaTipos) {
        tipos.clear();

        if (listaTipos != null) {
            for (TipoEntity tipo : listaTipos) {
                tipos.put(tipo.localId, tipo.descricao);
            }
        }

        notifyDataSetChanged();
    }

    public void submitMarcas(List<MarcaEntity> listaMarcas) {
        marcas.clear();

        if (listaMarcas != null) {
            for (MarcaEntity marca : listaMarcas) {
                marcas.put(marca.localId, marca.nome);
            }
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MaquinaViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemMaquinaBinding binding = ItemMaquinaBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new MaquinaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MaquinaViewHolder holder,
            int position
    ) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private String getStatusLabel(Context context, String status) {
        if ("D".equals(status)) {
            return context.getString(R.string.status_available);
        }

        if ("N".equals(status)) {
            return context.getString(R.string.status_negotiating);
        }

        if ("R".equals(status)) {
            return context.getString(R.string.status_reserved);
        }

        if ("V".equals(status)) {
            return context.getString(R.string.status_sold);
        }

        return context.getString(R.string.unknown_status);
    }

    private String formatValue(Context context, long centavos) {
        Locale locale = context.getResources()
                .getConfiguration()
                .getLocales()
                .get(0);

        NumberFormat formatter =
                NumberFormat.getCurrencyInstance(locale);

        formatter.setCurrency(Currency.getInstance("BRL"));
        formatter.setMinimumFractionDigits(2);
        formatter.setMaximumFractionDigits(2);

        BigDecimal value = BigDecimal.valueOf(centavos, 2);

        return formatter.format(value);
    }

    class MaquinaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMaquinaBinding binding;

        MaquinaViewHolder(ItemMaquinaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MaquinaEntity maquina) {
            Context context = binding.getRoot().getContext();

            String tipo = tipos.get(maquina.tipoLocalId);
            String marca = marcas.get(maquina.marcaLocalId);

            if (tipo == null) {
                tipo = context.getString(R.string.unknown_type);
            }

            if (marca == null) {
                marca = context.getString(R.string.unknown_brand);
            }

            binding.textDescricao.setText(maquina.descricao);

            binding.textTipoMarca.setText(
                    context.getString(
                            R.string.machine_type_brand,
                            tipo,
                            marca
                    )
            );

            binding.textAno.setText(
                    context.getString(
                            R.string.machine_year,
                            maquina.anoFabricacao
                    )
            );

            binding.textValor.setText(
                    formatValue(context, maquina.valorCentavos)
            );

            binding.textStatus.setText(
                    context.getString(
                            R.string.machine_status,
                            getStatusLabel(context, maquina.status)
                    )
            );

            binding.getRoot().setOnClickListener(
                    view -> listener.onItemClick(maquina)
            );
        }
    }
}