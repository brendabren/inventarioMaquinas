package br.edu.utfpr.inventariomaquinas.ui.sync;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import br.edu.utfpr.inventariomaquinas.R;

public class MachineReconcileActivity extends AppCompatActivity {

    private MachineReconcileViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setTitle(R.string.reconcile_title);

        viewModel = new ViewModelProvider(this)
                .get(MachineReconcileViewModel.class);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);

        int padding = Math.round(
                16 * getResources().getDisplayMetrics().density
        );

        content.setPadding(
                padding,
                padding,
                padding,
                padding
        );

        TextView record = new TextView(this);
        content.addView(record);

        TextView message = new TextView(this);
        message.setPadding(0, padding, 0, padding);
        content.addView(message);

        Button query = new Button(this);
        query.setText(R.string.reconcile_query);
        content.addView(query);

        Button create = new Button(this);
        create.setText(R.string.reconcile_create);
        content.addView(create);

        Spinner candidates = new Spinner(this);
        content.addView(candidates);

        Button link = new Button(this);
        link.setText(R.string.reconcile_link);
        content.addView(link);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);

        setContentView(scroll);

        query.setOnClickListener(view ->
                viewModel.queryCandidates()
        );

        create.setOnClickListener(view -> {
            MachineReconcileViewModel.State state =
                    viewModel.getState().getValue();

            if (state == null
                    || state.busy
                    || !state.canCreate) {
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.reconcile_create)
                    .setMessage(getString(
                            R.string.reconcile_create_question,
                            state.record
                    ))
                    .setNegativeButton(
                            R.string.action_cancel,
                            null
                    )
                    .setPositiveButton(
                            R.string.reconcile_create,
                            (dialog, which) ->
                                    viewModel.createRemote()
                    )
                    .show();
        });

        link.setOnClickListener(view -> {
            MachineReconcileViewModel.Candidate selected =
                    (MachineReconcileViewModel.Candidate)
                            candidates.getSelectedItem();

            if (selected == null) {
                return;
            }

            new AlertDialog.Builder(this)
                    .setTitle(R.string.reconcile_link)
                    .setMessage(getString(
                            R.string.reconcile_link_question,
                            selected.toString()
                    ))
                    .setNegativeButton(
                            R.string.action_cancel,
                            null
                    )
                    .setPositiveButton(
                            R.string.reconcile_link,
                            (dialog, which) ->
                                    viewModel.link(selected)
                    )
                    .show();
        });

        viewModel.getState().observe(this, state -> {
            record.setText(state.record);

            message.setText(
                    state.busy
                            ? getString(R.string.reconcile_working)
                            : state.message
            );

            ArrayAdapter<MachineReconcileViewModel.Candidate> adapter =
                    new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_spinner_item,
                            state.candidates
                    );

            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item
            );

            candidates.setAdapter(adapter);

            query.setEnabled(
                    !state.busy && state.hasItem
            );

            create.setEnabled(
                    !state.busy && state.canCreate
            );

            candidates.setEnabled(!state.busy);

            link.setEnabled(
                    !state.busy
                            && !state.candidates.isEmpty()
            );
        });
    }
}