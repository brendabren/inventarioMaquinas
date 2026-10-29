package br.edu.utfpr.inventariomaquinas.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.MarcaEntity;
import br.edu.utfpr.inventariomaquinas.databinding.ItemMarcaBinding;

public class MarcaAdapter
        extends RecyclerView.Adapter<MarcaAdapter.MarcaViewHolder> {

    private final List<MarcaEntity> items = new ArrayList<>();

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(MarcaEntity marca);
    }

    public MarcaAdapter(OnItemClickListener listener) {
        this.listener = listener;

        setStateRestorationPolicy(
                StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
    }

    public void submitList(List<MarcaEntity> marcas) {
        items.clear();

        if (marcas != null) {
            items.addAll(marcas);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MarcaViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemMarcaBinding binding = ItemMarcaBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new MarcaViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull MarcaViewHolder holder,
            int position
    ) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class MarcaViewHolder extends RecyclerView.ViewHolder {

        private final ItemMarcaBinding binding;

        MarcaViewHolder(ItemMarcaBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(MarcaEntity marca) {
            binding.textNome.setText(marca.nome);

            binding.textCodigo.setText(
                    binding.getRoot().getContext().getString(
                            R.string.record_local_id,
                            marca.localId
                    )
            );

            binding.getRoot().setOnClickListener(
                    view -> listener.onItemClick(marca)
            );
        }
    }
}