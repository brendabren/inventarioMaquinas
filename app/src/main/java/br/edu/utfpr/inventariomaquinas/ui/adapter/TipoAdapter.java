package br.edu.utfpr.inventariomaquinas.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import br.edu.utfpr.inventariomaquinas.R;
import br.edu.utfpr.inventariomaquinas.data.local.entity.TipoEntity;
import br.edu.utfpr.inventariomaquinas.databinding.ItemTipoBinding;

public class TipoAdapter
        extends RecyclerView.Adapter<TipoAdapter.TipoViewHolder> {

    private final List<TipoEntity> items = new ArrayList<>();

    private final OnItemClickListener listener;

    public interface OnItemClickListener {
        void onItemClick(TipoEntity tipo);
    }

    public TipoAdapter(OnItemClickListener listener) {
        this.listener = listener;

        setStateRestorationPolicy(
                StateRestorationPolicy.PREVENT_WHEN_EMPTY
        );
    }

    public void submitList(List<TipoEntity> tipos) {
        items.clear();

        if (tipos != null) {
            items.addAll(tipos);
        }

        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TipoViewHolder onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType
    ) {
        ItemTipoBinding binding = ItemTipoBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );

        return new TipoViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(
            @NonNull TipoViewHolder holder,
            int position
    ) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    class TipoViewHolder extends RecyclerView.ViewHolder {

        private final ItemTipoBinding binding;

        TipoViewHolder(ItemTipoBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(TipoEntity tipo) {
            binding.textDescricao.setText(tipo.descricao);

            binding.textCodigo.setText(
                    binding.getRoot().getContext().getString(
                            R.string.record_local_id,
                            tipo.localId
                    )
            );

            binding.getRoot().setOnClickListener(
                    view -> listener.onItemClick(tipo)
            );
        }
    }
}