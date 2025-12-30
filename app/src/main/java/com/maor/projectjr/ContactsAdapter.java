package com.maor.projectjr;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.function.IntConsumer;

public class ContactsAdapter extends RecyclerView.Adapter<ContactsAdapter.VH> {

    private final List<String> data;
    private final IntConsumer onRemove;

    public ContactsAdapter(List<String> data, IntConsumer onRemove) {
        this.data = data;
        this.onRemove = onRemove;
    }

    @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_contact, parent, false);
        return new VH(v);
    }

    @Override public void onBindViewHolder(@NonNull VH h, int pos) {
        h.number.setText(data.get(pos));
        h.remove.setOnClickListener(v -> onRemove.accept(h.getBindingAdapterPosition()));
    }

    @Override public int getItemCount() { return data.size(); }

    static class VH extends RecyclerView.ViewHolder {
        TextView number; Button remove;
        VH(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.contact_number);
            remove = itemView.findViewById(R.id.remove_btn);
        }
    }
}
