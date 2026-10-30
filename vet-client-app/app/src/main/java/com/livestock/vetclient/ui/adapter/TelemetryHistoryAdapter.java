package com.livestock.vetclient.ui.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.livestock.vetclient.R;
import com.livestock.vetclient.data.model.TelemetryRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Adapter for rendering chronological telemetry logs in the NodeDetailActivity.
 */
public class TelemetryHistoryAdapter extends RecyclerView.Adapter<TelemetryHistoryAdapter.HistoryViewHolder> {

    private final List<TelemetryRecord> historyList = new ArrayList<>();

    public void updateData(List<TelemetryRecord> newHistory) {
        this.historyList.clear();
        if (newHistory != null) {
            this.historyList.addAll(newHistory);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_telemetry_history, parent, false);
        return new HistoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        TelemetryRecord record = historyList.get(position);
        holder.bind(record);
    }

    @Override
    public int getItemCount() {
        return historyList.size();
    }

    static class HistoryViewHolder extends RecyclerView.ViewHolder {

        private final TextView tvTimestamp;
        private final TextView tvStatusBadge;
        private final TextView tvTemp;
        private final TextView tvDistance;
        private final TextView tvAccDetails;

        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTimestamp = itemView.findViewById(R.id.history_tv_timestamp);
            tvStatusBadge = itemView.findViewById(R.id.history_tv_status_badge);
            tvTemp = itemView.findViewById(R.id.history_tv_temp);
            tvDistance = itemView.findViewById(R.id.history_tv_distance);
            tvAccDetails = itemView.findViewById(R.id.history_tv_acc_details);
        }

        public void bind(TelemetryRecord record) {
            Context ctx = itemView.getContext();

            tvTimestamp.setText(record.getFormattedDateTime());
            tvTemp.setText(String.format(Locale.US, "%.1f °C", record.getTemperature()));
            tvDistance.setText(String.format(Locale.US, "Dist: %.0fm", record.getDistance()));

            tvAccDetails.setText(String.format(
                    Locale.US,
                    "|a| = %.2f m/s²",
                    record.getAccelerationMagnitude()
            ));

            if (record.isStray() || record.getAnomalyFlag() == 2) {
                int warningColor = ContextCompat.getColor(ctx, R.color.status_warning);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(warningColor);
                tvStatusBadge.setText("STRAY");
                tvDistance.setTextColor(warningColor);
            } else if (record.getAnomalyFlag() == 1) {
                int redColor = ContextCompat.getColor(ctx, R.color.status_anomaly);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvStatusBadge.setText("ANOMALY");
                tvDistance.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            } else {
                int greenColor = ContextCompat.getColor(ctx, R.color.status_healthy);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
                tvStatusBadge.setTextColor(greenColor);
                tvStatusBadge.setText("NORMAL");
                tvDistance.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            }

            if (record.isFever()) {
                tvTemp.setTextColor(ContextCompat.getColor(ctx, R.color.status_anomaly));
            } else {
                tvTemp.setTextColor(ContextCompat.getColor(ctx, R.color.text_primary));
            }
        }
    }
}
