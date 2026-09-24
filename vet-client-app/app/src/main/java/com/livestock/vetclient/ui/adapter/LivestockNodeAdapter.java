package com.livestock.vetclient.ui.adapter;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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
 * RecyclerView Adapter displaying the triage cards for monitored livestock nodes.
 * Visual status is dynamically color-coded: Red for Anomaly = 1, Green for Anomaly = 0.
 */
public class LivestockNodeAdapter extends RecyclerView.Adapter<LivestockNodeAdapter.NodeViewHolder> {

    public interface OnNodeClickListener {
        void onNodeClick(TelemetryRecord record);
    }

    private final List<TelemetryRecord> nodeList = new ArrayList<>();
    private final OnNodeClickListener clickListener;

    public LivestockNodeAdapter(OnNodeClickListener clickListener) {
        this.clickListener = clickListener;
    }

    public void updateData(List<TelemetryRecord> newRecords) {
        this.nodeList.clear();
        if (newRecords != null) {
            this.nodeList.addAll(newRecords);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_livestock_node, parent, false);
        return new NodeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NodeViewHolder holder, int position) {
        TelemetryRecord record = nodeList.get(position);
        holder.bind(record, clickListener);
    }

    @Override
    public int getItemCount() {
        return nodeList.size();
    }

    static class NodeViewHolder extends RecyclerView.ViewHolder {

        private final View viewStatusAccent;
        private final TextView tvNodeId;
        private final TextView tvStatusBadge;
        private final TextView tvDiagnosticSummary;
        private final TextView tvTemperature;
        private final ImageView imgTempIcon;
        private final TextView tvAccMagnitude;
        private final TextView tvTimestamp;

        public NodeViewHolder(@NonNull View itemView) {
            super(itemView);
            viewStatusAccent = itemView.findViewById(R.id.view_status_accent_bar);
            tvNodeId = itemView.findViewById(R.id.tv_node_id);
            tvStatusBadge = itemView.findViewById(R.id.tv_status_badge);
            tvDiagnosticSummary = itemView.findViewById(R.id.tv_diagnostic_summary);
            tvTemperature = itemView.findViewById(R.id.tv_node_temperature);
            imgTempIcon = itemView.findViewById(R.id.img_temp_icon);
            tvAccMagnitude = itemView.findViewById(R.id.tv_node_acc_mag);
            tvTimestamp = itemView.findViewById(R.id.tv_node_timestamp);
        }

        public void bind(final TelemetryRecord record, final OnNodeClickListener listener) {
            Context ctx = itemView.getContext();

            // Bind Node ID
            tvNodeId.setText(record.getNodeId());

            // Bind Temperature
            tvTemperature.setText(String.format(Locale.US, "%.1f °C", record.getTemperature()));

            // Bind 3D Acceleration Magnitude: |a| = sqrt(x^2 + y^2 + z^2)
            double magnitude = record.getAccelerationMagnitude();
            tvAccMagnitude.setText(String.format(Locale.US, "%.2f m/s²", magnitude));

            // Bind Timestamp
            tvTimestamp.setText("Last Synced: " + record.getFormattedDateTime());

            // Triage Evaluation: Anomaly Flag = 1 (Red Alert) vs 0 (Green Healthy)
            boolean isAnomaly = (record.getAnomalyFlag() == 1);
            boolean isFever = record.isFever();

            if (isAnomaly) {
                // Color Code: RED ALERT
                int redColor = ContextCompat.getColor(ctx, R.color.status_anomaly);
                viewStatusAccent.setBackgroundColor(redColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_anomaly);
                tvStatusBadge.setTextColor(redColor);
                tvDiagnosticSummary.setTextColor(redColor);

                if (isFever && record.isHighAcceleration()) {
                    tvStatusBadge.setText("CRITICAL ALERT");
                    tvDiagnosticSummary.setText("Fever (>39.5°C) & High Motion Detected");
                } else if (isFever) {
                    tvStatusBadge.setText("FEVER ALERT");
                    tvDiagnosticSummary.setText("Elevated Body Temp (>39.5°C)");
                } else {
                    tvStatusBadge.setText("DISTRESS ALERT");
                    tvDiagnosticSummary.setText("High Acceleration Spike (Predator/Trauma)");
                }

            } else {
                // Color Code: GREEN HEALTHY
                int greenColor = ContextCompat.getColor(ctx, R.color.status_healthy);
                viewStatusAccent.setBackgroundColor(greenColor);
                tvStatusBadge.setBackgroundResource(R.drawable.badge_healthy);
                tvStatusBadge.setTextColor(greenColor);
                tvStatusBadge.setText(R.string.status_healthy_badge);
                tvDiagnosticSummary.setTextColor(greenColor);
                tvDiagnosticSummary.setText(R.string.normal_status_desc);
            }

            // Highlight fever on temperature widget
            if (isFever) {
                int redColor = ContextCompat.getColor(ctx, R.color.status_anomaly);
                tvTemperature.setTextColor(redColor);
                imgTempIcon.setImageTintList(ColorStateList.valueOf(redColor));
            } else {
                int defaultTextColor = ContextCompat.getColor(ctx, R.color.text_primary);
                int greenColor = ContextCompat.getColor(ctx, R.color.status_healthy);
                tvTemperature.setTextColor(defaultTextColor);
                imgTempIcon.setImageTintList(ColorStateList.valueOf(greenColor));
            }

            // Click listener for diagnostic detail
            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onNodeClick(record);
                }
            });
        }
    }
}
