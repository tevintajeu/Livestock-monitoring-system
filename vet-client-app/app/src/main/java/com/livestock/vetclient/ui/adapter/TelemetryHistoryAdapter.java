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
 * Adapts labels to active role (Plain-Language for Farmer vs Technical for Vet).
 */
public class TelemetryHistoryAdapter extends RecyclerView.Adapter<TelemetryHistoryAdapter.HistoryViewHolder> {

    private final List<TelemetryRecord> historyList = new ArrayList<>();
    private boolean isVetMode = false;

    public void setVetMode(boolean vetMode) {
        this.isVetMode = vetMode;
        notifyDataSetChanged();
    }

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
        holder.bind(record, isVetMode);
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

        public void bind(TelemetryRecord record, boolean isVetMode) {
            Context ctx = itemView.getContext();

            tvTimestamp.setText(record.getFormattedDateTime());
            tvTemp.setText("🌡️ " + record.getHumanTemperatureLabel());
            tvDistance.setText("📍 " + record.getHumanDistanceShortLabel());

            if (isVetMode) {
                tvAccDetails.setText(String.format(
                        Locale.US,
                        "Dyn: %.1f m/s²",
                        record.getDynamicAcceleration()
                ));
            } else {
                tvAccDetails.setText("🐾 " + record.getHumanMotionShortLabel());
            }

            int greenColor = ContextCompat.getColor(ctx, R.color.status_healthy);
            int amberColor = ContextCompat.getColor(ctx, R.color.status_warning);
            int redColor = ContextCompat.getColor(ctx, R.color.status_anomaly);
            int defaultText = ContextCompat.getColor(ctx, R.color.text_primary);

            if (record.isPredatorOrPanic()) {
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvStatusBadge.setText("DANGER");
                tvDistance.setTextColor(defaultText);
            } else if (record.isStray()) {
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvStatusBadge.setText("STRAYED");
                tvDistance.setTextColor(redColor);
            } else if (record.isFever()) {
                tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
                tvStatusBadge.setTextColor(amberColor);
                tvStatusBadge.setText("FEVER");
                tvDistance.setTextColor(defaultText);
            } else if (record.isCalvingDrop()) {
                tvStatusBadge.setBackgroundResource(R.drawable.badge_warning);
                tvStatusBadge.setTextColor(amberColor);
                tvStatusBadge.setText("CALVING");
                tvDistance.setTextColor(defaultText);
            } else {
                tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
                tvStatusBadge.setTextColor(greenColor);
                tvStatusBadge.setText("HEALTHY");
                tvDistance.setTextColor(defaultText);
            }

            if (record.isFever()) {
                tvTemp.setTextColor(redColor);
            } else if (record.isCalvingDrop()) {
                tvTemp.setTextColor(amberColor);
            } else {
                tvTemp.setTextColor(greenColor);
            }
        }
    }
}
